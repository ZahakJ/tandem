package io.github.zahakj.tandem

/**
 * Android key layout files (`.kl`): which Linux key and axis codes a device sends for which Android key and axis.
 * Only the lines PadMap needs: `key <code> <KEYCODE>` and `axis <code> [invert] <AXIS> [flat <n>]`. `key usage`
 * lines and split axes are skipped. Codes may be decimal or hex.
 */
data class KeyLayout(val keys: Map<Int, String>, val axes: Map<Int, Axis>) {
    data class Axis(val name: String, val invert: Boolean = false, val flat: Int = 0)

    companion object {
        fun parse(text: String): KeyLayout {
            val keys = LinkedHashMap<Int, String>()
            val axes = LinkedHashMap<Int, Axis>()
            for (raw in text.lineSequence()) {
                val w = raw.substringBefore('#').trim().split(Regex("\\s+"))
                if (w.size < 3) continue
                val code = number(w[1]) ?: continue
                when (w[0]) {
                    "key" -> if (code !in keys) keys[code] = w[2]
                    "axis" -> {
                        if (w[2] == "split" || code in axes) continue
                        val invert = w[2] == "invert"
                        val name = (if (invert) w.getOrNull(3) else w[2]) ?: continue
                        val flat = w.indexOf("flat").takeIf { it > 0 }?.let { w.getOrNull(it + 1)?.toIntOrNull() } ?: 0
                        axes[code] = Axis(name, invert, flat)
                    }
                }
            }
            return KeyLayout(keys, axes)
        }

        private fun number(s: String): Int? =
            if (s.startsWith("0x", ignoreCase = true)) s.substring(2).toIntOrNull(16) else s.toIntOrNull()
    }
}

/**
 * The translation table for controller passthrough (PadLink.kt): from an external controller's key layout to the
 * built-in controller's, through the Android key and axis each code stands for, so the helper writes exactly what
 * the built-in pad would send for the same button. Pure, so it is tested on the JVM.
 *
 * - Buttons go by their Android key (A to A, Start to Start). With [nintendo] (the pad's Nintendo labels switch in
 *   Settings › Controls) A/B and X/Y swap first, as they do inside Tandem (Gamepads.remap), so the button printed A
 *   is A in games too.
 * - Back from a controller (older Xbox pads send their View button so) is Select, and its Home / guide button is the
 *   built-in Home key, as the handheld's own Home button is.
 * - Axes go by role: sticks, triggers (LTRIGGER and BRAKE are the same thing, as are RTRIGGER and GAS), the hat. An
 *   analog trigger also presses L2 / R2 past 30 %, since emulators bind those buttons (Eden's ZL and ZR are); a
 *   digital L2 / R2 also pulls the trigger axis all the way.
 */
object PadMap {
    sealed class Rule {
        abstract val arg: String
        data class Key(val from: Int, val to: Int) : Rule() { override val arg get() = "k$from:$to" }
        data class Axis(val from: Int, val to: Int, val invert: Boolean = false, val flat: Int = 0) : Rule() {
            override val arg get() = "a$from:$to" + (if (invert) ":i" else "") + (if (flat > 0) ":f$flat" else "")
        }
        data class TriggerKey(val from: Int, val to: Int) : Rule() { override val arg get() = "t$from:$to" }
        data class KeyAxis(val from: Int, val to: Int) : Rule() { override val arg get() = "b$from:$to" }
    }

    /** Android keys a controller can pass on. Volume, app switch and keyboard keys stay with their own device. */
    private fun padKey(name: String) = name.startsWith("BUTTON_") || name.startsWith("DPAD_") || name == "HOME" || name == "BACK"

    /** What a controller key means for the built-in controller. */
    private fun meaning(name: String, nintendo: Boolean): String = when {
        name == "BACK" -> "BUTTON_SELECT"
        name == "BUTTON_MODE" -> "HOME"
        nintendo && name == "BUTTON_A" -> "BUTTON_B"
        nintendo && name == "BUTTON_B" -> "BUTTON_A"
        nintendo && name == "BUTTON_X" -> "BUTTON_Y"
        nintendo && name == "BUTTON_Y" -> "BUTTON_X"
        else -> name
    }

