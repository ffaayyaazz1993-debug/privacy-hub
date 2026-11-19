package com.privacyhub.app.privacy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Black scrim with a transparent rounded aperture.
 *
 * The CLEAR xfermode punches real transparency out of the layer, so the app
 * underneath remains visible through the hole instead of merely being painted a
 * lighter colour.
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
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(90, 255, 255, 255)
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        dimPaint.alpha = (config.dimAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

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
        canvas.drawRoundRect(rect, radius, radius, clearPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }
}
