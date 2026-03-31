package org.schabi.newpipe.fragments.list.channel;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.state.State;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ReadyChannelTabListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder;
import org.schabi.newpipe.player.playqueue.ChannelTabPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.ui.emptystate.EmptyStateUtil;
import org.schabi.newpipe.util.ChannelTabHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.PlayButtonHelper;

import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.local.history.HistoryRecordManager;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;

public class ChannelTabFragment extends BaseListInfoFragment<InfoItem, ChannelTabInfo>
        implements PlaylistControlViewHolder {

    // states must be protected and not private for State being able to access them
    @State
    protected ListLinkHandler tabHandler;
    @State
    protected String channelName;

    private PlaylistControlBinding playlistControlBinding;

    @NonNull
    public static ChannelTabFragment getInstance(final int serviceId,
                                                 final ListLinkHandler tabHandler,
                                                 final String channelName) {
        final ChannelTabFragment instance = new ChannelTabFragment();
        instance.serviceId = serviceId;
        instance.tabHandler = tabHandler;
        instance.channelName = channelName;
        return instance;
    }

    public ChannelTabFragment() {
        super(UserAction.REQUESTED_CHANNEL);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        EmptyStateUtil.setEmptyStateComposable(rootView.findViewById(R.id.empty_state_view));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistControlBinding = null;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            final MenuItem item = menu.add(Menu.NONE, R.id.menu_item_feed_toggle_played_items,
                    Menu.NONE, R.string.feed_show_hide_streams);
            item.setIcon(R.drawable.ic_visibility_on);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_feed_toggle_played_items) {
            showStreamVisibilityDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showStreamVisibilityDialog() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final String showWatchedKey = getString(R.string.feed_show_watched_items_key);
        final String showPartiallyWatchedKey =
                getString(R.string.feed_show_partially_watched_items_key);

        final String[] dialogItems = {
                getString(R.string.feed_show_watched),
                getString(R.string.feed_show_partially_watched)
        };

        final boolean[] checkedDialogItems = {
                prefs.getBoolean(showWatchedKey, true),
                prefs.getBoolean(showPartiallyWatchedKey, true)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.feed_hide_streams_title)
                .setMultiChoiceItems(dialogItems, checkedDialogItems,
                        (dialog, which, isChecked) -> checkedDialogItems[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    boolean changed = false;
                    final SharedPreferences.Editor editor = prefs.edit();

                    if (prefs.getBoolean(showWatchedKey, true) != checkedDialogItems[0]) {
                        editor.putBoolean(showWatchedKey, checkedDialogItems[0]);
                        changed = true;
                    }
                    if (prefs.getBoolean(showPartiallyWatchedKey, true) != checkedDialogItems[1]) {
                        editor.putBoolean(showPartiallyWatchedKey, checkedDialogItems[1]);
                        changed = true;
                    }

                    if (changed) {
                        editor.apply();
                        startLoading(true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected Supplier<View> getListHeaderSupplier() {
        if (ChannelTabHelper.isStreamsTab(tabHandler)) {
            playlistControlBinding = PlaylistControlBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);
            return playlistControlBinding::getRoot;
        }
        return null;
    }

    @Override
    protected Single<ChannelTabInfo> loadResult(final boolean forceLoad) {
        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean showPlayed = prefs.getBoolean(
                getString(R.string.feed_show_watched_items_key), true);
        final boolean showPartiallyPlayed = prefs.getBoolean(
                getString(R.string.feed_show_partially_watched_items_key), true);

        final HistoryRecordManager historyRecordManager = new HistoryRecordManager(
                requireContext().getApplicationContext());

        return ExtractorHelper.getChannelTab(serviceId, tabHandler, forceLoad)
                .map(info -> filterStreams(info, showPlayed, showPartiallyPlayed,
                        historyRecordManager));
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage<InfoItem>> loadMoreItemsLogic() {
        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean showPlayed = prefs.getBoolean(
                getString(R.string.feed_show_watched_items_key), true);
        final boolean showPartiallyPlayed = prefs.getBoolean(
                getString(R.string.feed_show_partially_watched_items_key), true);

        final HistoryRecordManager historyRecordManager = new HistoryRecordManager(
                requireContext().getApplicationContext());

        return ExtractorHelper.getMoreChannelTabItems(serviceId, tabHandler, currentNextPage)
                .map(page -> filterStreamsPage(page, showPlayed, showPartiallyPlayed,
                        historyRecordManager));
    }

    private boolean shouldIncludeItem(final InfoItem item,
                                      final boolean showPlayed,
                                      final boolean showPartiallyPlayed,
                                      final HistoryRecordManager historyRecordManager) {
        if (!(item instanceof StreamInfoItem)) {
            return true;
        }
        final StreamInfoItem streamItem = (StreamInfoItem) item;
        try {
            final StreamStateEntity[] result =
                    historyRecordManager.loadStreamState(streamItem).blockingGet();
            final StreamStateEntity state =
                    (result != null && result.length > 0) ? result[0] : null;
            if (state != null) {
                final long duration = streamItem.getDuration();
                final boolean isFinished = state.isFinished(duration);
                final boolean isPartiallyPlayed = state.isValid(duration) && !isFinished;

                if (!showPlayed && isFinished) {
                    return false;
                }
                if (!showPartiallyPlayed && isPartiallyPlayed) {
                    return false;
                }
            }
        } catch (final Exception e) {
            Log.w(TAG, "Could not load stream state", e);
        }
        return true;
    }

    private ChannelTabInfo filterStreams(final ChannelTabInfo info,
                                         final boolean showPlayed,
                                         final boolean showPartiallyPlayed,
                                         final HistoryRecordManager historyRecordManager) {
        if (!ChannelTabHelper.isStreamsTab(tabHandler)
                || (showPlayed && showPartiallyPlayed)) {
            return info;
        }

        final List<InfoItem> filteredItems = info.getRelatedItems().stream()
                .filter(item -> shouldIncludeItem(item, showPlayed,
                        showPartiallyPlayed, historyRecordManager))
                .collect(Collectors.toList());

        info.setRelatedItems(filteredItems);

        return info;
    }

    private ListExtractor.InfoItemsPage<InfoItem> filterStreamsPage(
            final ListExtractor.InfoItemsPage<InfoItem> page,
            final boolean showPlayed,
            final boolean showPartiallyPlayed,
            final HistoryRecordManager historyRecordManager) {
        if (!ChannelTabHelper.isStreamsTab(tabHandler)
                || (showPlayed && showPartiallyPlayed)) {
            return page;
        }

        final List<InfoItem> filtered = page.getItems().stream()
                .filter(item -> shouldIncludeItem(item, showPlayed,
                        showPartiallyPlayed, historyRecordManager))
                .collect(Collectors.toList());

        return new ListExtractor.InfoItemsPage<>(filtered, page.getNextPage(), page.getErrors());
    }

    @Override
    public void setTitle(final String title) {
        // The channel name is displayed as title in the toolbar.
        // The title is always a description of the content of the tab fragment.
        // It should be unique for each channel because multiple channel tabs
        // can be added to the main page. Therefore, the channel name is used.
        // Using the title variable would cause the title to be the same for all channel tabs.
        super.setTitle(channelName);
    }

    @Override
    public void handleResult(@NonNull final ChannelTabInfo result) {
        super.handleResult(result);

        // FIXME this is a really hacky workaround, to avoid storing useless data in the fragment
        //  state. The problem is, `ReadyChannelTabListLinkHandler` might contain raw JSON data that
        //  uses a lot of memory (e.g. ~800KB for YouTube). While 800KB doesn't seem much, if
        //  you combine just a couple of channel tab fragments you easily go over the 1MB
        //  save&restore transaction limit, and get `TransactionTooLargeException`s. A proper
        //  solution would require rethinking about `ReadyChannelTabListLinkHandler`s.
        if (tabHandler instanceof ReadyChannelTabListLinkHandler) {
            try {
                // once `handleResult` is called, the parsed data was already saved to cache, so
                // we can discard any raw data in ReadyChannelTabListLinkHandler and create a
                // link handler with identical properties, but without any raw data
                final ListLinkHandlerFactory channelTabLHFactory = result.getService()
                        .getChannelTabLHFactory();
                if (channelTabLHFactory != null) {
                    // some services do not not have a ChannelTabLHFactory
                    tabHandler = channelTabLHFactory.fromQuery(tabHandler.getId(),
                            tabHandler.getContentFilters(), tabHandler.getSortFilter());
                }
            } catch (final ParsingException e) {
                // silently ignore the error, as the app can continue to function normally
                Log.w(TAG, "Could not recreate channel tab handler", e);
            }
        }

        if (playlistControlBinding != null) {
            // PlaylistControls should be visible only if there is some item in
            // infoListAdapter other than header
            if (infoListAdapter.getItemCount() > 1) {
                playlistControlBinding.getRoot().setVisibility(View.VISIBLE);
            } else {
                playlistControlBinding.getRoot().setVisibility(View.GONE);
            }

            PlayButtonHelper.initPlaylistControlClickListener(
                    activity, playlistControlBinding, this);
        }
    }

    @Override
    public PlayQueue getPlayQueue() {
        final List<StreamInfoItem> streamItems = infoListAdapter.getItemsList().stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toList());

        return new ChannelTabPlayQueue(currentInfo.getServiceId(), tabHandler,
                currentInfo.getNextPage(), streamItems, 0);
    }
}
