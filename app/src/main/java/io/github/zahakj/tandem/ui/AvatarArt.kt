package io.github.zahakj.tandem.ui

import androidx.collection.MutableIntObjectMap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.pass.AvatarColors
import io.github.zahakj.tandem.pass.AvatarSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Tandem Pass characters: Tandem's own friendly people, drawn in vectors from an AvatarSpec (pass/Avatar.kt). Flat
 * colour blocks with a thin outline in a darker shade of the same colour, a soft highlight here and there, a rounded
 * head with its features at natural heights, real hairstyles and a small soft body with mitten hands.
 *
 * Everything is drawn in a 100 × 120 box of "units" (scaled to whatever size it is shown at), so one drawing serves the
 * Plaza crowd, the option chips and the big preview alike. Hair and hats are drawn once for the default crown and
 * stretched to each face shape's ([Head.crown]). Characters aren't mirrored in Arabic: they are people, not a layout.
 */

/**
 * One moment of a character's life: eyes, glance, a wave, a squash, a cheer, a turn, a walk and a seat, each 0 at rest.
 * Its fields can change: the Plaza keeps one pose per person and fills it in every frame instead of making a new one
 * (never change [Rest]).
 */
@Stable
class AvatarPose(
    /** 0 open, 1 shut. */
    var blink: Float = 0f,
    /** Where the eyes look, -1..1 each way. */
    var lookX: Float = 0f,
    var lookY: Float = 0f,
    /** 0 arm down, 1 raised in a wave; [waveSwing] rocks the hand. */
    var wave: Float = 0f,
    var waveSwing: Float = 0f,
    /** Positive squashes (landing), negative stretches (a hop). */
    var squash: Float = 0f,
    /** 0..1: sparkles and a wider smile, for a change in the editor or someone new on the Plaza. */
    var cheer: Float = 0f,
    /** -1..1: the head turned towards the left or the right of the screen (a three-quarter view); 0 faces us. */
    var turn: Float = 0f,
    /** 0..1: how much walk is in the stride (0 stands still); [phase] is where in the stride the feet are, in radians. */
    var walk: Float = 0f,
    var phase: Float = 0f,
    /** 0..1: sitting down (on a bench). */
    var sit: Float = 0f,
) {
    companion object { val Rest = AvatarPose() }
}

private fun c(v: Long) = Color(v)
internal fun Color.deep(f: Float) = lerp(this, Color(0xFF231C2E), f)
internal fun Color.light(f: Float) = lerp(this, Color.White, f)

/** The outline of a colour: the same hue, darker (a touch cooler, so it reads as a line, not a shadow). */
internal fun Color.edge(f: Float = 0.36f) = Color(red * (1f - f), green * (1f - f * 1.04f), blue * (1f - f * 0.82f), alpha)

private val SIDES = floatArrayOf(-1f, 1f)
private val Blush = Color(0xFFFF8A9B)
private val MouthIn = Color(0xFF8E3248)
private val Tongue = Color(0xFFFF97A8)
private val Tie = Color(0xFFFF7FA2)

/** A character's own colour (its card's frame). */
fun avatarTheme(spec: AvatarSpec): Color = c(AvatarColors.theme[spec.theme])

/**
 * Draws [spec] in this scope, standing on its bottom edge and centred, as large as fits a 5:6 box.
 * [shadow] draws the soft spot it stands on. [box] is the room it has, from the scope's origin (the whole scope by
 * default; the Plaza draws its crowd in one canvas, each in a box of its own). [headroom] is room kept above the
 * character, in its own units, for a tall hat ([avatarHeadroom]).
 */
fun DrawScope.drawAvatar(spec: AvatarSpec, pose: AvatarPose = AvatarPose.Rest, shadow: Boolean = true, box: Size = size, headroom: Float = 0f) {
    val u = min(box.width / 100f, box.height / (120f + headroom))
    val ox = (box.width - 100f * u) / 2f
    val oy = box.height - 120f * u
    withTransform({
        translate(ox, oy)
        scale(u, u, Offset.Zero)
    }) {
        if (shadow) drawOval(Color.Black.copy(alpha = 0.16f), Offset(29f, 112.5f), Size(42f, 6.5f))
        val sq = pose.squash.coerceIn(-1f, 1f)
        // A walk leans a little into where it goes and sways gently from foot to foot.
        val lean = pose.walk * (pose.turn * 3.4f + sin(pose.phase) * 1.8f)
        withTransform({
            scale(1f + 0.06f * sq, 1f - 0.06f * sq, Offset(50f, 117f))
            if (lean != 0f) rotate(lean, Offset(50f, 117f))
        }) { character(spec, pose) }
    }
}

// --- Shapes, kept -------------------------------------------------------------------------------------------------

/**
 * Every shape here is drawn in the character's own 100 × 120 units, so a path depends only on the parts, never on the
 * size it is shown at: each is built once and kept under a number, instead of a new one per character per frame (the
 * Plaza draws a whole crowd every frame). Drawing happens on the main thread only.
 */
private object Paths {
    private val made = MutableIntObjectMap<Path>()

    inline fun of(key: Int, build: Path.() -> Unit): Path = made[key] ?: Path().apply(build).also {
        if (made.size > 1200) made.clear()
        made[key] = it
    }

    /** Paths that change every frame (the arms), reset and refilled instead of made anew. */
    val armBack = Path()
    val armFront = Path()
}

/** A key for [Paths]: what kind of shape, then up to three small numbers that tell it apart. */
private fun key(kind: Int, a: Int = 0, b: Int = 0, d: Int = 0) = ((kind * 64 + a) * 64 + b) * 128 + d

private object K {
    const val Head = 1; const val HairFront = 2; const val HairBack = 3; const val HairLine = 4; const val Torso = 5
    const val Mouth = 6; const val Brow = 7; const val Hat = 9; const val Top = 10; const val Star = 11
}

/** Strokes, made once per width (in tenths of a unit). */
private object Pens {
    private val made = arrayOfNulls<Stroke>(64)
    fun of(w: Float): Stroke {
        val i = (w * 10f).toInt().coerceIn(1, 63)
        return made[i] ?: Stroke(i / 10f, cap = StrokeCap.Round, join = StrokeJoin.Round).also { made[i] = it }
    }
}

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) = drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)

/** A filled shape with its outline. */
private fun DrawScope.shape(p: Path, fill: Color, edge: Color = fill.edge(), w: Float = 1.1f) {
    drawPath(p, fill)
    drawPath(p, edge, style = Pens.of(w))
}

/** A filled circle with its outline. */
private fun DrawScope.dot(fill: Color, x: Float, y: Float, r: Float, edge: Color = fill.edge(), w: Float = 1.1f) {
    drawCircle(edge, r + w / 2f, Offset(x, y))
    drawCircle(fill, r - w / 2f + 0.01f, Offset(x, y))
}

