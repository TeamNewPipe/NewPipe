package org.schabi.newpipe.error

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NavUtils
import androidx.preference.PreferenceManager
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityRecaptchaBinding
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.util.ThemeHelper
import java.io.UnsupportedEncodingException

/*
 * Created by beneth <bmauduit@beneth.fr> on 06.12.16.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * ReCaptchaActivity.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
class ReCaptchaActivity : AppCompatActivity() {
    private var recaptchaBinding: ActivityRecaptchaBinding? = null
    private var foundCookies = ""
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        recaptchaBinding = ActivityRecaptchaBinding.inflate(
            layoutInflater
        )
        setContentView(recaptchaBinding!!.root)
        setSupportActionBar(recaptchaBinding!!.toolbar)
        val url = sanitizeRecaptchaUrl(intent.getStringExtra(RECAPTCHA_URL_EXTRA))
        // set return to Cancel by default
        setResult(RESULT_CANCELED)

        // enable Javascript
        val webSettings = recaptchaBinding!!.reCaptchaWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.userAgentString = DownloaderImpl.USER_AGENT
        recaptchaBinding!!.reCaptchaWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "shouldOverrideUrlLoading: url=" + request.url.toString())
                }
                handleCookiesFromUrl(request.url.toString())
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                handleCookiesFromUrl(url)
            }
        }

        // cleaning cache, history and cookies from webView
        recaptchaBinding!!.reCaptchaWebView.clearCache(true)
        recaptchaBinding!!.reCaptchaWebView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        recaptchaBinding!!.reCaptchaWebView.loadUrl(url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recaptcha, menu)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false)
            actionBar.setTitle(R.string.title_activity_recaptcha)
            actionBar.setSubtitle(R.string.subtitle_activity_recaptcha)
        }
        return true
    }

    override fun onBackPressed() {
        saveCookiesAndFinish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_done) {
            saveCookiesAndFinish()
            return true
        }
        return false
    }

    private fun saveCookiesAndFinish() {
        // try to get cookies of unclosed page
        handleCookiesFromUrl(recaptchaBinding!!.reCaptchaWebView.url)
        if (MainActivity.DEBUG) {
            Log.d(TAG, "saveCookiesAndFinish: foundCookies=$foundCookies")
        }
        if (!foundCookies.isEmpty()) {
            // save cookies to preferences
            val prefs = PreferenceManager.getDefaultSharedPreferences(
                applicationContext
            )
            val key = applicationContext.getString(R.string.recaptcha_cookies_key)
            prefs.edit().putString(key, foundCookies).apply()

            // give cookies to Downloader class
            DownloaderImpl.getInstance().setCookie(RECAPTCHA_COOKIES_KEY, foundCookies)
            setResult(RESULT_OK)
        }

        // Navigate to blank page (unloads youtube to prevent background playback)
        recaptchaBinding!!.reCaptchaWebView.loadUrl("about:blank")
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        NavUtils.navigateUpTo(this, intent)
    }

    private fun handleCookiesFromUrl(url: String?) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "handleCookiesFromUrl: url=" + (url ?: "null"))
        }
        if (url == null) {
            return
        }
        val cookies = CookieManager.getInstance().getCookie(url)
        handleCookies(cookies)

        // sometimes cookies are inside the url
        val abuseStart = url.indexOf("google_abuse=")
        if (abuseStart != -1) {
            val abuseEnd = url.indexOf("+path")
            try {
                var abuseCookie: String? = url.substring(abuseStart + 13, abuseEnd)
                abuseCookie = Utils.decodeUrlUtf8(abuseCookie)
                handleCookies(abuseCookie)
            } catch (e: UnsupportedEncodingException) {
                if (MainActivity.DEBUG) {
                    e.printStackTrace()
                    Log.d(
                        TAG,
                        "handleCookiesFromUrl: invalid google abuse starting at " +
                            abuseStart + " and ending at " + abuseEnd + " for url " + url
                    )
                }
            } catch (e: StringIndexOutOfBoundsException) {
                if (MainActivity.DEBUG) {
                    e.printStackTrace()
                    Log.d(
                        TAG,
                        "handleCookiesFromUrl: invalid google abuse starting at " +
                            abuseStart + " and ending at " + abuseEnd + " for url " + url
                    )
                }
            }
        }
    }

    private fun handleCookies(cookies: String?) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "handleCookies: cookies=" + (cookies ?: "null"))
        }
        if (cookies == null) {
            return
        }
        addYoutubeCookies(cookies)
        // add here methods to extract cookies for other services
    }

    private fun addYoutubeCookies(cookies: String) {
        if (cookies.contains("s_gl=") || cookies.contains("goojf=") ||
            cookies.contains("VISITOR_INFO1_LIVE=") ||
            cookies.contains("GOOGLE_ABUSE_EXEMPTION=")
        ) {
            // youtube seems to also need the other cookies:
            addCookie(cookies)
        }
    }

    private fun addCookie(cookie: String) {
        if (foundCookies.contains(cookie)) {
            return
        }
        foundCookies += if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            cookie
        } else if (foundCookies.endsWith(";")) {
            " $cookie"
        } else {
            "; $cookie"
        }
    }

    companion object {
        const val RECAPTCHA_REQUEST = 10
        const val RECAPTCHA_URL_EXTRA = "recaptcha_url_extra"
        val TAG = ReCaptchaActivity::class.java.toString()
        const val YT_URL = "https://www.youtube.com"
        const val RECAPTCHA_COOKIES_KEY = "recaptcha_cookies"
        fun sanitizeRecaptchaUrl(url: String?): String {
            return if (url == null || url.trim { it <= ' ' }.isEmpty()) {
                YT_URL // YouTube is the most likely service to have thrown a recaptcha
            } else {
                // remove "pbj=1" parameter from YouYube urls, as it makes the page JSON and not HTML
                url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "")
            }
        }
    }
}
