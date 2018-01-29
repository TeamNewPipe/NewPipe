package org.schabi.newpipe.fragments.local;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.playlist.PlayQueueItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;

public final class PlaylistAppendDialog extends PlaylistDialog {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();

    private RecyclerView playlistRecyclerView;
    private LocalItemListAdapter playlistAdapter;

    public static PlaylistAppendDialog fromStreamInfo(final StreamInfo info) {
        PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setInfo(Collections.singletonList(new StreamEntity(info)));
        return dialog;
    }

    public static PlaylistAppendDialog fromStreamInfoItems(final List<StreamInfoItem> items) {
        PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        List<StreamEntity> entities = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            entities.add(new StreamEntity(item));
        }
        dialog.setInfo(entities);
        return dialog;
    }

    public static PlaylistAppendDialog fromPlayQueueItems(final List<PlayQueueItem> items) {
        PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        List<StreamEntity> entities = new ArrayList<>(items.size());
        for (final PlayQueueItem item : items) {
            entities.add(new StreamEntity(item));
        }
        dialog.setInfo(entities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        playlistAdapter = new LocalItemListAdapter(getActivity());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlists, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final View newPlaylistButton = view.findViewById(R.id.newPlaylist);
        playlistRecyclerView = view.findViewById(R.id.playlist_list);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        playlistRecyclerView.setAdapter(playlistAdapter);

        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(getContext()));

        newPlaylistButton.setOnClickListener(ignored -> openCreatePlaylistDialog());

        playlistAdapter.setSelectedListener(new OnLocalItemGesture<LocalItem>() {
            @Override
            public void selected(LocalItem selectedItem) {
                if (!(selectedItem instanceof PlaylistMetadataEntry) || getStreams() == null)
                    return;

                final long playlistId = ((PlaylistMetadataEntry) selectedItem).uid;
                final Toast successToast = Toast.makeText(getContext(),
                        R.string.playlist_add_stream_success, Toast.LENGTH_SHORT);

                playlistManager.appendToPlaylist(playlistId, getStreams())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(ignored -> successToast.show());

                getDialog().dismiss();
            }
        });

        playlistManager.getPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(metadataEntries -> {
                    if (metadataEntries.isEmpty()) {
                        openCreatePlaylistDialog();
                        return;
                    }

                    playlistAdapter.clearStreamItemList();
                    playlistAdapter.addInfoItemList(metadataEntries);
                    playlistRecyclerView.setVisibility(View.VISIBLE);
                });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    public void openCreatePlaylistDialog() {
        if (getStreams() == null || getFragmentManager() == null) return;

        PlaylistCreationDialog.newInstance(getStreams()).show(getFragmentManager(), TAG);
        getDialog().dismiss();
    }
}
