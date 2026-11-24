package org.schabi.newpipe.settings

import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.databinding.ToolbarLayoutBinding
import org.schabi.newpipe.databinding.ToolbarSearchLayoutBinding
import org.schabi.newpipe.fragments.list.search.VoiceSearchController
import org.schabi.newpipe.local.feed.FeedGroupSelector

@RunWith(AndroidJUnit4::class)
class InterfacePreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val alignmentKey = context.getString(R.string.toolbar_title_alignment_key)
    private val voiceKey = context.getString(R.string.show_voice_search_button_key)
    private lateinit var previous: Map<String, *>

    @Before
    fun setUp() {
        previous = preferences.all.filterKeys { it == alignmentKey || it == voiceKey }
        assertTrue(preferences.edit().remove(alignmentKey).remove(voiceKey).commit())
    }

    @After
    fun tearDown() {
        val editor = preferences.edit().remove(alignmentKey).remove(voiceKey)
        previous.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        assertTrue(editor.commit())
    }

    @Test
    fun normalTitlesUpdateWithoutOverlappingUnequalNavigationAndMenuWidths() {
        for (locale in listOf(Locale.ENGLISH, Locale("ar"))) {
            withToolbar(locale) { scenario, fixture ->
                scenario.onActivity {
                    val toolbar = fixture.binding.toolbar
                    preferences.edit().remove(alignmentKey).commit()
                    toolbar.title = "A long toolbar title that must stay between its actions"
                    fixture.layout()
                    assertFalse(toolbar.isTitleCentered)

                    preferences.edit().putString(alignmentKey, "center").commit()
                    fixture.layout()
                    val title = toolbar.children.filterIsInstance<TextView>().first { child -> child.text == toolbar.title }
                    assertTrue(toolbar.isTitleCentered)
                    assertTrue(kotlin.math.abs(toolbar.width / 2 - (title.left + title.right) / 2) <= 1)
                    assertNoOverlap(title, toolbar)

                    preferences.edit().putString(alignmentKey, "start").commit()
                    fixture.layout()
                    assertFalse(toolbar.isTitleCentered)
                    assertNoOverlap(title, toolbar)
                }
            }
        }
    }

    @Test
    fun feedTitleCentersAtTheToolbarMidpointAndSearchRestoresStartAlignment() {
        for (locale in listOf(Locale.ENGLISH, Locale("ar"))) {
            withToolbar(locale) { scenario, fixture ->
                scenario.onActivity { activity ->
                    val toolbar = fixture.binding.toolbar
                    val actionBar = requireNotNull(activity.supportActionBar)
                    val selector = FeedGroupSelector.createToolbarView(toolbar.context) as LinearLayout
                    val label = selector.findViewById<TextView>(R.id.feed_group_toolbar_label).apply {
                        text = "A very long channel group name ".repeat(5)
                        visibility = View.VISIBLE
                    }
                    actionBar.setCustomView(selector, ActionBar.LayoutParams(-1, -1))
                    FeedGroupSelector.updateSearchVisibility(actionBar, false)
                    preferences.edit().putString(alignmentKey, "center").commit()
                    fixture.layout()
                    assertTrue(kotlin.math.abs(toolbar.width / 2 - (selector.left + selector.right) / 2) <= 1)
                    assertTrue(selector.width > 0)
                    assertNoOverlap(selector, toolbar)
                    selector.children.filterIsInstance<TextView>().forEach { child ->
                        assertEquals(Gravity.CENTER_HORIZONTAL, child.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
                    }
                    label.text = "Group"
                    fixture.layout()
                    assertTrue(label.width < selector.width)
                    assertTrue(kotlin.math.abs(selector.width / 2 - (label.left + label.right) / 2) <= 1)

                    val search = fixture.binding.toolbarContextualSearchContainer.root
                    search.visibility = View.VISIBLE
                    FeedGroupSelector.updateSearchVisibility(actionBar, true)
                    fixture.layout()
                    assertTrue(selector.parent == null)
                    assertTrue(search.width > 0)
                    val searchWidth = search.width
                    preferences.edit().putString(alignmentKey, "start").commit()
                    fixture.layout()
                    assertEquals(searchWidth, search.width)

                    search.visibility = View.GONE
                    FeedGroupSelector.updateSearchVisibility(actionBar, false)
                    fixture.layout()
                    assertTrue(selector.parent === toolbar)
                    selector.children.filterIsInstance<TextView>().forEach { child ->
                        assertEquals(Gravity.START, child.gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                    }
                    if (selector.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        assertEquals(selector.width - selector.paddingRight, label.right)
                    } else {
                        assertEquals(selector.paddingLeft, label.left)
                    }
                    assertNoOverlap(selector, toolbar)
                    assertFalse(toolbar.isTitleCentered)

                    actionBar.setDisplayShowCustomEnabled(false)
                    actionBar.customView = null
                    actionBar.setDisplayShowTitleEnabled(true)
                    toolbar.title = "Subscriptions"
                    fixture.layout()
                    assertFalse(toolbar.isTitleCentered)
                    assertTrue(selector.parent == null)
                }
            }
        }
    }

    @Test
    fun detachedToolbarStopsObservingAndReadsTheCurrentPreferenceWhenReattached() {
        withToolbar(Locale.ENGLISH) { scenario, fixture ->
            scenario.onActivity {
                assertFalse(fixture.binding.toolbar.isTitleCentered)
                fixture.host.removeView(fixture.binding.root)
                preferences.edit().putString(alignmentKey, "center").commit()
                assertFalse(fixture.binding.toolbar.isTitleCentered)
                fixture.host.addView(fixture.binding.root)
                fixture.layout()
                assertTrue(fixture.binding.toolbar.isTitleCentered)
            }
        }
    }

    @Test
    fun microphoneAndFilterTogglesReclaimSpaceInBothLanguageDirections() {
        for (locale in listOf(Locale.ENGLISH, Locale("ar"))) {
            withToolbar(locale) { scenario, fixture ->
                scenario.onActivity { activity ->
                    val fragment = VoiceFragment()
                    activity.supportFragmentManager.beginTransaction().add(fixture.content.id, fragment).commitNow()
                    activity.supportActionBar?.setDisplayShowTitleEnabled(false)
                    val search = fixture.binding.toolbarSearchContainer
                    search.root.visibility = View.VISIBLE
                    search.toolbarSearchEditText.setText("Keep this query")
                    search.toolbarSearchEditText.requestFocus()
                    assertEquals(View.VISIBLE, search.toolbarSearchVoice.visibility)

                    for (filterVisible in listOf(true, false)) {
                        search.toolbarSearchFilter.visibility = if (filterVisible) View.VISIBLE else View.GONE
                        for (voiceVisible in listOf(true, false)) {
                            preferences.edit().putBoolean(voiceKey, voiceVisible).commit()
                            fixture.layout()
                            assertEquals(if (voiceVisible) View.VISIBLE else View.GONE, search.toolbarSearchVoice.visibility)
                            val controls = listOf(search.toolbarSearchFilter, search.toolbarSearchVoice, search.toolbarSearchClear)
                                .filter { it.visibility != View.GONE }
                            assertEquals(search.root.width - controls.sumOf { it.width }, search.toolbarSearchEditText.width)
                            assertTrue(search.toolbarSearchEditText.width > 0)
                            val ordered = listOf(search.toolbarSearchEditText) + controls
                            ordered.zipWithNext().forEach { (first, second) ->
                                assertTrue(
                                    if (search.root.layoutDirection == View.LAYOUT_DIRECTION_RTL) first.left >= second.right else first.right <= second.left
                                )
                            }
                            assertEquals("Keep this query", search.toolbarSearchEditText.text.toString())
                            assertTrue(search.toolbarSearchEditText.hasFocus())
                        }
                    }

                    activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
                    preferences.edit().putBoolean(voiceKey, true).commit()
                    assertEquals(View.GONE, search.toolbarSearchVoice.visibility)
                    assertFalse(search.toolbarSearchVoice.hasOnClickListeners())
                    activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
                    fixture.layout()
                    assertEquals(View.VISIBLE, search.toolbarSearchVoice.visibility)
                    assertTrue(search.toolbarSearchVoice.hasOnClickListeners())
                }
            }
        }
    }

    @Test
    fun unboundSharedSearchRowsReserveSpaceOnlyForTheirClearAction() {
        withToolbar(Locale("ar")) { scenario, fixture ->
            scenario.onActivity {
                val search = ToolbarSearchLayoutBinding.inflate(LayoutInflater.from(fixture.binding.toolbar.context))
                search.root.layoutDirection = View.LAYOUT_DIRECTION_RTL
                fixture.host.addView(search.root)
                measure(search.root, 320, 56)
                assertEquals(View.LAYOUT_DIRECTION_RTL, search.root.layoutDirection)
                assertEquals(View.GONE, search.toolbarSearchVoice.visibility)
                assertEquals(View.GONE, search.toolbarSearchFilter.visibility)
                assertEquals(search.root.width - search.toolbarSearchClear.width, search.toolbarSearchEditText.width)
            }
        }
    }

    private fun withToolbar(locale: Locale, test: (ActivityScenario<AboutActivity>, Fixture) -> Unit) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                val configuration = Configuration(activity.resources.configuration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
                val themed = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.LightTheme)
                val binding = ToolbarLayoutBinding.inflate(LayoutInflater.from(themed))
                binding.root.layoutDirection = configuration.layoutDirection
                val content = FrameLayout(activity).apply { id = View.generateViewId() }
                val host = FrameLayout(activity).apply {
                    addView(content, FrameLayout.LayoutParams(-1, -1))
                    addView(binding.root, FrameLayout.LayoutParams(-1, -2))
                }
                activity.setSupportActionBar(null)
                activity.setContentView(host)
                activity.setSupportActionBar(binding.toolbar)
                fixture = Fixture(binding, host, content)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                fixture.binding.toolbar.apply {
                    setNavigationIcon(R.drawable.ic_arrow_back)
                    menu.add("Search").setIcon(R.drawable.ic_search).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add("Close").setIcon(R.drawable.ic_close).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                fixture.layout()
                val expectedDirection = if (locale.language == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                assertEquals(expectedDirection, fixture.binding.toolbar.layoutDirection)
            }
            test(scenario, fixture)
        }
    }

    private fun assertNoOverlap(title: View, toolbar: ViewGroup) {
        toolbar.children.filter { it !== title && it.visibility != View.GONE && it.width > 0 }.forEach { action ->
            assertTrue("Title overlaps ${action.javaClass.simpleName}", title.right <= action.left || title.left >= action.right)
        }
    }

    private class Fixture(val binding: ToolbarLayoutBinding, val host: FrameLayout, val content: FrameLayout) {
        fun layout() = measure(binding.root, 320, 160)
    }

    class VoiceFragment : Fragment() {
        private val controller = VoiceSearchController(this) { }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext())

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            controller.bind(requireActivity().findViewById(R.id.toolbar_search_container))
        }

        override fun onDestroyView() {
            controller.unbind()
            super.onDestroyView()
        }
    }

    companion object {
        private fun measure(view: View, widthDp: Int, heightDp: Int) {
            val density = view.resources.displayMetrics.density
            val width = (widthDp * density + 0.5f).toInt()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((heightDp * density + 0.5f).toInt(), View.MeasureSpec.AT_MOST)
            )
            view.layout(0, 0, width, view.measuredHeight)
        }
    }
}
