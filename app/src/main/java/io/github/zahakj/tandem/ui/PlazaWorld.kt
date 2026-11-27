package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.pass.AvatarSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The Plaza's little world: where everyone stands and walks, and what they are up to. Plain numbers and no drawing
 * (PlazaArt.kt draws it), stepped once a frame by PlazaScreen with the time since the last one.
 *
 * The square is seen from the front and a little above: x runs across it (-1 left .. 1 right at the front), z from
 * the back (0) to the front (1). Distances on the ground are in x units with z counted as [DEPTH] of one, so a circle on
 * the ground is round, not as squashed as it looks from the camera. The fountain stands at the back in the middle;
 * the owner's own character stands at the front a little to the left, and everyone keeps clear of both.
 *
 * Walking is steering, not tweening: each walker heads for a spot, slows as it gets there, eases into its turns and
 * goes round the fountain and round other people, so paths curve and nobody slides through anyone. The stride follows
 * the ground covered, so feet never skate.
 */
class PlazaWorld(seed: Long = 7L) {

    /** Someone on the square: the owner ([isMe]) or a visitor. */
    class Walker internal constructor(val key: String, var spec: AvatarSpec, internal val seed: Int, val isMe: Boolean) {
        var x = 0f; internal set
        var z = 0f; internal set
        internal var vx = 0f
        internal var vz = 0f
        internal var tx = 0f
        internal var tz = 0f

        /** What they are doing ([Act]) and how long it has left. */
        var act = Act.Idle; internal set
        internal var timer = 0f

        // The pose, smoothed: everything eases in and out.
        var turn = 0f; internal set
        internal var turnV = 0f
        internal var turnTo = 0f
        var walk = 0f; internal set
        var phase = 0f; internal set
        var wave = 0f; internal set
        internal var waveTo = 0f
        var lookX = 0f; internal set
        internal var lookTo = 0f
        var sit = 0f; internal set
        var cheer = 0f; internal set
        var blink = 0f; internal set
        internal var blinkIn = 2f
        internal var blinkAge = 1f

        /** A hop: height above the ground (in character heights) and its speed; [squash] for the landing. */
        var hop = 0f; internal set
        internal var hopV = 0f
        var squash = 0f; internal set
        internal var squashV = 0f

        /** A thought bubble: what it shows ([Emote]), how long it has left, and how long it has shown. */
        var emote = -1; internal set
        var emoteLeft = 0f; internal set
        var emoteAge = 0f; internal set

        /** 0..1 fading in on arrival. */
        var shown = 1f; internal set

        var fresh = false
        var focused = false; internal set
        internal var focusAge = 0f
        internal var partner: Walker? = null
        internal var seat = -1
        internal var leadChat = false
        internal var since = 0f
        internal var stuck = 0f
        internal var talkedAt = 0f

        /** The pose PlazaArt fills in and draws; kept, so a frame makes nothing new. */
        val pose = AvatarPose()
    }

    object Act {
        const val Idle = 0
        const val Walk = 1
        const val Wave = 2
        const val ToChat = 3
        const val Chat = 4
        const val ToSeat = 5
        const val Sit = 6
        const val Enter = 7
        const val Focus = 8
    }

    /** The little pictures in thought bubbles while two people chat. */
    object Emote {
        const val Heart = 0
        const val Note = 1
        const val Star = 2
        const val Dots = 3
        const val Bang = 4
        const val Pad = 5
        const val Smile = 6
        const val COUNT = 7
    }

    private val rnd = java.util.Random(seed)
    private val list = ArrayList<Walker>()
    private var me: Walker? = null
    private var started = false
    private var clock = 0f

    /** Everyone, the owner included (in no order; [order] sorts back to front). */
    val walkers: List<Walker> get() = list

    /** Indices into [walkers], back to front, as of the last [step] or [arrange]. */
    var order = IntArray(0); private set
    var count = 0; private set