private fun Path.star(cx: Float, cy: Float, r: Float, inner: Float, points: Int) {
    for (i in 0 until points * 2) {
        val rr = if (i % 2 == 0) r else r * inner
        val a = (-90f + i * 180f / points) * PI.toFloat() / 180f
        val x = cx + cos(a) * rr
        val y = cy + sin(a) * rr
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A sparkle or star, centred on (0, 0) with radius 1: draw it with a translate and a scale. */
private fun starPath(points: Int, inner: Float): Path = Paths.of(key(K.Star, points, (inner * 20).toInt())) { star(0f, 0f, 1f, inner, points) }

private fun DrawScope.starAt(x: Float, y: Float, r: Float, color: Color, points: Int = 5, inner: Float = 0.45f, edge: Color? = null) {
    if (r <= 0.05f) return
    withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) {
        drawPath(starPath(points, inner), color)
        if (edge != null) drawPath(starPath(points, inner), edge, style = Stroke(1.1f / r, join = StrokeJoin.Round))
    }
}

// --- The head -----------------------------------------------------------------------------------------------------

/**
 * A face shape: the crown's half width [hw] (its widest, at [mid]), its [top], the jaw's half width [jawW] at [jawY], how
 * broad the chin is ([chinW]) and where it ends ([chin]). The six differ mostly below the eyes, where a face's shape shows.
 */
private class Head(val shape: Int, val hw: Float, val top: Float, val mid: Float, val jawW: Float, val jawY: Float, val chinW: Float, val chin: Float) {
    val cx = 50f
    val left get() = cx - hw
    val right get() = cx + hw
    val eyeY get() = top + (chin - top) * 0.545f
    val dx get() = 8.6f + hw * 0.07f
    val mouthY get() = eyeY + (chin - eyeY) * 0.6f
    val noseY get() = eyeY + (mouthY - eyeY) * 0.5f

    /** Hair and hats are drawn for the default crown (top 16, widest 25 each side at 41) and stretched to this one. */
    inline fun crown(scope: DrawScope, block: DrawScope.() -> Unit) = scope.withTransform({
        translate(0f, top - 16f)
        scale(hw / 25f, (mid - top) / 25f, Offset(50f, 16f))
    }, block)

    fun path(): Path = Paths.of(key(K.Head, shape)) {
        // The right half from the crown down to the chin, three curves meeting smoothly; the left is its mirror.
        val w = hw
        val t = top
        var dX = chinW - jawW
        var dY = chin - jawY + 5f
        val n = sqrt(dX * dX + dY * dY)
        dX /= n; dY /= n
        val a = (jawY - mid) * 0.38f
        val b = (chin - jawY) * 0.62f
        val p = floatArrayOf(
            cx, t,
            cx + w * 0.56f, t, cx + w, t + 0.44f * (mid - t), cx + w, mid,
            cx + w, mid + (jawY - mid) * 0.42f, cx + jawW - dX * a, jawY - dY * a, cx + jawW, jawY,
            cx + jawW + dX * b, jawY + dY * b, cx + chinW, chin, cx, chin,
        )
        fun x(i: Int) = p[i * 2]
        fun y(i: Int) = p[i * 2 + 1]
        fun mx(i: Int) = 2 * cx - p[i * 2]
        moveTo(x(0), y(0))
        cubicTo(x(1), y(1), x(2), y(2), x(3), y(3))
        cubicTo(x(4), y(4), x(5), y(5), x(6), y(6))
        cubicTo(x(7), y(7), x(8), y(8), x(9), y(9))
        cubicTo(mx(8), y(8), mx(7), y(7), mx(6), y(6))
        cubicTo(mx(5), y(5), mx(4), y(4), mx(3), y(3))
        cubicTo(mx(2), y(2), mx(1), y(1), x(0), y(0))
        close()
    }

    companion object {
        private val all = arrayOf(
            Head(0, 25f, 16f, 41f, 20.5f, 60.5f, 9f, 69f), // round
            Head(1, 23f, 14.5f, 40f, 17f, 61.5f, 5.5f, 71f), // oval
            Head(2, 25.5f, 16f, 41f, 24.5f, 62f, 14.5f, 69f), // soft square
            Head(3, 26.5f, 15.5f, 39.5f, 15.5f, 61f, 2f, 70.5f), // heart
            Head(4, 22f, 12.5f, 39f, 19.5f, 65f, 8f, 74f), // long
            Head(5, 28.5f, 18f, 42f, 25.5f, 59.5f, 12f, 67.5f), // wide
        )
        fun of(shape: Int): Head = all[shape.coerceIn(0, all.size - 1)]
    }
}

private fun DrawScope.character(s: AvatarSpec, pose: AvatarPose) {
    val head = Head.of(s.shape)
    val skin = c(AvatarColors.skin[s.skin])
    val hair = c(AvatarColors.hair[s.hairColor])
    val cloth = c(AvatarColors.top[s.topColor])
    // Lines a shade of the skin, so they belong to the face; the deepest tones get a darker line to still show.
    val ink = if (skin.luminance() < 0.1f) Color(0xFF12090E) else lerp(Color(0xFF2A1D2A), skin, 0.12f)
    Wardrobe.ink.apply {
        this.cloth = cloth; this.hair = hair; this.skin = skin; this.ink = ink
        iris = c(AvatarColors.eyes[s.eyeColor]); topColor = s.topColor
    }

    // The feet stay on the ground; everything above them rises a little as one foot passes the other (a walk's bob),
    // and sinks onto the seat when sitting.
    legs(s, pose, cloth, skin)
    val rise = pose.walk * kotlin.math.abs(sin(pose.phase)) * 1.8f - pose.sit.coerceIn(0f, 1f) * 7f
    translate(0f, -rise) { upper(s, pose, head, skin, hair, cloth, ink) }
    if (pose.sit > 0.3f) knees(s, pose, cloth, skin)
}

private fun DrawScope.upper(s: AvatarSpec, pose: AvatarPose, head: Head, skin: Color, hair: Color, cloth: Color, ink: Color) {
    // A turned head: the face slides towards where it looks (the nose a little further), the hair follows a little,
    // and the ears slip round the sides (the far one behind the head).
    val turn = pose.turn.coerceIn(-1f, 1f)
    val tx = turn * 3.2f
    val hat = Wardrobe.hats[s.headwear, head.shape]
    val hairPart = Wardrobe.hair[s.hair]
    val top = Wardrobe.tops[s.top]
    // A hat may hide the hair above its brim (and what sits up there: a bun, a top-knot), or all of it.
    val hides = hat?.hidesHair == true
    val clipY = hat?.clipY ?: Float.NaN

    // Behind everything: a cape, a big brim, long hair, tails, a bun.
    top?.draw(this, WL.Back)
    head.crown(this) {
        hat?.draw(this, WL.Back)
        if (!hides) underHat(clipY) { if (hairPart != null) hairPart.draw(this, WL.Back) else backHair(s.hair, hair, skin) }
    }

    // The body, the arms behind it, the hands in front.
    body(s, pose, cloth, skin)
    head.crown(this) {
        if (!hides) { if (hairPart != null) hairPart.draw(this, WL.Hanging) else hangingHair(s.hair, hair) }
        hat?.draw(this, WL.BehindHead)
    }
    hat?.draw(this, WL.BehindHeadBody)

    // Ears, the head, a soft light on the brow.
    val skinEdge = skin.edge(0.3f)
    for (side in SIDES) {
        val ex = head.cx + side * (head.hw - 0.8f - kotlin.math.abs(turn) * (if (side * turn > 0f) 4.6f else 1.4f))
        dot(skin, ex, head.eyeY + 2.6f, 4.4f, skinEdge)
        drawArc(skin.deep(0.16f), if (side < 0) 110f else -70f, 140f, false, Offset(ex - 2.2f, head.eyeY + 0.2f), Size(4.4f, 5f), style = Pens.of(1.1f))
    }
    val hp = head.path()
    drawPath(hp, skin)
    clipPath(hp) {
        // The hair's soft shadow on the brow, and a little light on the cheeks.
        if (!hides) head.crown(this) {
            underHat(clipY) {
                val sh = hairPart?.shadow
                if (sh != null) translate(0f, 1.8f) { drawPath(sh, skin.edge(0.2f).copy(alpha = 0.45f)) }
                else if (hairPart == null) hairShadow(s.hair, skin)
            }
        }
        oval(Color.White.copy(alpha = 0.10f), head.cx + tx * 0.5f, head.eyeY - 12f, head.hw * 0.6f, 6f)
    }
    drawPath(hp, skinEdge, style = Pens.of(1.2f))
    translate(tx * 0.3f, 0f) { earrings(s.earrings, head) }

    // The face.
    val ey = head.eyeY
    val dx = head.dx
    val extra = Wardrobe.extras[s.faceExtra, head.shape]
    translate(tx, 0f) {
        // Eyes first: the smiling ones push the cheek up, and the blush sits on it.
        eyes(s.eyes, head, c(AvatarColors.eyes[s.eyeColor]), ink, skin, pose)
        // The far cheek keeps inside the face's edge when it turns.
        val cheekIn = kotlin.math.abs(tx) * 0.45f
        if (s.cheeks == 1 || s.cheeks == 3) for (side in SIDES) {
            oval(Blush.copy(alpha = 0.38f), head.cx + side * (dx + 4.2f - if (side * turn > 0f) cheekIn else 0f), ey + 6.2f, 3.9f, 2.3f)
        }
        if (s.cheeks == 2 || s.cheeks == 3) for (side in SIDES) {
            val fx = head.cx + side * (dx + 3.4f - if (side * turn > 0f) cheekIn else 0f)
            drawCircle(skin.edge(0.32f), 0.62f, Offset(fx - 1.9f, ey + 5.4f))
            drawCircle(skin.edge(0.32f), 0.62f, Offset(fx + 0.2f, ey + 6.6f))
            drawCircle(skin.edge(0.32f), 0.62f, Offset(fx + 2.1f, ey + 5.3f))
        }
        if (extra != null && !extra.last) extra.draw(this, WL.Face)
        brows(s.brows, head, if (s.hair == 0) lerp(hair, ink, 0.4f) else lerp(hair.deep(0.3f), ink, 0.15f), pose.cheer)
        // A small, simple nose.
        drawArc(skin.edge(0.28f), 25f, 130f, false, Offset(head.cx - 1.9f + tx * 0.3f, head.noseY - 1.8f), Size(3.8f, 3f), style = Pens.of(1.2f))
        Wardrobe.beards[s.beard, head.shape]?.draw(this, WL.Face)
        mouth(s.mouth, head, ink, pose.cheer)
        glasses(s.glasses, head)
    }

    // On top: the hair, then whatever is worn on the head.
    translate(tx * 0.35f, 0f) {
        head.crown(this) {
            if (!hides) underHat(clipY) { if (hairPart != null) hairPart.draw(this, WL.Front) else frontHair(s.hair, hair, skin) }
            if (hat != null) hat.draw(this, WL.Front) else headwear(s.headwear)
        }
    }
    if (extra != null && extra.last) translate(tx, 0f) { extra.draw(this, WL.Face) }
    // A waving hand comes in front of everything.
    if (pose.wave > 0.02f) hand(armWrist(pose, front = true), skin, open = pose.wave > 0.5f)

    if (pose.cheer > 0.01f) {
        val a = pose.cheer
        val spark = Color(0xFFFFE27A).copy(alpha = a)
        starAt(head.left - 4f, head.top + 10f - 6f * a, 4.2f * a, spark, 4, 0.4f)
        starAt(head.right + 5f, head.top + 18f - 4f * a, 3.2f * a, spark, 4, 0.4f)
        starAt(head.right - 3f, head.top - 4f - 5f * a, 2.4f * a, Color.White.copy(alpha = a), 4, 0.4f)
    }
}

// --- Body ---------------------------------------------------------------------------------------------------------

/** The first outfits, drawn here; the rest (from 5 on: the blazer, the scarf...) come from the wardrobe. */
private object Top { const val Tee = 0; const val Hoodie = 1; const val Stripes = 2; const val Overalls = 3; const val Dress = 4 }

private val Denim = Color(0xFF55618A)
private val Shirt = Color(0xFFF7F3EA)
private val Shoe = Color(0xFF3F3752)

private fun torsoPath(dress: Boolean): Path = Paths.of(key(K.Torso, if (dress) 1 else 0)) {
    moveTo(41f, 70.5f)
    cubicTo(36f, 71f, 33.2f, 73.4f, 32.8f, 78f)
    if (dress) {
        cubicTo(32.6f, 83f, 34f, 87f, 34.6f, 89.5f)
        cubicTo(31.4f, 95f, 27.6f, 101f, 27f, 104.6f)
        quadraticTo(27f, 107.6f, 30.6f, 107.6f)
        lineTo(69.4f, 107.6f)
        quadraticTo(73f, 107.6f, 73f, 104.6f)
        cubicTo(72.4f, 101f, 68.6f, 95f, 65.4f, 89.5f)
        cubicTo(66f, 87f, 67.4f, 83f, 67.2f, 78f)
    } else {
        cubicTo(32.5f, 86f, 33.6f, 95f, 34.6f, 100.6f)
        quadraticTo(35f, 103.6f, 38.2f, 103.6f)
        lineTo(61.8f, 103.6f)
        quadraticTo(65f, 103.6f, 65.4f, 100.6f)
        cubicTo(66.4f, 95f, 67.5f, 86f, 67.2f, 78f)
    }
    cubicTo(66.8f, 73.4f, 64f, 71f, 59f, 70.5f)
    quadraticTo(50f, 69.6f, 41f, 70.5f)
    close()
}


/**
 * How far an arm swings forward in a walk (-1 back .. 1 forward), opposite to the leg on its side: the front arm is on
 * the right, like the right leg.
 */
private fun armSwing(pose: AvatarPose, front: Boolean): Float = pose.walk * cos(pose.phase) * (if (front) 1f else -1f)

/** Where a hand is: hanging at the side, swinging when walking, or up in a wave ([front] is the waving one). */
private fun armWrist(pose: AvatarPose, front: Boolean): Offset {
    val side = if (front) 1f else -1f
    // Forward is the way the character faces: sideways on screen when turned, towards us (a little up) when not.
    val swing = armSwing(pose, front)
    val turn = pose.turn.coerceIn(-1f, 1f)
    val rest = Offset(50f + side * 18.6f + swing * (turn * 5.6f + side * 0.6f), 97.2f - kotlin.math.abs(swing) * (0.6f + 0.8f * (1f - kotlin.math.abs(turn))))
    if (!front) return rest
    val raise = pose.wave.coerceIn(0f, 1f)
    if (raise <= 0f) return rest
    // The forearm rocks about the elbow.
    val a = (8f + pose.waveSwing * 12f) * PI.toFloat() / 180f
    val ex = 79f
    val ey = 85f
    val wx = ex + sin(a) * 21f
    val wy = ey - cos(a) * 21f
    return Offset(rest.x + (wx - rest.x) * raise, rest.y + (wy - rest.y) * raise)
}

private fun armElbow(pose: AvatarPose, front: Boolean): Offset {
    val side = if (front) 1f else -1f
    val swing = armSwing(pose, front)
    val rest = Offset(50f + side * 17.8f + swing * pose.turn.coerceIn(-1f, 1f) * 2f, 87.5f)
    if (!front) return rest
    val raise = pose.wave.coerceIn(0f, 1f)
    return Offset(rest.x + (79f - rest.x) * raise, rest.y + (85f - rest.y) * raise)
}

/** An arm as one soft curve from inside the shoulder, through the elbow, to the wrist; [sleeve] 1 is a long sleeve. */
private fun DrawScope.arm(p: Path, front: Boolean, pose: AvatarPose, cloth: Color, skin: Color, sleeve: Float) {
    val sx = if (front) 60f else 40f
    val sy = 76.5f
    val e = armElbow(pose, front)
    val w = armWrist(pose, front)
    // The control point that sends the curve through the elbow.
    val qx = 2f * e.x - (sx + w.x) / 2f
    val qy = 2f * e.y - (sy + w.y) / 2f
    p.reset()
    p.moveTo(sx, sy)
    p.quadraticTo(qx, qy, w.x, w.y)
    if (sleeve >= 1f) {
        drawPath(p, cloth.edge(), style = Pens.of(9.2f))
        drawPath(p, cloth, style = Pens.of(7f))
    } else {
        drawPath(p, skin.edge(0.3f), style = Pens.of(7.6f))
        drawPath(p, skin, style = Pens.of(5.4f))
        if (sleeve <= 0f) return
        // The sleeve: the first part of the same curve.
        val t = sleeve
        val cx = sx + (qx - sx) * t
        val cy = sy + (qy - sy) * t
        val bx = (1 - t) * (1 - t) * sx + 2 * (1 - t) * t * qx + t * t * w.x
        val by = (1 - t) * (1 - t) * sy + 2 * (1 - t) * t * qy + t * t * w.y
        p.reset()
        p.moveTo(sx, sy)
        p.quadraticTo(cx, cy, bx, by)
        drawPath(p, cloth.edge(), style = Pens.of(10f))
        drawPath(p, cloth, style = Pens.of(7.8f))
    }
}

/** A mitten hand: a soft round palm and a thumb; [open] is a waving palm, a little larger, thumb out. */
private fun DrawScope.hand(at: Offset, skin: Color, open: Boolean = false) {
    val edge = skin.edge(0.3f)
    if (open) {
        oval(edge, at.x, at.y - 1f, 4.4f, 5.1f)
        oval(edge, at.x - 4f, at.y + 0.8f, 2.6f, 1.75f)
        oval(skin, at.x, at.y - 1f, 3.8f, 4.5f)
        oval(skin, at.x - 4f, at.y + 0.8f, 2f, 1.15f)
        line(edge.copy(alpha = 0.5f), at.x - 1.2f, at.y - 4.4f, at.x - 1.2f, at.y - 2f, 0.6f)
        line(edge.copy(alpha = 0.5f), at.x + 1.2f, at.y - 4.4f, at.x + 1.2f, at.y - 2f, 0.6f)
        return
    }
    val tx = if (at.x < 50f) at.x + 2.6f else at.x - 2.6f
    drawCircle(edge, 4.15f, Offset(at.x, at.y + 0.6f))
    drawCircle(edge, 2.05f, Offset(tx, at.y - 2f))
    drawCircle(skin, 3.55f, Offset(at.x, at.y + 0.6f))
    drawCircle(skin, 1.45f, Offset(tx, at.y - 2f))
}

private fun legColor(s: AvatarSpec, cloth: Color, skin: Color): Color {
    Wardrobe.tops[s.top]?.let { return it.legs?.of(Wardrobe.ink) ?: Denim }
    return when (s.top) { Top.Dress -> skin; Top.Overalls -> cloth; else -> Denim }
}

/**
 * Legs and shoes. Walking, each foot in turn lifts and swings forward (sideways on screen when the character is turned,
 * a touch down towards us when not) while the other pushes back, so the feet never slide over the ground.
 */
private fun DrawScope.legs(s: AvatarSpec, pose: AvatarPose, cloth: Color, skin: Color) {
    val legs = legColor(s, cloth, skin)
    val turn = pose.turn.coerceIn(-1f, 1f)
    val sn = sin(pose.phase)
    val cs = cos(pose.phase)
    val sitting = pose.sit.coerceIn(0f, 1f)
    // An outfit's own legs (boots, shorts and socks, paws...): each side moves with its foot.
    val drawn = Wardrobe.tops[s.top]?.takeIf { it.has(WL.Legs) }
    if (drawn != null) {
        fun stepX(side: Float) = side * sitting * 1.6f + (-side * cs * pose.walk) * turn * 4f
        fun stepY(side: Float) = (-side * cs * pose.walk) * (1f - kotlin.math.abs(turn)) * 0.9f - pose.walk * kotlin.math.max(0f, side * sn) * 4.8f
        Shift.set(stepX(-1f), stepY(-1f), stepX(1f), stepY(1f), 0f, 0f)
        drawn.draw(this, WL.Legs)
        return
    }
    for (side in SIDES) {
        val lift = pose.walk * kotlin.math.max(0f, side * sn) * 4.8f
        val fwd = -side * cs * pose.walk
        val x = 50f + side * (4.6f + sitting * 1.6f) + fwd * turn * 4f
        val y = fwd * (1f - kotlin.math.abs(turn)) * 0.9f - lift
        val top = 99f + sitting * 4f
        drawRoundRect(legs.edge(0.3f), Offset(x - 4.1f, top + y), Size(8.2f, 112.6f - top), CornerRadius(3f))
        drawRoundRect(legs, Offset(x - 3.1f, top + y), Size(6.2f, 111.8f - top), CornerRadius(2.4f))
        val toe = side * 0.9f * (1f - kotlin.math.abs(turn)) + turn * 1.6f
        oval(Shoe, x + toe, 113.4f + y, 5.2f + kotlin.math.abs(turn) * 0.6f, 3.3f)
        oval(Color.White.copy(alpha = 0.22f), x + toe * 0.3f, 112.2f + y, 2.4f, 1f)
    }
}

/** Sitting: the lap, the thighs coming towards us over the seat (under a dress, its skirt). */
private fun DrawScope.knees(s: AvatarSpec, pose: AvatarPose, cloth: Color, skin: Color) {
    val a = pose.sit.coerceIn(0f, 1f)
    if (a < 0.3f) return
    val lap = Wardrobe.tops[s.top]?.lap?.of(Wardrobe.ink) ?: if (s.top == Top.Dress) cloth else legColor(s, cloth, skin)
    val top = 112f - 7.6f * a
    drawRoundRect(lap.edge(0.3f), Offset(36.2f, top - 0.8f), Size(27.6f, 112.6f - top), CornerRadius(4f))
    drawRoundRect(lap, Offset(37f, top), Size(26f, 111.6f - top), CornerRadius(3.4f))
    line(lap.edge(0.3f).copy(alpha = 0.7f), 50f, top + 1.6f, 50f, 111f, 0.9f)
    oval(Color.White.copy(alpha = 0.16f), 43f, top + 1.8f, 4f, 1.2f)
    oval(Color.White.copy(alpha = 0.16f), 57f, top + 1.8f, 4f, 1.2f)
}

/** An outfit from the wardrobe: its sleeves, its own torso and what is drawn inside and over it, then the neck and hands. */
private fun DrawScope.worn(top: WPart, pose: AvatarPose, skin: Color) {
    val k = Wardrobe.ink
    val sleeveColor = top.sleeveColor?.of(k) ?: k.cloth
    arm(Paths.armBack, false, pose, sleeveColor, skin, top.sleeve)
    arm(Paths.armFront, true, pose, sleeveColor, skin, top.sleeve)
    armCurve(pose)

    val torso = top.torso ?: torsoPath(false)
    val base = top.base?.of(k) ?: k.cloth
    drawPath(torso, base)
    clipPath(torso) {
        top.draw(this, WL.Inside)
        oval(Color.White.copy(alpha = 0.13f), 41f, 76f, 7f, 3.2f)
    }
    drawPath(torso, base.edge(), style = Pens.of(1.1f))
    top.draw(this, WL.OverTorso)
    top.draw(this, WL.BehindNeck)
    drawRoundRect(skin.edge(0.3f), Offset(44.6f, 62f), Size(10.8f, 12.6f), CornerRadius(5f))
    drawRoundRect(skin, Offset(45.4f, 62f), Size(9.2f, 11.8f), CornerRadius(4.4f))
    oval(skin.edge(0.25f).copy(alpha = 0.35f), 50f, 68.5f, 4.6f, 1.8f)
    top.draw(this, WL.Neck)
    hand(armWrist(pose, front = false), skin)
    if (pose.wave <= 0.02f) hand(armWrist(pose, front = true), skin)
    top.draw(this, WL.Front)
}

private fun DrawScope.body(s: AvatarSpec, pose: AvatarPose, cloth: Color, skin: Color) {
    Wardrobe.tops[s.top]?.let { worn(it, pose, skin); return }
    val dress = s.top == Top.Dress
    val long = s.top == Top.Hoodie || s.top == Top.Stripes
    val sleeveColor = if (s.top == Top.Overalls) Shirt else cloth
    val sleeve = if (long) 1f else 0.34f

    // Arms first, so the body hides where they join it.
    arm(Paths.armBack, false, pose, sleeveColor, skin, sleeve)
    arm(Paths.armFront, true, pose, sleeveColor, skin, sleeve)

    val torso = torsoPath(dress)
    val base = if (s.top == Top.Overalls) Shirt else cloth
    drawPath(torso, base)
    clipPath(torso) {
        when (s.top) {
            Top.Tee -> {
                drawRect(cloth.deep(0.1f), Offset(30f, 100.4f), Size(40f, 4f))
                starAt(55.5f, 85f, 3.2f, cloth.light(0.55f), 5, 0.5f)
            }
            Top.Hoodie -> {
                drawPath(Paths.of(key(K.Top, 1)) { addRoundRect(androidx.compose.ui.geometry.RoundRect(40f, 91f, 60f, 101f, CornerRadius(4.5f))) }, cloth.deep(0.1f))
                line(cloth.edge(0.25f), 40.5f, 92f, 59.5f, 92f, 0.8f)
                drawRect(cloth.deep(0.12f), Offset(30f, 101f), Size(40f, 4f))
            }
            Top.Stripes -> {
                for (i in 0 until 4) drawRect(cloth.light(0.6f), Offset(30f, 79f + i * 6.4f), Size(40f, 2.8f))
                drawRect(cloth.deep(0.12f), Offset(30f, 100.4f), Size(40f, 4f))
            }
            Top.Overalls -> {
                drawPath(Paths.of(key(K.Top, 3)) {
                    moveTo(39.5f, 85f); lineTo(60.5f, 85f); lineTo(62f, 92f); quadraticTo(66f, 92f, 67f, 98f); lineTo(67f, 106f); lineTo(33f, 106f); lineTo(33f, 98f)
                    quadraticTo(34f, 92f, 38f, 92f); close()
                }, cloth)
                drawRoundRect(cloth.deep(0.12f), Offset(45f, 88.5f), Size(10f, 6f), CornerRadius(2f))
                line(cloth, 38.5f, 71.5f, 40.5f, 86f, 3.4f)
                line(cloth, 61.5f, 71.5f, 59.5f, 86f, 3.4f)
                dot(Color(0xFFF2CF5B), 40.6f, 86f, 1.3f, Color(0xFFB98F2E), 0.7f)
                dot(Color(0xFFF2CF5B), 59.4f, 86f, 1.3f, Color(0xFFB98F2E), 0.7f)
                drawPath(Paths.of(key(K.Top, 3)) { }, cloth.edge(), style = Pens.of(0.9f))
            }
            Top.Dress -> {
                drawRect(cloth.light(0.45f), Offset(30f, 87.6f), Size(40f, 3f))
                for (i in 0 until 3) drawCircle(cloth.light(0.5f), 1.3f, Offset(40f + i * 10f, 99f + (i % 2) * 2.5f))
            }
        }
        // A soft light across the shoulders.
        oval(Color.White.copy(alpha = 0.13f), 41f, 76f, 7f, 3.2f)
    }
    drawPath(torso, base.edge(), style = Pens.of(1.1f))

    // The hood behind the neck, then the neck.
    if (s.top == Top.Hoodie) {
        oval(cloth.edge(), 50f, 71.6f, 12.6f, 5.4f)
        oval(cloth.deep(0.12f), 50f, 71.6f, 11.6f, 4.5f)
    }
    drawRoundRect(skin.edge(0.3f), Offset(44.6f, 62f), Size(10.8f, 12.6f), CornerRadius(5f))
    drawRoundRect(skin, Offset(45.4f, 62f), Size(9.2f, 11.8f), CornerRadius(4.4f))
    oval(skin.edge(0.25f).copy(alpha = 0.35f), 50f, 68.5f, 4.6f, 1.8f)
    when (s.top) {
        Top.Tee, Top.Dress, Top.Stripes -> drawArc(if (s.top == Top.Stripes) cloth.light(0.6f) else cloth.deep(0.15f), 15f, 150f, false, Offset(44f, 67f), Size(12f, 7.4f), style = Pens.of(1.6f))
        Top.Hoodie -> {
            line(Color.White.copy(alpha = 0.9f), 46.6f, 76f, 46f, 84f, 0.9f)
            line(Color.White.copy(alpha = 0.9f), 53.4f, 76f, 54f, 84f, 0.9f)
            drawCircle(Color.White, 0.9f, Offset(46f, 84.4f))
            drawCircle(Color.White, 0.9f, Offset(54f, 84.4f))
        }
        Top.Overalls -> drawArc(Shirt.deep(0.12f), 15f, 150f, false, Offset(44f, 67f), Size(12f, 7.4f), style = Pens.of(1.4f))
    }

    // Hands in front of the body (a waving one is drawn last of all).
    hand(armWrist(pose, front = false), skin)
    if (pose.wave <= 0.02f) hand(armWrist(pose, front = true), skin)
}

// --- Face ---------------------------------------------------------------------------------------------------------

private fun DrawScope.eyes(style: Int, h: Head, iris: Color, ink: Color, skin: Color, pose: AvatarPose) {
    val open = (1f - pose.blink).coerceIn(0f, 1f)
    val lx = pose.lookX.coerceIn(-1f, 1f) * 1.1f
    val ly = pose.lookY.coerceIn(-1f, 1f) * 0.8f
    val y = h.eyeY
    val drawn = Wardrobe.eyes[style]
    if (drawn != null) {
        if (!drawn.closed && open < 0.22f) {
            for (side in SIDES) drawArc(ink, 25f, 130f, false, Offset(h.cx + side * h.dx - 3.6f, y - 2.6f), Size(7.2f, 4.4f), style = Pens.of(1.6f))
            return
        }
        // Drawn for the default face: each eye moves to this face's spacing and height, and with the glance.
        val gx = if (drawn.closed) 0f else lx * 0.45f
        val gy = (if (drawn.closed) 0f else ly * 0.45f) + y - Wardrobe.EYE_Y0
        val spread = h.dx - Wardrobe.DX0
        Shift.set(gx - spread, gy, gx + spread, gy, gx, gy, if (drawn.closed) 1f else open, Wardrobe.EYE_Y0 + 1f)
        drawn.draw(this, WL.Face)
        return
    }
    for (side in SIDES) {
        val ex = h.cx + side * h.dx
        val happy = style == 2 || (style == 7 && side > 0)
        if (happy) {
            drawArc(ink, 205f, 130f, false, Offset(ex - 3.8f, y - 2.2f), Size(7.6f, 6f), style = Pens.of(1.8f))
            continue
        }
        if (open < 0.22f) {
            // A blink: the lid, a gentle curve.
            drawArc(ink, 25f, 130f, false, Offset(ex - 3.6f, y - 2.6f), Size(7.2f, 4.4f), style = Pens.of(1.6f))
            continue
        }
        withTransform({ scale(1f, open, Offset(ex, y + 1f)) }) {
            when (style) {
                0 -> {
                    oval(ink, ex + lx * 0.5f, y + ly * 0.5f, 2.3f, 3f)
                    drawCircle(Color.White, 0.8f, Offset(ex + lx * 0.5f + 0.7f, y + ly * 0.5f - 1.1f))
                }
                3 -> {
                    // Sleepy: a heavy lid over the top half.
                    clipRect(ex - 6f, y - 0.4f, ex + 6f, y + 6f) { bright(ex, y, 3.4f, 4.1f, iris, ink, lx, ly, 0f) }
                    drawArc(ink, 190f, 160f, false, Offset(ex - 4f, y - 2.4f), Size(8f, 4.8f), style = Pens.of(1.5f))
                }
                4 -> {
                    bright(ex, y, 3.9f, 4.7f, iris, ink, lx, ly, side)
                    starAt(ex + lx + 1.2f, y + ly - 1.4f, 1.9f, Color.White, 4, 0.38f)
                    drawCircle(Color.White, 0.75f, Offset(ex + lx - 1.5f, y + ly + 1.9f))
                }
                5 -> {
                    // Smiling eyes: the cheeks push up from below.
                    bright(ex, y, 3.6f, 4.2f, iris, ink, lx, ly, side)
                    oval(skin, ex, y + 5.6f, 5.4f, 3.2f)
                    drawArc(ink, 205f, 130f, false, Offset(ex - 3.4f, y + 2.6f), Size(6.8f, 3.6f), style = Pens.of(0.9f))
                }
                6 -> {
                    bright(ex, y, 3.5f, 4.3f, iris, ink, lx, ly, side)
                    line(ink, ex + side * 3.1f, y - 2.4f, ex + side * 5.1f, y - 4.2f, 1.1f)
                    line(ink, ex + side * 1.8f, y - 3.9f, ex + side * 3.1f, y - 6f, 1.1f)
                }
                else -> bright(ex, y, 3.5f, 4.3f, iris, ink, lx, ly, side)
            }
        }
    }
}

/** The round, bright eye most styles build on: a coloured iris with a dark rim, a pupil and two catchlights. */
private fun DrawScope.bright(ex: Float, ey: Float, rx: Float, ry: Float, iris: Color, ink: Color, lx: Float, ly: Float, side: Float) {
    val x = ex + lx * 0.45f
    val y = ey + ly * 0.45f
    oval(ink, x, y, rx + 0.55f, ry + 0.55f)
    oval(iris.deep(0.18f), x, y, rx - 0.1f, ry - 0.1f)
    oval(iris.light(0.18f), x, y + ry * 0.32f, rx * 0.72f, ry * 0.5f)
    oval(ink, x, y + 0.2f, rx * 0.46f, ry * 0.48f)
    drawCircle(Color.White, rx * 0.36f, Offset(x + rx * 0.32f, y - ry * 0.36f))
    drawCircle(Color.White.copy(alpha = 0.85f), rx * 0.16f, Offset(x - rx * 0.36f, y + ry * 0.42f))
    // The upper lid, a little heavier than the rim.
    drawArc(ink, 200f, 140f, false, Offset(x - rx - 0.5f, y - ry - 0.5f), Size(rx * 2 + 1f, ry * 2 + 1f), style = Pens.of(1.3f))
}

private fun DrawScope.brows(style: Int, h: Head, color: Color, cheer: Float) {
    if (style == 0) return
    val y = h.eyeY - 7.4f - cheer * 1.2f
    for (side in SIDES) {
        val bx = h.cx + side * (h.dx + 0.4f)
        val sideI = if (side < 0) 0 else 1
        val p = Paths.of(key(K.Brow, style, sideI, (h.eyeY * 2).toInt())) {
            val by = h.eyeY - 7.4f
            when (style) {
                2 -> { moveTo(bx - 3f, by); lineTo(bx + 3f, by) }
                3 -> { moveTo(bx - 3f, by + 0.6f); quadraticTo(bx, by - 1f, bx + 3f, by + 0.6f) }
                4 -> { moveTo(bx - 3.4f, by - 0.2f); quadraticTo(bx, by - 3.2f, bx + 3.4f, by - 0.2f) }
                5 -> { moveTo(bx - side * 3.4f, by + 0.5f); quadraticTo(bx, by - 0.8f, bx + side * 3.4f, by - 0.6f) }
                else -> { moveTo(bx - 3.4f, by + 0.7f); quadraticTo(bx, by - 1.9f, bx + 3.4f, by + 0.5f) }
            }
        }
        val w = when (style) { 3 -> 2.5f; 4 -> 1.2f; else -> 1.5f }
        translate(0f, y - (h.eyeY - 7.4f)) { drawPath(p, color, style = Pens.of(w)) }
    }
}

private fun DrawScope.mouth(style: Int, h: Head, ink: Color, cheer: Float) {
    // A cheer opens any smile into a grin for a moment.
    val m = if (cheer > 0.4f && style in SMILES) 1 else style
    val cx = h.cx
    val y = h.mouthY
    when (m) {
        1, 6 -> {
            val w = if (m == 6) 5.6f else 4.6f
            val d = if (m == 6) 5.2f else 5f
            val p = mouthPath(m, h.shape) {
                moveTo(cx - w, y - 1.2f)
                quadraticTo(cx, y - 0.2f, cx + w, y - 1.2f)
                cubicTo(cx + w * 0.95f, y + d * 0.7f, cx + w * 0.5f, y + d, cx, y + d)
                cubicTo(cx - w * 0.5f, y + d, cx - w * 0.95f, y + d * 0.7f, cx - w, y - 1.2f)
                close()
            }
            drawPath(p, MouthIn)
            clipPath(p) {
                oval(Tongue, cx, y + d + 0.4f, w * 0.62f, d * 0.48f)
                if (m == 6) drawRect(Color.White, Offset(cx - w, y - 2f), Size(w * 2, 2.6f))
            }
            drawPath(p, ink, style = Pens.of(1.1f))
        }
        2 -> drawPath(mouthPath(m, h.shape) {
            moveTo(cx - 4.2f, y - 0.6f)
            quadraticTo(cx - 2.1f, y + 2.8f, cx, y + 0.1f)
            quadraticTo(cx + 2.1f, y + 2.8f, cx + 4.2f, y - 0.6f)
        }, ink, style = Pens.of(1.4f))
        3 -> {
            oval(ink, cx, y + 1f, 2.3f, 2.8f)
            oval(MouthIn, cx, y + 1f, 1.5f, 2f)
        }
        4 -> {
            drawPath(mouthPath(m, h.shape) { addRoundRect(androidx.compose.ui.geometry.RoundRect(cx - 0.4f, y + 0.6f, cx + 3.8f, y + 5.2f, CornerRadius(2.1f))) }, Tongue)
            line(Color(0xFFE06A80), cx + 1.7f, y + 1.6f, cx + 1.7f, y + 3.6f, 0.6f)
            drawPath(Paths.of(key(K.Mouth, 0, h.shape)) { moveTo(cx - 4.4f, y - 0.8f); quadraticTo(cx, y + 3.4f, cx + 4.4f, y - 0.8f) }, ink, style = Pens.of(1.5f))
        }
        5 -> drawPath(mouthPath(m, h.shape) { moveTo(cx - 2.6f, y + 0.2f); quadraticTo(cx, y + 1.9f, cx + 2.6f, y + 0.2f) }, ink, style = Pens.of(1.5f))
        7 -> {
            drawPath(mouthPath(m, h.shape) { moveTo(cx - 4f, y + 0.4f); quadraticTo(cx + 1.4f, y + 2.8f, cx + 4.4f, y - 1.4f) }, ink, style = Pens.of(1.5f))
            drawCircle(ink.copy(alpha = 0.5f), 0.55f, Offset(cx + 5.4f, y - 1.8f))
        }
        else -> drawPath(mouthPath(m, h.shape) { moveTo(cx - 4.4f, y - 0.8f); quadraticTo(cx, y + 3.4f, cx + 4.4f, y - 0.8f) }, ink, style = Pens.of(1.5f))
    }
}

private val SMILES = setOf(0, 2, 5, 7)

private fun DrawScope.glasses(style: Int, h: Head) {
    if (style == 0) return
    Wardrobe.glasses[style, h.shape]?.let { it.draw(this, WL.Face); return }
    val frame = Color(0xFF3A3346)
    val y = h.eyeY
    val dx = h.dx
    val cx = h.cx
    when (style) {
        1 -> {
            for (side in SIDES) {
                drawCircle(Color.White.copy(alpha = 0.14f), 5.4f, Offset(cx + side * dx, y))
                drawCircle(frame, 5.4f, Offset(cx + side * dx, y), style = Pens.of(1.3f))
            }
            drawArc(frame, 200f, 140f, false, Offset(cx - dx + 5f, y - 2.4f), Size(2 * dx - 10f, 3f), style = Pens.of(1.2f))
        }
        2 -> {
            for (side in SIDES) {
                drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(cx + side * dx - 5.8f, y - 4.4f), Size(11.6f, 8.8f), CornerRadius(2.6f))
                drawRoundRect(frame, Offset(cx + side * dx - 5.8f, y - 4.4f), Size(11.6f, 8.8f), CornerRadius(2.6f), style = Pens.of(1.4f))
            }
            line(frame, cx - dx + 5.8f, y - 1.4f, cx + dx - 5.8f, y - 1.4f, 1.3f)
        }
        3 -> {
            for (side in SIDES) {
                drawRoundRect(Color(0xFF2B2433), Offset(cx + side * dx - 6f, y - 4f), Size(12f, 8.6f), CornerRadius(4.2f))
                line(Color.White.copy(alpha = 0.45f), cx + side * dx - 3.4f, y - 1.8f, cx + side * dx - 0.8f, y - 1.8f, 1.1f)
            }
            line(Color(0xFF2B2433), cx - dx + 5f, y - 1.6f, cx + dx - 5f, y - 1.6f, 1.5f)
        }
        4 -> {
            for (side in SIDES) starAt(cx + side * dx, y + 0.4f, 6.8f, Color(0xFFFF8FC0).copy(alpha = 0.9f), 5, 0.56f, Color(0xFFD9558F))
            line(Color(0xFFD9558F), cx - dx + 5.4f, y - 1f, cx + dx - 5.4f, y - 1f, 1.2f)
        }
    }
}

