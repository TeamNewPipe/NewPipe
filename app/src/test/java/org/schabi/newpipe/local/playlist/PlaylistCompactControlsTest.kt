package org.schabi.newpipe.local.playlist

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentPlaylistBinding
import org.schabi.newpipe.databinding.ListStreamFilterChipsBinding
import org.schabi.newpipe.util.StreamFilterControls
import org.schabi.newpipe.util.StreamFilterSelection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaylistCompactControlsTest {
    @Test
    fun localIconsKeepEditSelectionAndDownloadedFilterActionsIndependent() {
        val root = inflate(R.layout.fragment_playlist)
        val filters = FragmentPlaylistBinding.bind(root).streamFilterChips
        var savedFilters = ""
        StreamFilterControls.bind(filters, "", true) { savedFilters = it }
        var editing = false
        var selections = 0
        val edit = root.findViewById<ImageView>(R.id.playlist_edit_toggle)
        val select = root.findViewById<View>(R.id.collection_select_move)
        edit.setOnClickListener {
            editing = !editing
            PlaylistCompactControls.update(root, editing)
        }
        select.setOnClickListener { selections++ }

        PlaylistCompactControls.update(root, false)
        val pencil = edit.drawable
        assertEquals(View.VISIBLE, filters.localPlaylistEditControls.visibility)
        assertEquals(View.VISIBLE, select.visibility)
        assertEquals(root.context.getString(R.string.playlist_edit_mode), edit.contentDescription)
        assertEquals(root.context.getString(R.string.playlist_select_items), select.contentDescription)
        assertEquals(select.contentDescription, select.tooltipText)
        select.performClick()
        assertEquals(1, selections)
        assertFalse(editing)
        filters.filterDownloaded.performClick()
        assertTrue(StreamFilterSelection.parse(savedFilters).downloadedOnly)
        assertEquals(1, selections)

        edit.performClick()
        assertTrue(editing)
        assertNotSame(pencil, edit.drawable)
        assertEquals(root.context.getString(R.string.playlist_edit_done), edit.contentDescription)
        assertEquals(edit.contentDescription, edit.tooltipText)
        select.isEnabled = false
        edit.performClick()
        assertFalse(editing)
        assertFalse(select.isEnabled)
        assertEquals(root.context.getString(R.string.playlist_edit_mode), edit.contentDescription)
    }

    @Test
    fun bindingSharedFiltersDoesNotExposePlaylistActions() {
        val root = inflate(R.layout.list_stream_filter_chips)
        val filters = ListStreamFilterChipsBinding.bind(root)
        StreamFilterControls.bind(filters, "DOWNLOADED", true) { }
        assertEquals(View.GONE, filters.localPlaylistEditControls.visibility)
        assertTrue(filters.filterDownloaded.isChecked)
    }

    @Test
    fun overviewKeepsTheCategoryLabelAndManagementCallbacksAcrossEditStates() {
        val root = inflate(R.layout.fragment_bookmarks)
        val label = root.findViewById<MaterialButton>(R.id.playlist_category_filter)
        label.text = "Saved category"
        var openedCategories = 0
        var managedCategories = 0
        var selections = 0
        label.setOnClickListener { openedCategories++ }
        val manage = root.findViewById<View>(R.id.playlist_category_manage)
        manage.setOnClickListener { managedCategories++ }
        val select = root.findViewById<View>(R.id.collection_select_move)
        select.setOnClickListener { selections++ }

        PlaylistCompactControls.update(root, false)
        assertEquals(View.GONE, select.visibility)
        assertEquals("Saved category", label.text.toString())
        assertEquals(root.context.getString(R.string.playlist_manage_categories), manage.contentDescription)
        assertEquals(manage.contentDescription, manage.tooltipText)
        label.performClick()
        manage.performClick()

        PlaylistCompactControls.update(root, true)
        assertEquals(View.VISIBLE, select.visibility)
        assertEquals("Saved category", label.text.toString())
        assertEquals(root.context.getString(R.string.collection_select_move), select.contentDescription)
        assertEquals(select.contentDescription, select.tooltipText)
        assertEquals(root.context.getString(R.string.playlist_edit_done), root.findViewById<View>(R.id.playlist_edit_toggle).contentDescription)
        select.performClick()
        assertEquals(listOf(1, 1, 1), listOf(openedCategories, managedCategories, selections))

        PlaylistCompactControls.update(root, false)
        assertEquals(View.GONE, select.visibility)
        assertEquals("Saved category", label.text.toString())
    }

    private fun inflate(layoutId: Int): View {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        return LayoutInflater.from(context).inflate(layoutId, null)
    }
}
