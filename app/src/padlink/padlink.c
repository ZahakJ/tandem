/*
 * padlink: Tandem's controller passthrough helper (PadLink.kt). It reads one controller's evdev node and writes its
 * input, translated, into another input device (the handheld's built-in controller), so every game sees the
 * external pad as the built-in one. Tandem runs it through Shizuku (shell) or su and talks to it over stdin/stdout.
 *
 *   libpadlink.so <source node> <target node> <watch|link> [rule...]
 *
 * Rules (Tandem works them out from the key layouts, see PadMap.kt; codes are decimal):
 *   k<src>:<dst>            key to key
 *   a<src>:<dst>[:i][:f<n>] axis to axis, scaled between the two ranges; i inverts, f<n> is a dead zone around the
 *                           middle in source units
 *   t<src>:<dst>            axis (a trigger) to key: down past 30 % of its range, up below 20 %
 *   b<src>:<dst>            key to axis: the axis at its maximum while the key is down (a digital trigger)
 *   A<key>[,<key>...]       source keys that accept the offer while watching
 *
 * stdin: l link (grab the source and forward), w watch (let the source go), q quit. End of input (Tandem died or
 * let go) quits. stdout: "ready", "linked", "watching", "accept" (an accept key was let go while watching), "gone"
 * (the source disconnected), "error <what>".
 *
 * Linking grabs the source (EVIOCGRAB), so Android and the games stop seeing it as a second controller. Whenever
 * forwarding stops (w, q, end of input, a signal, the source going) every key it holds down on the target goes up
 * and every axis it moved goes back to rest before the grab is released, so nothing is ever left stuck.
 */
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <unistd.h>

#define MAX_OUT 4
#define QUEUE 64

struct axis_out { int abs; int key; int invert; };
struct axis_in { int count; int flat; struct axis_out out[MAX_OUT]; struct input_absinfo info; int have_info; };

static int key_map[KEY_CNT];
static int key_axis[KEY_CNT];
static unsigned char accept_key[KEY_CNT];
static struct axis_in axes[ABS_CNT];
static struct input_absinfo target_info[ABS_CNT];
static unsigned char target_has[ABS_CNT];

/* What this helper holds on the target, so stopping can put it all back. */
static unsigned char held_key[KEY_CNT];
static unsigned char moved_abs[ABS_CNT];
static int abs_value[ABS_CNT];
/* Source keys that were already down when linking began: their release is swallowed, their press never sent. */
static unsigned char stale[KEY_CNT];
/* Source keys seen going down while watching (only a full press accepts). */
static unsigned char watch_down[KEY_CNT];

static struct input_event queue[QUEUE];
static int queued;
static int src = -1, dst = -1;
static volatile sig_atomic_t stop_signal;

static void say(const char *s) {
    if (dprintf(STDOUT_FILENO, "%s\n", s) < 0) stop_signal = 1;
}

static int flush_queue(void) {
    if (!queued) return 0;
    struct input_event syn = { .type = EV_SYN, .code = SYN_REPORT, .value = 0 };
    queue[queued++] = syn;
    ssize_t want = (ssize_t)(queued * sizeof(struct input_event));
    ssize_t n = write(dst, queue, (size_t)want);
    queued = 0;
    return n == want ? 0 : -1;
}

static void push(int type, int code, int value) {
    if (queued >= QUEUE - 1) flush_queue();
    struct input_event e = { .type = (unsigned short)type, .code = (unsigned short)code, .value = value };
    queue[queued++] = e;
}

static void set_key(int key, int down) {
    if (key < 0 || key >= KEY_CNT || held_key[key] == down) return;
    held_key[key] = (unsigned char)down;
    push(EV_KEY, key, down);
}

static int rest_of(int abs) {
    const struct input_absinfo *t = &target_info[abs];
    return t->minimum < 0 ? (t->minimum + t->maximum) / 2 : t->minimum;
}

static void set_abs(int abs, int value) {
    if (abs < 0 || abs >= ABS_CNT || !target_has[abs]) return;
    if (moved_abs[abs] && abs_value[abs] == value) return;
    moved_abs[abs] = 1;
    abs_value[abs] = value;
    push(EV_ABS, abs, value);
}

/* Linear from the source range to the target's, rounded, with the dead zone and inversion applied first. */
static int scale(const struct axis_in *in, const struct axis_out *o, int v) {
    long long smin = in->info.minimum, smax = in->info.maximum;
    long long dmin = target_info[o->abs].minimum, dmax = target_info[o->abs].maximum;
    if (smax <= smin) return v;
    if (o->invert) v = (int)(smax - (v - smin));
    long long mid2 = smin + smax; /* twice the middle, so odd ranges stay exact */
    if (in->flat > 0 && llabs(2LL * v - mid2) <= 2LL * in->flat) return (int)((dmin + dmax) / 2);
    long long num = (v - smin) * (dmax - dmin);
    long long den = smax - smin;
    long long r = dmin + (num + den / 2) / den;
    if (r < dmin) r = dmin;
    if (r > dmax) r = dmax;
    return (int)r;
}

