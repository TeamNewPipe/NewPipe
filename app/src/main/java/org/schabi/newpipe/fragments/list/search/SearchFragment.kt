package org.schabi.newpipe.fragments.list.search

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.TooltipCompat
import androidx.collection.SparseArrayCompat
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import icepick.State
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiConsumer
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.schabi.newpipe.BaseFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentSearchBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.search.SearchExtractor.NothingFoundException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.peertube.linkHandler.PeertubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.utils.Utils
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.fragments.list.search.SuggestionListAdapter.OnSuggestionItemSelected
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.ktx.isInterruptedCaused
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KeyboardUtil
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ServiceHelper
import java.util.Arrays
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.stream.Collectors

class SearchFragment() : BaseListFragment<SearchInfo?, InfoItemsPage<*>?>(), BackPressable {
    private val suggestionPublisher: PublishSubject<String> = PublishSubject.create()

    @State
    var filterItemCheckedId: Int = -1

    @State
    protected var serviceId: Int = NO_SERVICE_ID

    // these three represents the current search query
    @State
    var searchString: String? = null

    /**
     * No content filter should add like contentFilter = all
     * be aware of this when implementing an extractor.
     */
    @State
    var contentFilter: Array<String?> = arrayOfNulls(0)

    @State
    var sortFilter: String? = null

    // these represents the last search
    @State
    var lastSearchedString: String? = null

    @State
    var searchSuggestion: String? = null

    @State
    var isCorrectedSearch: Boolean = false

    @State
    var metaInfo: Array<MetaInfo>?

    @State
    var wasSearchFocused: Boolean = false
    private val menuItemToFilterName: SparseArrayCompat<String> = SparseArrayCompat()
    private var service: StreamingService? = null
    private var nextPage: Page? = null
    private var showLocalSuggestions: Boolean = true
    private var showRemoteSuggestions: Boolean = true
    private var searchDisposable: Disposable? = null
    private var suggestionDisposable: Disposable? = null
    private val disposables: CompositeDisposable = CompositeDisposable()
    private var suggestionListAdapter: SuggestionListAdapter? = null
    private var historyRecordManager: HistoryRecordManager? = null

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    ////////////////////////////////////////////////////////////////////////// */
    private var searchBinding: FragmentSearchBinding? = null
    private var searchToolbarContainer: View? = null
    private var searchEditText: EditText? = null
    private var searchClear: View? = null
    private var suggestionsPanelVisible: Boolean = false
    /*//////////////////////////////////////////////////////////////////////// */
    /**
     * TextWatcher to remove rich-text formatting on the search EditText when pasting content
     * from the clipboard.
     */
    private var textWatcher: TextWatcher? = null

    /**
     * Set wasLoading to true so when the fragment onResume is called, the initial search is done.
     */
    private fun setSearchOnResume() {
        wasLoading.set(true)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onAttach(context: Context) {
        super.onAttach(context)
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences((activity)!!)
        showLocalSuggestions = NewPipeSettings.showLocalSearchSuggestions((activity)!!, prefs)
        showRemoteSuggestions = NewPipeSettings.showRemoteSearchSuggestions((activity)!!, prefs)
        suggestionListAdapter = SuggestionListAdapter()
        historyRecordManager = HistoryRecordManager(context)
    }

    public override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                     savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    public override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        searchBinding = FragmentSearchBinding.bind(rootView)
        super.onViewCreated(rootView, savedInstanceState)
        showSearchOnStart()
        initSearchListeners()
    }

    private fun updateService() {
        try {
            service = NewPipe.getService(serviceId)
        } catch (e: Exception) {
            showUiErrorSnackbar(this, "Getting service for id " + serviceId, e)
        }
    }

