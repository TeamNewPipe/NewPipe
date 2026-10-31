package org.schabi.newpipe

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper

/**
 * Handles the "Play <something>" voice intent ([android.media.action.MEDIA_PLAY_FROM_SEARCH]) when
 * it arrives as a plain text query (i.e. without a media URL), e.g. from Google Assistant or an
 * automation tool.
 *
 * Declaring this activity with a bare MEDIA_PLAY_FROM_SEARCH intent filter is also what makes
 * Android Auto route spoken play-from-search commands to our MediaSession (see
 * MediaBrowserPlaybackPreparer.onPrepareFromSearch); Auto uses the session path and does not
 * actually launch this activity.
 *
 * When launched directly, it searches the user's selected service and starts background playback of
 * the first matching stream, falling back to the search results screen if nothing is found.
 *
 * It has no UI of its own (translucent theme): it kicks off playback and finishes.
 */
class PlayMediaFromSearchActivity : Activity() {
    private var searchDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val query = intent?.getStringExtra(SearchManager.QUERY)
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)

        if (query.isNullOrBlank()) {
            // Nothing to search for; just open the app rather than failing silently.
            NavigationHelper.openMainActivity(this)
            finish()
            return
        }

        val serviceId = ServiceHelper.getSelectedServiceId(this)

        searchDisposable = ExtractorHelper.searchFor(serviceId, query, emptyList(), "")
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { searchInfo ->
                    val firstStream = searchInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                        .firstOrNull()
                    if (firstStream == null) {
                        // No playable result: show the search results screen as a fallback.
                        NavigationHelper.openSearch(this, serviceId, query)
                    } else {
                        NavigationHelper.playOnBackgroundPlayer(
                            this,
                            SinglePlayQueue(firstStream),
                            true
                        )
                    }
                    finish()
                },
                { throwable ->
                    Log.e(TAG, "Failed to play from search query [$query]", throwable)
                    // Don't dead-end the user: fall back to the search results screen.
                    NavigationHelper.openSearch(this, serviceId, query)
                    finish()
                }
            )
    }

    override fun onDestroy() {
        super.onDestroy()
        searchDisposable?.dispose()
    }

    companion object {
        private val TAG = PlayMediaFromSearchActivity::class.java.simpleName
    }
}
