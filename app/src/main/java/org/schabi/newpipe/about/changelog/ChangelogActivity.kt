package org.schabi.newpipe.about.changelog

import android.graphics.Color
import android.graphics.Color.YELLOW
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.widget.NestedScrollView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.about.getLicenseStylesheet
import org.schabi.newpipe.databinding.ActivityChangelogBinding
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.EdgeToEdgeHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

class ChangelogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChangelogBinding
    private var load: Disposable? = null
    private var webView: WebView? = null
    private var fallbackScroll: NestedScrollView? = null
    private var fallbackText: TextView? = null
    private var searchQuery = ""
    private var matches = emptyList<IntRange>()
    private var currentMatch = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this)
        binding = ActivityChangelogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeHelper.applySystemBarPadding(binding.root)
        setSupportActionBar(binding.changelogToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.changelog_title)
        val scroll = savedInstanceState?.getInt(SCROLL) ?: 0
        searchQuery = savedInstanceState?.getString(SEARCH_QUERY).orEmpty()
        val context = applicationContext
        load = Single.fromCallable { context.assets.open("changelog.html").bufferedReader().use { it.readText() } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { showContent(it, scroll) },
                {
                    Toast.makeText(this, R.string.changelog_load_failed, Toast.LENGTH_LONG).show()
                    finish()
                }
            )
    }

    private fun showContent(html: String, scroll: Int) {
        val viewer = if (DeviceUtils.supportsWebView()) runCatching { WebView(this) }.getOrNull() else null
        if (viewer == null) {
            val text = changelogTextView(this, html, false)
            binding.changelogContent.addView(text)
            fallbackScroll = text
            fallbackText = text.getChildAt(0) as? TextView
            text.post {
                text.scrollTo(0, scroll)
                if (searchQuery.isNotBlank()) showSearchMatches()
            }
            return
        }
        webView = viewer
        viewer.setBackgroundColor(Color.TRANSPARENT)
        viewer.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            textZoom = (100 * resources.configuration.fontScale).toInt()
        }
        var restoreScroll = scroll
        viewer.webViewClient = object : WebViewClient() {
            @Deprecated("Used on Android 6")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = openLink(url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = openLink(request.url.toString())

            override fun onPageFinished(view: WebView, url: String) {
                val position = restoreScroll
                restoreScroll = 0
                if (position > 0) view.post { view.scrollTo(0, position) }
                if (searchQuery.isNotBlank()) view.findAllAsync(searchQuery)
            }
        }
        binding.changelogContent.addView(viewer)
        val themed = html.replace("</head>", "<style>${getLicenseStylesheet(this)}</style></head>")
        viewer.loadDataWithBaseURL(BASE_URL, themed, "text/html", "UTF-8", null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_changelog_search, menu)
        val search = menu.findItem(R.id.changelog_search)?.actionView as? SearchView
        search?.queryHint = getString(R.string.changelog_search_hint)
        search?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                updateSearch(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                updateSearch(newText.orEmpty())
                return true
            }
        })
        if (searchQuery.isNotBlank()) {
            menu.findItem(R.id.changelog_search)?.expandActionView()
            search?.setQuery(searchQuery, false)
        }
        return true
    }

    private fun updateSearch(query: String) {
        searchQuery = query
        currentMatch = -1
        webView?.let { if (query.isBlank()) it.clearMatches() else it.findAllAsync(query) }
        showSearchMatches()
    }

    private fun showSearchMatches() {
        val text = fallbackText ?: return
        val spannable = SpannableStringBuilder(text.text)
        spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java).forEach {
            spannable.removeSpan(it)
        }
        matches = ChangelogSearch.findMatches(spannable, searchQuery)
        matches.forEach { range ->
            spannable.setSpan(
                BackgroundColorSpan(YELLOW),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        text.text = spannable
        if (matches.isNotEmpty()) {
            currentMatch = 0
            scrollToMatch()
        }
    }

    private fun navigateMatch(forward: Boolean) {
        if (webView != null) {
            webView?.findNext(forward)
            return
        }
        if (matches.isEmpty()) return
        currentMatch = if (forward) {
            (currentMatch + 1) % matches.size
        } else {
            (currentMatch + matches.size - 1) % matches.size
        }
        scrollToMatch()
    }

    private fun scrollToMatch() {
        val text = fallbackText ?: return
        val scroll = fallbackScroll ?: return
        val match = matches.getOrNull(currentMatch) ?: return
        text.post {
            val layout = text.layout ?: return@post
            scroll.smoothScrollTo(0, layout.getLineTop(layout.getLineForOffset(match.first)))
        }
    }

    private fun openLink(url: String): Boolean {
        if (url.startsWith("#") || url.startsWith("$BASE_URL#")) return false
        if (Uri.parse(url).scheme in listOf("https", "http")) ShareUtils.openUrlInBrowser(this, url)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SCROLL, webView?.scrollY ?: binding.changelogContent.getChildAt(0)?.scrollY ?: 0)
        outState.putString(SEARCH_QUERY, searchQuery)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.changelog_search_next -> {
                navigateMatch(true)
                return true
            }

            R.id.changelog_search_previous -> {
                navigateMatch(false)
                return true
            }
        }
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        load?.dispose()
        webView?.let {
            binding.changelogContent.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        fallbackScroll = null
        fallbackText = null
        super.onDestroy()
    }

    companion object {
        private const val BASE_URL = "https://appassets.androidplatform.net/assets/changelog.html"
        private const val SCROLL = "changelog-scroll"
        private const val SEARCH_QUERY = "changelog-search-query"
    }
}