    public override fun onStart() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "onStart() called")
        }
        super.onStart()
        updateService()
    }

    public override fun onPause() {
        super.onPause()
        wasSearchFocused = searchEditText!!.hasFocus()
        if (searchDisposable != null) {
            searchDisposable!!.dispose()
        }
        if (suggestionDisposable != null) {
            suggestionDisposable!!.dispose()
        }
        disposables.clear()
        hideKeyboardSearch()
    }

    public override fun onResume() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "onResume() called")
        }
        super.onResume()
        if (suggestionDisposable == null || suggestionDisposable!!.isDisposed()) {
            initSuggestionObserver()
        }
        if (!TextUtils.isEmpty(searchString)) {
            if (wasLoading.getAndSet(false)) {
                search((searchString)!!, contentFilter, sortFilter)
                return
            } else if (infoListAdapter!!.getItemsList().isEmpty()) {
                if (savedState == null) {
                    search((searchString)!!, contentFilter, sortFilter)
                    return
                } else if (!isLoading.get() && !wasSearchFocused && (lastPanelError == null)) {
                    infoListAdapter!!.clearStreamItemList()
                    showEmptyState()
                }
            }
        }
        handleSearchSuggestion()
        ExtractorHelper.showMetaInfoInTextView(if (metaInfo == null) null else Arrays.asList(*metaInfo),
                searchBinding!!.searchMetaInfoTextView, searchBinding!!.searchMetaInfoSeparator,
                disposables)
        if (TextUtils.isEmpty(searchString) || wasSearchFocused) {
            showKeyboardSearch()
            showSuggestionsPanel()
        } else {
            hideKeyboardSearch()
            hideSuggestionsPanel()
        }
        wasSearchFocused = false
    }

    public override fun onDestroyView() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "onDestroyView() called")
        }
        unsetSearchListeners()
        searchBinding = null
        super.onDestroyView()
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (searchDisposable != null) {
            searchDisposable!!.dispose()
        }
        if (suggestionDisposable != null) {
            suggestionDisposable!!.dispose()
        }
        disposables.clear()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ReCaptchaActivity.Companion.RECAPTCHA_REQUEST) {
            if ((resultCode == Activity.RESULT_OK
                            && !TextUtils.isEmpty(searchString))) {
                search((searchString)!!, contentFilter, sortFilter)
            } else {
                Log.e(TAG, "ReCaptcha failed")
            }
        } else {
            Log.e(TAG, "Request code from activity not supported [" + requestCode + "]")
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    ////////////////////////////////////////////////////////////////////////// */
    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        searchBinding!!.suggestionsList.setAdapter(suggestionListAdapter)
        // animations are just strange and useless, since the suggestions keep changing too much
        searchBinding!!.suggestionsList.setItemAnimator(null)
        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            public override fun getMovementFlags(recyclerView: RecyclerView,
                                                 viewHolder: RecyclerView.ViewHolder): Int {
                return getSuggestionMovementFlags(viewHolder)
            }

            public override fun onMove(recyclerView: RecyclerView,
                                       viewHolder: RecyclerView.ViewHolder,
                                       viewHolder1: RecyclerView.ViewHolder): Boolean {
                return false
            }

            public override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
                onSuggestionItemSwiped(viewHolder)
            }
        }).attachToRecyclerView(searchBinding!!.suggestionsList)
        searchToolbarContainer = activity!!.findViewById(R.id.toolbar_search_container)
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text)
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    ////////////////////////////////////////////////////////////////////////// */
    public override fun writeTo(objectsToSave: Queue<Any?>) {
        super.writeTo(objectsToSave)
        objectsToSave.add(nextPage)
    }

    @Throws(Exception::class)
    public override fun readFrom(savedObjects: Queue<Any>) {
        super.readFrom(savedObjects)
        nextPage = savedObjects.poll() as Page?
    }

    public override fun onSaveInstanceState(bundle: Bundle) {
        searchString = if (searchEditText != null) getSearchEditString().trim({ it <= ' ' }) else searchString
        super.onSaveInstanceState(bundle)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    ////////////////////////////////////////////////////////////////////////// */
    public override fun reloadContent() {
        if (!TextUtils.isEmpty(searchString) || ((searchEditText != null
                        && !isSearchEditBlank()))) {
            search((if (!TextUtils.isEmpty(searchString)) searchString else getSearchEditString())!!, contentFilter, "")
        } else {
            if (searchEditText != null) {
                searchEditText!!.setText("")
                showKeyboardSearch()
            }
            hideErrorPanel()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateOptionsMenu(menu: Menu,
                                            inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val supportActionBar: ActionBar? = activity!!.getSupportActionBar()
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false)
            supportActionBar.setDisplayHomeAsUpEnabled(true)
        }
        var itemId: Int = 0
        var isFirstItem: Boolean = true
        val c: Context? = getContext()
        if (service == null) {
            Log.w(TAG, "onCreateOptionsMenu() called with null service")
            updateService()
        }
        for (filter: String in service!!.getSearchQHFactory().getAvailableContentFilter()) {
            if ((filter == YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)) {
                val musicItem: MenuItem = menu.add(2,
                        itemId++,
                        0,
                        "YouTube Music")
                musicItem.setEnabled(false)
            } else if ((filter == PeertubeSearchQueryHandlerFactory.SEPIA_VIDEOS)) {
                val sepiaItem: MenuItem = menu.add(2,
                        itemId++,
                        0,
                        "Sepia Search")
                sepiaItem.setEnabled(false)
            }
            menuItemToFilterName.put(itemId, filter)
            val item: MenuItem = menu.add(1,
                    itemId++,
                    0,
                    ServiceHelper.getTranslatedFilterString(filter, c))
            if (isFirstItem) {
                item.setChecked(true)
                isFirstItem = false
            }
        }
        menu.setGroupCheckable(1, true, true)
        restoreFilterChecked(menu, filterItemCheckedId)
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val filter: List<String?> = listOf(menuItemToFilterName.get(item.getItemId()))
        changeContentFilter(item, filter)
        return true
    }

    private fun restoreFilterChecked(menu: Menu, itemId: Int) {
        if (itemId != -1) {
            val item: MenuItem? = menu.findItem(itemId)
            if (item == null) {
                return
            }
            item.setChecked(true)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    ////////////////////////////////////////////////////////////////////////// */
    private fun showSearchOnStart() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, ("showSearchOnStart() called, searchQuery → "
                    + searchString
                    + ", lastSearchedQuery → "
                    + lastSearchedString))
        }
        searchEditText!!.setText(searchString)
        if ((TextUtils.isEmpty(searchString)
                        || isSearchEditBlank())) {
            searchToolbarContainer!!.setTranslationX(100f)
            searchToolbarContainer!!.setAlpha(0.0f)
            searchToolbarContainer!!.setVisibility(View.VISIBLE)
            searchToolbarContainer!!.animate()
                    .translationX(0f)
                    .alpha(1.0f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator()).start()
        } else {
            searchToolbarContainer!!.setTranslationX(0f)
            searchToolbarContainer!!.setAlpha(1.0f)
            searchToolbarContainer!!.setVisibility(View.VISIBLE)
        }
    }

    private fun initSearchListeners() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "initSearchListeners() called")
        }
        searchClear!!.setOnClickListener(View.OnClickListener({ v: View ->
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]")
            }
            if (isSearchEditBlank()) {
                NavigationHelper.gotoMainFragment(getFM())
                return@setOnClickListener
            }
            searchBinding!!.correctSuggestion.setVisibility(View.GONE)
            searchEditText!!.setText("")
            suggestionListAdapter!!.submitList(null)
            showKeyboardSearch()
        }))
        TooltipCompat.setTooltipText((searchClear)!!, getString(R.string.clear))
        searchEditText!!.setOnClickListener(View.OnClickListener({ v: View ->
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]")
            }
            if ((showLocalSuggestions || showRemoteSuggestions) && !isErrorPanelVisible()) {
                showSuggestionsPanel()
            }
            if (DeviceUtils.isTv(getContext())) {
                showKeyboardSearch()
            }
        }))
        searchEditText!!.setOnFocusChangeListener(OnFocusChangeListener({ v: View, hasFocus: Boolean ->
            if (BaseFragment.Companion.DEBUG) {
                Log.d(TAG, ("onFocusChange() called with: "
                        + "v = [" + v + "], hasFocus = [" + hasFocus + "]"))
            }
            if (((showLocalSuggestions || showRemoteSuggestions)
                            && hasFocus && !isErrorPanelVisible())) {
                showSuggestionsPanel()
            }
        }))
        suggestionListAdapter!!.setListener(object : OnSuggestionItemSelected {
            public override fun onSuggestionItemSelected(item: SuggestionItem?) {
                search((item!!.query)!!, arrayOfNulls(0), "")
                searchEditText!!.setText(item.query)
            }

            public override fun onSuggestionItemInserted(item: SuggestionItem?) {
                searchEditText!!.setText(item!!.query)
                searchEditText!!.setSelection(searchEditText!!.getText().length)
            }

            public override fun onSuggestionItemLongClick(item: SuggestionItem?) {
                if (item!!.fromHistory) {
                    showDeleteSuggestionDialog(item)
                }
            }
        })
        if (textWatcher != null) {
            searchEditText!!.removeTextChangedListener(textWatcher)
        }
        textWatcher = object : TextWatcher {
            public override fun beforeTextChanged(s: CharSequence, start: Int,
                                                  count: Int, after: Int) {
                // Do nothing, old text is already clean
            }

            public override fun onTextChanged(s: CharSequence, start: Int,
                                              before: Int, count: Int) {
                // Changes are handled in afterTextChanged; CharSequence cannot be changed here.
            }

            public override fun afterTextChanged(s: Editable) {
                // Remove rich text formatting
                for (span: CharacterStyle? in s.getSpans(0, s.length, CharacterStyle::class.java)) {
                    s.removeSpan(span)
                }
                val newText: String = getSearchEditString().trim({ it <= ' ' })
                suggestionPublisher.onNext(newText)
            }
        }
        searchEditText!!.addTextChangedListener(textWatcher)
        searchEditText!!.setOnEditorActionListener(
                OnEditorActionListener({ v: TextView, actionId: Int, event: KeyEvent? ->
                    if (BaseFragment.Companion.DEBUG) {
                        Log.d(TAG, ("onEditorAction() called with: v = [" + v + "], "
                                + "actionId = [" + actionId + "], event = [" + event + "]"))
                    }
                    if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                        hideKeyboardSearch()
                    } else if ((event != null
                                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                    || event.getAction() == EditorInfo.IME_ACTION_SEARCH))) {
                        searchEditText!!.setText(getSearchEditString().trim({ it <= ' ' }))
                        search(getSearchEditString(), arrayOfNulls(0), "")
                        return@setOnEditorActionListener true
                    }
                    false
                }))
        if (suggestionDisposable == null || suggestionDisposable!!.isDisposed()) {
            initSuggestionObserver()
        }
    }

    private fun unsetSearchListeners() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "unsetSearchListeners() called")
        }
        searchClear!!.setOnClickListener(null)
        searchClear!!.setOnLongClickListener(null)
        searchEditText!!.setOnClickListener(null)
        searchEditText!!.setOnFocusChangeListener(null)
        searchEditText!!.setOnEditorActionListener(null)
        if (textWatcher != null) {
            searchEditText!!.removeTextChangedListener(textWatcher)
        }
        textWatcher = null
    }

    private fun showSuggestionsPanel() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "showSuggestionsPanel() called")
        }
        suggestionsPanelVisible = true
        searchBinding!!.suggestionsPanel.animate(true, 200, AnimationType.LIGHT_SLIDE_AND_ALPHA)
    }

    private fun hideSuggestionsPanel() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "hideSuggestionsPanel() called")
        }
        suggestionsPanelVisible = false
        searchBinding!!.suggestionsPanel.animate(false, 200, AnimationType.LIGHT_SLIDE_AND_ALPHA)
    }

    private fun showKeyboardSearch() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "showKeyboardSearch() called")
        }
        KeyboardUtil.showKeyboard(activity, searchEditText)
    }

    private fun hideKeyboardSearch() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "hideKeyboardSearch() called")
        }
        KeyboardUtil.hideKeyboard(activity, searchEditText)
    }

    private fun showDeleteSuggestionDialog(item: SuggestionItem?) {
        if ((activity == null) || (historyRecordManager == null) || (searchEditText == null)) {
            return
        }
        val query: String? = item!!.query
        AlertDialog.Builder(activity!!)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, DialogInterface.OnClickListener({ dialog: DialogInterface?, which: Int ->
                    val onDelete: Disposable = historyRecordManager!!.deleteSearchHistory(query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    Consumer<Int?>({ howManyDeleted: Int? ->
                                        suggestionPublisher
                                                .onNext(getSearchEditString())
                                    }),
                                    Consumer({ throwable: Throwable? ->
                                        showSnackBarError(ErrorInfo((throwable)!!,
                                                UserAction.DELETE_FROM_HISTORY,
                                                "Deleting item failed"))
                                    }))
                    disposables.add(onDelete)
                }))
                .show()
    }

    public override fun onBackPressed(): Boolean {
        if ((suggestionsPanelVisible
                        && !infoListAdapter!!.getItemsList().isEmpty()
                        && !isLoading.get())) {
            hideSuggestionsPanel()
            hideKeyboardSearch()
            searchEditText!!.setText(lastSearchedString)
            return true
        }
        return false
    }

    private fun getLocalSuggestionsObservable(
            query: String, similarQueryLimit: Int): Observable<MutableList<SuggestionItem>> {
        return historyRecordManager
                .getRelatedSearches(query, similarQueryLimit, 25)
                .toObservable()
                .map(io.reactivex.rxjava3.functions.Function<List<String?>?, MutableList<SuggestionItem>>({ searchHistoryEntries: List<String?>? ->
                    searchHistoryEntries!!.stream()
                            .map(java.util.function.Function({ entry: String? -> SuggestionItem(true, entry) }))
                            .collect(Collectors.toList())
                }))
    }

    private fun getRemoteSuggestionsObservable(query: String): Observable<MutableList<SuggestionItem>> {
        return ExtractorHelper.suggestionsFor(serviceId, query)
                .toObservable()
                .map(io.reactivex.rxjava3.functions.Function<List<String?>?, MutableList<SuggestionItem>>({ strings: List<String?>? ->
                    val result: MutableList<SuggestionItem> = ArrayList()
                    for (entry: String? in strings!!) {
                        result.add(SuggestionItem(false, entry))
                    }
                    result
                }))
    }

    private fun initSuggestionObserver() {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "initSuggestionObserver() called")
        }
        if (suggestionDisposable != null) {
            suggestionDisposable!!.dispose()
        }
        suggestionDisposable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE.toLong(), TimeUnit.MILLISECONDS)
                .startWithItem(if (searchString == null) "" else searchString)
                .switchMap<Notification<List<SuggestionItem>>>(io.reactivex.rxjava3.functions.Function<String, ObservableSource<out Notification<List<SuggestionItem>>>>({ query: String ->
                    // Only show remote suggestions if they are enabled in settings and
                    // the query length is at least THRESHOLD_NETWORK_SUGGESTION
                    val shallShowRemoteSuggestionsNow: Boolean = (showRemoteSuggestions
                            && query.length >= THRESHOLD_NETWORK_SUGGESTION)
                    if (showLocalSuggestions && shallShowRemoteSuggestionsNow) {
                        return@switchMap Observable.zip<MutableList<SuggestionItem>, MutableList<SuggestionItem>, List<SuggestionItem>>(
                                getLocalSuggestionsObservable(query, 3),
                                getRemoteSuggestionsObservable(query),
                                BiFunction<MutableList<SuggestionItem>, MutableList<SuggestionItem>, List<SuggestionItem>>({ local: MutableList<SuggestionItem>, remote: MutableList<SuggestionItem> ->
                                    remote.removeIf(Predicate({ remoteItem: SuggestionItem ->
                                        local.stream().anyMatch(
                                                Predicate({ localItem: SuggestionItem -> (localItem == remoteItem) }))
                                    }))
                                    local.addAll(remote)
                                    local
                                }))
                                .materialize()
                    } else if (showLocalSuggestions) {
                        return@switchMap getLocalSuggestionsObservable(query, 25)
                                .materialize()
                    } else if (shallShowRemoteSuggestionsNow) {
                        return@switchMap getRemoteSuggestionsObservable(query)
                                .materialize()
                    } else {
                        return@switchMap Single.fromCallable<List<SuggestionItem>>(Callable<List<SuggestionItem>>({ emptyList() }))
                                .toObservable()
                                .materialize()
                    }
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        Consumer<Notification<List<SuggestionItem>>>({ listNotification: Notification<List<SuggestionItem>> ->
                            if (listNotification.isOnNext()) {
                                if (listNotification.getValue() != null) {
                                    handleSuggestions(listNotification.getValue()!!)
                                }
                            } else if ((listNotification.isOnError()
                                            && (listNotification.getError() != null
                                            ) && !listNotification.getError()!!.isInterruptedCaused)) {
                                showSnackBarError(ErrorInfo(listNotification.getError()!!,
                                        UserAction.GET_SUGGESTIONS, (searchString)!!, serviceId))
                            }
                        }), Consumer<Throwable>({ throwable: Throwable? ->
                    showSnackBarError(ErrorInfo(
                            (throwable)!!, UserAction.GET_SUGGESTIONS, (searchString)!!, serviceId))
                }))
    }

    override fun doInitialLoadLogic() {
        // no-op
    }

    /**
     * Perform a search.
     * @param theSearchString the trimmed search string
     * @param theContentFilter the content filter to use. FIXME: unused param
     * @param theSortFilter FIXME: unused param
     */
    private fun search(theSearchString: String,
                       theContentFilter: Array<String?>,
                       theSortFilter: String?) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "search() called with: query = [" + theSearchString + "]")
        }
        if (theSearchString.isEmpty()) {
            return
        }

        // Check if theSearchString is a URL which can be opened by NewPipe directly
        // and open it if possible.
        try {
            val streamingService: StreamingService = NewPipe.getServiceByUrl(theSearchString)
            showLoading()
            disposables.add(Observable
                    .fromCallable(Callable({
                        NavigationHelper.getIntentByLink((activity)!!,
                                streamingService, theSearchString)
                    }))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(Consumer({ intent: Intent? ->
                        getFM().popBackStackImmediate()
                        activity!!.startActivity(intent)
                    }), Consumer({ throwable: Throwable? -> showTextError(getString(R.string.unsupported_url)) })))
            return
        } catch (ignored: Exception) {
            // Exception occurred, it's not a url
        }

        // prepare search
        lastSearchedString = searchString
        searchString = theSearchString
        infoListAdapter!!.clearStreamItemList()
        hideSuggestionsPanel()
        ExtractorHelper.showMetaInfoInTextView(null, searchBinding!!.searchMetaInfoTextView,
                searchBinding!!.searchMetaInfoSeparator, disposables)
        hideKeyboardSearch()

        // store search query if search history is enabled
        disposables.add(historyRecordManager!!.onSearched(serviceId, theSearchString)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        Consumer<Long?>({ ignored: Long? -> }),
                        Consumer({ throwable: Throwable? ->
                            showSnackBarError(ErrorInfo((throwable)!!, UserAction.SEARCHED,
                                    theSearchString, serviceId))
                        })
                ))

        // load search results
        suggestionPublisher.onNext(theSearchString)
        startLoading(false)
    }

    public override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        disposables.clear()
        if (searchDisposable != null) {
            searchDisposable!!.dispose()
        }
        searchDisposable = ExtractorHelper.searchFor(serviceId,
                searchString,
                Arrays.asList(*contentFilter),
                sortFilter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent(BiConsumer({ searchResult: SearchInfo?, throwable: Throwable? -> isLoading.set(false) }))
                .subscribe(Consumer<SearchInfo?>({ result: SearchInfo? -> this.handleResult(result) }), Consumer({ exception: Throwable -> onItemError(exception) }))
    }

    override fun loadMoreItems() {
        if (!Page.isValid(nextPage)) {
            return
        }
        isLoading.set(true)
        showListFooter(true)
        if (searchDisposable != null) {
            searchDisposable!!.dispose()
        }
        searchDisposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                searchString,
                Arrays.asList(*contentFilter),
                sortFilter,
                nextPage)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent(BiConsumer({ nextItemsResult: InfoItemsPage<InfoItem?>?, throwable: Throwable? -> isLoading.set(false) }))
                .subscribe(Consumer<InfoItemsPage<InfoItem?>?>({ result: InfoItemsPage<InfoItem?>? -> handleNextItems(result) }), Consumer({ exception: Throwable -> onItemError(exception) }))
    }

    override fun hasMoreItems(): Boolean {
        return Page.isValid(nextPage)
    }

    override fun onItemSelected(selectedItem: InfoItem) {
        super.onItemSelected(selectedItem)
        hideKeyboardSearch()
    }

    private fun onItemError(exception: Throwable) {
        if (exception is NothingFoundException) {
            infoListAdapter!!.clearStreamItemList()
            showEmptyState()
        } else {
            showError(ErrorInfo(exception, UserAction.SEARCHED, (searchString)!!, serviceId))
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    ////////////////////////////////////////////////////////////////////////// */
    private fun changeContentFilter(item: MenuItem, theContentFilter: List<String?>) {
        filterItemCheckedId = item.getItemId()
        item.setChecked(true)
        contentFilter = theContentFilter.toTypedArray<String?>()
        if (!TextUtils.isEmpty(searchString)) {
            search((searchString)!!, contentFilter, sortFilter)
        }
    }

    private fun setQuery(theServiceId: Int,
                         theSearchString: String?,
                         theContentFilter: Array<String?>,
                         theSortFilter: String) {
        serviceId = theServiceId
        searchString = theSearchString
        contentFilter = theContentFilter
        sortFilter = theSortFilter
    }

    private fun getSearchEditString(): String {
        return searchEditText!!.getText().toString()
    }

    private fun isSearchEditBlank(): Boolean {
        return Utils.isBlank(getSearchEditString())
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    ////////////////////////////////////////////////////////////////////////// */
    fun handleSuggestions(suggestions: List<SuggestionItem>) {
        if (BaseFragment.Companion.DEBUG) {
            Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]")
        }
        suggestionListAdapter!!.submitList(suggestions,
                Runnable({ searchBinding!!.suggestionsList.scrollToPosition(0) }))
        if (suggestionsPanelVisible && isErrorPanelVisible()) {
            hideLoading()
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    ////////////////////////////////////////////////////////////////////////// */
    public override fun hideLoading() {
        super.hideLoading()
        showListFooter(false)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search Results
    ////////////////////////////////////////////////////////////////////////// */
    public override fun handleResult(result: SearchInfo) {
        val exceptions: List<Throwable> = result.getErrors()
        if ((!exceptions.isEmpty()
                        && !(exceptions.size == 1
                        && exceptions.get(0) is NothingFoundException))) {
            showSnackBarError(ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                    (searchString)!!, serviceId))
        }
        searchSuggestion = result.getSearchSuggestion()
        if (searchSuggestion != null) {
            searchSuggestion = searchSuggestion!!.trim({ it <= ' ' })
        }
        isCorrectedSearch = result.isCorrectedSearch()

        // List<MetaInfo> cannot be bundled without creating some containers
        metaInfo = result.getMetaInfo().toTypedArray<MetaInfo>()
        ExtractorHelper.showMetaInfoInTextView(result.getMetaInfo(), searchBinding!!.searchMetaInfoTextView,
                searchBinding!!.searchMetaInfoSeparator, disposables)
        handleSearchSuggestion()
        lastSearchedString = searchString
        nextPage = result.getNextPage()
        if (infoListAdapter!!.getItemsList().isEmpty()) {
            if (!result.getRelatedItems().isEmpty()) {
                infoListAdapter!!.addInfoItemList(result.getRelatedItems())
            } else {
                infoListAdapter!!.clearStreamItemList()
                showEmptyState()
                return
            }
        }
        super.handleResult(result)
    }

    private fun handleSearchSuggestion() {
        if (TextUtils.isEmpty(searchSuggestion)) {
            searchBinding!!.correctSuggestion.setVisibility(View.GONE)
        } else {
            val helperText: String = getString(if (isCorrectedSearch) R.string.search_showing_result_for else R.string.did_you_mean)
            val highlightedSearchSuggestion: String = "<b><i>" + Html.escapeHtml(searchSuggestion) + "</i></b>"
            val text: String = String.format(helperText, highlightedSearchSuggestion)
            searchBinding!!.correctSuggestion.setText(HtmlCompat.fromHtml(text,
                    HtmlCompat.FROM_HTML_MODE_LEGACY))
            searchBinding!!.correctSuggestion.setOnClickListener(View.OnClickListener({ v: View? ->
                searchBinding!!.correctSuggestion.setVisibility(View.GONE)
                search((searchSuggestion)!!, contentFilter, sortFilter)
                searchEditText!!.setText(searchSuggestion)
            }))
            searchBinding!!.correctSuggestion.setOnLongClickListener(OnLongClickListener({ v: View? ->
                searchEditText!!.setText(searchSuggestion)
                searchEditText!!.setSelection(searchSuggestion!!.length)
                showKeyboardSearch()
                true
            }))
            searchBinding!!.correctSuggestion.setVisibility(View.VISIBLE)
        }
    }

    public override fun handleNextItems(result: InfoItemsPage<*>?) {
        showListFooter(false)
        infoListAdapter!!.addInfoItemList(result!!.getItems())
        nextPage = result.getNextPage()
        if (!result.getErrors().isEmpty()) {
            showSnackBarError(ErrorInfo(result.getErrors(), UserAction.SEARCHED,
                    ("\"" + searchString + "\" → pageUrl: " + nextPage.getUrl() + ", "
                            + "pageIds: " + nextPage.getIds() + ", "
                            + "pageCookies: " + nextPage.getCookies()),
                    serviceId))
        }
        super.handleNextItems(result)
    }

    public override fun handleError() {
        super.handleError()
        hideSuggestionsPanel()
        hideKeyboardSearch()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion item touch helper
    ////////////////////////////////////////////////////////////////////////// */
    fun getSuggestionMovementFlags(viewHolder: RecyclerView.ViewHolder): Int {
        val position: Int = viewHolder.getBindingAdapterPosition()
        if (position == RecyclerView.NO_POSITION) {
            return 0
        }
        val item: SuggestionItem = (suggestionListAdapter!!.getCurrentList().get(position))!!
        return if (item.fromHistory) ItemTouchHelper.Callback.makeMovementFlags(0,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) else 0
    }

    fun onSuggestionItemSwiped(viewHolder: RecyclerView.ViewHolder) {
        val position: Int = viewHolder.getBindingAdapterPosition()
        val query: String? = suggestionListAdapter!!.getCurrentList().get(position)!!.query
        val onDelete: Disposable = historyRecordManager!!.deleteSearchHistory(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        Consumer<Int?>({ howManyDeleted: Int? ->
                            suggestionPublisher
                                    .onNext(getSearchEditString())
                        }),
                        Consumer({ throwable: Throwable? ->
                            showSnackBarError(ErrorInfo((throwable)!!,
                                    UserAction.DELETE_FROM_HISTORY, "Deleting item failed"))
                        }))
        disposables.add(onDelete)
    }

    companion object {
        /*//////////////////////////////////////////////////////////////////////////
    // Search
    ////////////////////////////////////////////////////////////////////////// */
        /**
         * The suggestions will only be fetched from network if the query meet this threshold (>=).
         * (local ones will be fetched regardless of the length)
         */
        private val THRESHOLD_NETWORK_SUGGESTION: Int = 1

        /**
         * How much time have to pass without emitting a item (i.e. the user stop typing)
         * to fetch/show the suggestions, in milliseconds.
         */
        private val SUGGESTIONS_DEBOUNCE: Int = 120 //ms
        fun getInstance(serviceId: Int, searchString: String?): SearchFragment {
            val searchFragment: SearchFragment = SearchFragment()
            searchFragment.setQuery(serviceId, searchString, arrayOfNulls(0), "")
            if (!TextUtils.isEmpty(searchString)) {
                searchFragment.setSearchOnResume()
            }
            return searchFragment
        }
    }
}
