package com.privacyhub.app

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import com.privacyhub.app.privacy.HandleKind
import com.privacyhub.app.privacy.HandleView
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.privacy.MaskView

/**
 * Private reader: a WebView behind the blackout mask, all inside OUR OWN window.
 * Because nothing here is a system overlay over another app, Android's
 * obscured-touch (tapjacking) protection does not apply: the page stays
 * scrollable through the aperture and the handles are directly draggable.
 */
class ReaderActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var maskView: MaskView
    private lateinit var urlInput: EditText
    private lateinit var moveHandle: HandleView
    private lateinit var resizeHandle: HandleView
    private var config: MaskConfig = MaskStore.DEFAULT

    private val handleSizePx: Int get() = (52 * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = MaskStore.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10151F.toInt())
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 4)
        }
        urlInput = EditText(this).apply {
            hint = "https://example.com"
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setText(intent?.getStringExtra(EXTRA_URL) ?: "https://example.com")
        }
        row1.addView(urlInput)
        row1.addView(Button(this).apply { text = "Open"; setOnClickListener { loadUrl() } })
        row1.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
        root.addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 0, 8, 8)
        }
        row2.addView(Button(this).apply { text = "Conceal"; setOnClickListener { applyPreset(MaskStore.CONCEALED) } })
        row2.addView(Button(this).apply { text = "Reveal"; setOnClickListener { applyPreset(MaskStore.DEFAULT) } })
        val dark = SeekBar(this).apply {
            max = 68; progress = ((config.dimAlpha - 0.30f) * 100).toInt().coerceIn(0, 68)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) setConfig(config.copy(dimAlpha = 0.30f + p / 100f), save = true)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row2.addView(dark)
        root.addView(row2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        content.addView(webView)

        maskView = MaskView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            config = this@ReaderActivity.config
            isClickable = false
            isFocusable = false
            // Pass touches through to the WebView so the page stays scrollable.
            setOnTouchListener { _, _ -> false }
        }
        content.addView(maskView)

        moveHandle = HandleView(this, kind = HandleKind.MOVE).apply {
            isClickable = true
            setOnTouchListener { _, e -> onMoveTouch(e) }
        }
        resizeHandle = HandleView(this, kind = HandleKind.RESIZE).apply {
            isClickable = true
            setOnTouchListener { _, e -> onResizeTouch(e) }
        }
        content.addView(moveHandle, FrameLayout.LayoutParams(handleSizePx, handleSizePx))
        content.addView(resizeHandle, FrameLayout.LayoutParams(handleSizePx, handleSizePx))

        root.addView(content)
        setContentView(root)

        maskView.post { positionHandles() }
        if (savedInstanceState == null) loadUrl()
    }

    private fun applyPreset(c: MaskConfig) {
        setConfig(c.copy(dimAlpha = config.dimAlpha), save = true)
    }

    private fun setConfig(next: MaskConfig, save: Boolean) {
        config = MaskStore.normalize(next)
        if (save) MaskStore.save(this, config)
        maskView.config = config
        positionHandles()
    }

    private fun positionHandles() {
        val w = maskView.width; val h = maskView.height
        if (w == 0 || h == 0) { maskView.post { positionHandles() }; return }
        val r = MaskStore.apertureRectF(w, h, config, resources.displayMetrics.density)
        place(moveHandle, (r.centerX() - handleSizePx / 2f).toInt(), (r.bottom - handleSizePx / 2f).toInt())
        place(resizeHandle, (r.right - handleSizePx / 2f).toInt(), (r.bottom - handleSizePx / 2f).toInt())
    }

    private fun place(v: View, x: Int, y: Int) {
        val lp = v.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = x; lp.topMargin = y
        v.layoutParams = lp
    }

    private var sx = 0f; private var sy = 0f; private var sl = 0f; private var st = 0f
    private fun onMoveTouch(e: MotionEvent): Boolean {
        val w = maskView.width; val h = maskView.height
        if (w == 0 || h == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; sl = config.left; st = config.top; return true }
            MotionEvent.ACTION_MOVE -> {
                val cw = config.right - config.left; val ch = config.bottom - config.top
                val left = (sl + (e.rawX - sx) / w).coerceIn(0f, 1f - cw)
                val top = (st + (e.rawY - sy) / h).coerceIn(0f, 1f - ch)
                setConfig(config.copy(left = left, top = top, right = left + cw, bottom = top + ch), save = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { MaskStore.save(this, config); return true }
        }
        return false
    }

    private var rx = 0f; private var ry = 0f; private var sr = 0f; private var sb = 0f
    private fun onResizeTouch(e: MotionEvent): Boolean {
        val w = maskView.width; val h = maskView.height
        if (w == 0 || h == 0) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { rx = e.rawX; ry = e.rawY; sr = config.right; sb = config.bottom; return true }
            MotionEvent.ACTION_MOVE -> {
                val right = (sr + (e.rawX - rx) / w).coerceAtLeast(config.left + 0.06f).coerceAtMost(1f)
                val bottom = (sb + (e.rawY - ry) / h).coerceAtLeast(config.top + 0.06f).coerceAtMost(1f)
                setConfig(config.copy(right = right, bottom = bottom), save = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { MaskStore.save(this, config); return true }
        }
        return false
    }

    private fun loadUrl() {
        var url = urlInput.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        urlInput.setText(url)
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        runCatching {
            webView.stopLoading(); webView.loadUrl("about:blank")
            webView.clearHistory(); webView.clearCache(true)
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        super.onDestroy()
    }

    companion object { const val EXTRA_URL = "extra_url" }
}