    /**
     * Puts the crowd on the square: [specs] by [keys] (the newest first), the owner as [meSpec]; [fresh] marks the new
     * ones (they hop now and then). People already there stay where they are; on the first call everyone is placed
     * about the square, and later newcomers walk in from the nearer edge. Someone gone simply leaves.
     */
    fun sync(meSpec: AvatarSpec, keys: List<String>, specs: List<AvatarSpec>, fresh: List<Boolean>) {
        val owner = me ?: Walker(ME, meSpec, 0x5eed, isMe = true).also { w ->
            w.x = OWNER_X; w.z = OWNER_Z; w.tx = w.x; w.tz = w.z
            me = w; list += w
        }
        owner.spec = meSpec
        list.removeAll { !it.isMe && it.key !in keys }
        for (i in keys.indices) {
            val k = keys[i]
            val known = list.firstOrNull { it.key == k }
            if (known != null) { known.spec = specs[i]; known.fresh = fresh[i]; continue }
            val w = Walker(k, specs[i], k.hashCode(), isMe = false)
            w.fresh = fresh[i]
            w.blinkIn = 1f + rnd.nextFloat() * 3f
            if (!started) {
                placeFree(w)
                w.act = Act.Idle
                w.timer = 0.3f + rnd.nextFloat() * 2.5f
                w.turn = (rnd.nextFloat() - 0.5f) * 0.8f
                w.turnTo = w.turn
            } else {
                // Walks in from the nearer side, a little off screen, fading in as it comes.
                val left = rnd.nextBoolean()
                w.z = 0.25f + rnd.nextFloat() * 0.55f
                w.x = if (left) -EDGE_IN else EDGE_IN
                w.shown = 0f
                w.turn = if (left) 1f else -1f
                w.turnTo = w.turn
                w.act = Act.Enter
                pickSpot(w, if (left) -0.55f else 0.55f)
            }
            list += w
        }
        started = true
        // Mostly the same people in the same order: grow the order only when needed.
        if (order.size < list.size) order = IntArray(list.size + 8)
        count = list.size
        sortOrder()
    }

    /** The highlighted visitor ([key] null for none): it stops, turns to us and waves; the owner looks its way. */
    fun focus(key: String?) {
        for (w in list) {
            val on = !w.isMe && w.key == key
            if (on == w.focused) continue
            w.focused = on
            if (on) {
                endChat(w)
                leaveSeat(w)
                w.act = Act.Focus
                w.focusAge = 0f
                // Hidden behind the owner: a step out to the side first.
                stepClear(w)
                w.cheer = 1f
                if (w.hop <= 0f) w.hopV = HOP_V * 0.8f
                w.emote = -1
            } else if (w.act == Act.Focus) {
                w.act = Act.Idle
                w.timer = 0.8f + rnd.nextFloat()
                w.waveTo = 0f
            }
        }
    }

    /**
     * A calm, still arrangement (Reduce motion): everyone standing in loose rows in front of the fountain, facing us,
     * the highlighted one waving. Nothing moves until [step] runs again.
     */
    fun arrange() {
        var i = 0
        val visitors = list.count { !it.isMe }
        for (w in list) {
            w.vx = 0f; w.vz = 0f; w.walk = 0f; w.hop = 0f; w.hopV = 0f; w.squash = 0f; w.squashV = 0f
            w.emote = -1; w.sit = 0f; w.shown = 1f; w.blink = 0f; w.cheer = 0f
            w.partner = null; w.seat = -1
            if (w.isMe) {
                w.x = OWNER_X; w.z = OWNER_Z; w.turn = 0.25f; w.lookX = 0.3f; w.wave = 0f
                continue
            }
            val (x, z) = stillSpot(i, visitors)
            w.x = x; w.z = z; w.tx = x; w.tz = z
            w.turn = (-x * 0.35f).coerceIn(-0.35f, 0.35f)
            w.lookX = w.turn
            w.wave = if (w.focused) 1f else 0f
            w.act = if (w.focused) Act.Focus else Act.Idle
            w.timer = 2f
            i++
        }
        sortOrder()
    }

    /** Spots for the still arrangement: two loose arcs round the front of the fountain, clear of the owner. */
    private fun stillSpot(i: Int, n: Int): Pair<Float, Float> {
        val spots = STILL_SPOTS
        if (i < spots.size / 2) return spots[i * 2] to spots[i * 2 + 1]
        // More than there are spots: a third row along the back, spread evenly.
        val j = i - spots.size / 2
        val extra = (n - spots.size / 2).coerceAtLeast(1)
        return (-0.85f + 1.7f * (j + 0.5f) / extra) to 0.08f
    }

