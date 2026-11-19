package com.privacyhub.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.privacyhub.app.privacy.MaskConfig
import com.privacyhub.app.privacy.MaskStore
import com.privacyhub.app.privacy.MaskView

/**
 * Private reader: a WebView behind the same blackout mask used by the system
 * overlay. History/cookies are wiped when the reader closes.
 */
class ReaderActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var maskView: MaskView
    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10151F.toInt())
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
        }

        urlInput = EditText(this).apply {
            hint = "https://example.com"
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setText(intent?.getStringExtra(EXTRA_URL) ?: "https://example.com")
        }

        controls.addView(urlInput)
        controls.addView(Button(this).apply {
            text = "Open"
            setOnClickListener { loadUrl() }
        })
        controls.addView(Button(this).apply {
            text = "Conceal"
            setOnClickListener {
                val c = MaskStore.CONCEALED
                MaskStore.save(this@ReaderActivity, c)
                applyConfig(c)
            }
        })
        controls.addView(Button(this).apply {
            text = "Reveal"
            setOnClickListener {
                val c = MaskStore.DEFAULT
                MaskStore.save(this@ReaderActivity, c)
                applyConfig(c)
            }
        })
        controls.addView(Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        })

        root.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        content.addView(webView)

        maskView = MaskView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        content.addView(maskView)
        root.addView(content)

        setContentView(root)
        applyConfig(MaskStore.load(this))
        if (savedInstanceState == null) loadUrl()
    }

    private fun applyConfig(config: MaskConfig) {
        maskView.config = config
        maskView.invalidate()
    }

    private fun loadUrl() {
        var url = urlInput.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        urlInput.setText(url)
        webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        applyConfig(MaskStore.load(this))
    }

    override fun onDestroy() {
        clearPrivateData()
        super.onDestroy()
    }

    private fun clearPrivateData() {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}
