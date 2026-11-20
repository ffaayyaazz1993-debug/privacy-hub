package com.privacyhub.app

import androidx.compose.ui.geometry.Offset
import com.privacyhub.app.domain.layout.ZoomMath
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {
    @Test fun pureZoomKeepsAnchor() {
        val o = Offset(10f, 20f); val c = Offset(100f, 100f)
        val z = ZoomMath.zoomOffset(c, c, o, 2f)
        assertEquals(-80f, z.x, 0.001f); assertEquals(-60f, z.y, 0.001f)
    }
    @Test fun zoomWithPanTracksCentroid() {
        val o = Offset(0f, 0f); val prev = Offset(100f, 100f); val cur = Offset(120f, 100f)
        val z = ZoomMath.zoomOffset(cur, prev, o, 2f)
        assertEquals(-80f, z.x, 0.001f); assertEquals(-100f, z.y, 0.001f)
    }
    @Test fun factorOneIsPurePan() {
        val z = ZoomMath.zoomOffset(Offset(120f, 110f), Offset(100f, 100f), Offset(5f, 5f), 1f)
        assertEquals(25f, z.x, 0.001f); assertEquals(15f, z.y, 0.001f)
    }
}
