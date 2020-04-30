package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.PlaylistItemsUtils;

import java.util.List;
import java.util.Vector;

import io.reactivex.Flowable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SelectPlaylistFragment extends DialogFragment {
    /**
     * This contains the base display options for images.
     */
    private static final DisplayImageOptions DISPLAY_IMAGE_OPTIONS
            = new DisplayImageOptions.Builder().cacheInMemory(true).build();

    private final ImageLoader imageLoader = ImageLoader.getInstance();

    private OnSelectedLisener onSelectedLisener = null;
    private OnCancelListener onCancelListener = null;

    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;

    private List<PlaylistLocalItem> playlists = new Vector<>();

    public void setOnSelectedLisener(final OnSelectedLisener listener) {
        onSelectedLisener = listener;
    }

    public void setOnCancelListener(final OnCancelListener listener) {
        onCancelListener = listener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.select_playlist_fragment, container, false);
        recyclerView = v.findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SelectPlaylistAdapter playlistAdapter = new SelectPlaylistAdapter();
        recyclerView.setAdapter(playlistAdapter);

        progressBar = v.findViewById(R.id.progressBar);
        emptyView = v.findViewById(R.id.empty_state_view);
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        final AppDatabase database = NewPipeDatabase.getInstance(this.getContext());
        LocalPlaylistManager localPlaylistManager = new LocalPlaylistManager(database);
        RemotePlaylistManager remotePlaylistManager = new RemotePlaylistManager(database);

        Flowable.combineLatest(localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(), PlaylistItemsUtils::merge)
                .toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistsObserver());

        return v;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCancel(final DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);
        if (onCancelListener != null) {
            onCancelListener.onCancel();
        }
    }

    private void clickedItem(final int position) {
        if (onSelectedLisener != null) {
            LocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                onSelectedLisener
                        .onLocalPlaylistSelected(entry.uid, entry.name);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                onSelectedLisener.onRemotePlaylistSelected(
                        entry.getServiceId(), entry.getUrl(), entry.getName());
            }
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Item handling
    //////////////////////////////////////////////////////////////////////////*/

    private void displayPlaylists(final List<PlaylistLocalItem> newPlaylists) {
        this.playlists = newPlaylists;
        progressBar.setVisibility(View.GONE);
        if (newPlaylists.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        recyclerView.setVisibility(View.VISIBLE);

    }

    private Observer<List<PlaylistLocalItem>> getPlaylistsObserver() {
        return new Observer<List<PlaylistLocalItem>>() {
            @Override
            public void onSubscribe(final Disposable d) { }

            @Override
            public void onNext(final List<PlaylistLocalItem> newPlaylists) {
                displayPlaylists(newPlaylists);
            }

            @Override
            public void onError(final Throwable exception) {
                SelectPlaylistFragment.this.onError(exception);
            }

            @Override
            public void onComplete() { }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected void onError(final Throwable e) {
        final Activity activity = getActivity();
        ErrorActivity.reportError(activity, e, activity.getClass(), null, ErrorActivity.ErrorInfo
                .make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedLisener {
        void onLocalPlaylistSelected(long id, String name);
        void onRemotePlaylistSelected(int serviceId, String url, String name);
    }

    public interface OnCancelListener {
        void onCancel();
    }

    private class SelectPlaylistAdapter
            extends RecyclerView.Adapter<SelectPlaylistAdapter.SelectPlaylistItemHolder> {
        @Override
        public SelectPlaylistItemHolder onCreateViewHolder(final ViewGroup parent,
                                                          final int viewType) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_playlist_mini_item, parent, false);
            return new SelectPlaylistItemHolder(item);
        }

        @Override
        public void onBindViewHolder(final SelectPlaylistItemHolder holder, final int position) {
            PlaylistLocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);

                holder.titleView.setText(entry.name);
                holder.view.setOnClickListener(view -> clickedItem(position));
                imageLoader.displayImage(entry.thumbnailUrl, holder.thumbnailView,
                        DISPLAY_IMAGE_OPTIONS);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);

                holder.titleView.setText(entry.getName());
                holder.view.setOnClickListener(view -> clickedItem(position));
                imageLoader.displayImage(entry.getThumbnailUrl(), holder.thumbnailView,
                        DISPLAY_IMAGE_OPTIONS);
            }
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        public class SelectPlaylistItemHolder extends RecyclerView.ViewHolder {
            public final View view;
            final ImageView thumbnailView;
            final TextView titleView;

            SelectPlaylistItemHolder(final View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
        }
    }
}
