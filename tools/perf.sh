#!/usr/bin/env bash
# Measures Tandem on a connected device and checks the targets in docs/PERFORMANCE.md.
#
#   ADB=adb SERIAL=<serial> tools/perf.sh            # every step
#   STEPS="cold dpad" tools/perf.sh                  # only some steps
#
# Steps: cold (cold start), home (return from an app), dpad (D-pad hold on the grid), idle (idle CPU), mem (PSS),
# apk (APK size). Prints a table and writes the results as JSON (OUT, default perf-result.json).
# Install the release build first (docs/PERFORMANCE.md › Measuring); Tandem should be the Home app, on the home
# screen with the icon grid. The D-pad step needs root (su) to inject controller input.
set -u

ADB=${ADB:-adb}
SERIAL=${SERIAL:-}
PKG=${PKG:-io.github.zahakj.tandem}
ACTIVITY=${ACTIVITY:-$PKG/.MainActivity}
STEPS=${STEPS:-cold home dpad idle mem apk}
OUT=${OUT:-perf-result.json}
RUNS=${RUNS:-5}
# The app the Home-return step opens and leaves (any light app).
OTHER_APP=${OTHER_APP:-com.android.settings}
# Controller input for the D-pad step: the device node and DECIMAL key codes (547 = BTN_DPAD_RIGHT).
INPUT_DEV=${INPUT_DEV:-/dev/input/event9}
KEY_RIGHT=${KEY_RIGHT:-547}
HOLD_S=${HOLD_S:-5}
# Idle: measured from the last input, within a minute of it (a screensaver set to a minute would cut in later).
IDLE_SETTLE_S=${IDLE_SETTLE_S:-12}
IDLE_WINDOW_S=${IDLE_WINDOW_S:-40}
# Home returns measured (the worst one is reported).
HOME_RUNS=${HOME_RUNS:-3}
APK=${APK:-$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk}

adb_() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }
sh_() { adb_ shell "$@" | tr -d '\r'; }
log() { echo "[perf] $*" >&2; }
has() { case " $STEPS " in *" $1 "*) return 0;; esac; return 1; }

declare -A R   # results: key -> number (or null)
put() { R[$1]=${2:-null}; }

median() { sort -n | awk '{a[NR]=$1} END {if (NR == 0) print "null"; else if (NR % 2) print a[(NR+1)/2]; else print (a[NR/2]+a[NR/2+1])/2}'; }

# A logcat timestamp "now" on the device, for reading only the lines written after it.
log_mark() { sh_ "date +'%m-%d %H:%M:%S.000'"; }

top_activity() { sh_ "dumpsys activity activities | grep -m1 topResumedActivity"; }

# Frame statistics since the last reset, summed over Tandem's windows.
gfx_summary() {
    sh_ "dumpsys gfxinfo $PKG" | awk '
        /Total frames rendered:/ { t += $4 }
        /Janky frames:/ && !/legacy/ { j += $3 }
        /^50th percentile:/ { if ($3+0 > p50) p50 = $3+0 }
        /^90th percentile:/ { if ($3+0 > p90) p90 = $3+0 }
        /^95th percentile:/ { if ($3+0 > p95) p95 = $3+0 }
        /^99th percentile:/ { if ($3+0 > p99) p99 = $3+0 }
        /Number Slow UI thread:/ { ui += $5 }
        END { printf "%d %d %s %s %s %s %d\n", t, j, p50+0, p90+0, p95+0, p99+0, ui }'
}

# Per-frame durations in ms (FrameCompleted - IntendedVsync) from framestats, all of Tandem's windows (the top
# screen, its bottom screen and the bottom-screen home), counted from the top screen's first frame (the bottom-screen
# home may have drawn before it). Frames that changed the window's layout (flags 1, the first one back) count too.
# Prints "<ms since the first frame> <duration ms>" per frame.
frame_times() {
    sh_ "dumpsys gfxinfo $PKG framestats" | awk -F, '
        /ViewRootImpl/ && !/PROFILEDATA/ { top = ($0 ~ /MainActivity/) }
        /^---PROFILEDATA---/ { inblk = !inblk; hdr = 0; next }
        inblk && /^Flags/ { for (i = 1; i <= NF; i++) col[$i] = i; hdr = 1; next }
        inblk && hdr && ($1 == "0" || $1 == "1") {
            iv = $(col["IntendedVsync"]); fc = $(col["FrameCompleted"])
            if (fc > 0 && iv > 0) { n++; v[n] = iv; d[n] = fc - iv; if (top && (s == 0 || iv < s)) s = iv }
        }
        END { for (i = 1; i <= n; i++) if (v[i] >= s) printf "%.1f %.2f\n", (v[i] - s) / 1e6, d[i] / 1e6 }' | sort -n
}