private fun DrawScope.earrings(style: Int, h: Head) {
    if (style == 0) return
    Wardrobe.earrings[style, h.shape]?.let { it.draw(this, WL.Face); return }
    val gold = Color(0xFFF2C14E)
    for (side in SIDES) {
        val x = h.cx + side * (h.hw - 0.2f)
        val y = h.eyeY + 7f
        when (style) {
            1 -> dot(gold, x, y, 1.3f, gold.edge(), 0.6f)
            2 -> drawCircle(gold, 2.8f, Offset(x, y + 2.6f), style = Pens.of(1.1f))
            3 -> starAt(x, y + 2.2f, 2.8f, Color(0xFFFFE27A), 5, 0.45f, Color(0xFFD9A63A))
        }
    }
}

// --- Hair ---------------------------------------------------------------------------------------------------------
// Drawn on the default crown: top 16, sides 25 to 75 at their widest (y 41), eyes about y 44, brows 37, ears 21..29.

/**
 * The crown of the head, grown by [pad], from the left side at [y0] over the top to the right side at [y0]. [taper]
 * rounds the sides' ends in, as short hair meets the sideburns (the path then starts and ends that far in).
 */
private fun Path.dome(pad: Float, y0: Float, taper: Float = 0f) {
    val w = 25f + pad
    val t = 16f - pad
    val m = min(41f, y0 - taper * 1.6f)
    val c = t + 0.44f * (41f - t)
    if (taper > 0f) {
        moveTo(50f - w + taper, y0)
        quadraticTo(50f - w, y0, 50f - w, m)
    } else {
        moveTo(50f - w, y0)
        lineTo(50f - w, m)
    }
    cubicTo(50f - w, c, 50f - w * 0.56f, t, 50f, t)
    cubicTo(50f + w * 0.56f, t, 50f + w, c, 50f + w, m)
    if (taper > 0f) quadraticTo(50f + w, y0, 50f + w - taper, y0) else lineTo(50f + w, y0)
}

