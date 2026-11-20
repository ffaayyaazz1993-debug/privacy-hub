package com.privacyhub.app.domain.layout

import androidx.compose.ui.geometry.Offset

object ZoomMath {
    /** Keep the world point under the previous centroid anchored to the new centroid after scaling. */
    fun zoomOffset(curCentroid: Offset, prevCentroid: Offset, offset: Offset, factor: Float): Offset =
        curCentroid - (prevCentroid - offset) * factor
}
