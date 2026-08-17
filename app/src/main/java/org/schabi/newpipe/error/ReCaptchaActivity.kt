package org.schabi.newpipe.error

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NavUtils
import androidx.preference.PreferenceManager
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.utils.Utils

class ReCaptchaActivity : ComponentActivity() {

    private var foundCookies = ""
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = sanitizeRecaptchaUrl(intent.getStringExtra(RECAPTCHA_URL_EXTRA))
        setResult(RESULT_CANCELED)

        setContent {
            ReCaptchaScreen(
                url = url,
                onDone = { saveCookiesAndFinish() },
                onWebViewCreated = { webView = it }
            )
        }
    }

    @Composable
    fun ReCaptchaScreen(
        url: String,
        onDone: () -> Unit,
        onWebViewCreated: (WebView) -> Unit
    ) {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("ReCaptcha") },
                    actions = {
                        Button(onClick = onDone) {
                            Text("Done")
                        }
                    }
                )
            }
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.userAgentString = DownloaderImpl.USER_AGENT
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                handleCookiesFromUrl(request.url.toString())
                                return false
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                handleCookiesFromUrl(url)
                            }
                        }
                        clearCache(true)
                        clearHistory()
                        CookieManager.getInstance().removeAllCookies(null)
                        loadUrl(url)
                        onWebViewCreated(this)
                    }
                }
            )
        }
    }

    private fun saveCookiesAndFinish() {
        webView?.let { handleCookiesFromUrl(it.url) }

        if (foundCookies.isNotEmpty()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val key = applicationContext.getString(R.string.recaptcha_cookies_key)
            prefs.edit().putString(key, foundCookies).apply()
            DownloaderImpl.instance?.setCookie(RECAPTCHA_COOKIES_KEY, foundCookies)
            setResult(RESULT_OK)
        }

        webView?.loadUrl("about:blank")
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        NavUtils.navigateUpTo(this, intent)
    }

    private fun handleCookiesFromUrl(url: String?) {
        if (url == null) return
        val cookies = CookieManager.getInstance().getCookie(url)
        handleCookies(cookies)
        val abuseStart = url.indexOf("google_abuse=")
        if (abuseStart != -1) {
            val abuseEnd = url.indexOf("+path")
            try {
                handleCookies(Utils.decodeUrlUtf8(url.substring(abuseStart + 13, abuseEnd)))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun handleCookies(cookies: String?) {
        if (cookies == null) return
        if (cookies.contains("s_gl=") || cookies.contains("goojf=") ||
            cookies.contains("VISITOR_INFO1_LIVE=") ||
            cookies.contains("GOOGLE_ABUSE_EXEMPTION=")
        ) {
            if (!foundCookies.contains(cookies)) {
                foundCookies = when {
                    foundCookies.isEmpty() || foundCookies.endsWith("; ") -> foundCookies + cookies
                    foundCookies.endsWith(";") -> "$foundCookies $cookies"
                    else -> "$foundCookies; $cookies"
                }
            }
        }
    }

    companion object {
        const val RECAPTCHA_REQUEST = 10
        const val RECAPTCHA_URL_EXTRA = "recaptcha_url_extra"
        const val YT_URL = "https://www.youtube.com"
        const val RECAPTCHA_COOKIES_KEY = "recaptcha_cookies"

        @JvmStatic
        fun sanitizeRecaptchaUrl(url: String?): String {
            return if (url.isNullOrBlank()) YT_URL else url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "")
        }
    }
}
