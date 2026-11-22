package org.schabi.newpipe.about.changelog

import android.content.Intent
import android.util.Log
import android.view.ViewTreeObserver
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.util.KEY_LINK_TYPE
import org.schabi.newpipe.util.KEY_OPEN_SEARCH

class ChangelogPromptController internal constructor(
    private val activity: FragmentActivity,
    private val loadNotice: () -> Maybe<ChangelogNotice>
) : DefaultLifecycleObserver {
    constructor(activity: FragmentActivity) : this(
        activity,
        {
            val context = activity.applicationContext
            Maybe.fromCallable {
                val html = context.assets.open("changelog.html").bufferedReader().use { it.readText() }
                ChangelogCatalog.parse(html).notice(BuildConfig.VERSION_NAME, ChangelogPreferences(context).lastSeenCode)
            }.subscribeOn(Schedulers.io())
        }
    )

    private var load: Disposable? = null
    private var loaded = false
    private var pending: ChangelogNotice? = null
    private var launchIntent = activity.intent
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
        if (focused) maybeShow()
    }

    init {
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        activity.lifecycle.addObserver(this)
    }

    fun onNewIntent(intent: Intent) {
        launchIntent = intent
        maybeShow()
    }

    override fun onResume(owner: LifecycleOwner) {
        // Run after FragmentActivity has finished restoring/resuming its fragments.
        activity.window.decorView.post { maybeShow() }
    }

    fun maybeShow() {
        val fragments = activity.supportFragmentManager
        if (
            activity.isFinishing || activity.isDestroyed || fragments.isStateSaved ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !activity.hasWindowFocus() ||
            fragments.findFragmentByTag(ChangelogDialogFragment.TAG) != null
        ) {
            return
        }
        // Leave external playback/search intents uninterrupted; show on the next normal app launch.
        if (
            (launchIntent.action != null && launchIntent.action != Intent.ACTION_MAIN) ||
            launchIntent.hasExtra(KEY_LINK_TYPE) || launchIntent.hasExtra(KEY_OPEN_SEARCH)
        ) {
            return
        }
        if (!loaded) {
            loaded = true
            load = loadNotice()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        pending = it
                        maybeShow()
                    },
                    { Log.w(TAG, "Could not load bundled changelog", it) }
                )
        } else {
            pending?.let { notice ->
                pending = null
                ChangelogDialogFragment.newInstance(notice).showNow(fragments, ChangelogDialogFragment.TAG)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        load?.dispose()
        pending = null
    }

    companion object {
        private const val TAG = "ChangelogPrompt"
    }
}
