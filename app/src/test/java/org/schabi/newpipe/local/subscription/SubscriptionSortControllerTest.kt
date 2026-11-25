package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.databinding.ItemSubscriptionSortBinding
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.subscription.SubscriptionLayout.Entry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SubscriptionSortControllerTest {
    private lateinit var activityController: ActivityController<Activity>
    private lateinit var activity: Activity
    private lateinit var binding: FragmentSubscriptionBinding
    private lateinit var controller: SubscriptionSortController
    private val profile = "bulk-sort-test"
    private val channels = listOf(
        SubscriptionEntity(uid = 1, serviceId = 0, url = "https://example.org/a", name = "Cars"),
        SubscriptionEntity(uid = 2, serviceId = 1, url = "https://example.org/b", name = "Music"),
        SubscriptionEntity(uid = 3, serviceId = 0, url = "https://example.org/c", name = "Cooking")
    )
    private val scope = FeedScope(FeedScope.ALL_SERVICES, 0)
    private val keys = channels.map(SubscriptionLayout::key)
    private val separator = Entry("separator:target", "Favorites")

    @Before
    fun setup() {
        activityController = Robolectric.buildActivity(Activity::class.java)
        activity = activityController.get()
        activity.setTheme(R.style.LightTheme)
        activityController.setup()
        binding = FragmentSubscriptionBinding.inflate(LayoutInflater.from(activity))
        activity.setContentView(binding.root)
        binding.subscriptionSortPanel.visibility = View.VISIBLE
        SubscriptionLayout(activity, profile).save(keys.map(::Entry) + separator)
        controller = SubscriptionSortController(activity, profile, binding, null)
        controller.update(channels, keys.map(::Entry) + separator, scope)
    }

    @After
    fun teardown() {
        controller.close()
        activityController.pause().stop().destroy()
    }

    @Test
    fun incomingScrollAnchorWaitsForChannelsAndPreservesItsPixelOffset() {
        controller.close()
        controller = SubscriptionSortController(activity, profile, binding, null)
        val many = (0 until 50).map { index ->
            SubscriptionEntity(uid = index.toLong(), serviceId = 0, url = "https://example.org/$index", name = "Channel $index")
        }
        val anchor = SubscriptionScrollAnchor(SubscriptionLayout.key(many[25]), -17)
        controller.scrollTo(anchor)
        controller.update(many, many.map { Entry(SubscriptionLayout.key(it)) }, scope)
        row(25)
        assertEquals(anchor, controller.scrollAnchor())
        assertEquals(null, binding.subscriptionSortList.findViewHolderForAdapterPosition(0))
        assertSame(binding.subscriptionSortList, FragmentSubscriptionBinding.bind(binding.root).subscriptionSortList)
        controller.update(many, many.reversed().map { Entry(SubscriptionLayout.key(it)) }, scope)
        controller.scrollTo(anchor)
        row(24)
        assertEquals(anchor, controller.scrollAnchor())
    }

    @Test
    fun controlsAreExcludedFromTheChannelAnchorAndReattachAfterScrolling() {
        val many = (0 until 30).map { index ->
            SubscriptionEntity(uid = index.toLong(), serviceId = 0, url = "https://example.org/recycle/$index", name = "Channel $index")
        }
        controller.update(many, many.map { Entry(SubscriptionLayout.key(it)) }, scope)
        val first = row(0)
        val list = binding.subscriptionSortList
        assertTrue("The scrolling controls precede the first channel", first.root.top > 0)
        assertEquals(SubscriptionScrollAnchor(SubscriptionLayout.key(many[0]), first.root.top - list.paddingTop), controller.scrollAnchor())
        controller.scrollTo(SubscriptionScrollAnchor(SubscriptionLayout.key(many[20]), 0))
        row(20)
        assertEquals(null, list.findViewHolderForAdapterPosition(0))
        controller.scrollTo(null)
        row(0)
        val header = list.findViewHolderForAdapterPosition(0)!!
        assertSame("Recycling must preserve the live controls and their listeners", header.itemView, controller.controls.root.parent)
        controller.controls.subscriptionSortSelect.performClick()
        assertEquals(many.map { it.uid }, controller.selectedChannels().map { it.uid })
    }

    @Test
    fun savedScrollWaitsForChannelsAndANewerBrowseAnchorTakesPrecedence() {
        val many = (0 until 50).map { index ->
            SubscriptionEntity(uid = index.toLong(), serviceId = 0, url = "https://example.org/restore/$index", name = "Channel $index")
        }
        val entries = many.map { Entry(SubscriptionLayout.key(it)) }
        val restoredAnchor = SubscriptionScrollAnchor(SubscriptionLayout.key(many[25]), -17)
        controller.update(many, entries, scope)
        controller.scrollTo(restoredAnchor)
        row(25)
        val saved = Bundle()
        controller.saveState(saved)

        for (newerAnchor in listOf(null, SubscriptionScrollAnchor(SubscriptionLayout.key(many[30]), -9))) {
            controller.close()
            controller = SubscriptionSortController(activity, profile, binding, saved)
            controller.restoreScroll(saved)
            if (newerAnchor != null) controller.scrollTo(newerAnchor)
            layoutList()
            assertEquals("Only the scrolling controls are available before the database emits", 1, binding.subscriptionSortList.adapter!!.itemCount)
            assertEquals(RecyclerView.Adapter.StateRestorationPolicy.PREVENT, binding.subscriptionSortList.adapter!!.stateRestorationPolicy)
            controller.update(many, entries, scope)
            row(if (newerAnchor == null) 25 else 30)
            assertEquals(newerAnchor ?: restoredAnchor, controller.scrollAnchor())
            assertEquals(RecyclerView.Adapter.StateRestorationPolicy.ALLOW, binding.subscriptionSortList.adapter!!.stateRestorationPolicy)
        }
    }

    @Test
    fun scrollingControlsNeverBecomeAReorderSourceOrTarget() {
        row(0)
        val list = binding.subscriptionSortList
        val header = list.findViewHolderForAdapterPosition(0)!!
        val first = list.findViewHolderForAdapterPosition(1)!!
        val second = list.findViewHolderForAdapterPosition(2)!!
        val callback = controller.dragCallback
        assertEquals(0, callback.getMovementFlags(list, header))
        assertFalse(callback.canDropOver(list, first, header))
        assertFalse(callback.canDropOver(list, header, first))
        assertFalse(callback.onMove(list, first, header))
        assertFalse(callback.onMove(list, header, first))
        assertEquals(keys.map(::Entry) + separator, SubscriptionLayout(activity, profile).read())

        assertTrue(callback.canDropOver(list, first, second))
        callback.onSelectedChanged(first, ItemTouchHelper.ACTION_STATE_DRAG)
        assertTrue(callback.onMove(list, first, second))
        callback.clearView(list, first)
        assertEquals(listOf(keys[1], keys[0], keys[2], separator.key), SubscriptionLayout(activity, profile).read().map { it.key })
    }

    @Test
    fun selectionSurvivesSearchAndBulkMovePersistsHiddenChannels() {
        controller.setQuery("Cars")
        row(0).root.performLongClick()
        controller.setQuery("Cooking")
        row(0).sortSelected.performClick()
        assertTrue(controller.controls.subscriptionSortMove.isEnabled)
        controller.controls.subscriptionSortMove.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.listView.performItemClick(null, 1, 1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(keys[1], separator.key, keys[0], keys[2]), SubscriptionLayout(activity, profile).read().map { it.key })
        assertTrue(controller.controls.subscriptionSortCount.text.toString().startsWith("0"))
    }

    @Test
    fun scopeChangesAndRecreationRetainSelectionOnlyWithinTheSameProfile() {
        row(0).sortSelected.performClick()
        controller.update(channels, keys.map(::Entry) + separator, FeedScope(1, 0))
        assertEquals("Music", row(0).sortLabel.text.toString())
        val saved = Bundle()
        controller.saveState(saved)
        controller.close()
        controller = SubscriptionSortController(activity, profile, binding, saved)
        controller.update(channels, keys.map(::Entry) + separator, scope)
        assertTrue(row(0).sortSelected.isChecked)
        controller.close()
        controller = SubscriptionSortController(activity, "other-profile", binding, saved)
        controller.update(channels, keys.map(::Entry) + separator, scope)
        assertEquals(false, row(0).sortSelected.isChecked)
    }

    @Test
    fun newSeparatorMovesTheSelectionOnlyAfterItsNameIsConfirmed() {
        row(0).sortSelected.performClick()
        controller.controls.subscriptionSortMove.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).listView.performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        var dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(keys.map(::Entry) + separator, SubscriptionLayout(activity, profile).read())

        controller.controls.subscriptionSortMove.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        (ShadowDialog.getLatestDialog() as AlertDialog).listView.performItemClick(null, 0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        dialog = ShadowDialog.getLatestDialog() as AlertDialog
        findInput(dialog.window!!.decorView)!!.setText("Science")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val saved = SubscriptionLayout(activity, profile).read()
        assertEquals("Science", saved[saved.lastIndex - 1].separator)
        assertEquals(keys[0], saved.last().key)
        assertEquals(keys.toSet(), saved.filter { it.separator == null }.map { it.key }.toSet())
    }

    private fun findInput(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findInput(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    @Test
    fun disablingSortingRestoresTheNormalListAfterItWasFadedOut() {
        val fragment = mock(SubscriptionFragment::class.java, CALLS_REAL_METHODS)
        SubscriptionFragment::class.java.getDeclaredField("_binding").apply {
            isAccessible = true
            set(fragment, binding)
        }
        val enabled = SubscriptionFragment::class.java.getDeclaredField("sortingEnabled").apply { isAccessible = true }
        val update = SubscriptionFragment::class.java.getDeclaredMethod("updateSortingVisibility").apply { isAccessible = true }
        enabled.setBoolean(fragment, true)
        update.invoke(fragment)
        assertEquals(View.GONE, binding.itemsList.visibility)
        binding.itemsList.alpha = 0f
        enabled.setBoolean(fragment, false)
        update.invoke(fragment)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, binding.itemsList.visibility)
        assertEquals(1f, binding.itemsList.alpha, 0f)
    }

    private fun row(position: Int): ItemSubscriptionSortBinding {
        layoutList()
        val holder: RecyclerView.ViewHolder = binding.subscriptionSortList.findViewHolderForAdapterPosition(position + 1)!!
        return ItemSubscriptionSortBinding.bind(holder.itemView)
    }

    private fun layoutList() {
        shadowOf(Looper.getMainLooper()).idle()
        binding.root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        binding.root.layout(0, 0, 600, 1000)
    }
}