/* How far an axis is pulled, 0..1000, from its resting end (for triggers). */
static int pulled(const struct axis_in *in, int v, int invert) {
    long long smin = in->info.minimum, smax = in->info.maximum;
    if (smax <= smin) return v ? 1000 : 0;
    if (invert) v = (int)(smax - (v - smin));
    return (int)((v - smin) * 1000 / (smax - smin));
}

static void on_abs(int code, int v) {
    struct axis_in *in = &axes[code];
    for (int i = 0; i < in->count; i++) {
        const struct axis_out *o = &in->out[i];
        if (o->abs >= 0) set_abs(o->abs, scale(in, o, v));
        if (o->key >= 0) {
            int p = pulled(in, v, o->invert);
            if (!held_key[o->key] && p >= 300) set_key(o->key, 1);
            else if (held_key[o->key] && p < 200) set_key(o->key, 0);
        }
    }
}

static void on_key(int code, int down) {
    if (stale[code]) {
        if (!down) stale[code] = 0;
        return;
    }
    if (key_map[code] >= 0) set_key(key_map[code], down);
    int a = key_axis[code];
    if (a >= 0 && target_has[a]) set_abs(a, down ? target_info[a].maximum : rest_of(a));
}

/* Everything this helper pressed or moved on the target goes back, in one frame. */
static void release_all(void) {
    for (int k = 0; k < KEY_CNT; k++) if (held_key[k]) set_key(k, 0);
    for (int a = 0; a < ABS_CNT; a++) if (moved_abs[a] && abs_value[a] != rest_of(a)) set_abs(a, rest_of(a));
    flush_queue();
    memset(moved_abs, 0, sizeof moved_abs);
}

/* Reads the source's current axes into the target (after linking or lost events). */
static void sync_axes(void) {
    for (int a = 0; a < ABS_CNT; a++) {
        if (!axes[a].count) continue;
        struct input_absinfo now;
        if (ioctl(src, EVIOCGABS(a), &now) == 0) on_abs(a, now.value);
    }
}

/* After the kernel dropped events: catch up with what the source holds now. */
static void resync(void) {
    unsigned char bits[KEY_CNT / 8 + 1];
    memset(bits, 0, sizeof bits);
    if (ioctl(src, EVIOCGKEY(sizeof bits), bits) >= 0) {
        for (int k = 0; k < KEY_CNT; k++) {
            if (key_map[k] < 0 && key_axis[k] < 0) continue;
            int down = (bits[k / 8] >> (k % 8)) & 1;
            if (stale[k] && !down) stale[k] = 0;
            if (!stale[k]) on_key(k, down);
        }
    }
    sync_axes();
    flush_queue();
}

static int linked;

static int do_link(void) {
    if (linked) return 0;
    if (ioctl(src, EVIOCGRAB, (void *)1) != 0) { say("error grab"); return -1; }
    linked = 1;
    /* Keys already down (the press that accepted, say) stay out until they are let go. */
    unsigned char bits[KEY_CNT / 8 + 1];
    memset(bits, 0, sizeof bits);
    memset(stale, 0, sizeof stale);
    if (ioctl(src, EVIOCGKEY(sizeof bits), bits) >= 0)
        for (int k = 0; k < KEY_CNT; k++) stale[k] = (bits[k / 8] >> (k % 8)) & 1;
    sync_axes();
    flush_queue();
    say("linked");
    return 0;
}

static void do_unlink(void) {
    if (!linked) return;
    release_all();
    ioctl(src, EVIOCGRAB, (void *)0);
    linked = 0;
}

static void parse_rule(const char *r) {
    int a = -1, b = -1;
    switch (r[0]) {
    case 'k':
        if (sscanf(r + 1, "%d:%d", &a, &b) == 2 && a >= 0 && a < KEY_CNT && b >= 0 && b < KEY_CNT) key_map[a] = b;
        break;
    case 'b':
        if (sscanf(r + 1, "%d:%d", &a, &b) == 2 && a >= 0 && a < KEY_CNT && b >= 0 && b < ABS_CNT) key_axis[a] = b;
        break;
    case 'a':
    case 't':
        if (sscanf(r + 1, "%d:%d", &a, &b) != 2 || a < 0 || a >= ABS_CNT || b < 0) break;
        if (r[0] == 'a' && b >= ABS_CNT) break;
        if (r[0] == 't' && b >= KEY_CNT) break;
        if (axes[a].count >= MAX_OUT) break;
        struct axis_out o = { .abs = r[0] == 'a' ? b : -1, .key = r[0] == 't' ? b : -1, .invert = 0 };
        for (const char *p = strchr(r + 1, ':'); p && (p = strchr(p + 1, ':')); ) {
            if (p[1] == 'i') o.invert = 1;
            if (p[1] == 'f') axes[a].flat = atoi(p + 2);
        }
        axes[a].out[axes[a].count++] = o;
        break;
    case 'A':
        for (const char *p = r + 1; *p; ) {
            int k = atoi(p);
            if (k >= 0 && k < KEY_CNT) accept_key[k] = 1;
            const char *c = strchr(p, ',');
            if (!c) break;
            p = c + 1;
        }
        break;
    }
}

