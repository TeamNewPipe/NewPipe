/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.list.comments

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.post.PostInfoItem
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.list.BaseListInfoFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.info_list.InfoListAdapter
import org.schabi.newpipe.info_list.holder.PostInfoItemHolder
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.util.InfoCache
import org.schabi.newpipe.util.NavigationHelper

class PostCommentsNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun zeroAndUnknownCountsKeepTheActionWithoutReplacingTextAndCopyGestures() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var holder: PostInfoItemHolder
            lateinit var content: TextView
            var previousClip: ClipData? = null
            onActivity(scenario) { activity ->
                val parent = FrameLayout(activity)
                activity.setContentView(parent)
                // Inflated cards can receive nested themed contexts instead of the activity itself.
                holder = PostInfoItemHolder(InfoItemBuilder(ContextWrapper(ContextWrapper(activity))), parent)
                parent.addView(holder.itemView)
                content = holder.itemView.findViewById(R.id.itemPostContentView)
                for (count in listOf(0L, PostInfoItem.UNKNOWN_COUNT)) {
                    holder.updateFromItem(post().apply { commentCount = count }, null)
                    val comments = holder.itemView.findViewById<View>(R.id.itemPostCommentsButton)
                    assertEquals(View.VISIBLE, comments.visibility)
                    assertTrue(comments.isEnabled)
                    assertTrue(comments.isFocusable)
                    assertTrue(comments.hasOnClickListeners())
                }
                assertTrue(holder.itemView.findViewById<View>(R.id.itemOpenOriginalButton).hasOnClickListeners())
                assertTrue(holder.itemView.findViewById<View>(R.id.itemAvatarView).hasOnClickListeners())
                assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.itemAttachmentCard).visibility)
                previousClip = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            }
            try {
                await(scenario) { content.maxLines == 4 && content.text.endsWith("…") }
                onActivity(scenario) { assertTrue(holder.itemView.performClick()) }
                await(scenario) { content.maxLines == Int.MAX_VALUE && content.text.contains("Line 8") }
                onActivity(scenario) { activity ->
                    assertTrue(holder.itemView.performLongClick())
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    assertEquals("${post().content}\n$SOURCE_URL", clipboard.primaryClip!!.getItemAt(0).text.toString())

                    // Recycled non-post/unsupported-service cards must drop the old listener.
                    holder.updateFromItem(PostInfoItem(0, "https://www.youtube.com/watch?v=BaW_jenozKc", "Video URL"), null)
                    val comments = holder.itemView.findViewById<View>(R.id.itemPostCommentsButton)
                    assertEquals(View.GONE, comments.visibility)
                    assertFalse(comments.isEnabled)
                    assertFalse(comments.hasOnClickListeners())
                    holder.updateFromItem(PostInfoItem(ServiceList.SoundCloud.serviceId, POST_URL, "Other service"), null)
                    assertEquals(View.GONE, comments.visibility)
                    assertFalse(comments.hasOnClickListeners())
                }
            } finally {
                onActivity(scenario) { activity ->
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (previousClip != null) {
                        clipboard.setPrimaryClip(previousClip!!)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    }
                }
            }
        }
    }

    @Test(timeout = 60_000)
    fun commentsRepliesAndSearchRestoreOnThePostWhileAttachmentsKeepTheirDestination() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val tabsKey = context.getString(R.string.saved_tabs_key)
        val archiveKey = context.getString(R.string.comment_archive_enabled_key)
        val keys = listOf(tabsKey, archiveKey, "comment_sort_newest", "github_star_prompt_completed")
        val saved = keys.associateWith { preferences.all[it] }
        val changelog = context.getSharedPreferences("changelog", Context.MODE_PRIVATE)
        val previousRelease = changelog.all["last_seen_release"] as? Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        preferences.edit().putBoolean(archiveKey, false).putBoolean("comment_sort_newest", false)
            .putBoolean("github_star_prompt_completed", true).commit()
        changelog.edit().putInt("last_seen_release", Int.MAX_VALUE).commit()
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.BLANK.tab))
        cacheComments()
        cachePlaylist()
        try {
            val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                lateinit var origin: PlaylistFragment
                lateinit var originWorker: Disposable
                onActivity(scenario) { activity ->
                    NavigationHelper.openPlaylistFragment(
                        activity.supportFragmentManager,
                        0,
                        PLAYLIST_URL,
                        "Attached playlist"
                    )
                    activity.supportFragmentManager.executePendingTransactions()
                    origin = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as PlaylistFragment
                    originWorker = BaseListInfoFragment::class.java.getDeclaredField("currentWorker")
                        .apply { isAccessible = true }.get(origin) as Disposable
                    val holder = PostInfoItemHolder(InfoItemBuilder(ContextWrapper(activity)), FrameLayout(activity))
                    holder.updateFromItem(post(), null)
                    assertTrue(holder.itemView.findViewById<View>(R.id.itemPostCommentsButton).performClick())
                    activity.supportFragmentManager.executePendingTransactions()
                }
                await(scenario) { origin.view == null }
                onActivity(scenario) {
                    assertTrue("Removing an unresumed view must cancel its queued result", originWorker.isDisposed)
                }
                awaitComments(scenario, 2)
                onActivity(scenario) { activity ->
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as PostCommentsFragment
                    assertEquals(activity.getString(R.string.post_comments_title), activity.supportActionBar!!.title)
                    assertTrue(activity.supportActionBar!!.displayOptions and ActionBar.DISPLAY_HOME_AS_UP != 0)
                    fragment.requireView().findViewById<View>(R.id.comments_search_toggle).performClick()
                    fragment.requireView().findViewById<EditText>(R.id.comments_search_input).setText("needle")
                }
                awaitComments(scenario, 1)
                scenario.recreate()
                awaitComments(scenario, 1)
                lateinit var detail: VideoDetailFragment
                onActivity(scenario) { activity ->
                    // No stream is loaded: this real collapsed detail fragment needs no backend.
                    detail = VideoDetailFragment.getInstanceInCollapsedState()
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_player_holder, detail).commitNow()
                }
                await(scenario) { sheetState(it) == BottomSheetBehavior.STATE_COLLAPSED }
                onActivity(scenario) { activity ->
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as PostCommentsFragment
                    assertEquals("needle", fragment.requireView().findViewById<EditText>(R.id.comments_search_input).text.toString())
                    assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.comments_search_field).visibility)
                    val list = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
                    val comment = (list.adapter as InfoListAdapter).itemsList.single() as CommentsInfoItem
                    assertEquals(POST_URL, comment.url)
                    assertTrue(list.getChildAt(0).findViewById<View>(R.id.replies_button).performClick())
                    activity.supportFragmentManager.executePendingTransactions()
                    val replies = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as CommentRepliesFragment
                    assertEquals(POST_URL, replies.commentsInfoItem.url)
                    assertEquals("parent", replies.commentsInfoItem.commentId)
                    activity.onBackPressed()
                    activity.supportFragmentManager.executePendingTransactions()
                }
                awaitComments(scenario, 1)
                onActivity(scenario) { activity ->
                    assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheetState(activity))
                    assertSame(detail, activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder))
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as PostCommentsFragment
                    val list = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
                    assertTrue(list.getChildAt(0).findViewById<View>(R.id.replies_button).performClick())
                    activity.supportFragmentManager.executePendingTransactions()
                    val up = PopupMenu(activity, list).menu.add(0, android.R.id.home, 0, "Up")
                    assertTrue(activity.onOptionsItemSelected(up))
                }
                awaitComments(scenario, 1)
                onActivity(scenario) { activity ->
                    assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheetState(activity))
                    assertSame(detail, activity.supportFragmentManager.findFragmentById(R.id.fragment_player_holder))
                    val up = PopupMenu(activity, activity.findViewById(R.id.fragment_holder))
                        .menu.add(0, android.R.id.home, 0, "Up")
                    assertTrue(activity.onOptionsItemSelected(up))
                    assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is PlaylistFragment)
                }
                // Its first request was cancelled above; returning must restart and render it.
                awaitPlaylist(scenario)
                onActivity(scenario) { activity ->
                    assertEquals("Attached playlist", activity.supportActionBar!!.title)
                    activity.supportFragmentManager.popBackStackImmediate()
                    assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is MainFragment)
                    val holder = PostInfoItemHolder(InfoItemBuilder(activity), FrameLayout(activity))
                    holder.updateFromItem(post(), null)
                    assertTrue(holder.itemView.findViewById<View>(R.id.itemAttachmentCard).performClick())
                    activity.supportFragmentManager.executePendingTransactions()
                    assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is PlaylistFragment)
                    assertEquals("Attached playlist", activity.supportActionBar!!.title)
                }
                awaitPlaylist(scenario)
            }
        } finally {
            InfoCache.getInstance().removeInfo(0, POST_URL, InfoCache.Type.COMMENTS)
            InfoCache.getInstance().removeInfo(0, PLAYLIST_URL, InfoCache.Type.PLAYLIST)
            preferences.edit().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
            changelog.edit().apply {
                previousRelease?.let { putInt("last_seen_release", it) } ?: remove("last_seen_release")
            }.commit()
        }
    }

    private fun post() = PostInfoItem(0, SOURCE_URL, "Channel post").apply {
        postId = "UgkxPostUi1491"
        uploaderName = "Post author"
        uploaderUrl = "https://www.youtube.com/@post-author"
        content = (1..8).joinToString("\n") { "Line $it of the original post" }
        commentCount = PostInfoItem.UNKNOWN_COUNT
        attachment = PostInfoItem.Attachment(
            PostInfoItem.AttachmentType.PLAYLIST,
            PLAYLIST_URL,
            "Attached playlist",
            "",
            emptyList()
        )
    }

    private fun cacheComments() {
        val first = CommentsInfoItem(0, POST_URL, "Parent").apply {
            commentId = "parent"
            commentText = "A needle in the post discussion"
            uploaderName = "Comment author"
            uploaderUrl = ""
            uploaderAvatarUrl = ""
            textualUploadDate = "today"
            replyCount = 1
            // Navigation is independent of a live backend; an exhausted page avoids a network call.
            replies = Page("")
        }
        val second = CommentsInfoItem(0, POST_URL, "Second").apply {
            commentId = "second"
            commentText = "Another post comment"
            uploaderName = "Second author"
            uploaderUrl = ""
            uploaderAvatarUrl = ""
            textualUploadDate = "today"
        }
        val constructor = CommentsInfo::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            ListLinkHandler::class.java,
            String::class.java
        ).apply { isAccessible = true }
        val info = constructor.newInstance(0, ServiceList.YouTube.commentsLHFactory.fromUrl(POST_URL), "Post comments").apply {
            relatedItems = listOf(first, second)
        }
        InfoCache.getInstance().putInfo(0, POST_URL, info, InfoCache.Type.COMMENTS)
    }

    private fun cachePlaylist() {
        val constructor = PlaylistInfo::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            ListLinkHandler::class.java,
            String::class.java
        ).apply { isAccessible = true }
        val info = constructor.newInstance(0, ServiceList.YouTube.playlistLHFactory.fromUrl(PLAYLIST_URL), "Attached playlist").apply {
            relatedItems = emptyList()
            playlistType = PlaylistInfo.PlaylistType.NORMAL
            thumbnailUrl = ""
            bannerUrl = ""
            uploaderName = ""
            uploaderUrl = ""
            uploaderAvatarUrl = ""
            subChannelName = ""
            subChannelUrl = ""
            subChannelAvatarUrl = ""
        }
        InfoCache.getInstance().putInfo(0, PLAYLIST_URL, info, InfoCache.Type.PLAYLIST)
    }

    private fun awaitComments(scenario: ActivityScenario<MainActivity>, count: Int) = await(scenario) { activity ->
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? PostCommentsFragment
        val list = fragment?.view?.findViewById<RecyclerView>(R.id.items_list)
        val adapter = list?.adapter as? InfoListAdapter
        list != null && adapter?.itemsList?.size == count && list.childCount > 0 && list.isShown
    }

    private fun awaitPlaylist(scenario: ActivityScenario<MainActivity>) = await(scenario) { activity ->
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? PlaylistFragment
        val root = fragment?.view
        fragment?.isResumed == true && root != null &&
            root.findViewById<View>(R.id.playlist_sort_button)?.isShown == true &&
            root.findViewById<View>(R.id.loading_progress_bar)?.isShown == false
    }

    private fun sheetState(activity: MainActivity): Int = BottomSheetBehavior.from(activity.findViewById<FrameLayout>(R.id.fragment_player_holder)).state

    private fun <A : Activity> await(scenario: ActivityScenario<A>, condition: (A) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            onActivity(scenario) { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(25)
        }
        throw AssertionError("Post comments UI did not reach the expected state")
    }

    private fun <A : Activity> onActivity(scenario: ActivityScenario<A>, action: (A) -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity { action(it) }
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        assertTrue("Activity callback did not complete", completed.await(10, TimeUnit.SECONDS))
        failure?.let { throw it }
    }

    companion object {
        private const val POST_URL = "https://www.youtube.com/post/UgkxPostUi1491"
        private const val SOURCE_URL = "https://m.youtube.com/post/UgkxPostUi1491?lc=existing-comment"
        private const val PLAYLIST_URL = "https://www.youtube.com/playlist?list=PLPostCommentsAttachment1491"
    }
}