private object Hair {
    const val Buzz = 0; const val Crop = 1; const val Swept = 2; const val Bob = 3; const val Long = 4; const val Ponytail = 5; const val Bun = 6
    const val Curly = 7; const val Spiky = 8; const val SidePart = 9; const val Braids = 10; const val Afro = 11; const val Wavy = 12; const val Twins = 13
}

/** The hair over the brow and around the face, as one shape (null for the curly styles, drawn as curls). */
private fun frontPath(style: Int): Path? = when (style) {
    Hair.Curly, Hair.Afro -> null
    else -> Paths.of(key(K.HairFront, style)) {
        when (style) {
            Hair.Buzz -> {
                dome(0.9f, 41f, 1.4f)
                lineTo(74f, 41f)
                cubicTo(73.8f, 32.5f, 70.5f, 26.5f, 64f, 25.2f)
                quadraticTo(50f, 22f, 36f, 25.2f)
                cubicTo(29.5f, 26.5f, 26.2f, 32.5f, 26f, 41f)
            }
            Hair.Crop -> {
                dome(2.6f, 44f, 2.6f)
                lineTo(73.8f, 44f)
                cubicTo(74f, 35f, 71.5f, 29.5f, 67f, 27.6f)
                quadraticTo(63.5f, 28.4f, 61f, 31.2f)
                quadraticTo(58.5f, 28.6f, 55f, 28.4f)
                quadraticTo(52f, 29f, 49.5f, 31.6f)
                quadraticTo(47f, 28.8f, 43.5f, 28.4f)
                quadraticTo(40.5f, 28.8f, 38.2f, 31f)
                quadraticTo(36f, 28.4f, 33f, 27.8f)
                cubicTo(28.5f, 29.5f, 26.2f, 35f, 26.2f, 44f)
            }
            Hair.Swept -> {
                dome(3f, 45f, 2.8f)
                lineTo(73.6f, 45f)
                cubicTo(74.2f, 36f, 73f, 30f, 69f, 27f)
                cubicTo(63f, 30.5f, 57.4f, 32.6f, 52.4f, 33.8f)
                quadraticTo(52f, 31.8f, 50.2f, 31.4f)
                cubicTo(43f, 33.6f, 35.4f, 36.6f, 30.2f, 40.8f)
                quadraticTo(28.2f, 40.2f, 27.4f, 38f)
                cubicTo(26.6f, 40f, 26.3f, 42.5f, 26.4f, 45f)
            }
            Hair.Bob -> {
                dome(3.6f, 41f)
                lineTo(78.6f, 63f)
                quadraticTo(78.6f, 66.6f, 75f, 66.4f)
                cubicTo(74f, 58f, 74.6f, 46f, 73.6f, 38f)
                cubicTo(73f, 35f, 72f, 33.4f, 70.6f, 32.4f)
                quadraticTo(50f, 35.6f, 29.4f, 32.4f)
                cubicTo(28f, 33.4f, 27f, 35f, 26.4f, 38f)
                cubicTo(25.4f, 46f, 26f, 58f, 25f, 66.4f)
                quadraticTo(21.4f, 66.6f, 21.4f, 63f)
            }
            Hair.Long -> {
                dome(3f, 41f)
                lineTo(78f, 86f)
                quadraticTo(77f, 89f, 74.4f, 88f)
                cubicTo(73.4f, 75f, 74.4f, 56f, 73.6f, 44f)
                cubicTo(72f, 30f, 62f, 21.4f, 50.6f, 19.6f)
                lineTo(49.4f, 19.6f)
                cubicTo(38f, 21.4f, 28f, 30f, 26.4f, 44f)
                cubicTo(25.6f, 56f, 26.6f, 75f, 25.6f, 88f)
                quadraticTo(23f, 89f, 22f, 86f)
            }
            Hair.Ponytail, Hair.Bun -> {
                dome(2.4f, 43f, 2.2f)
                lineTo(73.6f, 43f)
                cubicTo(73.6f, 33f, 69.5f, 26.5f, 62.5f, 25f)
                if (style == Hair.Ponytail) {
                    cubicTo(56f, 25f, 46f, 28.5f, 38.5f, 32.4f)
                    quadraticTo(35f, 33.6f, 32.4f, 31.8f)
                } else {
                    quadraticTo(56f, 24.4f, 50f, 26.6f)
                    quadraticTo(44f, 24.4f, 37.5f, 25f)
                }
                cubicTo(29f, 28f, 26.4f, 34f, 26.4f, 43f)
            }
            Hair.Spiky -> {
                moveTo(22.6f, 42f)
                lineTo(22.6f, 33f)
                quadraticTo(19f, 26f, 15.6f, 21.6f)
                quadraticTo(21f, 22f, 25.6f, 23.4f)
                quadraticTo(24.6f, 15f, 26.6f, 8.6f)
                quadraticTo(31.6f, 12.6f, 35.6f, 16f)
                quadraticTo(38f, 8.4f, 42.4f, 3.6f)
                quadraticTo(45f, 9f, 47f, 13.6f)
                quadraticTo(52f, 6.4f, 58.4f, 3.4f)
                quadraticTo(59f, 9f, 60.2f, 14.2f)
                quadraticTo(66f, 9f, 72.4f, 7.4f)
                quadraticTo(70.6f, 13f, 71.4f, 18.6f)
                quadraticTo(78f, 17.6f, 84.4f, 19.4f)
                quadraticTo(80f, 23.6f, 77.4f, 30f)
                lineTo(77.4f, 42f)
                lineTo(73.8f, 42f)
                cubicTo(74f, 35f, 71.6f, 30.4f, 68f, 28.6f)
                quadraticTo(65.4f, 30.4f, 63.4f, 33.4f)
                quadraticTo(61.4f, 30.2f, 58.2f, 29.4f)
                quadraticTo(55.2f, 31f, 52.4f, 34.4f)
                quadraticTo(50.4f, 30.6f, 47.2f, 29.6f)
                quadraticTo(44f, 31f, 41.4f, 33.8f)
                quadraticTo(39.6f, 30.6f, 36.4f, 29.6f)
                quadraticTo(33.4f, 30.8f, 31.6f, 33.2f)
                cubicTo(28.4f, 32.6f, 26.2f, 37f, 26.2f, 42f)
            }
            Hair.SidePart -> {
                dome(3f, 44f, 2.8f)
                lineTo(73.6f, 44f)
                cubicTo(74f, 38f, 73.2f, 35.6f, 71.6f, 34.6f)
                quadraticTo(69f, 35.8f, 65.6f, 35f)
                cubicTo(58f, 33.4f, 50f, 29f, 44f, 24.6f)
                quadraticTo(41f, 22.6f, 38.6f, 22.2f)
                cubicTo(31f, 24.4f, 26.4f, 32f, 26.4f, 44f)
            }
            Hair.Braids -> {
                dome(3f, 41f)
                lineTo(78f, 50f)
                quadraticTo(76f, 53f, 74f, 50f)
                cubicTo(74f, 36f, 64f, 21.6f, 50.6f, 19.8f)
                lineTo(49.4f, 19.8f)
                cubicTo(36f, 21.6f, 26f, 36f, 26f, 50f)
                quadraticTo(24f, 53f, 22f, 50f)
            }
            Hair.Wavy -> {
                moveTo(21f, 41f)
                cubicTo(19f, 52f, 23.6f, 60f, 21.4f, 70f)
                cubicTo(19.6f, 77f, 24f, 81f, 22.6f, 86f)
                quadraticTo(24.8f, 89f, 27f, 86.4f)
                cubicTo(28.4f, 80f, 25f, 74f, 27f, 66f)
                cubicTo(28.4f, 58f, 25.6f, 50f, 26.6f, 42f)
                cubicTo(27f, 37f, 28.6f, 33.4f, 31f, 31.4f)
                cubicTo(40f, 35.4f, 52f, 33f, 60.6f, 25.4f)
                cubicTo(67f, 27f, 72.6f, 33f, 73.6f, 42f)
                cubicTo(74.4f, 50f, 71.6f, 58f, 73f, 66f)
                cubicTo(75f, 74f, 71.6f, 80f, 73f, 86.4f)
                quadraticTo(75.2f, 89f, 77.4f, 86f)
                cubicTo(76f, 81f, 80.4f, 77f, 78.6f, 70f)
                cubicTo(76.4f, 60f, 81f, 52f, 79f, 41f)
                cubicTo(79f, 25f, 66f, 13f, 50f, 13f)
                cubicTo(34f, 13f, 21f, 25f, 21f, 41f)
            }
            Hair.Twins -> {
                dome(2.6f, 41f)
                lineTo(77.6f, 46f)
                quadraticTo(75.6f, 48.4f, 74f, 45.6f)
                cubicTo(74f, 38f, 72.6f, 33f, 70f, 31f)
                quadraticTo(66.4f, 35.2f, 62.4f, 33.2f)
                quadraticTo(56.4f, 35.8f, 50f, 33.6f)
                quadraticTo(43.6f, 35.8f, 37.6f, 33.2f)
                quadraticTo(33.6f, 35.2f, 30f, 31f)
                cubicTo(27.4f, 33f, 26f, 38f, 26f, 45.6f)
                quadraticTo(24.4f, 48.4f, 22.4f, 46f)
            }
        }
        close()
    }
}