static void on_signal(int sig) { (void)sig; stop_signal = 1; }

int main(int argc, char **argv) {
    if (argc < 4) {
        fprintf(stderr, "usage: %s <source> <target> <watch|link> [rule...]\n", argv[0]);
        return 2;
    }
    for (int i = 0; i < KEY_CNT; i++) { key_map[i] = -1; key_axis[i] = -1; }
    for (int i = 4; i < argc; i++) parse_rule(argv[i]);

    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_handler = on_signal;
    sigaction(SIGTERM, &sa, NULL);
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGHUP, &sa, NULL);
    signal(SIGPIPE, SIG_IGN);
    /* Input goes straight through: ahead of ordinary work when allowed (root), harmless when not. */
    setpriority(PRIO_PROCESS, 0, -10);

    src = open(argv[1], O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (src < 0) { say("error source"); return 3; }
    dst = open(argv[2], O_WRONLY | O_CLOEXEC);
    if (dst < 0) { say("error target"); return 3; }
    for (int a = 0; a < ABS_CNT; a++) {
        if (ioctl(dst, EVIOCGABS(a), &target_info[a]) == 0 && target_info[a].maximum > target_info[a].minimum) target_has[a] = 1;
        if (axes[a].count && ioctl(src, EVIOCGABS(a), &axes[a].info) == 0 && axes[a].info.maximum > axes[a].info.minimum) axes[a].have_info = 1;
        if (axes[a].count && !axes[a].have_info) axes[a].count = 0;
    }
    say("ready");
    if (strcmp(argv[3], "link") == 0 && do_link() != 0) return 4;
    if (!linked) say("watching");

    int dropped = 0, code = 0;
    struct input_event ev[64];
    char cmd[64];
    while (!stop_signal) {
        struct pollfd fds[2] = { { .fd = src, .events = POLLIN }, { .fd = STDIN_FILENO, .events = POLLIN } };
        if (poll(fds, 2, -1) < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (fds[1].revents) {
            ssize_t n = read(STDIN_FILENO, cmd, sizeof cmd);
            if (n <= 0) break; /* Tandem is gone, or let go */
            int quit = 0;
            for (ssize_t i = 0; i < n; i++) {
                if (cmd[i] == 'l' && do_link() != 0) quit = 1;
                if (cmd[i] == 'w' && linked) { do_unlink(); memset(watch_down, 0, sizeof watch_down); say("watching"); }
                if (cmd[i] == 'q') quit = 1;
            }
            if (quit) break;
        }
        if (fds[0].revents & (POLLERR | POLLHUP | POLLNVAL)) { code = 1; break; }
        if (!(fds[0].revents & POLLIN)) continue;
        ssize_t n = read(src, ev, sizeof ev);
        if (n <= 0) {
            if (n < 0 && (errno == EAGAIN || errno == EINTR)) continue;
            code = 1; /* ENODEV: unplugged or switched off */
            break;
        }
        for (size_t i = 0; i < (size_t)n / sizeof ev[0]; i++) {
            const struct input_event *e = &ev[i];
            if (e->type == EV_SYN && e->code == SYN_DROPPED) { dropped = 1; continue; }
            if (e->type == EV_SYN && e->code == SYN_REPORT) {
                if (dropped) { dropped = 0; if (linked) resync(); continue; }
                if (linked && flush_queue() != 0) { code = 2; break; }
                continue;
            }
            if (dropped) continue;
            if (!linked) {
                if (e->type == EV_KEY && e->code < KEY_CNT && accept_key[e->code]) {
                    if (e->value == 1) watch_down[e->code] = 1;
                    else if (e->value == 0 && watch_down[e->code]) { watch_down[e->code] = 0; say("accept"); }
                }
                continue;
            }
            if (e->type == EV_KEY && e->code < KEY_CNT && e->value != 2) on_key(e->code, e->value != 0);
            else if (e->type == EV_ABS && e->code < ABS_CNT && axes[e->code].count) on_abs(e->code, e->value);
        }
        if (code) break;
    }
    do_unlink();
    if (code == 1) say("gone");
    if (code == 2) say("error target");
    return code == 2 ? 5 : 0;
}