cpu_ticks() { sh_ "cat /proc/$1/stat" | sed 's/.*) //' | awk '{print $12 + $13}'; }

go_home() { sh_ "input keyevent KEYCODE_HOME" >/dev/null; }

# ---- cold start -------------------------------------------------------------------------------------------------
if has cold; then
    log "cold start, $RUNS runs"
    times=(); homes=()
    for i in $(seq 1 "$RUNS"); do
        mark=$(log_mark)
        before=$(sh_ "pidof $PKG")
        out=$(sh_ "am start -S -W -n $ACTIVITY")
        state=$(echo "$out" | awk '/LaunchState:/ {print $2}')
        t=$(echo "$out" | awk '/TotalTime:/ {print $2}')
        # Some builds of Android keep the Home app alive through force-stop: then the process is killed instead,
        # and Android starts it again at once (a cold start without am's numbers).
        if [ -n "$before" ] && [ "$(sh_ "pidof $PKG")" = "$before" ]; then
            sh_ "su -c 'kill -9 $before'" >/dev/null
            t=""; state="killed"
        fi
        sleep 6
        # Tandem logs the time from the process start to the first frame drawn with the home (TandemPerf).
        h=$(sh_ "logcat -d -T '$mark' -s TandemPerf:I" | grep -m1 'home drawn' | sed -E 's/.*home drawn ([0-9]+) ms.*/\1/')
        log "  run $i: TotalTime=${t:-?} ms state=${state:-?} home-drawn=${h:-?} ms"
        if [ -n "$t" ] && [ "$state" = COLD ]; then times+=("$t"); fi
        # After a kill Android may bring the process back for a service first, so only real cold starts count.
        if [ -n "$h" ] && [ "$state" != killed ]; then homes+=("$h"); fi
        sleep 2
    done
    put cold_start_ms "$(printf '%s\n' "${times[@]}" | grep . | median)"
    put cold_home_ms "$(printf '%s\n' "${homes[@]}" | grep . | median)"
    put cold_runs "${#homes[@]}"
    sleep 3
fi

# ---- return from an app -----------------------------------------------------------------------------------------
if has home; then
    log "Home return from $OTHER_APP"
    slow=0; worst=0; frames=0
    for i in $(seq 1 "$HOME_RUNS"); do
        sh_ "monkey -p $OTHER_APP -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1
        sleep 3
        sh_ "dumpsys gfxinfo $PKG reset" >/dev/null
        [ "$i" = 1 ] && mark=$(log_mark)
        go_home
        sleep 1.2
        ft=$(frame_times)
        s=$(echo "$ft" | awk '$1 <= 500 && $2 > 16.7' | grep -c .)
        w=$(echo "$ft" | awk '$1 <= 500 { if ($2 > m) m = $2 } END { print m + 0 }')
        log "  run $i: $s frames over 16.7 ms, worst $w ms"
        if [ "$s" -gt "$slow" ]; then slow=$s; fi
        worst=$(awk -v a="$worst" -v b="$w" 'BEGIN { print (b > a) ? b : a }')
        frames=$(echo "$ft" | awk '$1 <= 500' | grep -c .)
    done
    put home_frames_500ms "$frames"
    put home_slow_frames_500ms "$slow"
    put home_worst_frame_ms "$worst"
    # Tandem rescans changed folders a moment after coming back; it logs how long that took (TandemPerf).
    scan=""
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        scan=$(sh_ "logcat -d -T '$mark' -s TandemPerf:I" | grep -m1 'scan [0-9]* ms' | sed -E 's/.*scan ([0-9]+) ms.*/\1/')
        [ -n "$scan" ] && break
        sleep 1
    done
    put rescan_ms "${scan:-null}"
fi

# ---- D-pad hold on the grid ---------------------------------------------------------------------------------------
if has dpad; then
    log "D-pad Right held ${HOLD_S}s on $INPUT_DEV"
    go_home; sleep 2; go_home; sleep 2   # the second Home goes back to the start of the grid
    sh_ "dumpsys gfxinfo $PKG reset" >/dev/null
    ev="sendevent $INPUT_DEV"
    sh_ "su -c '$ev 1 $KEY_RIGHT 1; $ev 0 0 0; sleep $HOLD_S; $ev 1 $KEY_RIGHT 0; $ev 0 0 0'"
    sleep 0.5
    read -r t j p50 p90 p95 p99 ui <<<"$(gfx_summary)"
    put dpad_frames "$t"
    put dpad_janky_pct "$(awk -v j="$j" -v t="$t" 'BEGIN { if (t > 0) printf "%.2f", 100 * j / t; else print "null" }')"
    put dpad_p50_ms "$p50"; put dpad_p90_ms "$p90"; put dpad_p95_ms "$p95"; put dpad_p99_ms "$p99"
    put dpad_slow_ui "$ui"
    sleep 1; go_home; sleep 2
