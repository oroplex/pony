package app.pony.companion.a11y

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureBuilderTest {
    @Test
    fun longPressHoldsAtOnePointForItsDuration() {
        val strokes = GestureBuilder.longPress(100f, 200f, 800)
        assertEquals(1, strokes.size)
        val stroke = strokes.single()
        assertEquals(listOf(GestureBuilder.Point(100f, 200f)), stroke.points)
        assertEquals(0L, stroke.startAt)
        assertEquals(800L, stroke.durationMs)
    }

    @Test
    fun longPressDefaultsToALongHold() {
        assertEquals(GestureBuilder.LONG_PRESS_MS, GestureBuilder.longPress(1f, 1f).single().durationMs)
    }

    @Test
    fun dragGoesFromStartToEndAsOneStroke() {
        val stroke = GestureBuilder.drag(10f, 20f, 300f, 400f, 500).single()
        assertEquals(GestureBuilder.Point(10f, 20f), stroke.points.first())
        assertEquals(GestureBuilder.Point(300f, 400f), stroke.points.last())
        assertEquals(500L, stroke.durationMs)
    }

    @Test
    fun pinchUsesTwoFingersAboveAndBelowTheCenter() {
        // Spread apart: fingers start 100px apart (±50) and end 400px apart (±200).
        val strokes = GestureBuilder.pinch(540f, 1000f, 100f, 400f, 300)
        assertEquals(2, strokes.size)
        val top = strokes[0]
        val bottom = strokes[1]
        assertEquals(GestureBuilder.Point(540f, 950f), top.points.first())
        assertEquals(GestureBuilder.Point(540f, 800f), top.points.last())
        assertEquals(GestureBuilder.Point(540f, 1050f), bottom.points.first())
        assertEquals(GestureBuilder.Point(540f, 1200f), bottom.points.last())
        // Both fingers share the same center x, so it's a straight vertical pinch.
        assertTrue(strokes.all { s -> s.points.all { it.x == 540f } })
    }

    @Test
    fun pinchInwardConvergesTowardTheCenter() {
        val strokes = GestureBuilder.pinch(0f, 0f, 400f, 0f)
        // From ±200 to 0: the fingers meet at the center.
        assertEquals(-200f, strokes[0].points.first().y)
        assertEquals(0f, strokes[0].points.last().y)
        assertEquals(200f, strokes[1].points.first().y)
        assertEquals(0f, strokes[1].points.last().y)
    }

    @Test
    fun durationsAreClampedToASaneRange() {
        assertEquals(GestureBuilder.MIN_MS, GestureBuilder.longPress(1f, 1f, 0).single().durationMs)
        assertEquals(GestureBuilder.MIN_MS, GestureBuilder.drag(0f, 0f, 1f, 1f, -5).single().durationMs)
        assertEquals(GestureBuilder.MAX_MS, GestureBuilder.pinch(0f, 0f, 10f, 20f, 999_999).first().durationMs)
    }

    @Test
    fun spanIsTheLatestStrokeEnd() {
        assertEquals(600L, GestureBuilder.spanMs(GestureBuilder.drag(0f, 0f, 1f, 1f, 600)))
        assertEquals(0L, GestureBuilder.spanMs(emptyList()))
    }
}