    /** Moves everyone on by [dt] seconds. */
    fun step(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.05f)
        if (dt <= 0f) return
        clock += dt
        for (w in list) think(w, dt)
        for (w in list) move(w, dt)
        separate()
        for (w in list) animate(w, dt)
        sortOrder()
    }

    // --- Deciding what to do ---------------------------------------------------------------------------------------

    private fun think(w: Walker, dt: Float) {
        w.since += dt
        if (w.isMe) {
            // The owner stays put, looks at whoever is highlighted (or about), and waves back at a waver now and then.
            val f = list.firstOrNull { it.focused }
            w.timer -= dt
            if (f != null) {
                w.turnTo = ((f.x - w.x) * 1.6f).coerceIn(-0.75f, 0.75f)
                w.lookTo = w.turnTo
            } else if (w.timer <= 0f) {
                w.timer = 2.5f + rnd.nextFloat() * 3f
                w.turnTo = (rnd.nextFloat() - 0.5f) * 0.9f
                w.lookTo = w.turnTo + (rnd.nextFloat() - 0.5f) * 0.4f
            }
            val waver = list.firstOrNull { !it.isMe && it.act == Act.Wave && it.timer > 0.6f }
            w.waveTo = if (waver != null && f == null) 1f else 0f
            return
        }
        w.timer -= dt
        when (w.act) {
            Act.Idle -> {
                w.waveTo = 0f
                // Never left standing where the owner hides them.
                if (behindMe(w.x, w.z)) { pickSpot(w, w.x); w.act = Act.Walk; return }
                // Glances about while standing.
                if (rnd.nextFloat() < dt * 0.6f) {
                    w.turnTo = (rnd.nextFloat() - 0.5f) * 1.2f
                    w.lookTo = w.turnTo * 0.7f + (rnd.nextFloat() - 0.5f) * 0.5f
                }
                // Someone new can't keep still: a happy hop now and then.
                if (w.fresh && w.hop <= 0f && rnd.nextFloat() < dt * 0.35f) { w.hopV = HOP_V; w.cheer = max(w.cheer, 0.8f) }
                if (w.timer <= 0f) decide(w)
            }
            Act.Walk, Act.Enter -> {
                if (arrived(w)) {
                    if (w.act == Act.Enter) {
                        // Just arrived: a hop and a wave at the owner.
                        w.hopV = HOP_V
                        w.cheer = 1f
                        startWave(w)
                    } else {
                        w.act = Act.Idle
                        w.timer = 1.2f + rnd.nextFloat() * 2.8f
                    }
                } else if (w.stuck > 2.5f) {
                    // Hemmed in: try somewhere else.
                    w.stuck = 0f
                    pickSpot(w, w.x)
                }
            }
            Act.Wave -> {
                w.waveTo = if (w.timer > 0.35f) 1f else 0f
                if (w.timer <= 0f) { w.act = Act.Idle; w.timer = 0.8f + rnd.nextFloat() * 1.5f }
            }
            Act.ToChat -> {
                val p = w.partner
                if (p == null || p.partner !== w || p.focused) { endChat(w); w.act = Act.Idle; w.timer = 0.5f; return }
                if (arrived(w) && (p.act == Act.Chat || arrived(p))) {
                    w.act = Act.Chat
                    w.timer = CHAT_MIN + rnd.nextFloat() * 3.5f
                    if (p.act == Act.ToChat) { p.act = Act.Chat; p.timer = w.timer }
                    if (w.leadChat) emote(w, 0.2f)
                } else if (w.since > 9f) {
                    endChat(w); w.act = Act.Idle; w.timer = 0.5f
                }
            }
            Act.Chat -> {
                val p = w.partner
                if (p == null || p.partner !== w) { endChat(w); w.act = Act.Idle; w.timer = 0.6f; return }
                // Face each other, and take turns with a bubble.
                val side = sign(p.x - w.x).let { if (it == 0f) 1f else it }
                w.turnTo = side * 0.85f
                w.lookTo = side
                if (w.emoteLeft <= 0f && p.emoteLeft <= 0f && rnd.nextFloat() < dt * 1.6f) {
                    // Whoever spoke last listens now: a nod.
                    emote(if (w.talkedAt <= p.talkedAt) w else p, 0f)
                }
                if (w.timer <= 0f) {
                    endChat(w)
                    // Off they go, or a wave goodbye.
                    if (rnd.nextFloat() < 0.4f) startWave(w) else { pickSpot(w, w.x); w.act = Act.Walk }
                }
            }
            Act.ToSeat -> {
                if (arrived(w)) {
                    w.act = Act.Sit
                    w.timer = 5f + rnd.nextFloat() * 6f
                    w.turnTo = 0f
                    w.x = w.tx; w.z = w.tz
                }
            }
            Act.Sit -> {
                if (rnd.nextFloat() < dt * 0.4f) {
                    w.turnTo = (rnd.nextFloat() - 0.5f) * 0.9f
                    w.lookTo = w.turnTo
                }
                if (w.timer <= 0f) {
                    leaveSeat(w)
                    pickSpot(w, w.x)
                    w.act = Act.Walk
                }
            }
            Act.Focus -> {
                w.focusAge += dt
                // A step out from behind the owner, if needed; jostled about, it simply stops where it is.
                if (w.focusAge > 1.2f && !behindMe(w.x, w.z)) { w.tx = w.x; w.tz = w.z }
                val stepping = dist(w.x, w.z, w.tx, w.tz) > 0.02f
                if (!stepping) { w.turnTo = 0f; w.lookTo = 0f }
                w.waveTo = if (w.focusAge > 0.25f && !stepping) 1f else 0f
            }
        }
    }

    private fun decide(w: Walker) {
        val r = rnd.nextFloat()
        when {
            r < 0.16f -> startWave(w)
            r < 0.40f && startChat(w) -> Unit
            r < 0.54f && startSeat(w) -> Unit
            else -> { pickSpot(w, w.x); w.act = Act.Walk }
        }
    }

    private fun startWave(w: Walker) {
        val m = me
        w.act = Act.Wave
        w.timer = 1.6f + rnd.nextFloat() * 1.2f
        if (m != null) {
            w.turnTo = ((m.x - w.x) * 2f).coerceIn(-0.7f, 0.7f)
            w.lookTo = sign(m.x - w.x)
        }
    }

    private fun startChat(w: Walker): Boolean {
        var best: Walker? = null
        var bestD = 0.9f
        for (o in list) {
            if (o === w || o.isMe || o.focused || o.partner != null) continue
            if (o.act != Act.Idle && o.act != Act.Walk) continue
            val d = dist(w.x, w.z, o.x, o.z)
            if (d < bestD) { bestD = d; best = o }
        }
        val o = best ?: return false
        // Meet halfway (somewhere free), side by side across, a little apart.
        var mx = (w.x + o.x) / 2f
        var mz = ((w.z + o.z) / 2f).coerceIn(Z_MIN + 0.05f, Z_MAX - 0.05f)
        for (tries in 0 until 6) {
            if (free(mx - CHAT_GAP / 2f, mz, w, o) && free(mx + CHAT_GAP / 2f, mz, w, o)) break
            mx = (rnd.nextFloat() - 0.5f) * 1.5f
            mz = Z_MIN + rnd.nextFloat() * (Z_MAX - Z_MIN)
            if (tries == 5) return false
        }
        val wLeft = w.x <= o.x
        setTarget(w, mx + (if (wLeft) -CHAT_GAP else CHAT_GAP) / 2f, mz)
        setTarget(o, mx + (if (wLeft) CHAT_GAP else -CHAT_GAP) / 2f, mz)
        w.partner = o; o.partner = w
        w.leadChat = true; o.leadChat = false
        w.act = Act.ToChat; o.act = Act.ToChat
        w.since = 0f; o.since = 0f
        return true
    }

    private fun startSeat(w: Walker): Boolean {
        val taken = BooleanArray(SEATS.size / 2)
        for (o in list) if (o.seat >= 0) taken[o.seat] = true
        var pick = -1
        var bestD = 9f
        for (i in taken.indices) {
            if (taken[i]) continue
            val d = dist(w.x, w.z, SEATS[i * 2], SEATS[i * 2 + 1]) + rnd.nextFloat() * 0.3f
            if (d < bestD) { bestD = d; pick = i }
        }
        if (pick < 0) return false
        w.seat = pick
        setTarget(w, SEATS[pick * 2], SEATS[pick * 2 + 1])
        w.act = Act.ToSeat
        return true
    }

    private fun leaveSeat(w: Walker) { w.seat = -1 }

    private fun endChat(w: Walker) {
        val p = w.partner
        w.partner = null
        if (p != null && p.partner === w) {
            p.partner = null
            if (p.act == Act.Chat || p.act == Act.ToChat) { p.act = Act.Idle; p.timer = 0.6f + rnd.nextFloat() }
        }
    }

    private fun emote(w: Walker, delay: Float) {
        w.emote = rnd.nextInt(Emote.COUNT)
        w.emoteLeft = 1.5f + rnd.nextFloat() * 0.6f + delay
        w.emoteAge = -delay
        w.talkedAt = clock
        // A little bounce as the bubble pops up.
        w.squashV -= 1.4f
    }

    /** A free spot somewhere on the square, preferring a stroll of a sensible length from [fromX]. */
    /**
     * A spot somewhere on the square for [w] to stroll to: of a dozen tries, the one with the most room around it
     * (so the crowd spreads over the whole square instead of bunching), not too near nor too far from [fromX].
     */
    private fun pickSpot(w: Walker, fromX: Float) {
        var bx = w.x
        var bz = w.z
        var best = -9f
        for (tries in 0 until 12) {
            val x = (fromX + (rnd.nextFloat() - 0.5f) * 1.5f).coerceIn(-X_MAX, X_MAX)
            val z = Z_MIN + rnd.nextFloat() * (Z_MAX - Z_MIN)
            if (blockedBy(x, z, ROOM) || behindMe(x, z)) continue
            val far = dist(x, z, w.x, w.z)
            if (far < 0.16f) continue
            val score = room(x, z, w, null) - max(0f, far - 0.9f) * 0.3f
            if (score > best) { best = score; bx = x; bz = z }
        }
        setTarget(w, bx, bz)
        w.since = 0f
    }


    private fun placeFree(w: Walker) {
        for (tries in 0 until 40) {
            val x = (rnd.nextFloat() * 2f - 1f) * X_MAX
            val z = Z_MIN + rnd.nextFloat() * (Z_MAX - Z_MIN)
            w.x = x; w.z = z
            if (free(x, z, w, null)) break
        }
        w.tx = w.x; w.tz = w.z
    }

    /** Stays where [w] is, or, if that is behind the owner, steps just clear of them, to the nearer side. */
    private fun stepClear(w: Walker) {
        if (!behindMe(w.x, w.z)) { w.tx = w.x; w.tz = w.z; return }
        val side = if (w.x < OWNER_X) -1f else 1f
        var x = OWNER_X + side * (ME_COLUMN + 0.03f)
        if (abs(x) > X_MAX) x = OWNER_X - side * (ME_COLUMN + 0.03f)
        setTarget(w, x, w.z)
    }

    private fun setTarget(w: Walker, x: Float, z: Float) {
        w.tx = x.coerceIn(-X_MAX, X_MAX)
        w.tz = z.coerceIn(Z_MIN, Z_MAX)
        w.stuck = 0f
    }

    /** Room at ([x], [z]) and clear of the owner's view: nobody within a comfortable distance. */
    private fun free(x: Float, z: Float, self: Walker, other: Walker?): Boolean =
        !blockedBy(x, z, ROOM) && !behindMe(x, z) && room(x, z, self, other) >= ROOM * 1.6f

    /** How far the nearest other person is from ([x], [z]) (where they are headed, if walking), as the crowd sees it. */
    private fun room(x: Float, z: Float, self: Walker, other: Walker?): Float {
        var near = 9f
        for (o in list) {
            if (o === self || o === other) continue
            val moving = o.act == Act.Walk || o.act == Act.ToChat || o.act == Act.ToSeat || o.act == Act.Enter
            val d = sepDist(x, z, if (moving) o.tx else o.x, if (moving) o.tz else o.z) - (if (o.isMe) ME_ROOM - ROOM else 0f)
            if (d < near) near = d
        }
        return near
    }

    /** Standing here, someone would be hidden behind the owner (or hide them): the owner's own column of the screen. */
    private fun behindMe(x: Float, z: Float): Boolean = abs(x - OWNER_X) < ME_COLUMN && z < OWNER_Z + 0.08f


    /** Inside (or within [pad] of) the fountain, a planter or the owner's spot. */
    private fun blockedBy(x: Float, z: Float, pad: Float): Boolean {
        for (i in 0 until OBSTACLES.size / 3) {
            if (dist(x, z, OBSTACLES[i * 3], OBSTACLES[i * 3 + 1]) < OBSTACLES[i * 3 + 2] + pad) return true
        }
        return false
    }

    private fun arrived(w: Walker): Boolean = dist(w.x, w.z, w.tx, w.tz) < 0.025f && w.vx * w.vx + w.vz * w.vz < 0.004f

    // --- Moving ----------------------------------------------------------------------------------------------------

    private fun move(w: Walker, dt: Float) {
        if (w.isMe) return
        if (w.act == Act.Sit) { w.vx = 0f; w.vz = 0f; return }
        val going = w.act == Act.Walk || w.act == Act.Enter || w.act == Act.ToChat || w.act == Act.ToSeat ||
            (w.act == Act.Focus && dist(w.x, w.z, w.tx, w.tz) > 0.02f)
        // Ground distances: z counts DEPTH of an x unit.
        var dx = w.tx - w.x
        var dz = (w.tz - w.z) * DEPTH
        val d = sqrt(dx * dx + dz * dz)
        var desX = 0f
        var desZ = 0f
        if (going && d > 0.001f) {
            dx /= d; dz /= d
            // Easing out: slow down over the last stretch.
            val speed = MAX_SPEED * min(1f, d / SLOW_RADIUS) * (if (w.act == Act.Enter) 1.1f else 1f)
            desX = dx * speed
            desZ = dz * speed
            // A gentle meander, different for each walker, so paths curve.
            val bend = sin(clock * 0.55f + (w.seed and 0xff) * 0.1f) * 0.32f * min(1f, d / 0.3f)
            desX += -dz * speed * bend
            desZ += dx * speed * bend
            // Round the fountain, planters and the owner: when one is ahead, bear away from it to the nearer side.
            for (i in 0 until OBSTACLES.size / 3) {
                val ox = OBSTACLES[i * 3]
                val oz = OBSTACLES[i * 3 + 1] * DEPTH
                val r = OBSTACLES[i * 3 + 2] + ROOM
                avoid(w, ox, oz, r, dx, dz, d, speed)
            }
            desX += avoidX; desZ += avoidZ
            avoidX = 0f; avoidZ = 0f
            for (o in list) {
                if (o === w) continue
                val r = (if (o.isMe) ME_ROOM else ROOM) + ROOM * 0.6f
                avoid(w, o.x, o.z * DEPTH, r, dx, dz, d, speed)
            }
            desX += avoidX * 0.7f; desZ += avoidZ * 0.7f
            avoidX = 0f; avoidZ = 0f
        }
        // Keep a little room from everyone (standing or walking).
        for (o in list) {
            if (o === w) continue
            val room = if (o.isMe) ME_ROOM else ROOM
            val ex = w.x - o.x
            val ez = (w.z - o.z) * SEP_DEPTH
            val e = sqrt(ex * ex + ez * ez)
            if (e < room * 1.5f && e > 0.0001f) {
                val push = (1f - e / (room * 1.5f)).let { it * it } * MAX_SPEED * (if (going) 1.6f else 0.9f)
                desX += ex / e * push
                desZ += ez / e * push
            }
        }
        // Steering: accelerate towards the wished velocity, never faster than a person can.
        var ax = (desX - w.vx) * 4.5f
        var az = (desZ - w.vz * DEPTH) * 4.5f
        val a = sqrt(ax * ax + az * az)
        if (a > MAX_ACCEL) { ax *= MAX_ACCEL / a; az *= MAX_ACCEL / a }
        w.vx += ax * dt
        var gz = w.vz * DEPTH + az * dt
        var sp = sqrt(w.vx * w.vx + gz * gz)
        val cap = MAX_SPEED * 1.15f
        if (sp > cap) { w.vx *= cap / sp; gz *= cap / sp; sp = cap }
        w.vz = gz / DEPTH
        val ox = w.x
        val oz = w.z
        w.x += w.vx * dt
        w.z += w.vz * dt
        if (w.act != Act.Enter) {
            w.x = w.x.coerceIn(-X_MAX - 0.02f, X_MAX + 0.02f)
        }
        w.z = w.z.coerceIn(Z_MIN - 0.02f, Z_MAX + 0.02f)
        // Not getting anywhere while trying to.
        if (going && dist(ox, oz, w.x, w.z) < MAX_SPEED * dt * 0.2f && d > 0.05f) w.stuck += dt else w.stuck = 0f
    }

    private var avoidX = 0f
    private var avoidZ = 0f

    /** Adds to [avoidX]/[avoidZ] a sideways bearing round a circle at ([cx], [cz]) of radius [r] if it lies ahead. */
    private fun avoid(w: Walker, cx: Float, cz: Float, r: Float, dx: Float, dz: Float, d: Float, speed: Float) {
        val px = cx - w.x
        val pz = cz - w.z * DEPTH
        val along = px * dx + pz * dz
        if (along <= 0f || along > min(d + r, 0.55f)) return
        // How far the obstacle's centre is off the straight line, signed.
        val off = px * -dz + pz * dx
        if (abs(off) >= r) return
        val side = if (off > 0f) -1f else 1f
        val k = (1f - abs(off) / r) * (1f - along / 0.6f).coerceAtLeast(0.2f) * 1.6f
        avoidX += -dz * side * speed * k
        avoidZ += dx * side * speed * k
    }

    /** Nobody stands inside anybody or the fountain: whatever overlaps is pushed apart, on the ground. */
    private fun separate() {
        for (pass in 0 until 2) {
            for (i in list.indices) {
                val a = list[i]
                if (a.isMe || a.act == Act.Sit) continue
                // Obstacles.
                for (k in 0 until OBSTACLES.size / 3) {
                    val ox = OBSTACLES[k * 3]
                    val oz = OBSTACLES[k * 3 + 1]
                    val r = OBSTACLES[k * 3 + 2] + BODY
                    val ex = a.x - ox
                    val ez = (a.z - oz) * DEPTH
                    val e = sqrt(ex * ex + ez * ez)
                    if (e < r) {
                        val n = if (e < 1e-4f) 1f else e
                        a.x = ox + ex / n * r
                        a.z = oz + ez / n * r / DEPTH
                    }
                }
                for (j in list.indices) {
                    if (j == i) continue
                    val b = list[j]
                    if (a.act == Act.Enter && a.shown < 0.5f) continue
                    val min = if (b.isMe) ME_ROOM * 0.85f else BODY * 2f
                    val ex = a.x - b.x
                    val ez = (a.z - b.z) * SEP_DEPTH
                    val e = sqrt(ex * ex + ez * ez)
                    if (e >= min) continue
                    val n = if (e < 1e-4f) 1f else e
                    val nx = if (e < 1e-4f) (if (i < j) -1f else 1f) else ex / n
                    val nz = if (e < 1e-4f) 0f else ez / n
                    // The owner and someone sitting don't budge; otherwise each moves half the way.
                    val fixedB = b.isMe || b.act == Act.Sit
                    val share = if (fixedB) 1f else 0.5f
                    val push = (min - e) * share
                    a.x += nx * push
                    a.z += nz * push / DEPTH
                    if (!fixedB) { b.x -= nx * push; b.z -= nz * push / DEPTH }
                }
                if (a.act != Act.Enter) a.x = a.x.coerceIn(-X_MAX - 0.03f, X_MAX + 0.03f)
                a.z = a.z.coerceIn(Z_MIN - 0.03f, Z_MAX + 0.03f)
            }
        }
    }

    // --- The pose --------------------------------------------------------------------------------------------------

    private fun animate(w: Walker, dt: Float) {
        val gz = w.vz * DEPTH
        val speed = sqrt(w.vx * w.vx + gz * gz)
        // Facing: where they walk (across the screen turns the head; towards or away from us faces us).
        if (speed > 0.035f && w.act != Act.Sit) {
            val across = w.vx / speed
            var t = (across * 1.35f).coerceIn(-1f, 1f)
            // Walking away from us they'd seem to walk backwards: keep them turned to the side they go.
            if (gz < 0f && abs(t) < 0.65f) t = (if (abs(across) > 0.05f) sign(across) else sign(w.turn).let { if (it == 0f) 1f else it }) * 0.65f
            w.turnTo = t
            w.lookTo = w.turnTo
        }
        // A quick turn with a little overshoot (a spring), like a head turning.
        val k = 150f
        val c = 2f * sqrt(k) * 0.8f
        w.turnV += (k * (w.turnTo - w.turn) - c * w.turnV) * dt
        w.turn = (w.turn + w.turnV * dt).coerceIn(-1.08f, 1.08f)
        w.lookX = approach(w.lookX, w.lookTo, dt, 10f)

        // The stride follows the ground covered: a step every STEP of the way.
        val walkTo = (speed / (MAX_SPEED * 0.45f)).coerceIn(0f, 1f)
        w.walk = approach(w.walk, walkTo, dt, 9f)
        w.phase += speed / STEP * PI.toFloat() * dt
        if (w.walk < 0.02f) {
            // Standing: the feet settle together.
            val target = (Math.round(w.phase / PI.toFloat()) * PI).toFloat()
            w.phase = approach(w.phase, target, dt, 8f)
        }
        if (w.phase > 1000f) w.phase -= (2 * PI * 150).toFloat()

        w.wave = approach(w.wave, w.waveTo, dt, 7f)
        w.sit = approach(w.sit, if (w.act == Act.Sit) 1f else 0f, dt, 8f)
        w.cheer = max(0f, w.cheer - dt * 0.7f)
        w.shown = min(1f, w.shown + dt * 1.6f)

        // Hop: up, gravity, and a squash on landing that springs back.
        if (w.hopV != 0f || w.hop > 0f) {
            w.hop += w.hopV * dt
            w.hopV -= GRAVITY * dt
            if (w.hop <= 0f) {
                w.hop = 0f
                if (w.hopV < -0.3f) w.squashV += -w.hopV * 2.2f
                w.hopV = 0f
            }
        }
        // While going up, stretched; the squash springs back to 0.
        val sk = 260f
        w.squashV += (-sk * w.squash - 2f * sqrt(sk) * 0.45f * w.squashV) * dt
        w.squash = (w.squash + w.squashV * dt).coerceIn(-0.8f, 0.8f)

        // Blinks: now and then, sometimes twice.
        w.blinkIn -= dt
        if (w.blinkIn <= 0f) {
            w.blinkAge = 0f
            w.blinkIn = if (rnd.nextInt(6) == 0) 0.28f else 2.4f + rnd.nextFloat() * 3.4f
        }
        w.blinkAge += dt
        w.blink = if (w.blinkAge < 0.16f) sin(w.blinkAge / 0.16f * PI.toFloat()) else 0f

        if (w.emoteLeft > 0f) {
            w.emoteLeft -= dt
            w.emoteAge += dt
            if (w.emoteLeft <= 0f) w.emote = -1
        }
    }

    private fun sortOrder() {
        val n = list.size
        count = n
        if (order.size < n) order = IntArray(n + 8)
        // Keep the last order where it still holds (an insertion sort on an almost sorted list), else start afresh.
        var valid = true
        for (i in 0 until n) if (order[i] >= n) valid = false
        if (!valid || seenCount != n) { for (i in 0 until n) order[i] = i; seenCount = n }
        for (i in 1 until n) {
            val v = order[i]
            val z = list[v].z
            var j = i - 1
            while (j >= 0 && list[order[j]].z > z) { order[j + 1] = order[j]; j-- }
            order[j + 1] = v
        }
    }

    private var seenCount = -1

    companion object {
        const val ME = "\u0000me"

        /** One z unit is this much of an x unit on the ground. */
        const val DEPTH = 0.5f

        /**
         * Between people z counts for less: two people one behind the other still crowd each other on screen, so they
         * spread out across the square rather than stand in a stack.
         */
        const val SEP_DEPTH = 0.3f

        const val X_MAX = 0.9f
        const val Z_MIN = 0.1f
        const val Z_MAX = 0.98f

        /** Where newcomers start, just off the side of the screen. */
        const val EDGE_IN = 1.32f

        const val OWNER_X = -0.3f
        const val OWNER_Z = 0.9f

        /** The fountain: its centre and radius on the ground (z 0 is its middle, at the back). */
        const val FOUNTAIN_X = 0f
        const val FOUNTAIN_Z = 0f
        const val FOUNTAIN_R = 0.25f

        /** Where the benches stand (centre x, z) and how wide they are; people sit just in front. */
        val BENCHES = floatArrayOf(-0.68f, 0.03f, 0.68f, 0.03f)
        const val BENCH_W = 0.26f

        /** Seats: x, z of each (two to a bench). */
        val SEATS = floatArrayOf(-0.74f, 0.1f, -0.62f, 0.1f, 0.62f, 0.1f, 0.74f, 0.1f)

        /** Round things in the way: x, z, radius (the fountain first, then the planters at the front corners). */
        val OBSTACLES = floatArrayOf(FOUNTAIN_X, FOUNTAIN_Z + 0.12f, FOUNTAIN_R + 0.05f, -1.0f, 1.0f, 0.16f, 1.0f, 1.0f, 0.16f)

        /** A body's own radius, the room each likes around them, and the larger room round the owner. */
        const val BODY = 0.052f
        const val ROOM = 0.085f
        const val ME_ROOM = 0.16f

        /** Half the width of the strip behind the owner where nobody stands still. */
        const val ME_COLUMN = 0.15f

        const val MAX_SPEED = 0.15f
        const val MAX_ACCEL = 0.42f
        const val SLOW_RADIUS = 0.16f

        /** Ground covered by one step. */
        const val STEP = 0.052f

        const val HOP_V = 1.25f
        const val GRAVITY = 9f

        const val CHAT_GAP = 0.2f
        const val CHAT_MIN = 4f

        /** The still arrangement's spots (x, z pairs), front row first, clear of the owner and the fountain. */
        private val STILL_SPOTS = floatArrayOf(
            0.05f, 0.8f, 0.38f, 0.88f, 0.7f, 0.76f, -0.66f, 0.74f,
            -0.62f, 0.46f, 0.5f, 0.5f, -0.84f, 0.3f, 0.84f, 0.36f,
            -0.48f, 0.24f, 0.46f, 0.24f, 0.22f, 0.96f, -0.88f, 0.92f,
            0.9f, 0.96f, 0.3f, 0.52f,
        )

        private fun sepDist(x1: Float, z1: Float, x2: Float, z2: Float): Float {
            val dx = x1 - x2
            val dz = (z1 - z2) * SEP_DEPTH
            return sqrt(dx * dx + dz * dz)
        }

        private fun dist(x1: Float, z1: Float, x2: Float, z2: Float): Float {
            val dx = x1 - x2
            val dz = (z1 - z2) * DEPTH
            return sqrt(dx * dx + dz * dz)
        }

        /** Eases [v] towards [to] at [rate] per second (exponential, so it never overshoots). */
        private fun approach(v: Float, to: Float, dt: Float, rate: Float): Float = to + (v - to) * exp(-rate * dt)
    }
}
