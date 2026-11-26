/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.room.Room
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.profiles.ProfileManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubscriptionGroupMembershipActionsTest {
    private lateinit var host: ActivityController<FragmentActivity>
    private lateinit var fragment: HostFragment
    private lateinit var database: AppDatabase
    private lateinit var actions: SubscriptionGroupMembershipActions
    private val profile = ProfileManager.DEFAULT_PROFILE_ID
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private val groups get() = database.feedGroupDAO()
    private val context get() = host.get()

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
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        host.setup()
        fragment = HostFragment()
        context.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
        actions = SubscriptionGroupMembershipActions(fragment, profile)
    }

    @After
    fun tearDown() {
        actions.close()
        host.pause().stop().destroy()
        databaseField.set(null, previousDatabase)
        database.close()
        RxJavaPlugins.reset()
        RxAndroidPlugins.reset()
    }

    @Test
    fun singlePickerChecksExistingGroupsAndCancelDoesNotWrite() {
        val channel = channel("Single")
        val first = group("First")
        group("Second")
        group("Other profile", "other")
        groups.setGroupsForSubscriptionForProfile(profile, channel.uid, listOf(first))
        actions.showSingle(channel.toChannelInfoItem())
        val picker = picker()
        assertEquals(listOf("First", "Second"), labels(picker))
        assertTrue(picker.listView.isItemChecked(0))
        assertFalse(picker.listView.isItemChecked(1))
        toggle(picker, 0)
        toggle(picker, 1)
        picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(setOf(first), memberships(channel))
    }

    @Test
    fun singleSaveChangesOnlyToggledGroupsAndCanRemoveTheLastMembership() {
        val channel = channel("Single")
        val first = group("First")
        val second = group("Second")
        groups.setGroupsForSubscriptionForProfile(profile, channel.uid, listOf(first))
        actions.showSingle(channel.toChannelInfoItem())
        val picker = picker()
        val concurrent = group("Added while editing")
        groups.changeGroupsForSubscriptionsForProfile(profile, listOf(channel.uid), listOf(concurrent), emptyList())
        toggle(picker, 0)
        toggle(picker, 1)
        save(picker)
        assertEquals(setOf(second, concurrent), memberships(channel))

        actions.showSingle(channel.toChannelInfoItem())
        val emptyPicker = picker()
        toggle(emptyPicker, 1)
        toggle(emptyPicker, 2)
        assertTrue(emptyPicker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        save(emptyPicker)
        assertTrue(memberships(channel).isEmpty())
        assertEquals(channel, database.subscriptionDAO().getSubscriptionForProfile(profile, channel.uid))
    }

    @Test
    fun bulkAddAndRemoveLeaveOtherMembershipsAndUnselectedChannelsAlone() {
        val first = channel("First")
        val second = channel("Second")
        val untouched = channel("Untouched")
        val news = group("News")
        val shared = group("Shared")
        groups.setGroupsForSubscriptionForProfile(profile, first.uid, listOf(news))
        groups.setGroupsForSubscriptionForProfile(profile, untouched.uid, listOf(news, shared))
        actions.showBulk(listOf(first, second), add = true)
        val add = picker()
        assertFalse(add.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        toggle(add, 1)
        save(add)
        assertEquals(setOf(news, shared), memberships(first))
        assertEquals(setOf(shared), memberships(second))

        actions.showBulk(listOf(first, second), add = false)
        val remove = picker()
        assertFalse(remove.listView.isItemChecked(1))
        toggle(remove, 1)
        save(remove)
        assertEquals(setOf(news), memberships(first))
        assertTrue(memberships(second).isEmpty())
        assertEquals(setOf(news, shared), memberships(untouched))
    }

    @Test
    fun editorSelectionHiddenBySearchIsIncludedWithoutChangingItsLayout() {
        val channels = listOf(channel("Cars"), channel("Cooking"), channel("Music"))
        val entries = channels.map { SubscriptionLayout.Entry(SubscriptionLayout.key(it)) }
        val store = SubscriptionLayout(context, profile)
        store.save(entries)
        val target = group("Selected")
        val binding = FragmentSubscriptionBinding.bind(fragment.requireView())
        val editor = SubscriptionSortController(context, profile, binding, null)
        try {
            editor.update(channels, entries, FeedScope(FeedScope.ALL_SERVICES, 0))
            editor.setQuery("Cars")
            editor.controls.subscriptionSortSelect.performClick()
            editor.setQuery("Cooking")
            editor.controls.subscriptionSortSelect.performClick()
            assertEquals(channels.take(2), editor.selectedChannels())
            actions.showBulk(editor.selectedChannels(), add = true)
            val picker = picker()
            toggle(picker, 0)
            save(picker)
            assertEquals(setOf(target), memberships(channels[0]))
            assertEquals(setOf(target), memberships(channels[1]))
            assertTrue(memberships(channels[2]).isEmpty())
            assertEquals(entries, store.read())
            assertEquals(channels.take(2), editor.selectedChannels())
        } finally {
            editor.close()
        }
    }

    @Test
    fun profileSwitchBeforeSaveLeavesTheOriginalSubscriptionsUnchanged() {
        val channel = channel("Local")
        group("Local")
        actions.showSingle(channel.toChannelInfoItem())
        val picker = picker()
        toggle(picker, 0)
        val other = requireNotNull(ProfileManager.createProfile(context, "Other"))
        assertTrue(ProfileManager.setActiveProfile(context, other.id))
        save(picker)
        assertTrue(memberships(channel).isEmpty())
    }

    @Test
    fun closingCancelsPendingLoadsAndDismissesAnOpenPicker() {
        val channel = channel("Local")
        group("Local")
        actions.showSingle(channel.toChannelInfoItem())
        val original = picker()
        actions.close()
        assertFalse(original.isShowing)

        val scheduler = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { scheduler }
        actions.showSingle(channel.toChannelInfoItem())
        actions.close()
        scheduler.triggerActions()
        assertFalse(original.isShowing)
        assertTrue(memberships(channel).isEmpty())
        RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
        actions.showSingle(channel.toChannelInfoItem())
        assertNotSame(original, picker())
    }

    @Test
    fun noGroupsExplainsHowToContinueAndDoesNotSubscribeOrCreateAnything() {
        val channel = channel("Local")
        actions.showSingle(channel.toChannelInfoItem())
        val picker = picker()
        assertEquals(
            context.getString(R.string.subscription_groups_create_first),
            picker.findViewById<android.widget.TextView>(android.R.id.message)?.text.toString()
        )
        assertTrue(groups.getAllDirectForProfile(profile).isEmpty())
        assertEquals(listOf(channel), database.subscriptionDAO().getAllDirectForProfile(profile))
    }

    private fun group(name: String, profileId: String = profile): Long = groups.insert(
        FeedGroupEntity(0, name, FeedGroupIcon.ALL, profileId = profileId)
    )

    private fun channel(name: String): SubscriptionEntity = SubscriptionEntity(
        serviceId = 0,
        url = "https://example.com/$name",
        name = name,
        profileId = profile,
        youtubeModeMask = SubscriptionEntity.YOUTUBE_MODE_MUSIC
    ).apply { uid = database.subscriptionDAO().insert(this) }

    private fun memberships(channel: SubscriptionEntity): Set<Long> = groups.getGroupIdsForSubscriptionDirectForProfile(profile, channel.uid).toSet()

    private fun picker(): AlertDialog {
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    private fun labels(picker: AlertDialog): List<String> = (0 until picker.listView.adapter.count).map { picker.listView.adapter.getItem(it).toString() }

    private fun toggle(picker: AlertDialog, position: Int) {
        picker.listView.performItemClick(null, position, position.toLong())
    }

    private fun save(picker: AlertDialog) {
        picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
