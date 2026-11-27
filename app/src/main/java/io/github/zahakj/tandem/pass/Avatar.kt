package io.github.zahakj.tandem.pass

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * The parts of a Tandem Pass character, in the order they are encoded (append new parts at the end, never reorder:
 * old cards must still read). [count] is how many choices the part has; the drawing is ui/AvatarArt.kt, the colours
 * are [AvatarColors].
 */
enum class AvatarPart(val count: Int) {
    Shape(6), Skin(14), Eyes(20), EyeColor(16), Brows(6), Mouth(8), Cheeks(4),
    Hair(34), HairColor(12), Headwear(37), Top(38), TopColor(12), Glasses(15), Earrings(10), Theme(10),
    // Added with the wardrobe: face details (freckles, face paint, a plaster...) and facial hair.
    FaceExtra(8), Beard(5),
}

/**
 * A character, as plain numbers: one choice per [AvatarPart]. Saved by name in plaza.json, and as [AvatarCodec]'s 18
 * bytes on a card. Any number out of range reads as the part's first choice, so a card from a newer Tandem with
 * more choices still draws.
 */
@Serializable
data class AvatarSpec(
    @SerialName("shape") val shape: Int = 0,
    @SerialName("skin") val skin: Int = 2,
    @SerialName("eyes") val eyes: Int = 1,
    @SerialName("eyeColor") val eyeColor: Int = 1,
    @SerialName("brows") val brows: Int = 1,
    @SerialName("mouth") val mouth: Int = 0,
    @SerialName("cheeks") val cheeks: Int = 1,
    @SerialName("hair") val hair: Int = 2,
    @SerialName("hairColor") val hairColor: Int = 1,
    @SerialName("headwear") val headwear: Int = 0,
    @SerialName("top") val top: Int = 1,
    @SerialName("topColor") val topColor: Int = 5,
    @SerialName("glasses") val glasses: Int = 0,
    @SerialName("earrings") val earrings: Int = 0,
    @SerialName("theme") val theme: Int = 4,
    @SerialName("faceExtra") val faceExtra: Int = 0,
    @SerialName("beard") val beard: Int = 0,
) {
    operator fun get(part: AvatarPart): Int = when (part) {
        AvatarPart.Shape -> shape
        AvatarPart.Skin -> skin
        AvatarPart.Eyes -> eyes
        AvatarPart.EyeColor -> eyeColor
        AvatarPart.Brows -> brows
        AvatarPart.Mouth -> mouth
        AvatarPart.Cheeks -> cheeks
        AvatarPart.Hair -> hair
        AvatarPart.HairColor -> hairColor
        AvatarPart.Headwear -> headwear
        AvatarPart.Top -> top
        AvatarPart.TopColor -> topColor
        AvatarPart.Glasses -> glasses
        AvatarPart.Earrings -> earrings
        AvatarPart.Theme -> theme
        AvatarPart.FaceExtra -> faceExtra
        AvatarPart.Beard -> beard
    }.let { if (it in 0 until part.count) it else 0 }

    /** This character with [part] set to [value] (kept in range). */
    fun with(part: AvatarPart, value: Int): AvatarSpec {
        val v = ((value % part.count) + part.count) % part.count
        return when (part) {
            AvatarPart.Shape -> copy(shape = v)
            AvatarPart.Skin -> copy(skin = v)
            AvatarPart.Eyes -> copy(eyes = v)
            AvatarPart.EyeColor -> copy(eyeColor = v)
            AvatarPart.Brows -> copy(brows = v)
            AvatarPart.Mouth -> copy(mouth = v)
            AvatarPart.Cheeks -> copy(cheeks = v)
            AvatarPart.Hair -> copy(hair = v)
            AvatarPart.HairColor -> copy(hairColor = v)
            AvatarPart.Headwear -> copy(headwear = v)
            AvatarPart.Top -> copy(top = v)
            AvatarPart.TopColor -> copy(topColor = v)
            AvatarPart.Glasses -> copy(glasses = v)
            AvatarPart.Earrings -> copy(earrings = v)
            AvatarPart.Theme -> copy(theme = v)
            AvatarPart.FaceExtra -> copy(faceExtra = v)
            AvatarPart.Beard -> copy(beard = v)
        }
    }

    /** Every part kept in range (a hand-edited or newer file). */
    fun clean(): AvatarSpec = AvatarPart.entries.fold(this) { s, p -> s.with(p, s[p]) }

    companion object {
        /** The mouths that smile: the gentle one, the open one, the cat's, the tongue, the grin. */
        private val CHEERFUL = intArrayOf(0, 1, 2, 4, 6)

        /**
         * Hats and outfits that are a costume or a uniform (crowns, wizards, helmets, a chef's whites, a cape...):
         * fine to wear, rare in a random crowd. Everything else is everyday wear.
         */
        internal val COSTUME_HATS = intArrayOf(6, 17, 18, 19, 20, 23, 24, 25, 26, 27, 28, 31, 32, 33, 34, 36)
        internal val COSTUME_TOPS = intArrayOf(20, 21, 22, 23, 27, 28, 29, 30, 36)

        /**
         * A random character that looks put together: the rarer extras (a hat, glasses, earrings, a face detail, a
         * beard) only some of the time, costumes rarer still, everything else from the whole range. [random] is passed
         * in so tests and previews can repeat one.
         */
        fun random(random: Random = Random.Default): AvatarSpec {
            fun pick(p: AvatarPart) = random.nextInt(p.count)
            fun some(p: AvatarPart, chance: Float) = if (random.nextFloat() < chance) 1 + random.nextInt(p.count - 1) else 0
            /** A choice from 0 until [count], mostly not one of [rare]. */
            fun mostly(count: Int, rare: IntArray, rareChance: Float, from: Int = 0): Int {
                val costume = random.nextFloat() < rareChance
                val pool = (from until count).filter { (it in rare) == costume }
                return pool[random.nextInt(pool.size)]
            }
            return AvatarSpec(
                shape = pick(AvatarPart.Shape),
                skin = pick(AvatarPart.Skin),
                eyes = pick(AvatarPart.Eyes),
                eyeColor = pick(AvatarPart.EyeColor),
                brows = pick(AvatarPart.Brows),
                // Mostly one of the smiles, so a crowd of strangers looks glad to be there.
                mouth = if (random.nextFloat() < 0.75f) CHEERFUL[random.nextInt(CHEERFUL.size)] else pick(AvatarPart.Mouth),
                cheeks = pick(AvatarPart.Cheeks),
                hair = pick(AvatarPart.Hair),
                hairColor = pick(AvatarPart.HairColor),
                headwear = if (random.nextFloat() < 0.4f) mostly(AvatarPart.Headwear.count, COSTUME_HATS, 0.15f, from = 1) else 0,
                top = mostly(AvatarPart.Top.count, COSTUME_TOPS, 0.08f),
                topColor = pick(AvatarPart.TopColor),
                glasses = some(AvatarPart.Glasses, 0.3f),
                earrings = some(AvatarPart.Earrings, 0.3f),
                theme = pick(AvatarPart.Theme),
                faceExtra = some(AvatarPart.FaceExtra, 0.15f),
                beard = some(AvatarPart.Beard, 0.15f),
            )
        }
    }
}

