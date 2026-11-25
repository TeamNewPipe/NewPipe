/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.fragments.detail

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DetailPlayerViewListenersTest {
    private lateinit var activityController: ActivityController<FragmentActivity>
    private lateinit var activity: FragmentActivity
    private lateinit var sheet: FrameLayout
    private lateinit var behavior: TrackingSheetBehavior
    private var containerId = 0
    private var navigationId = 0

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activity = activityController.get()
        containerId = View.generateViewId()
        activity.setContentView(FrameLayout(activity).apply { id = containerId })
        sheet = FrameLayout(activity)
        behavior = TrackingSheetBehavior()
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
    }

    @Test
    fun recreatingAndReplacingTheViewKeepsOnlyItsCurrentSheetAndNavigationCallbacks() {
        val manager = activity.supportFragmentManager
        val owner = newOwner()
        manager.beginTransaction().add(containerId, owner).commitNow()

        repeat(3) {
            assertEquals(1, behavior.registered.size)
            val events = owner.generations.last()
            behavior.dispatchState(sheet, BottomSheetBehavior.STATE_COLLAPSED)
            behavior.dispatchSlide(sheet, 0.5f)
            navigateAndReturn()
            assertEquals(listOf(1, 1, 2), events.snapshot())

            val previous = owner.generations.map(Events::snapshot)
            val lateCallback = behavior.registered.single()
            val oldRegistration = requireNotNull(owner.registration)
            // Separate transactions really destroy/recreate the view while retaining its
            // fragment and the activity-owned sheet/FragmentManager.
            manager.beginTransaction().detach(owner).commitNow()
            assertTrue(behavior.registered.isEmpty())
            lateCallback.onStateChanged(sheet, BottomSheetBehavior.STATE_EXPANDED)
            lateCallback.onSlide(sheet, 0.75f)
            navigateAndReturn()
            assertEquals(previous, owner.generations.map(Events::snapshot))

            oldRegistration.close()
            manager.beginTransaction().attach(owner).commitNow()
            assertEquals(1, behavior.registered.size)
            oldRegistration.close()
            assertEquals("Closing an old registration cannot remove the new one", 1, behavior.registered.size)
        }

        manager.beginTransaction().remove(owner).commitNow()
        val oldEvents = owner.generations.map(Events::snapshot)
        assertTrue(behavior.registered.isEmpty())
        val replacement = newOwner()
        manager.beginTransaction().add(containerId, replacement).commitNow()
        assertEquals(1, behavior.registered.size)
        behavior.dispatchState(sheet, BottomSheetBehavior.STATE_EXPANDED)
        behavior.dispatchSlide(sheet, 1f)
        navigateAndReturn()
        assertEquals(listOf(1, 1, 2), replacement.generations.single().snapshot())
        assertEquals(oldEvents, owner.generations.map(Events::snapshot))

        // Explicit teardown and the later lifecycle event are both safe.
        requireNotNull(replacement.registration).close()
        requireNotNull(replacement.registration).close()
        navigateAndReturn()
        assertEquals(listOf(1, 1, 2), replacement.generations.single().snapshot())
        manager.beginTransaction().remove(replacement).commitNow()
        assertTrue(behavior.registered.isEmpty())
    }

    @Test
    fun anAlreadyDestroyedOwnerCannotRegisterCallbacks() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        val events = Events()
        bind(owner, events).use {
            assertTrue(behavior.registered.isEmpty())
            behavior.dispatchState(sheet, BottomSheetBehavior.STATE_EXPANDED)
            navigateAndReturn()
            assertEquals(listOf(0, 0, 0), events.snapshot())
        }
    }

    private fun newOwner() = ViewOwnerFragment().apply { bind = this@DetailPlayerViewListenersTest::bind }

    private fun bind(owner: LifecycleOwner, events: Events) = DetailPlayerViewListeners(
        owner,
        behavior,
        activity.supportFragmentManager,
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                events.states++
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                events.slides++
            }
        },
        Runnable { events.navigation++ }
    )

    private fun navigateAndReturn() {
        val manager = activity.supportFragmentManager
        manager.beginTransaction().add(Fragment(), "navigation-${navigationId++}")
            .addToBackStack(null).commit()
        manager.executePendingTransactions()
        assertTrue(manager.popBackStackImmediate())
    }

    class Events(var states: Int = 0, var slides: Int = 0, var navigation: Int = 0) {
        fun snapshot() = listOf(states, slides, navigation)
    }

    class ViewOwnerFragment : Fragment() {
        lateinit var bind: (LifecycleOwner, Events) -> DetailPlayerViewListeners
        val generations = mutableListOf<Events>()
        var registration: DetailPlayerViewListeners? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext())

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val events = Events().also(generations::add)
            registration = bind(viewLifecycleOwner, events)
            // No explicit cleanup: the real fragment view lifecycle must release listeners.
        }
    }

    private class TrackingSheetBehavior : BottomSheetBehavior<FrameLayout>() {
        val registered = mutableListOf<BottomSheetCallback>()

        override fun addBottomSheetCallback(callback: BottomSheetCallback) {
            super.addBottomSheetCallback(callback)
            if (callback !in registered) registered += callback
        }

        override fun removeBottomSheetCallback(callback: BottomSheetCallback) {
            super.removeBottomSheetCallback(callback)
            registered -= callback
        }

        fun dispatchState(sheet: View, state: Int) = registered.toList().forEach { it.onStateChanged(sheet, state) }

        fun dispatchSlide(sheet: View, offset: Float) = registered.toList().forEach { it.onSlide(sheet, offset) }
    }
}
