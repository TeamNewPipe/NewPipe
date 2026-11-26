package org.schabi.newpipe.local.subscription

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.xwray.groupie.GroupieViewHolder
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.databinding.ItemSubscriptionSortBinding
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.OnClickGesture

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubscriptionFeedMuteActionsTest {
    private lateinit var host: ActivityController<FragmentActivity>
    private lateinit var fragment: HostFragment
    private lateinit var database: AppDatabase
    private lateinit var single: SubscriptionChannelActions
    private lateinit var bulk: SubscriptionFeedMuteActions
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val context get() = host.get()
    private val dao get() = database.subscriptionDAO()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    class HostFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FragmentSubscriptionBinding.inflate(inflater, container, false).root
    }

    @Before
    fun setUp() {
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        host = Robolectric.buildActivity(FragmentActivity::class.java)
        context.setTheme(R.style.LightTheme)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        host.setup()
        fragment = HostFragment()
        context.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        createActions()
    }

    @After
    fun tearDown() {
        single.close()
        bulk.close()
        host.pause().stop().destroy()
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun longPressMenuReadsCurrentStateAndCanMuteThenUnmuteWithoutChangingAlertPreferences() {
        val channel = channel("Single")
        single.show(channel.toChannelInfoItem())
        choose(R.string.feed_mute)
        assertEquals(channel.copy(feedMuted = true), stored(channel))
        single.show(channel.toChannelInfoItem())
        choose(R.string.feed_unmute)
        assertEquals(channel, stored(channel))
    }

    @Test
    fun explicitBulkActionsUnifyMixedStatesIncludingCheckedChannelsHiddenBySearchAndRestoreSelection() {
        val channels = listOf(channel("Cars"), channel("Cooking", muted = true), channel("Music"))
        val entries = channels.map { SubscriptionLayout.Entry(SubscriptionLayout.key(it)) } + SubscriptionLayout.Entry("separator:end", "End")
        val layout = SubscriptionLayout(context, profile)
        layout.save(entries)
        val binding = FragmentSubscriptionBinding.bind(fragment.requireView())
        binding.subscriptionSortPanel.visibility = View.VISIBLE
        var editor = SubscriptionSortController(context, profile, binding, null)
        try {
            editor.update(channels, entries, FeedScope(FeedScope.ALL_SERVICES, 0))
            editor.setQuery("Cars")
            editor.controls.subscriptionSortSelect.performClick()
            editor.setQuery("Cooking")
            editor.controls.subscriptionSortSelect.performClick()
            assertEquals(channels.take(2), editor.selectedChannels())
            bulk.set(editor.selectedChannels(), muted = true)
            assertTrue(stored(channels[0]).feedMuted)
            assertTrue(stored(channels[1]).feedMuted)
            assertFalse(stored(channels[2]).feedMuted)
            editor.update(channels.map(::stored), entries, FeedScope(FeedScope.ALL_SERVICES, 0))
            assertEquals(channels.take(2).map { it.uid }, editor.selectedChannels().map { it.uid })
            val saved = Bundle()
            editor.saveState(saved)
            editor.close()

            editor = SubscriptionSortController(context, profile, binding, saved)
            editor.update(channels.map(::stored), entries, FeedScope(FeedScope.ALL_SERVICES, 0))
            assertEquals(channels.take(2).map { it.uid }, editor.selectedChannels().map { it.uid })
            bulk.set(editor.selectedChannels(), muted = false)
            assertTrue(channels.map(::stored).none { it.feedMuted })
            assertEquals(entries, layout.read())

            editor.update(channels.map(::stored), entries, FeedScope(FeedScope.ALL_SERVICES, 0))
            binding.root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
            binding.root.layout(0, 0, 600, 1000)
            val label = ItemSubscriptionSortBinding.bind(binding.subscriptionSortList.findViewHolderForAdapterPosition(1)!!.itemView).sortLabel
            assertEquals(context.getString(R.string.feed_unmuted_state), ViewCompat.getStateDescription(label))
            val separator = ItemSubscriptionSortBinding.bind(binding.subscriptionSortList.findViewHolderForAdapterPosition(4)!!.itemView).sortLabel
            assertNull(ViewCompat.getStateDescription(separator))
            assertNull(separator.compoundDrawablesRelative[2])
        } finally {
            editor.close()
        }
    }

    @Test
    fun channelBadgesResetAcrossBindingAndRecyclingAndMutedChannelsRemainClickable() {
        val channel = channel("A long channel name with accessible feed state")
        for (version in ChannelItem.ItemVersion.entries) {
            for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                var selected: ChannelInfoItem? = null
                val item = ChannelItem(channel.toChannelInfoItem(), channel.uid, version, feedMuted = true).apply {
                    gesturesListener = object : OnClickGesture<ChannelInfoItem> {
                        override fun selected(selectedItem: ChannelInfoItem) {
                            selected = selectedItem
                        }
                    }
                }
                val root = LayoutInflater.from(context).inflate(item.getLayout(), null)
                root.layoutDirection = direction
                val holder = GroupieViewHolder(root)
                val label = root.findViewById<TextView>(R.id.itemTitleView)
                item.bind(holder, 0)
                label.textSize = 32f
                root.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertNotNull(label.compoundDrawablesRelative[2])
                assertTrue(label.width >= label.compoundPaddingLeft + label.compoundPaddingRight)
                assertEquals(context.getString(R.string.feed_muted_state), ViewCompat.getStateDescription(label))
                assertTrue(root.performClick())
                assertEquals(channel.url, selected?.url)

                ChannelItem(channel.toChannelInfoItem(), channel.uid, version, feedMuted = false).bind(holder, 0)
                assertNotNull(label.compoundDrawablesRelative[2])
                assertEquals(context.getString(R.string.feed_unmuted_state), ViewCompat.getStateDescription(label))
                item.unbind(holder)
                assertNull(ViewCompat.getStateDescription(label))
                assertNull(label.compoundDrawablesRelative[2])
                ChannelItem(channel.toChannelInfoItem(), itemVersion = version).bind(holder, 0)
                assertNull(label.compoundDrawablesRelative[2])
            }
        }
    }

    @Test
    fun switchingProfileOrDestroyingTheViewCancelsPendingActionsAndAllowsFreshLookup() {
        val channel = channel("Lifecycle")
        single.show(channel.toChannelInfoItem())
        val menu = latestMenu()
        val other = requireNotNull(ProfileManager.createProfile(context, "Other"))
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        choose(R.string.feed_mute)
        assertFalse(stored(channel).feedMuted)
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        single.close()
        assertFalse(menu.isShowing)

        val scheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        single.show(channel.toChannelInfoItem())
        bulk.set(listOf(channel), muted = true)
        single.close()
        bulk.close()
        context.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        scheduler.triggerActions()
        assertFalse(stored(channel).feedMuted)
        assertFalse(menu.isShowing)
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        fragment = HostFragment()
        context.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        createActions()
        single.show(channel.toChannelInfoItem())
        choose(R.string.feed_mute)
        assertTrue(stored(channel).feedMuted)
    }

    private fun createActions() {
        val manager = SubscriptionManager(context, profile)
        single = SubscriptionChannelActions(fragment, manager)
        bulk = SubscriptionFeedMuteActions(fragment, manager, profile)
    }

    private fun channel(name: String, muted: Boolean = false) = SubscriptionEntity(
        serviceId = 0,
        url = "https://example.com/$name",
        name = name,
        notificationMode = NotificationMode.KEYWORDS_ONLY,
        notificationKeywords = "science",
        feedMuted = muted
    ).apply { uid = dao.insert(this) }

    private fun stored(channel: SubscriptionEntity) = requireNotNull(dao.getSubscriptionForProfile(profile, channel.uid))

    private fun latestMenu(): AlertDialog {
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun choose(label: Int) {
        val menu = latestMenu()
        val position = (0 until menu.listView.adapter.count).first { menu.listView.adapter.getItem(it) == context.getString(label) }
        menu.listView.performItemClick(null, position, position.toLong())
    }
}