/** Curls: x, y, radius each, front (over the brow) and back (behind the head). */
private val CurlFront = floatArrayOf(
    23.4f, 37f, 6.4f, 25.6f, 28f, 7f, 31f, 20.6f, 7.4f, 40f, 15.6f, 7.6f, 50f, 14f, 7.6f, 60f, 15.6f, 7.6f, 69f, 20.6f, 7.4f, 74.4f, 28f, 7f, 76.6f, 37f, 6.4f,
    33.6f, 27.6f, 5.6f, 42f, 28.6f, 5.8f, 50.6f, 28.8f, 5.8f, 59f, 28.4f, 5.8f, 66.6f, 27.4f, 5.4f, 23.6f, 44f, 5.4f, 76.4f, 44f, 5.4f,
)
private val CurlBack = floatArrayOf(21.4f, 50f, 5.8f, 78.6f, 50f, 5.8f, 23f, 56f, 5f, 77f, 56f, 5f)

private fun DrawScope.curls(list: FloatArray, color: Color, texture: Boolean, filler: Boolean = false) {
    val edge = color.edge()
    var i = 0
    while (i < list.size) { drawCircle(edge, list[i + 2] + 0.6f, Offset(list[i], list[i + 1])); i += 3 }
    i = 0
    if (filler) oval(color, 50f, 23f, 21f, 9f)
    while (i < list.size) { drawCircle(color, list[i + 2] - 0.5f, Offset(list[i], list[i + 1])); i += 3 }
    if (!texture) return
    val swirl = color.deep(0.22f)
    i = 0
    while (i < list.size) {
        val r = list[i + 2] * 0.5f
        drawArc(swirl, 150f, 150f, false, Offset(list[i] - r, list[i + 1] - r), Size(r * 2, r * 2), style = Pens.of(0.9f))
        i += 3
    }
}

