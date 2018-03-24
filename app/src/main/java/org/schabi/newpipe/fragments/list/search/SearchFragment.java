package org.schabi.newpipe.fragments.list.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.TooltipCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.history.HistoryRecordManager;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.LayoutManagerSmoothScroller;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SearchFragment
        extends BaseListFragment<SearchResult, ListExtractor.InfoItemsPage>
        implements BackPressable {

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * The suggestions will only be fetched from network if the query meet this threshold (>=).
     * (local ones will be fetched regardless of the length)
     */
    private static final int THRESHOLD_NETWORK_SUGGESTION = 1;

    /**
     * How much time have to pass without emitting a item (i.e. the user stop typing) to fetch/show the suggestions, in milliseconds.
     */
    private static final int SUGGESTIONS_DEBOUNCE = 120; //ms

    @State
    protected int filterItemCheckedId = -1;
    private SearchEngine.Filter filter = SearchEngine.Filter.ANY;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String searchQuery;
    @State
    protected String lastSearchedQuery;
    @State
    protected boolean wasSearchFocused = false;

    private int currentPage = 0;
    private int currentNextPage = 0;
    private String contentCountry;
    private boolean isSuggestionsEnabled = true;
    private boolean isSearchHistoryEnabled = true;

    private PublishSubject<String> suggestionPublisher = PublishSubject.create();
    private Disposable searchDisposable;
    private Disposable suggestionDisposable;
    private CompositeDisposable disposables = new CompositeDisposable();

    private SuggestionListAdapter suggestionListAdapter;
    private HistoryRecordManager historyRecordManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View searchToolbarContainer;
    private EditText searchEditText;
    private View searchClear;

    private View suggestionsPanel;
    private RecyclerView suggestionsRecyclerView;

    /*////////////////////////////////////////////////////////////////////////*/

    public static SearchFragment getInstance(int serviceId, String query) {
        SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(serviceId, query);

        if (!TextUtils.isEmpty(query)) {
            searchFragment.setSearchOnResume();
        }

        return searchFragment;
    }

    /**
     * Set wasLoading to true so when the fragment onResume is called, the initial search is done.
     */
    private void setSearchOnResume() {
        wasLoading.set(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        suggestionListAdapter = new SuggestionListAdapter(activity);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        isSearchHistoryEnabled = preferences.getBoolean(getString(R.string.enable_search_history_key), true);
        suggestionListAdapter.setShowSuggestionHistory(isSearchHistoryEnabled);

        historyRecordManager = new HistoryRecordManager(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        isSuggestionsEnabled = preferences.getBoolean(getString(R.string.show_search_suggestions_key), true);
        contentCountry = preferences.getString(getString(R.string.content_country_key), getString(R.string.default_country_value));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(View rootView, Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        showSearchOnStart();
        initSearchListeners();
    }

    @Override
    public void onPause() {
        super.onPause();

        wasSearchFocused = searchEditText.hasFocus();

        if (searchDisposable != null) searchDisposable.dispose();
        if (suggestionDisposable != null) suggestionDisposable.dispose();
        if (disposables != null) disposables.clear();
        hideKeyboardSearch();
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.d(TAG, "onResume() called");
        super.onResume();

        if (!TextUtils.isEmpty(searchQuery)) {
            if (wasLoading.getAndSet(false)) {
                if (currentNextPage > currentPage) loadMoreItems();
                else search(searchQuery);
            } else if (infoListAdapter.getItemsList().size() == 0) {
                if (savedState == null) {
                    search(searchQuery);
                } else if (!isLoading.get() && !wasSearchFocused) {
                    infoListAdapter.clearStreamItemList();
                    showEmptyState();
                }
            }
        }

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) initSuggestionObserver();

        if (TextUtils.isEmpty(searchQuery) || wasSearchFocused) {
            showKeyboardSearch();
            showSuggestionsPanel();
        } else {
            hideKeyboardSearch();
            hideSuggestionsPanel();
        }
        wasSearchFocused = false;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        unsetSearchListeners();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (searchDisposable != null) searchDisposable.dispose();
        if (suggestionDisposable != null) suggestionDisposable.dispose();
        if (disposables != null) disposables.clear();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK && !TextUtils.isEmpty(searchQuery)) {
                    search(searchQuery);
                } else Log.e(TAG, "ReCaptcha failed");
                break;

            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        suggestionsPanel = rootView.findViewById(R.id.suggestions_panel);
        suggestionsRecyclerView = rootView.findViewById(R.id.suggestions_list);
        suggestionsRecyclerView.setAdapter(suggestionListAdapter);
        suggestionsRecyclerView.setLayoutManager(new LayoutManagerSmoothScroller(activity));

        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container);
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text);
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(currentPage);
        objectsToSave.add(currentNextPage);
    }

    @Override
    public void readFrom(@NonNull Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        currentPage = (int) savedObjects.poll();
        currentNextPage = (int) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        searchQuery = searchEditText != null ? searchEditText.getText().toString() : searchQuery;
        super.onSaveInstanceState(bundle);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void reloadContent() {
        if (!TextUtils.isEmpty(searchQuery) || (searchEditText != null && !TextUtils.isEmpty(searchEditText.getText()))) {
            search(!TextUtils.isEmpty(searchQuery) ? searchQuery : searchEditText.getText().toString());
        } else {
            if (searchEditText != null) {
                searchEditText.setText("");
                showKeyboardSearch();
            }
            animateView(errorPanelRoot, false, 200);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        inflater.inflate(R.menu.menu_search, menu);
        restoreFilterChecked(menu, filterItemCheckedId);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_filter_all:
            case R.id.menu_filter_video:
            case R.id.menu_filter_channel:
            case R.id.menu_filter_playlist:
                changeFilter(item, getFilterFromMenuId(item.getItemId()));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void restoreFilterChecked(Menu menu, int itemId) {
        if (itemId != -1) {
            MenuItem item = menu.findItem(itemId);
            if (item == null) return;

            item.setChecked(true);
            filter = getFilterFromMenuId(itemId);
        }
    }

    private SearchEngine.Filter getFilterFromMenuId(int itemId) {
        switch (itemId) {
            case R.id.menu_filter_video:
                return SearchEngine.Filter.STREAM;
            case R.id.menu_filter_channel:
                return SearchEngine.Filter.CHANNEL;
            case R.id.menu_filter_playlist:
                return SearchEngine.Filter.PLAYLIST;
            case R.id.menu_filter_all:
            default:
                return SearchEngine.Filter.ANY;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private TextWatcher textWatcher;

    private void showSearchOnStart() {
        if (DEBUG) Log.d(TAG, "showSearchOnStart() called, searchQuery → " + searchQuery+", lastSearchedQuery → " + lastSearchedQuery);
        searchEditText.setText(searchQuery);

        if (TextUtils.isEmpty(searchQuery) || TextUtils.isEmpty(searchEditText.getText())) {
            searchToolbarContainer.setTranslationX(100);
            searchToolbarContainer.setAlpha(0f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
            searchToolbarContainer.animate().translationX(0).alpha(1f).setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            searchToolbarContainer.setTranslationX(0);
            searchToolbarContainer.setAlpha(1f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
        }
    }

    private void initSearchListeners() {
        if (DEBUG) Log.d(TAG, "initSearchListeners() called");
        searchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.d(TAG, "onClick() called with: v = [" + v + "]");
                if (TextUtils.isEmpty(searchEditText.getText())) {
                    NavigationHelper.gotoMainFragment(getFragmentManager());
                    return;
                }

                searchEditText.setText("");
                suggestionListAdapter.setItems(new ArrayList<SuggestionItem>());
                showKeyboardSearch();
            }
        });

        TooltipCompat.setTooltipText(searchClear, getString(R.string.clear));

        searchEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.d(TAG, "onClick() called with: v = [" + v + "]");
                if (isSuggestionsEnabled && errorPanelRoot.getVisibility() != View.VISIBLE) {
                    showSuggestionsPanel();
                }
            }
        });

        searchEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (DEBUG) Log.d(TAG, "onFocusChange() called with: v = [" + v + "], hasFocus = [" + hasFocus + "]");
                if (isSuggestionsEnabled && hasFocus && errorPanelRoot.getVisibility() != View.VISIBLE) {
                    showSuggestionsPanel();
                }
            }
        });

        suggestionListAdapter.setListener(new SuggestionListAdapter.OnSuggestionItemSelected() {
            @Override
            public void onSuggestionItemSelected(SuggestionItem item) {
                search(item.query);
                searchEditText.setText(item.query);
            }

            @Override
            public void onSuggestionItemInserted(SuggestionItem item) {
                searchEditText.setText(item.query);
                searchEditText.setSelection(searchEditText.getText().length());
            }

            @Override
            public void onSuggestionItemLongClick(SuggestionItem item) {
                if (item.fromHistory) showDeleteSuggestionDialog(item);
            }
        });

        if (textWatcher != null) searchEditText.removeTextChangedListener(textWatcher);
        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String newText = searchEditText.getText().toString();
                suggestionPublisher.onNext(newText);
            }
        };
        searchEditText.addTextChangedListener(textWatcher);
        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (DEBUG) {
                    Log.d(TAG, "onEditorAction() called with: v = [" + v + "], actionId = [" + actionId + "], event = [" + event + "]");
                }
                if (event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                    search(searchEditText.getText().toString());
                    return true;
                }
                return false;
            }
        });

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) initSuggestionObserver();
    }

    private void unsetSearchListeners() {
        if (DEBUG) Log.d(TAG, "unsetSearchListeners() called");
        searchClear.setOnClickListener(null);
        searchClear.setOnLongClickListener(null);
        searchEditText.setOnClickListener(null);
        searchEditText.setOnFocusChangeListener(null);
        searchEditText.setOnEditorActionListener(null);

        if (textWatcher != null) searchEditText.removeTextChangedListener(textWatcher);
        textWatcher = null;
    }

    private void showSuggestionsPanel() {
        if (DEBUG) Log.d(TAG, "showSuggestionsPanel() called");
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, true, 200);
    }

    private void hideSuggestionsPanel() {
        if (DEBUG) Log.d(TAG, "hideSuggestionsPanel() called");
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, false, 200);
    }

    private void showKeyboardSearch() {
        if (DEBUG) Log.d(TAG, "showKeyboardSearch() called");
        if (searchEditText == null) return;

        if (searchEditText.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboardSearch() {
        if (DEBUG) Log.d(TAG, "hideKeyboardSearch() called");
        if (searchEditText == null) return;

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        searchEditText.clearFocus();
    }

    private void showDeleteSuggestionDialog(final SuggestionItem item) {
        if (activity == null || historyRecordManager == null || suggestionPublisher == null ||
                searchEditText == null || disposables == null) return;
        final String query = item.query;
        new AlertDialog.Builder(activity)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    howManyDeleted -> suggestionPublisher
                                            .onNext(searchEditText.getText().toString()),
                                    throwable -> showSnackBarError(throwable,
                                            UserAction.SOMETHING_ELSE, "none",
                                            "Deleting item failed", R.string.general_error)
                            );
                    disposables.add(onDelete);
                })
                .show();
    }

    @Override
    public boolean onBackPressed() {
        if (suggestionsPanel.getVisibility() == View.VISIBLE && infoListAdapter.getItemsList().size() > 0 && !isLoading.get()) {
            hideSuggestionsPanel();
            hideKeyboardSearch();
            searchEditText.setText(lastSearchedQuery);
            return true;
        }
        return false;
    }

    public void giveSearchEditTextFocus() {
        showKeyboardSearch();
    }

    private void initSuggestionObserver() {
        if (DEBUG) Log.d(TAG, "initSuggestionObserver() called");
        if (suggestionDisposable != null) suggestionDisposable.dispose();

        final Observable<String> observable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
                .startWith(searchQuery != null ? searchQuery : "")
                .filter(query -> isSuggestionsEnabled);

        suggestionDisposable = observable
                .switchMap(query -> {
                    final Flowable<List<SearchHistoryEntry>> flowable = historyRecordManager
                            .getRelatedSearches(query, 3, 25);
                    final Observable<List<SuggestionItem>> local = flowable.toObservable()
                            .map(searchHistoryEntries -> {
                                List<SuggestionItem> result = new ArrayList<>();
                                for (SearchHistoryEntry entry : searchHistoryEntries)
                                    result.add(new SuggestionItem(true, entry.getSearch()));
                                return result;
                            });

                    if (query.length() < THRESHOLD_NETWORK_SUGGESTION) {
                        // Only pass through if the query length is equal or greater than THRESHOLD_NETWORK_SUGGESTION
                        return local.materialize();
                    }

                    final Observable<List<SuggestionItem>> network = ExtractorHelper
                            .suggestionsFor(serviceId, query, contentCountry)
                            .toObservable()
                            .map(strings -> {
                                List<SuggestionItem> result = new ArrayList<>();
                                for (String entry : strings) {
                                    result.add(new SuggestionItem(false, entry));
                                }
                                return result;
                            });

                    return Observable.zip(local, network, (localResult, networkResult) -> {
                        List<SuggestionItem> result = new ArrayList<>();
                        if (localResult.size() > 0) result.addAll(localResult);

                        // Remove duplicates
                        final Iterator<SuggestionItem> iterator = networkResult.iterator();
                        while (iterator.hasNext() && localResult.size() > 0) {
                            final SuggestionItem next = iterator.next();
                            for (SuggestionItem item : localResult) {
                                if (item.query.equals(next.query)) {
                                    iterator.remove();
                                    break;
                                }
                            }
                        }

                        if (networkResult.size() > 0) result.addAll(networkResult);
                        return result;
                    }).materialize();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(listNotification -> {
                    if (listNotification.isOnNext()) {
                        handleSuggestions(listNotification.getValue());
                    } else if (listNotification.isOnError()) {
                        Throwable error = listNotification.getError();
                        if (!ExtractorHelper.hasAssignableCauseThrowable(error,
                                IOException.class, SocketException.class,
                                InterruptedException.class, InterruptedIOException.class)) {
                            onSuggestionError(error);
                        }
                    }
                });
    }

    @Override
    protected void doInitialLoadLogic() {
        // no-op
    }

    private void search(final String query) {
        if (DEBUG) Log.d(TAG, "search() called with: query = [" + query + "]");
        if (query.isEmpty()) return;

        try {
            final StreamingService service = NewPipe.getServiceByUrl(query);
            if (service != null) {
                showLoading();
                disposables.add(Observable
                        .fromCallable(new Callable<Intent>() {
                            @Override
                            public Intent call() throws Exception {
                                return NavigationHelper.getIntentByLink(activity, service, query);
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<Intent>() {
                            @Override
                            public void accept(Intent intent) throws Exception {
                                getFragmentManager().popBackStackImmediate();
                                activity.startActivity(intent);
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                showError(getString(R.string.url_not_supported_toast), false);
                            }
                        }));
                return;
            }
        } catch (Exception e) {
            // Exception occurred, it's not a url
        }

        lastSearchedQuery = query;
        searchQuery = query;
        currentPage = 0;
        infoListAdapter.clearStreamItemList();
        hideSuggestionsPanel();
        hideKeyboardSearch();

        historyRecordManager.onSearched(serviceId, query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> {},
                        error -> showSnackBarError(error, UserAction.SEARCHED,
                                NewPipe.getNameOfService(serviceId), query, 0)
                );
        suggestionPublisher.onNext(query);
        startLoading(false);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);
        if (disposables != null) disposables.clear();
        if (searchDisposable != null) searchDisposable.dispose();
        searchDisposable = ExtractorHelper.searchFor(serviceId, searchQuery, currentPage, contentCountry, filter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((searchResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleResult, this::onError);
    }

    @Override
    protected void loadMoreItems() {
        isLoading.set(true);
        showListFooter(true);
        if (searchDisposable != null) searchDisposable.dispose();
        currentNextPage = currentPage + 1;
        searchDisposable = ExtractorHelper.getMoreSearchItems(serviceId, searchQuery, currentNextPage, contentCountry, filter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((nextItemsResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleNextItems, this::onError);
    }

    @Override
    protected boolean hasMoreItems() {
        // TODO: No way to tell if search has more items in the moment
        return true;
    }

    @Override
    protected void onItemSelected(InfoItem selectedItem) {
        super.onItemSelected(selectedItem);
        hideKeyboardSearch();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void changeFilter(MenuItem item, SearchEngine.Filter filter) {
        this.filter = filter;
        this.filterItemCheckedId = item.getItemId();
        item.setChecked(true);

        if (!TextUtils.isEmpty(searchQuery)) {
            search(searchQuery);
        }
    }

    private void setQuery(int serviceId, String searchQuery) {
        this.serviceId = serviceId;
        this.searchQuery = searchQuery;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    //////////////////////////////////////////////////////////////////////////*/

    public void handleSuggestions(@NonNull final List<SuggestionItem> suggestions) {
        if (DEBUG) Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]");
        suggestionsRecyclerView.smoothScrollToPosition(0);
        suggestionsRecyclerView.post(() -> suggestionListAdapter.setItems(suggestions));

        if (errorPanelRoot.getVisibility() == View.VISIBLE) {
            hideLoading();
        }
    }

    public void onSuggestionError(Throwable exception) {
        if (DEBUG) Log.d(TAG, "onSuggestionError() called with: exception = [" + exception + "]");
        if (super.onError(exception)) return;

        int errorId = exception instanceof ParsingException ? R.string.parsing_error : R.string.general_error;
        onUnrecoverableError(exception, UserAction.GET_SUGGESTIONS, NewPipe.getNameOfService(serviceId), searchQuery, errorId);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void hideLoading() {
        super.hideLoading();
        showListFooter(false);
    }

    @Override
    public void showError(String message, boolean showRetryButton) {
        super.showError(message, showRetryButton);
        hideSuggestionsPanel();
        hideKeyboardSearch();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull SearchResult result) {
        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.SEARCHED, NewPipe.getNameOfService(serviceId), searchQuery, 0);
        }

        lastSearchedQuery = searchQuery;

        if (infoListAdapter.getItemsList().size() == 0) {
            if (!result.getResults().isEmpty()) {
                infoListAdapter.addInfoItemList(result.getResults());
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
                return;
            }
        }

        super.handleResult(result);
    }

    @Override
    public void handleNextItems(ListExtractor.InfoItemsPage result) {
        showListFooter(false);
        currentPage = Integer.parseInt(result.getNextPageUrl());
        infoListAdapter.addInfoItemList(result.getItems());

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(), UserAction.SEARCHED, NewPipe.getNameOfService(serviceId)
                    , "\"" + searchQuery + "\" → page " + currentPage, 0);
        }
        super.handleNextItems(result);
    }

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        if (exception instanceof SearchEngine.NothingFoundException) {
            infoListAdapter.clearStreamItemList();
            showEmptyState();
        } else {
            int errorId = exception instanceof ParsingException ? R.string.parsing_error : R.string.general_error;
            onUnrecoverableError(exception, UserAction.SEARCHED, NewPipe.getNameOfService(serviceId), searchQuery, errorId);
        }

        return true;
    }
}
