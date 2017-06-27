package org.schabi.newpipe.fragments.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
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
import android.widget.Toast;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.fragments.BaseFragment;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.workers.SearchWorker;
import org.schabi.newpipe.workers.SuggestionWorker;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SearchFragment extends BaseFragment implements SuggestionWorker.OnSuggestionResult, SearchWorker.OnSearchResult {
    private final String TAG = "SearchFragment@" + Integer.toHexString(hashCode());
    // savedInstanceBundle arguments
    private static final String QUERY_KEY = "query_key";
    private static final String PAGE_NUMBER_KEY = "page_number_key";
    private static final String INFO_LIST_KEY = "info_list_key";
    private static final String WAS_LOADING_KEY = "was_loading_key";
    private static final String ERROR_KEY = "error_key";
    private static final String FILTER_CHECKED_ID_KEY = "filter_checked_id_key";

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private int filterItemCheckedId = -1;
    private EnumSet<SearchEngine.Filter> filter = EnumSet.of(SearchEngine.Filter.CHANNEL, SearchEngine.Filter.STREAM);

    private int serviceId = -1;
    private String searchQuery = "";
    private int pageNumber = 0;
    private boolean showSuggestions = true;

    private SearchWorker curSearchWorker;
    private SuggestionWorker curSuggestionWorker;
    private SuggestionListAdapter suggestionListAdapter;
    private InfoListAdapter infoListAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View searchToolbarContainer;
    private AutoCompleteTextView searchEditText;
    private View searchClear;

    private RecyclerView resultRecyclerView;

    /*////////////////////////////////////////////////////////////////////////*/

    public static SearchFragment getInstance(int serviceId, String query) {
        SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(serviceId, query);
        return searchFragment;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(QUERY_KEY);
            serviceId = savedInstanceState.getInt(Constants.KEY_SERVICE_ID, 0);
            pageNumber = savedInstanceState.getInt(PAGE_NUMBER_KEY, 0);
            wasLoading.set(savedInstanceState.getBoolean(WAS_LOADING_KEY, false));
            filterItemCheckedId = savedInstanceState.getInt(FILTER_CHECKED_ID_KEY, 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView() called with: inflater = [" + inflater + "], container = [" + container + "], savedInstanceState = [" + savedInstanceState + "]");
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        if (DEBUG) Log.d(TAG, "onViewCreated() called with: rootView = [" + rootView + "], savedInstanceState = [" + savedInstanceState + "]");

        if (savedInstanceState != null && savedInstanceState.getBoolean(ERROR_KEY, false)) {
            search(searchQuery, 0, true);
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume() called");
        if (wasLoading.getAndSet(false) && !TextUtils.isEmpty(searchQuery)) {
            if (pageNumber > 0) search(searchQuery, pageNumber);
            else search(searchQuery, 0, true);
        }

        showSuggestions = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(getString(R.string.show_search_suggestions_key), true);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (DEBUG) Log.d(TAG, "onStop() called");

        hideSoftKeyboard(searchEditText);

        wasLoading.set(curSearchWorker != null && curSearchWorker.isRunning());
        if (curSearchWorker != null && curSearchWorker.isRunning()) curSearchWorker.cancel();
        if (curSuggestionWorker != null && curSuggestionWorker.isRunning()) curSuggestionWorker.cancel();
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        unsetSearchListeners();

        resultRecyclerView.removeAllViews();

        searchToolbarContainer = null;
        searchEditText = null;
        searchClear = null;

        resultRecyclerView = null;

        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (DEBUG) Log.d(TAG, "onSaveInstanceState() called with: outState = [" + outState + "]");

        String query = searchEditText != null && !TextUtils.isEmpty(searchEditText.getText().toString())
                ? searchEditText.getText().toString() : searchQuery;
        outState.putString(QUERY_KEY, query);
        outState.putInt(Constants.KEY_SERVICE_ID, serviceId);
        outState.putInt(PAGE_NUMBER_KEY, pageNumber);
        outState.putSerializable(INFO_LIST_KEY, ((ArrayList<InfoItem>) infoListAdapter.getItemsList()));
        outState.putBoolean(WAS_LOADING_KEY, curSearchWorker != null && curSearchWorker.isRunning());

        if (errorPanel != null && errorPanel.getVisibility() == View.VISIBLE) outState.putBoolean(ERROR_KEY, true);
        if (filterItemCheckedId != -1) outState.putInt(FILTER_CHECKED_ID_KEY, filterItemCheckedId);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK && searchQuery.length() != 0) {
                    search(searchQuery, pageNumber, true);
                } else Log.e(TAG, "ReCaptcha failed");
                break;

            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        resultRecyclerView = ((RecyclerView) rootView.findViewById(R.id.result_list_view));
        resultRecyclerView.setLayoutManager(new LinearLayoutManager(activity));

        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(getActivity());
            if (savedInstanceState != null) {
                //noinspection unchecked
                ArrayList<InfoItem> serializable = (ArrayList<InfoItem>) savedInstanceState.getSerializable(INFO_LIST_KEY);
                infoListAdapter.addInfoItemList(serializable);
            }

            infoListAdapter.setFooter(activity.getLayoutInflater().inflate(R.layout.pignate_footer, resultRecyclerView, false));
            infoListAdapter.showFooter(false);
            infoListAdapter.setOnStreamInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
                @Override
                public void selected(int serviceId, String url, String title) {
                    NavigationHelper.openVideoDetailFragment(getFragmentManager(), serviceId, url, title);
                }
            });
            infoListAdapter.setOnChannelInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
                @Override
                public void selected(int serviceId, String url, String title) {
                    NavigationHelper.openChannelFragment(getFragmentManager(), serviceId, url, title);
                }
            });
        }

        resultRecyclerView.setAdapter(infoListAdapter);
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        resultRecyclerView.clearOnScrollListeners();
        resultRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                //check for scroll down
                if (dy > 0) {
                    LinearLayoutManager layoutManager = (LinearLayoutManager) resultRecyclerView.getLayoutManager();
                    visibleItemCount = resultRecyclerView.getLayoutManager().getChildCount();
                    totalItemCount = resultRecyclerView.getLayoutManager().getItemCount();
                    pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount && !isLoading.get()) {
                        pageNumber++;
                        recyclerView.post(new Runnable() {
                            @Override
                            public void run() {
                                infoListAdapter.showFooter(true);
                            }
                        });
                        search(searchQuery, pageNumber);
                    }
                }
            }
        });
    }

    @Override
    protected void reloadContent() {
        if (DEBUG) Log.d(TAG, "reloadContent() called");
        if (!TextUtils.isEmpty(searchQuery) || (searchEditText != null && !TextUtils.isEmpty(searchEditText.getText()))) {
            search(!TextUtils.isEmpty(searchQuery) ? searchQuery : searchEditText.getText().toString(), 0, true);
        } else {
            if (searchEditText != null) {
                searchEditText.setText("");
                showSoftKeyboard(searchEditText);
            }
            animateView(errorPanel, false, 200);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "], inflater = [" + inflater + "]");
        inflater.inflate(R.menu.search_menu, menu);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container);
        searchEditText = (AutoCompleteTextView) searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text);
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear);
        setupSearchView();

        restoreFilterChecked(menu, filterItemCheckedId);
    }

    private void restoreFilterChecked(Menu menu, int itemId) {
        if (itemId != -1) {
            MenuItem item = menu.findItem(itemId);
            if (item == null) return;

            item.setChecked(true);
            switch (itemId) {
                case R.id.menu_filter_all:
                    filter = EnumSet.of(SearchEngine.Filter.STREAM, SearchEngine.Filter.CHANNEL);
                    break;
                case R.id.menu_filter_video:
                    filter = EnumSet.of(SearchEngine.Filter.STREAM);
                    break;
                case R.id.menu_filter_channel:
                    filter = EnumSet.of(SearchEngine.Filter.CHANNEL);
                    break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DEBUG) Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");

        switch (item.getItemId()) {
            case R.id.menu_filter_all:
                changeFilter(item, EnumSet.of(SearchEngine.Filter.STREAM, SearchEngine.Filter.CHANNEL));
                return true;
            case R.id.menu_filter_video:
                changeFilter(item, EnumSet.of(SearchEngine.Filter.STREAM));
                return true;
            case R.id.menu_filter_channel:
                changeFilter(item, EnumSet.of(SearchEngine.Filter.CHANNEL));
                return true;
            default:
                return false;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private TextWatcher textWatcher;

    private void setupSearchView() {
        searchEditText.setText(searchQuery != null ? searchQuery : "");
        searchEditText.setHint(getString(R.string.search) + "...");
        ////searchEditText.setCursorVisible(true);

        suggestionListAdapter = new SuggestionListAdapter(activity);
        searchEditText.setAdapter(suggestionListAdapter);


        if (TextUtils.isEmpty(searchQuery) || TextUtils.isEmpty(searchEditText.getText())) {
            searchToolbarContainer.setTranslationX(100);
            searchToolbarContainer.setAlpha(0f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
            searchToolbarContainer.animate().translationX(0).alpha(1f).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            searchToolbarContainer.setTranslationX(0);
            searchToolbarContainer.setAlpha(1f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
        }

        //
        initSearchListeners();

        if (TextUtils.isEmpty(searchQuery)) showSoftKeyboard(searchEditText);
        else hideSoftKeyboard(searchEditText);

        if (!TextUtils.isEmpty(searchQuery) && searchQuery.length() > 2 && suggestionListAdapter != null && suggestionListAdapter.isEmpty()) {
            searchSuggestions(searchQuery);
        }
    }

    private void initSearchListeners() {
        searchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DEBUG) Log.d(TAG, "onClick() called with: v = [" + v + "]");
                if (TextUtils.isEmpty(searchEditText.getText())) {
                    NavigationHelper.openMainFragment(getFragmentManager());
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    searchEditText.setText("", false);
                } else searchEditText.setText("");
                suggestionListAdapter.updateAdapter(new ArrayList<String>());
                showSoftKeyboard(searchEditText);
            }
        });

        searchClear.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (DEBUG) Log.d(TAG, "onLongClick() called with: v = [" + v + "]");
                showMenuTooltip(v, getString(R.string.clear));
                return true;
            }
        });

        searchEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchEditText.showDropDown();
            }
        });

        searchEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) searchEditText.showDropDown();
            }
        });

        searchEditText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (DEBUG) Log.d(TAG, "onItemClick() called with: parent = [" + parent + "], view = [" + view + "], position = [" + position + "], id = [" + id + "]");
                String s = suggestionListAdapter.getSuggestion(position);
                if (DEBUG) Log.d(TAG, "onItemClick text = " + s);
                submitQuery(s);
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
                if (!TextUtils.isEmpty(newText) && newText.length() > 1) onQueryTextChange(newText);
            }
        };
        searchEditText.addTextChangedListener(textWatcher);

        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (DEBUG) Log.d(TAG, "onEditorAction() called with: v = [" + v + "], actionId = [" + actionId + "], event = [" + event + "]");
                if (event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                    submitQuery(searchEditText.getText().toString());
                    return true;
                }
                return false;
            }
        });
    }

    private void unsetSearchListeners() {
        searchClear.setOnClickListener(null);
        searchClear.setOnLongClickListener(null);
        if (textWatcher != null) searchEditText.removeTextChangedListener(textWatcher);
        searchEditText.setOnClickListener(null);
        searchEditText.setOnItemClickListener(null);
        searchEditText.setOnFocusChangeListener(null);
        searchEditText.setOnEditorActionListener(null);

        textWatcher = null;
    }

    public void showSoftKeyboard(View view) {
        if (DEBUG) Log.d(TAG, "showSoftKeyboard() called with: view = [" + view + "]");
        if (view == null) return;

        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideSoftKeyboard(View view) {
        if (DEBUG) Log.d(TAG, "hideSoftKeyboard() called with: view = [" + view + "]");
        if (view == null) return;

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

        view.clearFocus();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void changeFilter(MenuItem item, EnumSet<SearchEngine.Filter> filter) {
        this.filter = filter;
        this.filterItemCheckedId = item.getItemId();
        item.setChecked(true);
        if (searchQuery != null && !searchQuery.isEmpty()) search(searchQuery, 0, true);
    }

    public void submitQuery(String query) {
        if (DEBUG) Log.d(TAG, "submitQuery() called with: query = [" + query + "]");
        if (query.isEmpty()) return;
        search(query, 0, true);
        searchQuery = query;
    }

    public void onQueryTextChange(String newText) {
        if (DEBUG) Log.d(TAG, "onQueryTextChange() called with: newText = [" + newText + "]");
        if (!newText.isEmpty()) searchSuggestions(newText);
    }

    private void setQuery(int serviceId, String searchQuery) {
        this.serviceId = serviceId;
        this.searchQuery = searchQuery;
    }

    private void searchSuggestions(String query) {
        if (!showSuggestions) {
            if (DEBUG) Log.d(TAG, "searchSuggestions() showSuggestions is disabled");
            return;
        }

        if (DEBUG) Log.d(TAG, "searchSuggestions() called with: query = [" + query + "]");
        if (curSuggestionWorker != null && curSuggestionWorker.isRunning()) curSuggestionWorker.cancel();
        curSuggestionWorker = SuggestionWorker.startForQuery(activity, serviceId, query, this);
    }

    private void search(String query, int pageNumber) {
        if (DEBUG) Log.d(TAG, "search() called with: query = [" + query + "], pageNumber = [" + pageNumber + "]");
        search(query, pageNumber, false);
    }

    private void search(String query, int pageNumber, boolean clearList) {
        if (DEBUG) Log.d(TAG, "search() called with: query = [" + query + "], pageNumber = [" + pageNumber + "], clearList = [" + clearList + "]");
        isLoading.set(true);
        hideSoftKeyboard(searchEditText);

        searchQuery = query;
        this.pageNumber = pageNumber;

        if (clearList) {
            animateView(resultRecyclerView, false, 50);
            infoListAdapter.clearStreamItemList();
            infoListAdapter.showFooter(false);
            animateView(loadingProgressBar, true, 200);
        }
        animateView(errorPanel, false, 200);

        if (curSearchWorker != null && curSearchWorker.isRunning()) curSearchWorker.cancel();
        curSearchWorker = SearchWorker.startForQuery(activity, serviceId, query, pageNumber, filter, this);
    }

    protected void setErrorMessage(String message, boolean showRetryButton) {
        super.setErrorMessage(message, showRetryButton);

        animateView(resultRecyclerView, false, 400);
        hideSoftKeyboard(searchEditText);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnSuggestionResult
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onSuggestionResult(@NonNull List<String> suggestions) {
        if (DEBUG) Log.d(TAG, "onSuggestionResult() called with: suggestions = [" + suggestions + "]");
        suggestionListAdapter.updateAdapter(suggestions);
    }

    @Override
    public void onSuggestionError(int messageId) {
        if (DEBUG) Log.d(TAG, "onSuggestionError() called with: messageId = [" + messageId + "]");
        setErrorMessage(getString(messageId), true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // SearchWorkerResultListener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onSearchResult(SearchResult result) {
        if (DEBUG) Log.d(TAG, "onSearchResult() called with: result = [" + result + "]");
        infoListAdapter.addInfoItemList(result.resultList);
        animateView(resultRecyclerView, true, 400);
        animateView(loadingProgressBar, false, 200);
        isLoading.set(false);
    }

    @Override
    public void onNothingFound(String message) {
        if (DEBUG) Log.d(TAG, "onNothingFound() called with: messageId = [" + message + "]");
        setErrorMessage(message, false);
    }

    @Override
    public void onSearchError(int messageId) {
        if (DEBUG) Log.d(TAG, "onSearchError() called with: messageId = [" + messageId + "]");
        //Toast.makeText(getActivity(), messageId, Toast.LENGTH_LONG).show();
        setErrorMessage(getString(messageId), true);
    }

    @Override
    public void onReCaptchaChallenge() {
        if (DEBUG) Log.d(TAG, "onReCaptchaChallenge() called");
        Toast.makeText(getActivity(), R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
        setErrorMessage(getString(R.string.recaptcha_request_toast), false);

        // Starting ReCaptcha Challenge Activity
        startActivityForResult(new Intent(getActivity(), ReCaptchaActivity.class), ReCaptchaActivity.RECAPTCHA_REQUEST);
    }

}
