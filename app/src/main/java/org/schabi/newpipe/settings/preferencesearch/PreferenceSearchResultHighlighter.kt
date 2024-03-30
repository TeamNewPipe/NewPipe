package org.schabi.newpipe.settings.preferencesearch

import android.content.res.Resources.Theme
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup.PreferencePositionCallback
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R

object PreferenceSearchResultHighlighter {
    private val TAG: String = "PrefSearchResHighlter"

    /**
     * Highlight the specified preference.
     * <br></br>
     * Note: This function is Thread independent (can be called from outside of the main thread).
     *
     * @param item The item to highlight
     * @param prefsFragment The fragment where the items is located on
     */
    fun highlight(
            item: PreferenceSearchItem,
            prefsFragment: PreferenceFragmentCompat
    ) {
        Handler(Looper.getMainLooper()).post(Runnable({ doHighlight(item, prefsFragment) }))
    }

    private fun doHighlight(
            item: PreferenceSearchItem,
            prefsFragment: PreferenceFragmentCompat
    ) {
        val prefResult: Preference? = prefsFragment.findPreference(item.getKey())
        if (prefResult == null) {
            Log.w(TAG, "Preference '" + item.getKey() + "' not found on '" + prefsFragment + "'")
            return
        }
        val recyclerView: RecyclerView = prefsFragment.getListView()
        val adapter: RecyclerView.Adapter<*>? = recyclerView.getAdapter()
        if (adapter is PreferencePositionCallback) {
            val position: Int = (adapter as PreferencePositionCallback)
                    .getPreferenceAdapterPosition(prefResult)
            if (position != RecyclerView.NO_POSITION) {
                recyclerView.scrollToPosition(position)
                recyclerView.postDelayed(Runnable({
                    val holder: RecyclerView.ViewHolder? = recyclerView.findViewHolderForAdapterPosition(position)
                    if (holder != null) {
                        val background: Drawable = holder.itemView.getBackground()
                        if (background is RippleDrawable) {
                            showRippleAnimation(background)
                            return@postDelayed
                        }
                    }
                    highlightFallback(prefsFragment, prefResult)
                }), 200)
                return
            }
        }
        highlightFallback(prefsFragment, prefResult)
    }

    /**
     * Alternative highlighting (shows an → arrow in front of the setting)if ripple does not work.
     *
     * @param prefsFragment
     * @param prefResult
     */
    private fun highlightFallback(
            prefsFragment: PreferenceFragmentCompat,
            prefResult: Preference
    ) {
        // Get primary color from text for highlight icon
        val typedValue: TypedValue = TypedValue()
        val theme: Theme = prefsFragment.getActivity()!!.getTheme()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val arr: TypedArray = prefsFragment.getActivity()
                .obtainStyledAttributes(
                        typedValue.data, intArrayOf(android.R.attr.textColorPrimary))
        val color: Int = arr.getColor(0, -0x1ac6cb)
        arr.recycle()

        // Show highlight icon
        val oldIcon: Drawable? = prefResult.getIcon()
        val oldSpaceReserved: Boolean = prefResult.isIconSpaceReserved()
        val highlightIcon: Drawable? = AppCompatResources.getDrawable(
                prefsFragment.requireContext(),
                R.drawable.ic_play_arrow)
        highlightIcon!!.setColorFilter(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN))
        prefResult.setIcon(highlightIcon)
        prefsFragment.scrollToPreference(prefResult)
        Handler(Looper.getMainLooper()).postDelayed(Runnable({
            prefResult.setIcon(oldIcon)
            prefResult.setIconSpaceReserved(oldSpaceReserved)
        }), 1000)
    }

    private fun showRippleAnimation(rippleDrawable: RippleDrawable) {
        rippleDrawable.setState(intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled))
        Handler(Looper.getMainLooper())
                .postDelayed(Runnable({ rippleDrawable.setState(intArrayOf()) }), 1000)
    }
}
