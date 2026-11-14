package io.github.zahakj.tandem

/*
 * How the Deck's keys and pointer leave Tandem (Deck.kt), as plain formatting with no Android in it, so
 * DeckWireTest can check every line and byte on a desktop JVM.
 */

/**
 * The shell fallback: lines for the `input` command, written one after another to a single `sh` kept running
 * through Shizuku. Every line names the display, so the keys land on the game's screen whichever window has the
 * focus. `input` starts a new process for each line (about 150 to 300 ms on the Thor), so this path types but
 * doesn't move a pointer: clicks go where the Deck's own cursor points.
 */
object DeckShell {
    /** The game's screen: Android's default display. */
    const val DISPLAY = 0

    /** How long the press for a right click lasts, as a touch, which Winlator and GameNative read as one. */
    const val RIGHT_HOLD_MS = 600

    private const val PREFIX = "input -d $DISPLAY"

    /** One key, with its modifiers from [meta] (a KeyEvent meta state) held: `keyevent`, or `keycombination`. */
    fun key(code: Int, meta: Int = 0): String {
        val mods = DeckModifiers.fromMeta(meta)
        return if (mods.isEmpty()) "$PREFIX keyevent $code"
        else "$PREFIX keycombination ${mods.joinToString(" ") { it.code.toString() }} $code"
    }

    /**
     * Text typed as `input text` wants it: single-quoted for the shell (a quote becomes `'\''`) and spaces as `%s`,
     * which `input` turns back into spaces. Null for text the command can't carry (a line break).
     */
    fun text(s: String): String? {
        if (s.isEmpty() || s.any { it == '\n' || it == '\r' }) return null
        val body = s.replace("'", "'\\''").replace(" ", "%s")
        return "$PREFIX text '$body'"
    }

    /** A left click: a tap on the game's screen at the pointer. */
    fun tap(x: Int, y: Int): String = "$PREFIX tap $x $y"

    /** A right click: a press held in place for [ms]. */
    fun hold(x: Int, y: Int, ms: Int = RIGHT_HOLD_MS): String = "$PREFIX swipe $x $y $x $y $ms"

    /** A scroll by [notches] wheel notches at the pointer: a short swipe, up the screen for a finger moving down. */
    fun scroll(x: Int, y: Int, notches: Int, height: Int, pxPerNotch: Int = 120): String? {
        if (notches == 0) return null
        val to = (y - notches * pxPerNotch).coerceIn(0, height - 1)
        if (to == y) return null
        return "$PREFIX swipe $x $y $x $to 150"
    }
}

/**
 * The direct path (BinderDeck) builds KeyEvents and MotionEvents with the public constructors, which can't set two
 * things the game's screen needs: the display id (keys go to the focused display otherwise, which may be the
 * bottom one) and the action button of a mouse click (Winlator reads it). Both are plain ints at fixed places in
 * the event's parcel, so the event is written out, patched here and read back in. Every patch first checks the
 * fields around the target read as expected, so a different Android layout is left alone rather than corrupted.
 * Parcels are native-endian; Android devices are little-endian.
 */
object DeckParcel {
    /** The first int of a parcelled KeyEvent / MotionEvent (InputEvent.PARCEL_TOKEN_*). */
    const val TOKEN_MOTION = 1
    const val TOKEN_KEY = 2

    fun readInt(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8) or ((b[at + 2].toInt() and 0xff) shl 16) or ((b[at + 3].toInt() and 0xff) shl 24)

    fun writeInt(b: ByteArray, at: Int, v: Int) {
        b[at] = v.toByte(); b[at + 1] = (v shr 8).toByte(); b[at + 2] = (v shr 16).toByte(); b[at + 3] = (v shr 24).toByte()
    }

    /** A byte array in a parcel takes its length, then the bytes padded to four; -1 is a null array. */
    private fun bytesLength(len: Int): Int = if (len < 0) 0 else (len + 3) / 4 * 4

    /**
     * Sets the display id of a parcelled KeyEvent (Android 11 and later: token, id, device, source, display, hmac,
     * action, key code, ...). Checks the device, source, action and key code it was built with. Returns false, with
     * [b] untouched, when the layout doesn't match.
     */
    fun keyDisplay(b: ByteArray, deviceId: Int, source: Int, action: Int, keyCode: Int, displayId: Int): Boolean {
        if (b.size < 32 || readInt(b, 0) != TOKEN_KEY || readInt(b, 8) != deviceId || readInt(b, 12) != source) return false
        val hmac = readInt(b, 20)
        if (hmac > 64) return false
        val actionAt = 24 + bytesLength(hmac)
        if (b.size < actionAt + 8 || readInt(b, actionAt) != action || readInt(b, actionAt + 4) != keyCode) return false
        writeInt(b, 16, displayId)
        return true
    }

    /**
     * Sets the display id and action button of a parcelled MotionEvent (Android 11 and later: token, pointer count,
     * sample count, id, device, source, display, hmac, action, action button, flags, edge flags, meta state, button
     * state, ...). Checks the pointer count, source, action and button state it was built with. Returns false, with
     * [b] untouched, when the layout doesn't match.
     */
    fun motionFields(b: ByteArray, pointerCount: Int, source: Int, action: Int, buttonState: Int, displayId: Int, actionButton: Int): Boolean {
        if (b.size < 40 || readInt(b, 0) != TOKEN_MOTION || readInt(b, 4) != pointerCount || readInt(b, 20) != source) return false
        val hmac = readInt(b, 28)
        if (hmac > 64) return false
        val actionAt = 32 + bytesLength(hmac)
        if (b.size < actionAt + 24 || readInt(b, actionAt) != action || readInt(b, actionAt + 20) != buttonState) return false
        writeInt(b, 24, displayId)
        writeInt(b, actionAt + 4, actionButton)
        return true
    }
}