/**
 * The card's form of an [AvatarSpec]: a version byte, then one byte per [AvatarPart] in order (18 bytes today).
 * Reading is forgiving both ways: a shorter (older) encoding leaves the parts it lacks at their defaults, a longer
 * (newer) one has its extra parts ignored, and a value out of range reads as the first choice.
 */
object AvatarCodec {
    const val VERSION = 1
    /** Bytes a card spends on the character today: the version and one per part. */
    val SIZE: Int get() = 1 + AvatarPart.entries.size
    /** What a card allows for it, room for parts to come. */
    const val MAX_BYTES = 32

    fun encode(spec: AvatarSpec): ByteArray {
        val out = ByteArray(SIZE)
        out[0] = VERSION.toByte()
        for (p in AvatarPart.entries) out[1 + p.ordinal] = spec[p].toByte()
        return out
    }

    /** The character in [bytes], or null when there is none (empty, or a version of 0). */
    fun decode(bytes: ByteArray): AvatarSpec? {
        if (bytes.isEmpty() || bytes[0].toInt() == 0 || bytes.size > MAX_BYTES) return null
        var s = AvatarSpec()
        for (p in AvatarPart.entries) {
            val v = bytes.getOrNull(1 + p.ordinal)?.toInt()?.and(0xff) ?: continue
            s = s.with(p, if (v < p.count) v else 0)
        }
        return s
    }
}

