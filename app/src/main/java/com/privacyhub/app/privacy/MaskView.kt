package com.privacyhub.app.privacy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Black scrim with a transparent rounded aperture.
 *
 * Drawn as ONE even-odd path (outer rect + inner rounded rect). No xfermode and
 * no hardware layer: a screen-sized hardware layer over every app can exhaust
 * GPU memory and hang the compositor on low-end phones. A single path fill is
 * cheap and renders identically.
 */
class MaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var config: MaskConfig = MaskStore.load(context)
        set(value) {
            field = MaskStore.normalize(value)
            invalidate()
        }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(90, 255, 255, 255)
    }

    private val maskPath = Path()

    init {
        // Explicitly no layer; default software/hardware view rendering is fine
        // for a single path and avoids a full-screen GPU texture.
        setLayerType(LAYER_TYPE_NONE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val rect = MaskStore.apertureRectF(
            width,
            height,
            config,
            resources.displayMetrics.density
        )
        val radius = MaskStore.cornerRadiusPx(
            config,
            resources.displayMetrics.density
        )

        dimPaint.alpha = (config.dimAlpha * 255f).toInt().coerceIn(0, 255)

        maskPath.reset()
        maskPath.fillType = Path.FillType.EVEN_ODD
        maskPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        maskPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.drawPath(maskPath, dimPaint)

        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }
}