fi

# ---- idle CPU -----------------------------------------------------------------------------------------------------
if has idle; then
    log "idle CPU: settle ${IDLE_SETTLE_S}s after Home, then measure ${IDLE_WINDOW_S}s"
    go_home
    sleep "$IDLE_SETTLE_S"
    pid=$(sh_ "pidof $PKG" | awk '{print $1}')
    hz=$(sh_ "getconf CLK_TCK"); hz=${hz:-100}
    sh_ "dumpsys gfxinfo $PKG reset" >/dev/null
    a=$(cpu_ticks "$pid"); s0=$(date +%s.%N)
    sleep "$IDLE_WINDOW_S"
    b=$(cpu_ticks "$pid"); s1=$(date +%s.%N)
    put idle_cpu_pct "$(awk -v a="$a" -v b="$b" -v hz="$hz" -v s0="$s0" -v s1="$s1" 'BEGIN { printf "%.2f", 100 * (b - a) / hz / (s1 - s0) }')"
    read -r t j _ _ _ _ ui <<<"$(gfx_summary)"
    put idle_frames "$t"; put idle_janky "$j"
    log "  front at the end: $(top_activity)"
fi

# ---- memory -------------------------------------------------------------------------------------------------------
if has mem; then
    pss=$(sh_ "dumpsys meminfo $PKG" | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}')
    put pss_mb "$(awk -v k="$pss" 'BEGIN { if (k > 0) printf "%.1f", k / 1024; else print "null" }')"
fi

# ---- APK size -----------------------------------------------------------------------------------------------------
if has apk; then
    path=$(sh_ "pm path $PKG" | sed -n 's/^package://p' | head -1)
    inst=$([ -n "$path" ] && sh_ "stat -c %s $path")
    put apk_installed_mb "$(awk -v b="${inst:-0}" 'BEGIN { if (b > 0) printf "%.2f", b / 1048576; else print "null" }')"
    if [ -f "$APK" ]; then put apk_local_mb "$(awk -v b="$(stat -c %s "$APK")" 'BEGIN { printf "%.2f", b / 1048576 }')"; fi
fi

# ---- report -------------------------------------------------------------------------------------------------------
# key | label | target (a test on the value v, as awk) | target text
targets=(
    "cold_start_ms|Cold start (TotalTime, median)|v <= 700|<= 700 ms"
    "cold_home_ms|Cold start (home drawn, median)|v <= 700|<= 700 ms"
    "home_slow_frames_500ms|Home return: frames > 16.7 ms (worst run)|v == 0|0"
    "home_worst_frame_ms|Home return: worst frame (ms)||"
    "rescan_ms|Unchanged rescan (ms)|v <= 250|<= 250 ms"
    "dpad_janky_pct|D-pad hold: janky %|v < 1|< 1 %"
    "dpad_p50_ms|D-pad hold: p50 (ms)||"
    "dpad_p90_ms|D-pad hold: p90 (ms)||"
    "dpad_p95_ms|D-pad hold: p95 (ms)|v <= 8.3|<= 8.3 ms"
    "dpad_p99_ms|D-pad hold: p99 (ms)||"
    "dpad_slow_ui|D-pad hold: slow UI thread frames||"
    "idle_cpu_pct|Idle CPU (% of one core)|v < 3|< 3 % still, < 15 % animated"
    "idle_janky|Idle: janky frames||"
    "pss_mb|PSS (MB)|v <= 220|<= 220 MB"
    "apk_installed_mb|APK installed (MB)||"
    "apk_local_mb|APK built (MB)||"
)
printf '\n%-42s %10s  %-28s %s\n' "Measure" "Value" "Target" ""
json="{"
for row in "${targets[@]}"; do
    IFS='|' read -r key label test text <<<"$row"
    [ -n "${R[$key]+x}" ] || continue
    v=${R[$key]}
    mark=""
    if [ -n "$test" ] && [ "$v" != null ]; then
        mark=$(awk -v v="$v" "BEGIN { print ($test) ? \"ok\" : \"MISS\" }")
    fi
    printf '%-44s %10s  %-28s %s\n' "$label" "$v" "$text" "$mark"
    json+="\"$key\": $v, "
done
json+="\"device\": \"$(sh_ "getprop ro.product.model")\", \"time\": \"$(date -Iseconds)\"}"
echo "$json" >"$OUT"
log "wrote $OUT"
