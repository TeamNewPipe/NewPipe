package org.schabi.newpipe.frost;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.info_list.ItemViewMode;
import org.schabi.newpipe.local.LocalItemListAdapter;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Library overview backed by the same database and holders as History and Bookmarks. */
public class LibraryHubFragment extends Fragment {
    private final CompositeDisposable viewDisposables = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library_hub, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View root, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        final View.OnClickListener historyClick = v ->
                NavigationHelper.openStatisticFragment(getParentFragmentManager());
        final View.OnClickListener playlistsClick = v ->
                NavigationHelper.openBookmarksFragment(getParentFragmentManager());
        root.findViewById(R.id.hub_downloads).setOnClickListener(v ->
                NavigationHelper.openDownloads(requireActivity()));
        root.findViewById(R.id.hub_history).setOnClickListener(historyClick);
        root.findViewById(R.id.hub_history_all).setOnClickListener(historyClick);
        root.findViewById(R.id.hub_playlists).setOnClickListener(playlistsClick);
        root.findViewById(R.id.hub_playlists_all).setOnClickListener(playlistsClick);

        final RecyclerView recent = root.findViewById(R.id.hub_recent);
        final LocalItemListAdapter historyAdapter = new LocalItemListAdapter(requireContext()) {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                              final int viewType) {
                final RecyclerView.ViewHolder holder = super.onCreateViewHolder(parent, viewType);
                final ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
                params.width = DeviceUtils.dpToPx(240, parent.getContext());
                holder.itemView.setLayoutParams(params);
                return holder;
            }
        };
        historyAdapter.setItemViewMode(ItemViewMode.CARD);
        historyAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                final StreamEntity stream = ((StreamStatisticsEntry) selectedItem).getStreamEntity();
                NavigationHelper.openVideoDetailFragment(requireContext(), getParentFragmentManager(),
                        stream.getServiceId(), stream.getUrl(), stream.getTitle(), null, false);
            }
        });
        recent.setLayoutManager(new LinearLayoutManager(requireContext(),
                RecyclerView.HORIZONTAL, false));
        recent.setAdapter(historyAdapter);
        final TextView recentEmpty = root.findViewById(R.id.hub_recent_empty);
        viewDisposables.add(new HistoryRecordManager(requireContext()).getStreamStatistics()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(entries -> {
                    final List<StreamStatisticsEntry> unfinished = new ArrayList<>();
                    for (final StreamStatisticsEntry entry : entries) {
                        final long duration = entry.getStreamEntity().getDuration() * 1000;
                        if (entry.getProgressMillis() > 0 && duration > entry.getProgressMillis()) {
                            unfinished.add(entry);
                        }
                    }
                    unfinished.sort(Comparator.comparing(
                            StreamStatisticsEntry::getLatestAccessDate).reversed());
                    historyAdapter.clearStreamItemList();
                    historyAdapter.addItems(unfinished.subList(0, Math.min(10, unfinished.size())));
                    recent.setVisibility(unfinished.isEmpty() ? View.GONE : View.VISIBLE);
                    recentEmpty.setVisibility(unfinished.isEmpty() ? View.VISIBLE : View.GONE);
                }, error -> recentEmpty.setText(R.string.hub_loading_error)));

        final RecyclerView playlists = root.findViewById(R.id.hub_saved_playlists);
        final LocalItemListAdapter playlistAdapter = new LocalItemListAdapter(requireContext());
        playlistAdapter.setItemViewMode(ItemViewMode.LIST);
        playlistAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                final PlaylistMetadataEntry playlist = (PlaylistMetadataEntry) selectedItem;
                NavigationHelper.openLocalPlaylistFragment(getParentFragmentManager(),
                        playlist.getUid(), playlist.getOrderingName());
            }
        });
        playlists.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlists.setAdapter(playlistAdapter);
        final TextView playlistsEmpty = root.findViewById(R.id.hub_playlists_empty);
        viewDisposables.add(new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()))
                .getPlaylists().observeOn(AndroidSchedulers.mainThread())
                .subscribe(entries -> {
                    playlistAdapter.clearStreamItemList();
                    playlistAdapter.addItems(entries.subList(0, Math.min(8, entries.size())));
                    playlists.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
                    playlistsEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
                }, error -> playlistsEmpty.setText(R.string.hub_loading_error)));
    }

    @Override
    public void onDestroyView() {
        viewDisposables.clear();
        final View root = getView();
        if (root != null) {
            ((RecyclerView) root.findViewById(R.id.hub_recent)).setAdapter(null);
            ((RecyclerView) root.findViewById(R.id.hub_saved_playlists)).setAdapter(null);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        final ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(R.string.rail_library);
        }
    }
}
