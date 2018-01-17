package org.schabi.newpipe.fragments.local;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.info_list.stored.LocalPlaylistInfoItem;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;

public class PlaylistAppendDialog extends DialogFragment {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();
    private static final String INFO_KEY = "info_key";

    private StreamInfo streamInfo;

    private View newPlaylistButton;
    private RecyclerView playlistRecyclerView;
    private InfoListAdapter playlistAdapter;

    public static PlaylistAppendDialog newInstance(final StreamInfo info) {
        PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setInfo(info);
        return dialog;
    }

    private void setInfo(StreamInfo info) {
        this.streamInfo = info;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        playlistAdapter = new InfoListAdapter(getActivity());
        playlistAdapter.useMiniItemVariants(true);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            Serializable serial = savedInstanceState.getSerializable(INFO_KEY);
            if (serial instanceof StreamInfo) streamInfo = (StreamInfo) serial;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlists, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        newPlaylistButton = view.findViewById(R.id.newPlaylist);
        playlistRecyclerView = view.findViewById(R.id.playlist_list);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        playlistRecyclerView.setAdapter(playlistAdapter);

        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(getContext()));

        newPlaylistButton.setOnClickListener(ignored -> openCreatePlaylistDialog());

        playlistAdapter.setOnPlaylistSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener<PlaylistInfoItem>() {
            @Override
            public void selected(PlaylistInfoItem selectedItem) {
                if (!(selectedItem instanceof LocalPlaylistInfoItem)) return;
                final long playlistId = ((LocalPlaylistInfoItem) selectedItem).getPlaylistId();
                final Toast successToast =
                        Toast.makeText(getContext(), "Added", Toast.LENGTH_SHORT);

                playlistManager.appendToPlaylist(playlistId, new StreamEntity(streamInfo))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(ignored -> successToast.show());

                getDialog().dismiss();
            }

            @Override
            public void held(PlaylistInfoItem selectedItem) {}
        });

        playlistManager.getPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(metadataEntries -> {
                    if (metadataEntries.isEmpty()) {
                        openCreatePlaylistDialog();
                        return;
                    }

                    List<InfoItem> playlistInfoItems = new ArrayList<>(metadataEntries.size());
                    for (final PlaylistMetadataEntry metadataEntry : metadataEntries) {
                        playlistInfoItems.add(metadataEntry.toStoredPlaylistInfoItem());
                    }

                    playlistAdapter.clearStreamItemList();
                    playlistAdapter.addInfoItemList(playlistInfoItems);
                    playlistRecyclerView.setVisibility(View.VISIBLE);
                });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INFO_KEY, streamInfo);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    public void openCreatePlaylistDialog() {
        if (streamInfo == null || getFragmentManager() == null) return;

        PlaylistCreationDialog.newInstance(streamInfo).show(getFragmentManager(), TAG);
        getDialog().dismiss();
    }
}