private val AfroBack: FloatArray = FloatArray(16 * 3).also { a ->
    for (i in 0 until 16) {
        val ang = (140f + i * 260f / 15f) * PI.toFloat() / 180f
        a[i * 3] = 50f + cos(ang) * 28f
        a[i * 3 + 1] = 32f + sin(ang) * 22f
        a[i * 3 + 2] = 9f
    }
}

private fun DrawScope.backHair(style: Int, color: Color, skin: Color) {
    val back = color.deep(0.14f)
    val edge = color.edge()
    when (style) {
        Hair.Bob -> shape(backPath(style) {
            moveTo(50f, 11.6f)
            cubicTo(71f, 11.6f, 81.4f, 22f, 81.4f, 40f)
            lineTo(81.4f, 60f)
            quadraticTo(81.4f, 67.4f, 74f, 67.4f)
            lineTo(26f, 67.4f)
            quadraticTo(18.6f, 67.4f, 18.6f, 60f)
            lineTo(18.6f, 40f)
            cubicTo(18.6f, 22f, 29f, 11.6f, 50f, 11.6f)
            close()
        }, back, edge)
        Hair.Long -> shape(backPath(style) {
            moveTo(50f, 12f)
            cubicTo(70f, 12f, 80.4f, 22f, 80.8f, 40f)
            lineTo(82f, 92f)
            quadraticTo(82f, 96f, 77.6f, 96f)
            lineTo(22.4f, 96f)
            quadraticTo(18f, 96f, 18f, 92f)
            lineTo(19.2f, 40f)
            cubicTo(19.6f, 22f, 30f, 12f, 50f, 12f)
            close()
        }, back, edge)
        Hair.Wavy -> shape(backPath(style) {
            moveTo(50f, 12f)
            cubicTo(71f, 12f, 81f, 23f, 81f, 40f)
            cubicTo(81f, 52f, 85f, 60f, 83f, 70f)
            cubicTo(81f, 78f, 86f, 85f, 83f, 93f)
            quadraticTo(80f, 97.4f, 74f, 95.4f)
            quadraticTo(68f, 98.6f, 62f, 95.6f)
            quadraticTo(56f, 98.6f, 50f, 95.6f)
            quadraticTo(44f, 98.6f, 38f, 95.6f)
            quadraticTo(32f, 98.6f, 26f, 95.4f)
            quadraticTo(20f, 97.4f, 17f, 93f)
            cubicTo(14f, 85f, 19f, 78f, 17f, 70f)
            cubicTo(15f, 60f, 19f, 52f, 19f, 40f)
            cubicTo(19f, 23f, 29f, 12f, 50f, 12f)
            close()
        }, back, edge)
        Hair.Ponytail -> {
            shape(backPath(style) {
                moveTo(68f, 19f)
                cubicTo(84f, 15f, 92f, 30f, 89f, 46f)
                cubicTo(87.4f, 56f, 89f, 63f, 92.6f, 68f)
                quadraticTo(84.6f, 67.6f, 82.4f, 57f)
                cubicTo(80.6f, 46f, 81f, 35f, 72f, 30f)
                close()
            }, color.deep(0.06f), edge)
            dot(Tie, 75.4f, 22.4f, 3.4f)
        }
        Hair.Bun -> {
            dot(color.deep(0.04f), 50f, 8.6f, 8.4f, edge)
            drawArc(color.deep(0.25f), 150f, 140f, false, Offset(45f, 3.6f), Size(10f, 10f), style = Pens.of(0.9f))
            drawRoundRect(Tie.edge(), Offset(44.4f, 14.2f), Size(11.2f, 4.4f), CornerRadius(2.2f))
            drawRoundRect(Tie, Offset(45f, 14.8f), Size(10f, 3.2f), CornerRadius(1.6f))
        }
        Hair.Twins -> {
            for (side in SIDES) {
                val p = Paths.of(key(K.HairBack, style, if (side < 0) 0 else 1)) {
                    fun x(v: Float) = 50f + side * (v - 50f)
                    moveTo(x(26f), 24f)
                    cubicTo(x(11f), 20f, x(5f), 38f, x(8.6f), 54f)
                    cubicTo(x(10.4f), 63f, x(14f), 70f, x(19.6f), 72.4f)
                    quadraticTo(x(15f), 64f, x(16f), 54f)
                    cubicTo(x(17f), 42f, x(20f), 34f, x(27f), 32f)
                    close()
                }
                shape(p, color.deep(0.06f), edge)
                dot(Tie, 50f + side * 25.4f, 27.6f, 3.4f)
            }
        }
        Hair.Curly -> curls(CurlBack, back, false)
        Hair.Afro -> {
            curls(AfroBack, color, false)
            drawCircle(color, 28f, Offset(50f, 33f))
            var i = 0
            val tex = color.deep(0.2f)
            while (i < AfroBack.size) {
                drawArc(tex, 160f, 140f, false, Offset(AfroBack[i] - 3f, AfroBack[i + 1] - 3f), Size(6f, 6f), style = Pens.of(0.9f))
                i += 3
            }
        }
    }
}

/** Hair that hangs in front of the shoulders, over the body but behind the head (braids). */
private fun DrawScope.hangingHair(style: Int, color: Color) {
    if (style != Hair.Braids) return
    val edge = color.edge()
    for (side in SIDES) {
        val x = 50f + side * 26.6f
        for (i in 5 downTo 0) {
            val y = 53f + i * 5.4f
            val w = 4.4f - i * 0.18f
            oval(edge, x, y, w + 0.6f, 3.6f)
            oval(if (i % 2 == 0) color else color.deep(0.1f), x, y, w - 0.2f, 2.9f)
            drawArc(color.deep(0.25f), 200f, 140f, false, Offset(x - w * 0.6f, y - 1.6f), Size(w * 1.2f, 3f), style = Pens.of(0.7f))
        }
        dot(Tie, x, 86.6f, 1.9f)
        shape(Paths.of(key(K.HairBack, style, if (side < 0) 0 else 1)) {
            moveTo(x - 2f, 88f); quadraticTo(x - 3.4f, 92.6f, x, 93.8f); quadraticTo(x + 3.4f, 92.6f, x + 2f, 88f); close()
        }, color, edge, 0.9f)
    }
}

/** The fringe's soft shadow on the brow (drawn inside the head, under the hair). */
private fun DrawScope.hairShadow(style: Int, skin: Color) {
    val p = frontPath(style) ?: return
    translate(0f, 1.8f) { drawPath(p, skin.edge(0.2f).copy(alpha = 0.45f)) }
}

private fun DrawScope.frontHair(style: Int, color: Color, skin: Color) {
    val edge = color.edge()
    when (style) {
        Hair.Curly -> { curls(CurlFront, color, true, filler = true); return }
        Hair.Afro -> {
            // The front of the cloud: the hairline, and the curls' grain.
            val p = Paths.of(key(K.HairFront, style)) {
                dome(4.6f, 40f)
                lineTo(74.6f, 40f)
                cubicTo(74.6f, 31f, 67f, 25f, 50f, 24.4f)
                cubicTo(33f, 25f, 25.4f, 31f, 25.4f, 40f)
                close()
            }
            drawPath(p, color)
            drawPath(Paths.of(key(K.HairLine, style)) {
                moveTo(74.6f, 40f)
                cubicTo(74.6f, 31f, 67f, 25f, 50f, 24.4f)
                cubicTo(33f, 25f, 25.4f, 31f, 25.4f, 40f)
            }, edge, style = Pens.of(1.1f))
            drawArc(Color.White.copy(alpha = 0.2f), 205f, 50f, false, Offset(26f, 8f), Size(40f, 30f), style = Pens.of(2.4f))
            return
        }
    }
    val p = frontPath(style) ?: return
    val fill = if (style == Hair.Buzz) lerp(color, skin, 0.3f) else color
    shape(p, fill, if (style == Hair.Buzz) fill.edge(0.25f) else edge)
    val shine = Color.White.copy(alpha = if (style == Hair.Buzz) 0.12f else 0.24f)
    val grain = color.deep(0.22f)
    when (style) {
        Hair.Swept -> {
            line(grain, 63.4f, 18.6f, 66.4f, 25f, 0.9f)
            drawArc(grain, 200f, 50f, false, Offset(44f, 22f), Size(30f, 20f), style = Pens.of(0.8f))
        }
        Hair.Bob, Hair.Twins -> for (i in 0 until 3) line(grain, 40f + i * 10f, 28f, 40.6f + i * 10f, 32.6f, 0.7f)
        Hair.Long, Hair.Braids -> line(grain, 50f, 12.4f, 50f, 19.4f, 0.9f)
        Hair.Ponytail, Hair.Bun -> {
            drawArc(grain, 220f, 60f, false, Offset(36f, 18f), Size(28f, 22f), style = Pens.of(0.7f))
            drawArc(grain, 230f, 60f, false, Offset(30f, 20f), Size(28f, 22f), style = Pens.of(0.7f))
        }
        Hair.SidePart -> {
            line(grain, 38.6f, 22.2f, 36.6f, 13.6f, 0.9f)
            drawArc(grain, 215f, 60f, false, Offset(42f, 18f), Size(34f, 24f), style = Pens.of(0.8f))
        }
        Hair.Wavy -> line(grain, 60.6f, 25.4f, 61.8f, 13.4f, 0.9f)
    }
    if (style != Hair.Spiky) drawArc(shine, 200f, 56f, false, Offset(29f, 15f), Size(38f, 26f), style = Pens.of(2.4f))
    else drawArc(shine, 210f, 50f, false, Offset(30f, 14f), Size(36f, 24f), style = Pens.of(2f))
}

// --- Headwear -----------------------------------------------------------------------------------------------------

/** The two first hats the wardrobe didn't redraw: a flower clip and a star clip (the rest come from the wardrobe). */
private fun DrawScope.headwear(style: Int) {
    when (style) {
        5 -> {
            val x = 33f
            val y = 22f
            for (i in 0 until 5) {
                val a = (i * 72f - 90f) * PI.toFloat() / 180f
                dot(Color(0xFFFFB3D1), x + cos(a) * 4f, y + sin(a) * 4f, 3.4f, Color(0xFFE07FA6), 0.8f)
            }
            dot(Color(0xFFFFD86B), x, y, 2.6f, Color(0xFFD9A63A), 0.8f)
        }
        11 -> starAt(66f, 23f, 5.4f, Color(0xFFFFD86B), 5, 0.45f, Color(0xFFD99A2E))
    }
}

private inline fun mouthPath(style: Int, shape: Int, build: Path.() -> Unit) = Paths.of(key(K.Mouth, style, shape), build)
private inline fun backPath(style: Int, build: Path.() -> Unit) = Paths.of(key(K.HairBack, style), build)

