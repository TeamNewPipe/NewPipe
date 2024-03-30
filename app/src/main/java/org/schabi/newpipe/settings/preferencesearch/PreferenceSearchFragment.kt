package org.schabi.newpipe.settings.preferencesearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.schabi.newpipe.databinding.SettingsPreferencesearchFragmentBinding
import java.util.function.Consumer

/**
 * Displays the search results.
 */
class PreferenceSearchFragment() : Fragment() {
    private var searcher: PreferenceSearcher? = null
    private var binding: SettingsPreferencesearchFragmentBinding? = null
    private var adapter: PreferenceSearchAdapter? = null
    fun setSearcher(searcher: PreferenceSearcher?) {
        this.searcher = searcher
    }

    public override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = SettingsPreferencesearchFragmentBinding.inflate(inflater, container, false)
        binding!!.searchResults.setLayoutManager(LinearLayoutManager(getContext()))
        adapter = PreferenceSearchAdapter()
        adapter!!.setOnItemClickListener(Consumer({ item: PreferenceSearchItem? -> onItemClicked((item)!!) }))
        binding!!.searchResults.setAdapter(adapter)
        return binding!!.getRoot()
    }

    fun updateSearchResults(keyword: String) {
        if (adapter == null || searcher == null) {
            return
        }
        val results: List<PreferenceSearchItem?>? = searcher!!.searchFor(keyword)
        adapter!!.submitList(results)
        setEmptyViewShown(results!!.isEmpty())
    }

    private fun setEmptyViewShown(shown: Boolean) {
        binding!!.emptyStateView.setVisibility(if (shown) View.VISIBLE else View.GONE)
        binding!!.searchResults.setVisibility(if (shown) View.GONE else View.VISIBLE)
    }

    fun onItemClicked(item: PreferenceSearchItem) {
        if (!(getActivity() is PreferenceSearchResultListener)) {
            throw ClassCastException(
                    getActivity().toString() + " must implement SearchPreferenceResultListener")
        }
        (getActivity() as PreferenceSearchResultListener?)!!.onSearchResultClicked(item)
    }

    companion object {
        val NAME: String = PreferenceSearchFragment::class.java.getSimpleName()
    }
}
