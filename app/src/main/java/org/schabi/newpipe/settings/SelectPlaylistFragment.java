package org.schabi.newpipe.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import org.schabi.newpipe.databinding.ListPlaylistMiniItemBinding;
import org.schabi.newpipe.databinding.SelectPlaylistFragmentBinding;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.ErrorInfo;
import org.schabi.newpipe.report.UserAction;

import java.util.List;
import java.util.Vector;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;

public class SelectPlaylistFragment extends DialogFragment {
    /**
     * This contains the base display options for images.
     */
    private static final DisplayImageOptions DISPLAY_IMAGE_OPTIONS
            = new DisplayImageOptions.Builder().cacheInMemory(true).build();

    private final ImageLoader imageLoader = ImageLoader.getInstance();

    private OnSelectedListener onSelectedListener = null;

    private SelectPlaylistFragmentBinding binding;

    private Disposable disposable = null;

    private List<PlaylistLocalItem> playlists = new Vector<>();

    public void setOnSelectedListener(final OnSelectedListener listener) {
        onSelectedListener = listener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        binding = SelectPlaylistFragmentBinding.inflate(inflater, container, false);

        binding.itemsList.setLayoutManager(new LinearLayoutManager(getContext()));
        final SelectPlaylistAdapter playlistAdapter = new SelectPlaylistAdapter();
        binding.itemsList.setAdapter(playlistAdapter);

        loadPlaylists();
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and display playlists
    //////////////////////////////////////////////////////////////////////////*/

    private void loadPlaylists() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.itemsList.setVisibility(View.GONE);
        binding.emptyStateView.setVisibility(View.GONE);

        final AppDatabase database = NewPipeDatabase.getInstance(requireContext());
        final LocalPlaylistManager localPlaylistManager = new LocalPlaylistManager(database);
        final RemotePlaylistManager remotePlaylistManager = new RemotePlaylistManager(database);

        disposable = Flowable.combineLatest(localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(), PlaylistLocalItem::merge)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::displayPlaylists, this::onError);
    }

    private void displayPlaylists(final List<PlaylistLocalItem> newPlaylists) {
        playlists = newPlaylists;
        binding.progressBar.setVisibility(View.GONE);
        binding.emptyStateView.setVisibility(newPlaylists.isEmpty() ? View.VISIBLE : View.GONE);
        binding.itemsList.setVisibility(newPlaylists.isEmpty() ? View.GONE : View.VISIBLE);
    }

    protected void onError(final Throwable e) {
        final Activity activity = requireActivity();
        ErrorActivity.reportError(activity, e, activity.getClass(), null, ErrorInfo
                .make(UserAction.UI_ERROR, "none", "load_playlists", R.string.app_ui_crash));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    private void clickedItem(final int position) {
        if (onSelectedListener != null) {
            final LocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                onSelectedListener.onLocalPlaylistSelected(entry.uid, entry.name);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                onSelectedListener.onRemotePlaylistSelected(
                        entry.getServiceId(), entry.getUrl(), entry.getName());
            }
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedListener {
        void onLocalPlaylistSelected(long id, String name);
        void onRemotePlaylistSelected(int serviceId, String url, String name);
    }

    private class SelectPlaylistAdapter
            extends RecyclerView.Adapter<SelectPlaylistAdapter.SelectPlaylistItemHolder> {
        @NonNull
        @Override
        public SelectPlaylistItemHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                           final int viewType) {
            return new SelectPlaylistItemHolder(ListPlaylistMiniItemBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final SelectPlaylistItemHolder holder,
                                     final int position) {
            final PlaylistLocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);

                holder.binding.itemTitleView.setText(entry.name);
                holder.binding.getRoot().setOnClickListener(view -> clickedItem(position));
                imageLoader.displayImage(entry.thumbnailUrl, holder.binding.itemThumbnailView,
                        DISPLAY_IMAGE_OPTIONS);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);

                holder.binding.itemTitleView.setText(entry.getName());
                holder.binding.getRoot().setOnClickListener(view -> clickedItem(position));
                imageLoader.displayImage(entry.getThumbnailUrl(), holder.binding.itemThumbnailView,
                        DISPLAY_IMAGE_OPTIONS);
            }
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        public class SelectPlaylistItemHolder extends RecyclerView.ViewHolder {
            private final ListPlaylistMiniItemBinding binding;

            SelectPlaylistItemHolder(final ListPlaylistMiniItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
