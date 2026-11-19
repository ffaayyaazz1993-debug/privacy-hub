package com.privacyhub.app.privacy

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

enum class HandleKind { MOVE, RESIZE, CLOSE }

/** Small touchable affordance drawn above the non-touchable mask. */
class HandleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    val kind: HandleKind = HandleKind.MOVE,
) : View(context, attrs) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 16, 32, 56)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(220, 255, 255, 255)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = stroke.strokeWidth
        val cx = width / 2f
        val cy = height / 2f
        when (kind) {
            HandleKind.MOVE -> {
                val r = (minOf(width, height) / 2f) - pad
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, stroke)
                canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, stroke)
                canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, stroke)
            }
            HandleKind.RESIZE -> {
                val rect = android.graphics.RectF(pad, pad, width - pad, height - pad)
                canvas.drawRect(rect, fill)
                canvas.drawRect(rect, stroke)
                canvas.drawLine(rect.left + rect.width() * 0.25f, rect.top + rect.height() * 0.75f,
                    rect.right - rect.width() * 0.15f, rect.top + rect.height() * 0.25f, stroke)
            }
            HandleKind.CLOSE -> {
                val r = (minOf(width, height) / 2f) - pad
                canvas.drawCircle(cx, cy, r, fill)
                canvas.drawCircle(cx, cy, r, stroke)
                val k = r * 0.42f
                canvas.drawLine(cx - k, cy - k, cx + k, cy + k, stroke)
                canvas.drawLine(cx - k, cy + k, cx + k, cy - k, stroke)
            }
        }
    }
}