    /** Axis roles, so a pad's LTRIGGER finds the built-in's BRAKE. */
    private fun role(name: String): String = when (name) {
        "LTRIGGER", "BRAKE" -> "LT"
        "RTRIGGER", "GAS" -> "RT"
        else -> name
    }

    /**
     * Where a source axis goes on the target: the same role, or for a right stick on RX/RY the target's Z/RZ (and
     * back), which Android pads use for the same stick.
     */
    private fun targetAxis(name: String, target: Map<String, Int>): Int? = target[role(name)] ?: when (name) {
        "RX" -> target["Z"]
        "RY" -> target["RZ"]
        "Z" -> target["RX"]
        "RZ" -> target["RY"]
        else -> null
    }

    fun build(source: KeyLayout, target: KeyLayout, nintendo: Boolean = false): List<Rule> {
        // The target's code for each Android key and axis role (its first line wins, as in Android).
        val keyTo = HashMap<String, Int>()
        for ((code, name) in target.keys) keyTo.putIfAbsent(name, code)
        val axisTo = HashMap<String, Int>()
        for ((code, a) in target.axes) if (!a.invert) axisTo.putIfAbsent(role(a.name), code)
        val rules = ArrayList<Rule>()
        val analog = HashSet<String>()
        for ((code, a) in source.axes) {
            val to = targetAxis(a.name, axisTo) ?: continue
            val r = role(a.name)
            rules += Rule.Axis(code, to, a.invert, a.flat)
            if (r == "LT" || r == "RT") {
                analog += r
                keyTo[if (r == "LT") "BUTTON_L2" else "BUTTON_R2"]?.let { rules += Rule.TriggerKey(code, it) }
            }
        }
        for ((code, name) in source.keys) {
            if (!padKey(name)) continue
            val to = keyTo[meaning(name, nintendo)] ?: continue
            rules += Rule.Key(code, to)
            val r = when (name) { "BUTTON_L2" -> "LT"; "BUTTON_R2" -> "RT"; else -> null }
            if (r != null && r !in analog) axisTo[r]?.let { rules += Rule.KeyAxis(code, it) }
        }
        return rules
    }

    /** The source codes for A and Start (after the Nintendo swap): either accepts an offer to play with the pad. */
    fun acceptKeys(source: KeyLayout, nintendo: Boolean = false): List<Int> =
        source.keys.filter { (_, name) -> meaning(name, nintendo) in setOf("BUTTON_A", "BUTTON_START") }.keys.sorted()
}

/** Input devices as `dumpsys input` lists them (its Event Hub part): what the helper needs to find their nodes. */
object InputNodes {
    data class Node(val name: String, val path: String, val descriptor: String, val keyLayout: String?)

    fun parse(dump: String): List<Node> {
        val hub = dump.substringAfter("Event Hub State:", "").substringBefore("Input Reader State:")
        val nodes = ArrayList<Node>()
        var name: String? = null
        var path: String? = null
        var descriptor: String? = null
        var kl: String? = null
        fun flush() {
            val n = name; val p = path; val d = descriptor
            if (n != null && p != null && d != null) nodes += Node(n, p, d, kl?.takeIf { it.isNotBlank() })
            name = null; path = null; descriptor = null; kl = null
        }
        val head = Regex("^\\s{4}-?\\d+: (.*)$")
        for (line in hub.lineSequence()) {
            val h = head.find(line)
            if (h != null) { flush(); name = h.groupValues[1]; continue }
            val t = line.trim()
            when {
                t.startsWith("Path:") -> path = t.removePrefix("Path:").trim()
                t.startsWith("Descriptor:") -> descriptor = t.removePrefix("Descriptor:").trim()
                t.startsWith("KeyLayoutFile:") -> kl = t.removePrefix("KeyLayoutFile:").trim()
            }
        }
        flush()
        return nodes
    }
}