/** The colours of the parts that come in colours, as ARGB, so the pure code and the drawing agree. */
object AvatarColors {
    /** Ten everyday skin tones, light to deep, then four storybook ones. */
    val skin = longArrayOf(
        0xFFFDE3D0, 0xFFF8D2B5, 0xFFF1C09A, 0xFFE3A87F, 0xFFD39168, 0xFFBB7A52, 0xFF9C6240, 0xFF7E4B31, 0xFF633826, 0xFF4A291C,
        0xFFB8EBD3, 0xFFD8C8F5, 0xFFBFDDF7, 0xFFFFC9D6,
    )
    val hair = longArrayOf(
        0xFF2B2530, 0xFF4A3226, 0xFF7A5134, 0xFF9A3F2B, 0xFFD9772F, 0xFFF0CD6E,
        0xFFF4EBD6, 0xFFB9B4BE, 0xFFF59BC0, 0xFF7FD8B5, 0xFF7DB8F0, 0xFFB59AEE,
    )
    val eyes = longArrayOf(
        0xFF2E2433, 0xFF6B4226, 0xFF8C7A3A, 0xFF3F8F5E, 0xFF3F7FC9, 0xFF7A8794, 0xFF7E5BC9, 0xFFC98A2E,
        // teal, rose, ice blue, indigo, ruby, gold, silver, leaf green
        0xFF2AA19C, 0xFFD2607F, 0xFF86C3EA, 0xFF3E48A6, 0xFFB8363F, 0xFFE2B93B, 0xFFB6BECB, 0xFF84B63A,
    )
    val top = longArrayOf(
        0xFFF27D72, 0xFFF6A96B, 0xFFF2CF5B, 0xFF6FCF9F, 0xFF3FB6B0, 0xFF63A8EE,
        0xFF3D5A9E, 0xFFA98BE8, 0xFFEE8BB8, 0xFFF2E8D5, 0xFF4A4A57, 0xFFD9484F,
    )
    /** A card's own colour (its frame on the Plaza and the bottom screen). */
    val theme = longArrayOf(0xFFFFB3A7, 0xFFFFD39A, 0xFFFFE98F, 0xFFA8E6C1, 0xFF9FD9F2, 0xFFB5C3FF, 0xFFD3B5FF, 0xFFFFB8DA, 0xFFC9D6A3, 0xFFE0E0EA)

    init {
        check(skin.size == AvatarPart.Skin.count && hair.size == AvatarPart.HairColor.count && eyes.size == AvatarPart.EyeColor.count)
        check(top.size == AvatarPart.TopColor.count && theme.size == AvatarPart.Theme.count)
    }
}

/**
 * The greetings a card can carry: preset lines only, never free text, so a stranger can't put words on your screen.
 * A card holds the number; each Tandem shows it in its own language (strings_plaza.xml, `plaza_greeting_<n>`).
 * Numbers are forever: add new ones at the end.
 */
object Greetings {
    const val COUNT = 16
    val ids: List<Int> = (1..COUNT).toList()
    const val DEFAULT = 1

    /** A greeting number from a card, or the default when it is one this Tandem doesn't know. */
    fun known(id: Int): Int = if (id in 1..COUNT) id else DEFAULT
}
