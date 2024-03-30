package org.schabi.newpipe.error

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.ActionBar
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
class ReCaptchaActivity() : AppCompatActivity() {
    private var recaptchaBinding: ActivityRecaptchaBinding? = null
    private var foundCookies: String = ""
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        recaptchaBinding = ActivityRecaptchaBinding.inflate(getLayoutInflater())
        setContentView(recaptchaBinding!!.getRoot())
        setSupportActionBar(recaptchaBinding!!.toolbar)
        val url: String = sanitizeRecaptchaUrl(getIntent().getStringExtra(RECAPTCHA_URL_EXTRA))
        // set return to Cancel by default
        setResult(RESULT_CANCELED)

        // enable Javascript
        val webSettings: WebSettings = recaptchaBinding!!.reCaptchaWebView.getSettings()
        webSettings.setJavaScriptEnabled(true)
        webSettings.setUserAgentString(DownloaderImpl.Companion.USER_AGENT)
        recaptchaBinding!!.reCaptchaWebView.setWebViewClient(object : WebViewClient() {
            public override fun shouldOverrideUrlLoading(view: WebView,
                                                         request: WebResourceRequest): Boolean {
                if (MainActivity.Companion.DEBUG) {
                    Log.d(TAG, "shouldOverrideUrlLoading: url=" + request.getUrl().toString())
                }
                handleCookiesFromUrl(request.getUrl().toString())
                return false
            }

            public override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                handleCookiesFromUrl(url)
            }
        })

        // cleaning cache, history and cookies from webView
        recaptchaBinding!!.reCaptchaWebView.clearCache(true)
        recaptchaBinding!!.reCaptchaWebView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        recaptchaBinding!!.reCaptchaWebView.loadUrl(url)
    }

    public override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.menu_recaptcha, menu)
        val actionBar: ActionBar? = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false)
            actionBar.setTitle(R.string.title_activity_recaptcha)
            actionBar.setSubtitle(R.string.subtitle_activity_recaptcha)
        }
        return true
    }

    public override fun onBackPressed() {
        saveCookiesAndFinish()
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.menu_item_done) {
            saveCookiesAndFinish()
            return true
        }
        return false
    }

    private fun saveCookiesAndFinish() {
        // try to get cookies of unclosed page
        handleCookiesFromUrl(recaptchaBinding!!.reCaptchaWebView.getUrl())
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "saveCookiesAndFinish: foundCookies=" + foundCookies)
        }
        if (!foundCookies.isEmpty()) {
            // save cookies to preferences
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext())
            val key: String = getApplicationContext().getString(R.string.recaptcha_cookies_key)
            prefs.edit().putString(key, foundCookies).apply()

            // give cookies to Downloader class
            DownloaderImpl.Companion.getInstance()!!.setCookie(RECAPTCHA_COOKIES_KEY, foundCookies)
            setResult(RESULT_OK)
        }

        // Navigate to blank page (unloads youtube to prevent background playback)
        recaptchaBinding!!.reCaptchaWebView.loadUrl("about:blank")
        val intent: Intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        NavUtils.navigateUpTo(this, intent)
    }

    private fun handleCookiesFromUrl(url: String?) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "handleCookiesFromUrl: url=" + (if (url == null) "null" else url))
        }
        if (url == null) {
            return
        }
        val cookies: String = CookieManager.getInstance().getCookie(url)
        handleCookies(cookies)

        // sometimes cookies are inside the url
        val abuseStart: Int = url.indexOf("google_abuse=")
        if (abuseStart != -1) {
            val abuseEnd: Int = url.indexOf("+path")
            try {
                var abuseCookie: String? = url.substring(abuseStart + 13, abuseEnd)
                abuseCookie = Utils.decodeUrlUtf8(abuseCookie)
                handleCookies(abuseCookie)
            } catch (e: UnsupportedEncodingException) {
                if (MainActivity.Companion.DEBUG) {
                    e.printStackTrace()
                    Log.d(TAG, ("handleCookiesFromUrl: invalid google abuse starting at "
                            + abuseStart + " and ending at " + abuseEnd + " for url " + url))
                }
            } catch (e: StringIndexOutOfBoundsException) {
                if (MainActivity.Companion.DEBUG) {
                    e.printStackTrace()
                    Log.d(TAG, ("handleCookiesFromUrl: invalid google abuse starting at "
                            + abuseStart + " and ending at " + abuseEnd + " for url " + url))
                }
            }
        }
    }

    private fun handleCookies(cookies: String?) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "handleCookies: cookies=" + (if (cookies == null) "null" else cookies))
        }
        if (cookies == null) {
            return
        }
        addYoutubeCookies(cookies)
        // add here methods to extract cookies for other services
    }

    private fun addYoutubeCookies(cookies: String) {
        if ((cookies.contains("s_gl=") || cookies.contains("goojf=")
                        || cookies.contains("VISITOR_INFO1_LIVE=")
                        || cookies.contains("GOOGLE_ABUSE_EXEMPTION="))) {
            // youtube seems to also need the other cookies:
            addCookie(cookies)
        }
    }

    private fun addCookie(cookie: String) {
        if (foundCookies.contains(cookie)) {
            return
        }
        if (foundCookies.isEmpty() || foundCookies.endsWith("; ")) {
            foundCookies += cookie
        } else if (foundCookies.endsWith(";")) {
            foundCookies += " " + cookie
        } else {
            foundCookies += "; " + cookie
        }
    }

    companion object {
        val RECAPTCHA_REQUEST: Int = 10
        val RECAPTCHA_URL_EXTRA: String = "recaptcha_url_extra"
        val TAG: String = ReCaptchaActivity::class.java.toString()
        val YT_URL: String = "https://www.youtube.com"
        val RECAPTCHA_COOKIES_KEY: String = "recaptcha_cookies"
        fun sanitizeRecaptchaUrl(url: String?): String {
            if (url == null || url.trim({ it <= ' ' }).isEmpty()) {
                return YT_URL // YouTube is the most likely service to have thrown a recaptcha
            } else {
                // remove "pbj=1" parameter from YouYube urls, as it makes the page JSON and not HTML
                return url.replace("&pbj=1", "").replace("pbj=1&", "").replace("?pbj=1", "")
            }
        }
    }
}
