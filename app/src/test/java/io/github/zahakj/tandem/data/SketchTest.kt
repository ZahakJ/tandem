package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Drawings in notes (Sketch.kt): the strokes file, the eraser, crops and thinning. */
class SketchTest {
    private val line = SketchStroke("#24212B", 4f, listOf(0f, 0f, 100f, 0f))
    private val dot = SketchStroke("#E0473C", 4f, listOf(50f, 50f))

    @Test fun roundTrip() {
        val d = SketchData(1920, 1080, background = "shot.png", crop = SketchCrop(10, 20, 500, 400), strokes = listOf(line, dot))
        assertEquals(d, Sketches.decode(Sketches.encode(d)))
        // Defaults stay out of the file, and unknown fields from a newer version are ignored.
        assertEquals("""{"w":800,"h":800}""", Sketches.encode(Sketches.sheet(square = true)))
        assertEquals(SketchData(10, 10), Sketches.decode("""{"w":10,"h":10,"layers":[]}"""))
        assertNull(Sketches.decode("not json"))
    }

    @Test fun colours() {
        assertEquals(0xFFE0473C.toInt(), Sketches.argb("#E0473C"))
        assertEquals(0xFF000000.toInt(), Sketches.argb("oops"))
    }

    @Test fun eraserTakesWholeStrokes() {
        val strokes = listOf(line, dot)
        assertEquals(listOf(dot), Sketches.erase(strokes, 60f, 5f, 4f))
        assertEquals(listOf(line), Sketches.erase(strokes, 52f, 52f, 4f))
        assertEquals(strokes, Sketches.erase(strokes, 50f, 30f, 4f))
    }

    @Test fun segmentDistance() {
        assertEquals(5f, Sketches.segmentDistance(50f, 5f, 0f, 0f, 100f, 0f), 0.001f)
        assertEquals(5f, Sketches.segmentDistance(-3f, 4f, 0f, 0f, 100f, 0f), 0.001f)
        assertEquals(5f, Sketches.segmentDistance(3f, 4f, 0f, 0f, 0f, 0f), 0.001f)
    }

    @Test fun cropsStayInside() {
        assertEquals(SketchCrop(0, 10, 300, 200), Sketches.crop(300f, 200f, -40f, 10f, 1000, 800))
        assertEquals(SketchCrop(900, 700, 1000, 800), Sketches.crop(900f, 700f, 2000f, 2000f, 1000, 800))
        assertNull(Sketches.crop(10f, 10f, 20f, 300f, 1000, 800))
    }

    @Test fun thinningKeepsEnds() {
        val pts = listOf(0f, 0f, 1f, 0f, 2f, 0f, 10f, 0f, 11f, 0f)
        assertEquals(listOf(0f, 0f, 10f, 0f, 11f, 0f), Sketches.thin(pts, 5f))
        assertEquals(listOf(0f, 0f, 1f, 1f), Sketches.thin(listOf(0f, 0f, 1f, 1f), 5f))
    }
}
