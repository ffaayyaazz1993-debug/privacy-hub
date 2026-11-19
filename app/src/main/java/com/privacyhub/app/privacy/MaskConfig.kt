package com.privacyhub.app.privacy

import android.content.Context

/**
 * Saved geometry for the privacy mask. All aperture values are fractions of the
 * masked surface, so the same layout survives rotation and different screens.
 */
data class MaskConfig(
    val dimAlpha: Float = 0.92f,
    val left: Float = 0.18f,
    val top: Float = 0.32f,
    val right: Float = 0.82f,
    val bottom: Float = 0.62f,
    val cornerRadiusDp: Float = 24f,
)

object MaskStore {

    const val PREFS = "smart_privacy_prefs"
    const val KEY_ENABLED = "enabled"
    private const val KEY_ALPHA = "dim_alpha"
    private const val KEY_LEFT = "aperture_left"
    private const val KEY_TOP = "aperture_top"
    private const val KEY_RIGHT = "aperture_right"
    private const val KEY_BOTTOM = "aperture_bottom"
    private const val KEY_RADIUS = "corner_radius"

    val DEFAULT = MaskConfig()
    val CONCEALED = MaskConfig(
        dimAlpha = 0.98f,
        left = 0.47f,
        top = 0.47f,
        right = 0.53f,
        bottom = 0.53f,
        cornerRadiusDp = 8f,
    )

    fun load(context: Context): MaskConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return normalize(
            MaskConfig(
                dimAlpha = p.getFloat(KEY_ALPHA, DEFAULT.dimAlpha),
                left = p.getFloat(KEY_LEFT, DEFAULT.left),
                top = p.getFloat(KEY_TOP, DEFAULT.top),
                right = p.getFloat(KEY_RIGHT, DEFAULT.right),
                bottom = p.getFloat(KEY_BOTTOM, DEFAULT.bottom),
                cornerRadiusDp = p.getFloat(KEY_RADIUS, DEFAULT.cornerRadiusDp),
            )
        )
    }

    fun save(context: Context, config: MaskConfig) {
        val c = normalize(config)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_ALPHA, c.dimAlpha)
            .putFloat(KEY_LEFT, c.left)
            .putFloat(KEY_TOP, c.top)
            .putFloat(KEY_RIGHT, c.right)
            .putFloat(KEY_BOTTOM, c.bottom)
            .putFloat(KEY_RADIUS, c.cornerRadiusDp)
            .apply()
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun normalize(c: MaskConfig): MaskConfig {
        val minSize = 0.06f
        val maxAlpha = 0.98f

        var left = c.left.coerceIn(0f, 1f)
        var right = c.right.coerceIn(0f, 1f)
        var top = c.top.coerceIn(0f, 1f)
        var bottom = c.bottom.coerceIn(0f, 1f)

        if (right - left < minSize) {
            val center = (left + right) / 2f
            left = (center - minSize / 2f).coerceIn(0f, 1f - minSize)
            right = left + minSize
        }
        if (bottom - top < minSize) {
            val center = (top + bottom) / 2f
            top = (center - minSize / 2f).coerceIn(0f, 1f - minSize)
            bottom = top + minSize
        }

        return MaskConfig(
            dimAlpha = c.dimAlpha.coerceIn(0f, maxAlpha),
            left = left.coerceIn(0f, 1f),
            top = top.coerceIn(0f, 1f),
            right = right.coerceIn(0f, 1f),
            bottom = bottom.coerceIn(0f, 1f),
            cornerRadiusDp = c.cornerRadiusDp.coerceIn(0f, 48f),
        )
    }

    fun apertureRectF(width: Int, height: Int, c: MaskConfig, density: Float): android.graphics.RectF {
        val r = MaskStore.normalize(c)
        return android.graphics.RectF(
            r.left * width,
            r.top * height,
            r.right * width,
            r.bottom * height,
        )
    }

    fun cornerRadiusPx(c: MaskConfig, density: Float): Float = c.cornerRadiusDp * density
}
