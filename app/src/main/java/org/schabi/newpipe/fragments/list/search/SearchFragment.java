package org.schabi.newpipe.fragments.list.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
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
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.history.HistoryListener;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StateSaver;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.Notification;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SearchFragment extends BaseListFragment<SearchResult, ListExtractor.NextItemsResult> {

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * The suggestions will appear only if the query meet this threshold (>=).
     */
    private static final int THRESHOLD_SUGGESTION = 3;

    /**
     * How much time have to pass without emitting a item (i.e. the user stop typing) to fetch/show the suggestions, in milliseconds.
     */
    private static final int SUGGESTIONS_DEBOUNCE = 150; //ms

    @State
    protected int filterItemCheckedId = -1;
    private SearchEngine.Filter filter = SearchEngine.Filter.ANY;

    @State
    protected int serviceId = -1;
    @State
    protected String searchQuery = "";
    @State
    protected boolean wasSearchFocused = false;

    private int currentPage = 0;
    private int currentNextPage = 0;
    private String searchLanguage;
    private boolean showSuggestions = true;

    private PublishSubject<String> suggestionPublisher = PublishSubject.create();
    private Disposable searchDisposable;
    private Disposable suggestionWorkerDisposable;
    private CompositeDisposable disposables = new CompositeDisposable();

    private SuggestionListAdapter suggestionListAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View searchToolbarContainer;
    private AutoCompleteTextView searchEditText;
    private View searchClear;

    /*////////////////////////////////////////////////////////////////////////*/

    public static SearchFragment getInstance(int serviceId, String query) {
        SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(serviceId, query);
        searchFragment.searchOnResume();
        return searchFragment;
    }

    /**
     * Set wasLoading to true so when the fragment onResume is called, the initial search is done.
     * (it will only start searching if the query is not null or empty)
     */
    private void searchOnResume() {
        if (!TextUtils.isEmpty(searchQuery)) {
            wasLoading.set(true);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        suggestionListAdapter = new SuggestionListAdapter(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        wasSearchFocused = searchEditText.hasFocus();

        if (searchDisposable != null) searchDisposable.dispose();
        if (suggestionWorkerDisposable != null) suggestionWorkerDisposable.dispose();
        if (disposables != null) disposables.clear();
        hideSoftKeyboard(searchEditText);
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.d(TAG, "onResume() called");
        super.onResume();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        showSuggestions = preferences.getBoolean(getString(R.string.show_search_suggestions_key), true);
        searchLanguage = preferences.getString(getString(R.string.search_language_key), getString(R.string.default_language_value));

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

        if (suggestionWorkerDisposable == null || suggestionWorkerDisposable.isDisposed()) initSuggestionObserver();
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
        if (!activity.isChangingConfigurations()) StateSaver.onDestroy(savedState);

        if (searchDisposable != null) searchDisposable.dispose();
        if (suggestionWorkerDisposable != null) suggestionWorkerDisposable.dispose();
        if (disposables != null) disposables.clear();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK && searchQuery.length() != 0) {
                    search(searchQuery);
                } else Log.e(TAG, "ReCaptcha failed");
                break;

            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
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
        searchQuery = searchEditText != null && !TextUtils.isEmpty(searchEditText.getText().toString())
                ? searchEditText.getText().toString() : searchQuery;
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
                showSoftKeyboard(searchEditText);
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

        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container);
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text);
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear);
        setupSearchView();

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
            case R.id.menu_filter_all:
                return SearchEngine.Filter.ANY;
            case R.id.menu_filter_video:
                return SearchEngine.Filter.STREAM;
            case R.id.menu_filter_channel:
                return SearchEngine.Filter.CHANNEL;
            case R.id.menu_filter_playlist:
                return SearchEngine.Filter.PLAYLIST;
            default:
                return SearchEngine.Filter.ANY;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private TextWatcher textWatcher;

    private void setupSearchView() {
        searchEditText.setText(searchQuery != null ? searchQuery : "");
        searchEditText.setAdapter(suggestionListAdapter);

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

        initSearchListeners();

        if (TextUtils.isEmpty(searchQuery) || wasSearchFocused) showSoftKeyboard(searchEditText);
        else hideSoftKeyboard(searchEditText);
        wasSearchFocused = false;
    }

    private void initSearchListeners() {
        searchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.d(TAG, "onClick() called with: v = [" + v + "]");
                if (TextUtils.isEmpty(searchEditText.getText())) {
                    NavigationHelper.gotoMainFragment(getFragmentManager());
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    searchEditText.setText("", false);
                } else searchEditText.setText("");
                suggestionListAdapter.updateAdapter(new ArrayList<String>());
                showSoftKeyboard(searchEditText);
            }
        });

        TooltipCompat.setTooltipText(searchClear, getString(R.string.clear));

        searchEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.d(TAG, "onClick() called with: v = [" + v + "]");
                searchEditText.showDropDown();
            }
        });

        searchEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (DEBUG) Log.d(TAG, "onFocusChange() called with: v = [" + v + "], hasFocus = [" + hasFocus + "]");
                if (hasFocus) searchEditText.showDropDown();
            }
        });

        searchEditText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (DEBUG) {
                    Log.d(TAG, "onItemClick() called with: parent = [" + parent + "], view = [" + view + "], position = [" + position + "], id = [" + id + "]");
                }
                String s = suggestionListAdapter.getSuggestion(position);
                if (DEBUG) Log.d(TAG, "onItemClick text = " + s);
                submitQuery(s);
            }
        });
        searchEditText.setThreshold(THRESHOLD_SUGGESTION);

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
                if (!TextUtils.isEmpty(newText)) suggestionPublisher.onNext(newText);
            }
        };
        searchEditText.addTextChangedListener(textWatcher);

        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (DEBUG)
                    Log.d(TAG, "onEditorAction() called with: v = [" + v + "], actionId = [" + actionId + "], event = [" + event + "]");
                if (event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                    submitQuery(searchEditText.getText().toString());
                    return true;
                }
                return false;
            }
        });

        if (suggestionWorkerDisposable == null || suggestionWorkerDisposable.isDisposed()) initSuggestionObserver();
    }

    private void unsetSearchListeners() {
        searchClear.setOnClickListener(null);
        searchClear.setOnLongClickListener(null);
        searchEditText.setOnClickListener(null);
        searchEditText.setOnItemClickListener(null);
        searchEditText.setOnFocusChangeListener(null);
        searchEditText.setOnEditorActionListener(null);

        if (textWatcher != null) searchEditText.removeTextChangedListener(textWatcher);
        textWatcher = null;
    }

    private void showSoftKeyboard(View view) {
        if (DEBUG) Log.d(TAG, "showSoftKeyboard() called with: view = [" + view + "]");
        if (view == null) return;

        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSoftKeyboard(View view) {
        if (DEBUG) Log.d(TAG, "hideSoftKeyboard() called with: view = [" + view + "]");
        if (view == null) return;

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        view.clearFocus();
    }

    public void giveSearchEditTextFocus() {
        showSoftKeyboard(searchEditText);
    }

    private void initSuggestionObserver() {
        if (suggestionWorkerDisposable != null) suggestionWorkerDisposable.dispose();
        final Predicate<String> checkEnabledAndLength = new Predicate<String>() {
            @Override
            public boolean test(@io.reactivex.annotations.NonNull String s) throws Exception {
                boolean lengthCheck = s.length() >= THRESHOLD_SUGGESTION;
                // Clear the suggestions adapter if the length check fails
                if (!lengthCheck && !suggestionListAdapter.isEmpty()) {
                    suggestionListAdapter.updateAdapter(new ArrayList<String>());
                }
                // Only pass through if suggestions is enabled and the query length is equal or greater than THRESHOLD_SUGGESTION
                return showSuggestions && lengthCheck;
            }
        };

        suggestionWorkerDisposable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
                .startWith(!TextUtils.isEmpty(searchQuery) ? searchQuery : "")
                .filter(checkEnabledAndLength)
                .switchMap(new Function<String, Observable<Notification<List<String>>>>() {
                    @Override
                    public Observable<Notification<List<String>>> apply(@io.reactivex.annotations.NonNull String query) throws Exception {
                        return ExtractorHelper.suggestionsFor(serviceId, query, searchLanguage).toObservable().materialize();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Notification<List<String>>>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull Notification<List<String>> listNotification) throws Exception {
                        if (listNotification.isOnNext()) {
                            handleSuggestions(listNotification.getValue());
                            if (errorPanelRoot.getVisibility() == View.VISIBLE) {
                                hideLoading();
                            }
                        } else if (listNotification.isOnError()) {
                            Throwable error = listNotification.getError();
                            if (!ExtractorHelper.isInterruptedCaused(error)) {
                                onSuggestionError(error);
                            }
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
                                hideLoading();
                            }
                        }));
                return;
            }
        } catch (Exception e) {
            // Exception occurred, it's not a url
        }

        hideSoftKeyboard(searchEditText);
        this.searchQuery = query;
        this.currentPage = 0;
        infoListAdapter.clearStreamItemList();

        if (activity instanceof HistoryListener) {
            ((HistoryListener) activity).onSearch(serviceId, query);
        }

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        final String searchLanguageKey = getContext().getString(R.string.search_language_key);
        searchLanguage = sharedPreferences.getString(searchLanguageKey, getContext().getString(R.string.default_language_value));
        startLoading(false);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);
        if (disposables != null) disposables.clear();
        if (searchDisposable != null) searchDisposable.dispose();
        searchDisposable = ExtractorHelper.searchFor(serviceId, searchQuery, currentPage, searchLanguage, filter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<SearchResult>() {
                    @Override
                    public void accept(@NonNull SearchResult result) throws Exception {
                        isLoading.set(false);
                        handleResult(result);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        isLoading.set(false);
                        onError(throwable);
                    }
                });
    }

    @Override
    protected void loadMoreItems() {
        isLoading.set(true);
        showListFooter(true);
        if (searchDisposable != null) searchDisposable.dispose();
        currentNextPage = currentPage + 1;
        searchDisposable = ExtractorHelper.getMoreSearchItems(serviceId, searchQuery, currentNextPage, searchLanguage, filter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<ListExtractor.NextItemsResult>() {
                    @Override
                    public void accept(@NonNull ListExtractor.NextItemsResult result) throws Exception {
                        isLoading.set(false);
                        handleNextItems(result);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(@NonNull Throwable throwable) throws Exception {
                        isLoading.set(false);
                        onError(throwable);
                    }
                });
    }

    @Override
    protected boolean hasMoreItems() {
        // TODO: No way to tell if search has more items in the moment
        return true;
    }

    @Override
    protected void onItemSelected(InfoItem selectedItem) {
        super.onItemSelected(selectedItem);
        hideSoftKeyboard(searchEditText);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void changeFilter(MenuItem item, SearchEngine.Filter filter) {
        this.filter = filter;
        this.filterItemCheckedId = item.getItemId();
        item.setChecked(true);
        if (searchQuery != null && !searchQuery.isEmpty()) search(searchQuery);
    }

    private void submitQuery(String query) {
        if (DEBUG) Log.d(TAG, "submitQuery() called with: query = [" + query + "]");
        if (query.isEmpty()) return;
        search(query);
    }

    private void setQuery(int serviceId, String searchQuery) {
        this.serviceId = serviceId;
        this.searchQuery = searchQuery;
    }

    @Override
    public void showError(String message, boolean showRetryButton) {
        super.showError(message, showRetryButton);
        hideSoftKeyboard(searchEditText);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    //////////////////////////////////////////////////////////////////////////*/

    public void handleSuggestions(@NonNull List<String> suggestions) {
        if (DEBUG) Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]");
        suggestionListAdapter.updateAdapter(suggestions);
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

    /*//////////////////////////////////////////////////////////////////////////
    // Search Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull SearchResult result) {
        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.SEARCHED, NewPipe.getNameOfService(serviceId), searchQuery, 0);
        }

        if (infoListAdapter.getItemsList().size() == 0) {
            if (result.resultList.size() > 0) {
                infoListAdapter.addInfoItemList(result.resultList);
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
                return;
            }
        }

        super.handleResult(result);
    }

    @Override
    public void handleNextItems(ListExtractor.NextItemsResult result) {
        showListFooter(false);
        currentPage = Integer.parseInt(result.nextItemsUrl);
        infoListAdapter.addInfoItemList(result.nextItemsList);

        if (!result.errors.isEmpty()) {
            showSnackBarError(result.errors, UserAction.SEARCHED, NewPipe.getNameOfService(serviceId)
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