// --- The wardrobe -------------------------------------------------------------------------------------------------
// Most hats, outfits, hairstyles and the newer face parts are drawn from small display lists (WardrobeData.kt,
// generated from the wardrobe kit): fills and outlines in the user's colours, read once into paths and kept.

/** The colours the wardrobe's parts take from a character: one, filled in per character as it is drawn. */
internal class WardrobeInk {
    var cloth = Color.Unspecified
    var hair = Color.Unspecified
    var skin = Color.Unspecified
    var iris = Color.Unspecified
    var ink = Color.Unspecified
    var topColor = 0

    /** A second colour that follows the outfit's: the palette entry [step] places on. */
    fun accent(step: Int) = Color(AvatarColors.top[(topColor + step) % AvatarColors.top.size])

    /** The kaftan's trim: two places on, or four from the reds and the orange it would clash with. */
    fun kaftanAccent() = accent(if (topColor == 0 || topColor == 1 || topColor == 11) 4 else 2)
}

/** A part's layers, in the order [character] draws them. */
private object WL {
    const val Back = 0; const val BehindHead = 1; const val BehindHeadBody = 2; const val Front = 3; const val Hanging = 4
    const val Inside = 5; const val OverTorso = 6; const val BehindNeck = 7; const val Neck = 8; const val Legs = 9; const val Face = 10
    const val COUNT = 11
    fun of(name: String) = when (name) {
        "back" -> Back; "behind_head" -> BehindHead; "behind_head_body" -> BehindHeadBody; "front" -> Front
        "hanging" -> Hanging; "inside" -> Inside; "over_torso" -> OverTorso; "behind_neck" -> BehindNeck; "neck" -> Neck
        "legs" -> Legs; else -> Face
    }
}

/** A colour of a part: a fixed one, or one of the user's ([wardrobeColor]), at an opacity. */
private class WPaint(val expr: Int, val fixed: Color, val alpha: Float) {
    fun of(k: WardrobeInk): Color {
        val c = if (expr < 0) fixed else wardrobeColor(expr, k)
        return if (alpha < 1f) c.copy(alpha = c.alpha * alpha) else c
    }

    companion object {
        fun parse(token: String, alpha: Float = 1f): WPaint =
            if (token.startsWith("#")) WPaint(-1, Color(0xFF000000 or token.substring(1).toLong(16)), alpha)
            else WPaint(token.substring(1).toInt(), Color.Unspecified, alpha)
    }
}

/**
 * Where a part's sides go: the eyes follow each face's spacing and the glance, the legs their stride. Set before a
 * layer with sides is drawn; side -1 is the screen's left, 1 its right, 0 the middle.
 */
private object Shift {
    var leftX = 0f; var leftY = 0f; var rightX = 0f; var rightY = 0f; var midX = 0f; var midY = 0f
    /** A squash of each side about [pivot] (a blink). */
    var scaleY = 1f; var pivot = 0f

    fun set(lx: Float, ly: Float, rx: Float, ry: Float, mx: Float, my: Float, sy: Float = 1f, py: Float = 0f) {
        leftX = lx; leftY = ly; rightX = rx; rightY = ry; midX = mx; midY = my; scaleY = sy; pivot = py
    }
}

private abstract class WOp { abstract fun draw(s: DrawScope, k: WardrobeInk) }

private class WPath(val paint: WPaint, val path: Path, val pen: Stroke?) : WOp() {
    override fun draw(s: DrawScope, k: WardrobeInk) = if (pen == null) s.drawPath(path, paint.of(k)) else s.drawPath(path, paint.of(k), style = pen)
}

private class WOval(val paint: WPaint, val x: Float, val y: Float, val w: Float, val h: Float, val pen: Stroke?) : WOp() {
    override fun draw(s: DrawScope, k: WardrobeInk) = s.drawOval(paint.of(k), Offset(x, y), Size(w, h), style = pen ?: androidx.compose.ui.graphics.drawscope.Fill)
}

private class WRect(val paint: WPaint, val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val pen: Stroke?) : WOp() {
    override fun draw(s: DrawScope, k: WardrobeInk) =
        s.drawRoundRect(paint.of(k), Offset(x, y), Size(w, h), CornerRadius(r), style = pen ?: androidx.compose.ui.graphics.drawscope.Fill)
}

/** A group: moved by a matrix, clipped to a shape, or one side of a part that moves with [Shift]. */
private class WGroup(val matrix: androidx.compose.ui.graphics.Matrix?, val clip: Path?, val side: Int, val kids: Array<WOp>) : WOp() {
    override fun draw(s: DrawScope, k: WardrobeInk) {
        when {
            side != NO_SIDE -> {
                val dx = when (side) { -1 -> Shift.leftX; 1 -> Shift.rightX; else -> Shift.midX }
                val dy = when (side) { -1 -> Shift.leftY; 1 -> Shift.rightY; else -> Shift.midY }
                s.withTransform({
                    translate(dx, dy)
                    if (Shift.scaleY != 1f) scale(1f, Shift.scaleY, Offset(50f, Shift.pivot))
                }) { for (op in kids) op.draw(this, k) }
            }
            matrix != null -> s.withTransform({ transform(matrix) }) { for (op in kids) op.draw(this, k) }
            clip != null -> s.clipPath(clip) { for (op in kids) op.draw(this, k) }
            else -> for (op in kids) op.draw(s, k)
        }
    }

    companion object { const val NO_SIDE = 99 }
}

/** The arms' curves for the pose being drawn: shoulder, control, wrist, for the back (0) and front (6) arm. */
private val ArmNow = FloatArray(12)
private val ArmPart = Path()

private fun armCurve(pose: AvatarPose) {
    for (i in 0..1) {
        val front = i == 1
        val e = armElbow(pose, front)
        val w = armWrist(pose, front)
        val sx = if (front) 60f else 40f
        val o = i * 6
        ArmNow[o] = sx; ArmNow[o + 1] = 76.5f
        ArmNow[o + 2] = 2f * e.x - (sx + w.x) / 2f; ArmNow[o + 3] = 2f * e.y - (76.5f + w.y) / 2f
        ArmNow[o + 4] = w.x; ArmNow[o + 5] = w.y
    }
}

/** The piece of an arm's curve from [t0] to [t1] (a quadratic's piece is a quadratic), moved [dx] across, in [ArmPart]. */
private fun armPiece(front: Boolean, t0: Float, t1: Float, dx: Float = 0f): Path {
    val o = if (front) 6 else 0
    fun at(t: Float, i: Int) = (1 - t) * (1 - t) * ArmNow[o + i] + 2 * (1 - t) * t * ArmNow[o + 2 + i] + t * t * ArmNow[o + 4 + i]
    // The piece's control point: the curve's blossom at (t0, t1).
    fun ctl(i: Int) = (1 - t0) * (1 - t1) * ArmNow[o + i] + ((1 - t0) * t1 + t0 * (1 - t1)) * ArmNow[o + 2 + i] + t0 * t1 * ArmNow[o + 4 + i]
    ArmPart.reset()
    ArmPart.moveTo(at(t0, 0) + dx, at(t0, 1))
    ArmPart.quadraticTo(ctl(0) + dx, ctl(1), at(t1, 0) + dx, at(t1, 1))
    return ArmPart
}

private val CuffEdge = Stroke(9.6f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
private val CuffFill = Stroke(8.2f, cap = StrokeCap.Butt, join = StrokeJoin.Round)

/** A long sleeve's cuff, just above the hand, wherever the arm is (and its knit stripes, if it has them). */
private class WCuff(val paint: WPaint, val stripe: WPaint?) : WOp() {
    override fun draw(s: DrawScope, k: WardrobeInk) {
        val c = paint.of(k)
        for (front in booleanArrayOf(false, true)) {
            s.drawPath(armPiece(front, 0.71f, 0.9f), c.edge(), style = CuffEdge)
            s.drawPath(armPiece(front, 0.725f, 0.9f), c, style = CuffFill)
            if (stripe != null) {
                val st = stripe.of(k)
                s.drawPath(armPiece(front, 0.78f, 0.795f), st, style = CuffFill)
                s.drawPath(armPiece(front, 0.83f, 0.845f), st, style = CuffFill)
            }
        }
    }
}

/** A stripe down the outer side of a long sleeve (a tracksuit's), following the arm. */
private class WStripe(val paint: WPaint, val side: Int, val off: Float, val pen: Stroke, val t0: Float, val t1: Float) : WOp() {
    override fun draw(s: DrawScope, k: WardrobeInk) = s.drawPath(armPiece(side > 0, t0, t1, side * off), paint.of(k), style = pen)
}

/** One part read from its text (see the wardrobe kit's gen.py): its layers and what it says about itself. */
private class WPart(text: String) {
    val layers = arrayOfNulls<Array<WOp>>(WL.COUNT)
    /** A hat: the hair is hidden above this line (crown units), or all of it. */
    var clipY = Float.NaN
    var hidesHair = false
    /** Hair: its front's shape, for the soft shadow on the brow. */
    var shadow: Path? = null
    /** An outfit: sleeve length (0 none, 1 long), sleeve, body and leg colours, its own torso shape, the lap's colour. */
    var sleeve = 0.34f
    var sleeveColor: WPaint? = null
    var base: WPaint? = null
    var torso: Path? = null
    var legs: WPaint? = null
    var lap: WPaint? = null
    /** Eyes drawn closed (they don't blink). */
    var closed = false
    /** A face detail drawn over everything (a sweat drop over the hair's edge). */
    var last = false
    /** The highest point drawn on the head (the smallest y), in the part's own units. */
    var top = Float.POSITIVE_INFINITY

    init {
        val stack = ArrayList<ArrayList<WOp>>()
        val groups = ArrayList<WGroup>()
        // The transforms the parse is inside, so the part knows how high it reaches.
        val mats = ArrayList<FloatArray>()
        var layer = -1
        var ops = ArrayList<WOp>()
        fun reach(x0: Float, y0: Float, x1: Float, y1: Float) {
            if (layer == WL.BehindHeadBody) return
            var ymin = Float.POSITIVE_INFINITY
            for (cx in floatArrayOf(x0, x1)) for (cy in floatArrayOf(y0, y1)) {
                var x = cx
                var y = cy
                for (i in mats.indices.reversed()) {
                    val m = mats[i]
                    val nx = m[0] * x + m[2] * y + m[4]
                    y = m[1] * x + m[3] * y + m[5]
                    x = nx
                }
                ymin = min(ymin, y)
            }
            top = min(top, ymin)
        }
        fun path(d: String, evenOdd: Boolean = false): Path =
            androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath().also {
                if (evenOdd) it.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            }
        fun close() { if (layer >= 0) layers[layer] = ops.toTypedArray() }
        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            val f = line.split(' ', limit = 2)
            val rest = f.getOrElse(1) { "" }
            when (f[0]) {
                "L" -> { close(); layer = WL.of(rest); ops = ArrayList() }
                "M" -> {
                    val kv = rest.split(' ', limit = 2)
                    val v = kv[1]
                    when (kv[0]) {
                        "clip" -> clipY = v.toFloat()
                        "hides" -> hidesHair = true
                        "shadow" -> shadow = path(v)
                        "sleeve" -> sleeve = v.toFloat()
                        "sleeveColor" -> sleeveColor = WPaint.parse(v)
                        "base" -> base = WPaint.parse(v)
                        "torso" -> torso = path(v)
                        "legs" -> legs = WPaint.parse(v)
                        "lap" -> lap = WPaint.parse(v)
                        "closed" -> closed = true
                        "last" -> last = true
                    }
                }
                "G", "C", "H" -> {
                    stack.add(ops)
                    ops = ArrayList()
                    when (f[0]) {
                        "G" -> {
                            val v = rest.split(' ').map { it.toFloat() }
                            mats.add(v.toFloatArray())
                            val m = androidx.compose.ui.graphics.Matrix()
                            m.values[androidx.compose.ui.graphics.Matrix.ScaleX] = v[0]
                            m.values[androidx.compose.ui.graphics.Matrix.SkewY] = v[1]
                            m.values[androidx.compose.ui.graphics.Matrix.SkewX] = v[2]
                            m.values[androidx.compose.ui.graphics.Matrix.ScaleY] = v[3]
                            m.values[androidx.compose.ui.graphics.Matrix.TranslateX] = v[4]
                            m.values[androidx.compose.ui.graphics.Matrix.TranslateY] = v[5]
                            groups.add(WGroup(m, null, WGroup.NO_SIDE, emptyArray()))
                        }
                        "C" -> groups.add(WGroup(null, path(rest), WGroup.NO_SIDE, emptyArray()))
                        else -> groups.add(WGroup(null, null, rest.toInt(), emptyArray()))
                    }
                }
                "}" -> {
                    val g = groups.removeAt(groups.size - 1)
                    if (g.matrix != null) mats.removeAt(mats.size - 1)
                    val kids = ops.toTypedArray()
                    ops = stack.removeAt(stack.size - 1)
                    ops.add(WGroup(g.matrix, g.clip, g.side, kids))
                }
                "F", "E" -> {
                    val a = rest.split(' ', limit = 3)
                    val p = path(a[2], f[0] == "E")
                    val b = p.getBounds()
                    reach(b.left, b.top, b.right, b.bottom)
                    ops.add(WPath(WPaint.parse(a[0], a[1].toFloat()), p, null))
                }
                "S", "D" -> {
                    val a = rest.split(' ', limit = 4)
                    val w = a[2].toFloat()
                    val p = path(a[3])
                    val b = p.getBounds()
                    reach(b.left, b.top - w / 2f, b.right, b.bottom)
                    val pen = if (f[0] == "S") Pens.of(w)
                    else Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.3f, 1.3f)))
                    ops.add(WPath(WPaint.parse(a[0], a[1].toFloat()), p, pen))
                }
                "O", "P" -> {
                    val a = rest.split(' ')
                    val stroked = f[0] == "P"
                    val n = if (stroked) 3 else 2
                    val cx = a[n].toFloat(); val cy = a[n + 1].toFloat(); val rx = a[n + 2].toFloat(); val ry = a[n + 3].toFloat()
                    val w = if (stroked) a[2].toFloat() else 0f
                    reach(cx - rx, cy - ry - w / 2f, cx + rx, cy + ry)
                    ops.add(WOval(WPaint.parse(a[0], a[1].toFloat()), cx - rx, cy - ry, rx * 2, ry * 2, if (stroked) Pens.of(w) else null))
                }
                "R", "Q" -> {
                    val a = rest.split(' ')
                    val stroked = f[0] == "Q"
                    val n = if (stroked) 3 else 2
                    val x = a[n].toFloat(); val y = a[n + 1].toFloat(); val w = a[n + 2].toFloat(); val h = a[n + 3].toFloat()
                    reach(x, y, x + w, y + h)
                    ops.add(WRect(WPaint.parse(a[0], a[1].toFloat()), x, y, w, h, a[n + 4].toFloat(), if (stroked) Pens.of(a[2].toFloat()) else null))
                }
                "X" -> {
                    val a = rest.split(' ')
                    if (a[0] == "cuff") ops.add(WCuff(WPaint.parse(a[1]), if (a[2] == "-") null else WPaint.parse(a[2])))
                    else ops.add(WStripe(WPaint.parse(a[1]), a[2].toInt(), a[3].toFloat(), Pens.of(a[4].toFloat()), a[5].toFloat(), a[6].toFloat()))
                }
            }
        }
        close()
    }

    fun has(layer: Int) = layers[layer] != null

    fun draw(s: DrawScope, layer: Int) {
        val list = layers[layer] ?: return
        for (op in list) op.draw(s, Wardrobe.ink)
    }
}

