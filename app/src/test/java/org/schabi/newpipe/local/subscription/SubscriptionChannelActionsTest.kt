package org.schabi.newpipe.local.subscription

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.feed.FeedAllExclusions
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SubscriptionChannelActionsTest {
    private lateinit var activityController: ActivityController<FragmentActivity>
    private lateinit var activity: FragmentActivity
    private lateinit var actions: SubscriptionChannelActions
    private lateinit var manager: SubscriptionManager
    private val channel = ChannelInfoItem(0, "https://example.org/channel", "Example")

    class HostFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = View(inflater.context)
    }

    @Before
    fun setup() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        activityController = Robolectric.buildActivity(FragmentActivity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.LightTheme)
        activityController.setup()
        val fragment = HostFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        manager = mock(SubscriptionManager::class.java)
        `when`(manager.getSubscriptionFlowable(anyInt(), anyString())).thenAnswer {
            Flowable.just(listOf(SubscriptionEntity(uid = 1, serviceId = it.getArgument(0), url = it.getArgument(1))))
        }
        actions = SubscriptionChannelActions(fragment, manager)
    }

    @After
    fun teardown() {
        actions.close()
        activityController.pause().stop().destroy()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun singleProfileMenuRemovesTheSelectedChannelFromTheWholeLibrary() {
        `when`(manager.deleteLibrarySubscription(channel.serviceId, channel.url)).thenReturn(Completable.complete())
        actions.show(channel)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = latestDialog()
        assertEquals(
            listOf(
                R.string.share,
                R.string.open_in_browser,
                R.string.subscription_groups_manage,
                R.string.feed_mute,
                R.string.feed_exclude_from_all,
                R.string.unsubscribe
            ).map(activity::getString),
            labels(dialog)
        )
        val unsubscribePosition = labels(dialog).indexOf(activity.getString(R.string.unsubscribe))
        dialog.listView.performItemClick(null, unsubscribePosition, unsubscribePosition.toLong())
        verify(manager).deleteLibrarySubscription(channel.serviceId, channel.url)
        verify(manager, never()).deleteSubscription(channel.serviceId, channel.url)
    }

    @Test
    fun excludeFromAllCanBeReversedWithoutUnsubscribing() {
        val profileId = ProfileManager.getActiveProfileId(activity)
        val testChannel = ChannelInfoItem(0, "https://example.org/channel-exclusion", "Exclusion")
        if (FeedAllExclusions.isExcluded(activity, profileId, testChannel.serviceId, testChannel.url)) {
            FeedAllExclusions.toggle(activity, profileId, testChannel.serviceId, testChannel.url)
        }

        actions.show(testChannel)
        shadowOf(Looper.getMainLooper()).idle()
        val excludeDialog = latestDialog()
        val excludePosition = labels(excludeDialog).indexOf(activity.getString(R.string.feed_exclude_from_all))
        assertTrue(excludePosition >= 0)
        excludeDialog.listView.performItemClick(null, excludePosition, excludePosition.toLong())
        assertTrue(FeedAllExclusions.isExcluded(activity, profileId, testChannel.serviceId, testChannel.url))

        actions.show(testChannel)
        shadowOf(Looper.getMainLooper()).idle()
        val includeDialog = latestDialog()
        val includePosition = labels(includeDialog).indexOf(activity.getString(R.string.feed_include_in_all))
        assertTrue(includePosition >= 0)
        includeDialog.listView.performItemClick(null, includePosition, includePosition.toLong())
        assertFalse(FeedAllExclusions.isExcluded(activity, profileId, testChannel.serviceId, testChannel.url))
        verify(manager, never()).deleteLibrarySubscription(testChannel.serviceId, testChannel.url)
    }

    @Test
    fun profilePickerExcludesActiveProfileAndClosesWithTheView() {
        val other = requireNotNull(ProfileManager.createProfile(activity, "Other"))
        ProfileManager.setActiveProfile(activity, ProfileManager.DEFAULT_PROFILE_ID)
        actions.show(channel)
        shadowOf(Looper.getMainLooper()).idle()
        val menu = latestDialog()
        val index = labels(menu).indexOf(activity.getString(R.string.channel_subscribe_to_profile))
        menu.listView.performItemClick(null, index, index.toLong())
        shadowOf(Looper.getMainLooper()).idle()
        val picker = latestDialog()
        assertEquals(listOf(ProfileManager.getDisplayName(activity, other)), labels(picker))
        actions.close()
        assertFalse(picker.isShowing)
    }

    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun labels(dialog: AlertDialog): List<String> = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
}