/**
 * One kind of part, by choice number: read when first worn, then kept (a face part has one per face shape). Reading
 * one parses tens of paths, so [Wardrobe.warm] reads them ahead on a background thread: the slots are atomic, so a
 * part read there shows whole on the main thread (two threads reading the same one at once both get a good one).
 */
private class Shelf(private val data: Array<Array<String>?>) {
    private val parts = java.util.concurrent.atomic.AtomicReferenceArray<java.util.concurrent.atomic.AtomicReferenceArray<WPart?>>(data.size)

    operator fun get(choice: Int, shape: Int = 0): WPart? {
        val texts = data.getOrNull(choice) ?: return null
        val row = parts.get(choice) ?: java.util.concurrent.atomic.AtomicReferenceArray<WPart?>(texts.size).let { made ->
            if (parts.compareAndSet(choice, null, made)) made else parts.get(choice)
        }
        val v = if (texts.size == 1) 0 else shape.coerceIn(0, texts.size - 1)
        return row.get(v) ?: WPart(texts[v]).let { made -> if (row.compareAndSet(v, null, made)) made else row.get(v) }
    }

    /** Reads every choice for [shape] ahead. */
    fun warm(shape: Int) { for (i in data.indices) get(i, shape) }
}

private object Wardrobe {
    val hats = Shelf(WardrobeData.hats)
    val tops = Shelf(WardrobeData.tops)
    val hair = Shelf(WardrobeData.hair)
    val eyes = Shelf(WardrobeData.eyes)
    val glasses = Shelf(WardrobeData.glasses)
    val earrings = Shelf(WardrobeData.earrings)
    val extras = Shelf(WardrobeData.extras)
    val beards = Shelf(WardrobeData.beards)
    val ink = WardrobeInk()

    /** The default face's eye spacing and height, which the eyes are drawn for. */
    const val DX0 = 10.35f
    const val EYE_Y0 = 44.885f

    /**
     * Reads every part for a face [shape] ahead (call it off the main thread): in the order Me's tabs show them, so the
     * next tab's choices are ready first. Parsing a part was the cost of a tab's first visit and the Plaza's first frame.
     */
    fun warm(shape: Int) {
        for (shelf in arrayOf(eyes, hair, hats, tops, glasses, earrings, extras, beards)) shelf.warm(shape)
    }
}

/** The face shapes [warmWardrobe] has read the parts for. */
private val warmed = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

/**
 * Reads the wardrobe's parts for [shapes] on a background thread, once per face shape and process, so drawing a
 * character (Me's choices, the Plaza's crowd) never parses them on the main thread.
 */
suspend fun warmWardrobe(shapes: Collection<Int>) {
    val todo = shapes.filter { it !in warmed }
    if (todo.isEmpty()) return
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        for (s in todo) { Wardrobe.warm(s); warmed += s }
    }
}

/** Reads, on a background thread, just the parts [specs] wear (the Plaza's crowd and its strip). */
suspend fun warmWorn(specs: Collection<AvatarSpec>) {
    if (specs.isEmpty()) return
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        for (s in specs) {
            val shape = Head.of(s.shape).shape
            Wardrobe.hats[s.headwear, shape]; Wardrobe.hair[s.hair]; Wardrobe.tops[s.top]; Wardrobe.eyes[s.eyes]
            Wardrobe.glasses[s.glasses, shape]; Wardrobe.earrings[s.earrings, shape]
            Wardrobe.extras[s.faceExtra, shape]; Wardrobe.beards[s.beard, shape]
        }
    }
}

/** Draws [block] with the hair hidden above [clipY] (crown units; NaN shows it all). */
private inline fun DrawScope.underHat(clipY: Float, crossinline block: DrawScope.() -> Unit) {
    if (clipY.isNaN()) block() else clipRect(-40f, clipY, 140f, 240f) { block() }
}

/** How high each of the first hairstyles reaches (crown units), for [avatarTop]. */
private val LegacyHairTop = floatArrayOf(15f, 13.4f, 13f, 11.6f, 12f, 13f, 0.2f, 6.4f, 3.4f, 13f, 13f, 1f, 12f, 13.4f)

/**
 * How high [spec] reaches in its 100 × 120 box (the top of the head is about 16; a top hat goes above 0). Screens that
 * show a character in a fixed window use it to leave room for what is on its head.
 */
fun avatarTop(spec: AvatarSpec): Float {
    val head = Head.of(spec.shape)
    val hat = Wardrobe.hats[spec.headwear, spec.shape]
    var y = 16f
    if (hat?.hidesHair != true) {
        var h = Wardrobe.hair[spec.hair]?.top ?: LegacyHairTop.getOrElse(spec.hair) { 16f }
        if (hat != null && !hat.clipY.isNaN()) h = kotlin.math.max(h, hat.clipY)
        y = min(y, h)
    }
    if (hat != null) y = min(y, hat.top)
    return head.top + (y - 16f) * (head.mid - head.top) / 25f
}

/** The room to leave above the 100 × 120 box so all of [spec] shows (0 for most). */
fun avatarHeadroom(spec: AvatarSpec): Float = kotlin.math.max(0f, 1.5f - avatarTop(spec))

// --- Living characters -------------------------------------------------------------------------------------------

/**
 * A character that lives: it bobs on the ambient clock, blinks now and then, glances about, and when [react] changes
 * (a part picked in the editor, a newcomer on the Plaza) it hops, cheers and waves. [waving] keeps a wave going.
 * With Reduce motion, or animations off, it simply stands still with its eyes open. [headroom] keeps room above it
 * for a tall hat ([avatarHeadroom]).
 */
@Composable
fun AvatarView(
    spec: AvatarSpec,
    modifier: Modifier = Modifier,
    react: Any? = null,
    waving: Boolean = false,
    lookX: Float = 0f,
    shadow: Boolean = true,
    seed: Int = 0,
    headroom: Float = 0f,
) {
    val still = Themes.still || Store.settings.reduceMotion
    val ambient = rememberAmbient(!still)
    val blink = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val cheer = remember { Animatable(0f) }
    val wave = remember { Animatable(if (waving) 1f else 0f) }
    val look = remember { Animatable(0f) }
    LaunchedEffect(still) {
        if (still) { blink.snapTo(0f); return@LaunchedEffect }
        val rnd = java.util.Random(seed.toLong() xor System.nanoTime())
        delay(600L + rnd.nextInt(2400))
        while (isActive) {
            // Rests with its eyes open once Tandem has settled, until the next input.
            Ambient.awaitAwake()
            kotlinx.coroutines.withContext(AmbientFrames) {
                blink.animateTo(1f, tween(70))
                blink.animateTo(0f, tween(110))
                if (rnd.nextInt(5) == 0) { delay(90); blink.animateTo(1f, tween(60)); blink.animateTo(0f, tween(100)) }
            }
            delay(2200L + rnd.nextInt(2600))
            if (rnd.nextInt(3) == 0) kotlinx.coroutines.withContext(AmbientFrames) { look.animateTo((rnd.nextFloat() - 0.5f) * 1.6f, spring(0.7f, 300f)) }
        }
    }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(react) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (still) return@LaunchedEffect
        cheer.snapTo(1f)
        hop.snapTo(0f)
        hop.animateTo(-1f, tween(120))
        hop.animateTo(0.6f, tween(150))
        hop.animateTo(0f, spring(0.4f, 380f))
        cheer.animateTo(0f, tween(700))
    }
    LaunchedEffect(waving, still) { wave.animateTo(if (waving) 1f else 0f, if (still) tween(0) else spring(0.6f, 260f)) }
    val bobbing = ambient && !still
    Canvas(
        modifier.graphicsLayer {
            // The gentle idle bob, and the hop of a reaction, in the layer only.
            val bob = if (bobbing) Ambient.pingPong(1300 + (seed and 0xff) * 2, lively = true) else 0f
            val lift = if (hop.value < 0f) -hop.value else 0f
            translationY = -(bob * 0.025f + lift * 0.1f) * size.height
        },
    ) {
        // A soft, unhurried wave (about one rock a second and a quarter).
        val swing = if (ambient) kotlin.math.sin(Ambient.ms / 200.0).toFloat() else 0f
        drawAvatar(
            spec,
            AvatarPose(
                blink = blink.value,
                lookX = lookX + look.value,
                wave = wave.value,
                waveSwing = if (wave.value > 0.5f) swing else 0f,
                squash = hop.value,
                cheer = cheer.value,
            ),
            shadow = shadow,
            headroom = headroom,
        )
    }
}
