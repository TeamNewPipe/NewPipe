package org.schabi.newpipe.local.bookmark;

import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public final class BookmarkFragment extends BaseLocalListFragment<List<PlaylistLocalItem>, Void> {
    @State
    protected Parcelable itemsListState;

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;

    public ArrayList<PlaylistMetadataEntry> checkedList = new ArrayList<PlaylistMetadataEntry>();
    public boolean merger = false;
    public TextView debugText;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (activity == null) {
            return;
        }
        final AppDatabase database = NewPipeDatabase.getInstance(activity);
        localPlaylistManager = new LocalPlaylistManager(database);
        remotePlaylistManager = new RemotePlaylistManager(database);
        disposables = new CompositeDisposable();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {

        if (!useAsFrontPage) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        debugText = activity.findViewById(R.id.debugSelect);

        final Button mergeAll = activity.findViewById(R.id.mergeButton);
        mergeAll.setOnClickListener(v -> {

            final ArrayList<StreamEntity> allStreams = new ArrayList<StreamEntity>();
            for (int i = 0; i < checkedList.size(); i++) {
                final List<StreamEntity> streamList = localPlaylistManager.getPlaylistStreamsEntity(
                        checkedList.get(i).uid).blockingFirst();
                allStreams.addAll(streamList);
            }

            showMergeDialog(allStreams);


        });
        final Button deleteAll = activity.findViewById(R.id.deleteButton);
        deleteAll.setOnClickListener(v -> {
            String names = "Delete the Following Playlists? : ";
            for (int i = 0; i < checkedList.size(); i++) {
                names = names + ", " + checkedList.get(i).name;
            }
            showDeleteDialog(names,
                    localPlaylistManager.deleteMultiPlaylists(checkedList));
            checkedList.clear();
        });
        final Button multiSelect = activity.findViewById(R.id.multiButton);
        multiSelect.setOnClickListener(v -> {
            if (multiSelect.getText().equals("select")) {
                merger = true;
                multiSelect.setText("deselect all");
                activity.findViewById(R.id.deleteButton).setVisibility(View.VISIBLE);
                activity.findViewById(R.id.mergeButton).setVisibility(View.VISIBLE);
            } else {
                merger = false;
                multiSelect.setText("select");
                activity.findViewById(R.id.deleteButton).setVisibility(View.INVISIBLE);
                activity.findViewById(R.id.mergeButton).setVisibility(View.INVISIBLE);
                checkedList.clear();
            }
            printSelectedList();
        });

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                final FragmentManager fragmentManager = getFM();

                if (selectedItem instanceof PlaylistMetadataEntry) {
                    final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                    if (merger) {
                        if (!checkedList.contains(entry)) {
                            checkedList.add(entry);
                        } else {
                            checkedList.remove(entry);
                        }
                        printSelectedList();
                    } else {
                        NavigationHelper.openLocalPlaylistFragment(fragmentManager, entry.uid,
                                entry.name);
                    }
                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            entry.getServiceId(),
                            entry.getUrl(),
                            entry.getName());
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistMetadataEntry) {
                    showLocalDialog((PlaylistMetadataEntry) selectedItem);
                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    showRemoteDeleteDialog((PlaylistRemoteEntity) selectedItem);
                }
            }

        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        Flowable.combineLatest(localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(), PlaylistLocalItem::merge)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistsSubscriber());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (disposables != null) {
            disposables.clear();
        }
        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }

        databaseSubscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) {
            disposables.dispose();
        }

        disposables = null;
        localPlaylistManager = null;
        remotePlaylistManager = null;
        itemsListState = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistLocalItem>> getPlaylistsSubscriber() {
        return new Subscriber<List<PlaylistLocalItem>>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();
                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<PlaylistLocalItem> subscriptions) {
                handleResult(subscriptions);
                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(new ErrorInfo(exception,
                        UserAction.REQUESTED_BOOKMARK, "Loading playlists"));
            }

            @Override
            public void onComplete() { }
        };
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistLocalItem> result) {
        super.handleResult(result);

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(result);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        hideLoading();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (disposables != null) {
            disposables.clear();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private void showRemoteDeleteDialog(final PlaylistRemoteEntity item) {
        showDeleteDialog(item.getName(), remotePlaylistManager.deletePlaylist(item.getUid()));
    }

    private void showLocalDialog(final PlaylistMetadataEntry selectedItem) {
        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setText(selectedItem.name);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.rename_playlist, (dialog, which) ->
                        changeLocalPlaylistName(
                                selectedItem.uid,
                                dialogBinding.dialogEditText.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.delete, (dialog, which) -> {
                    showDeleteDialog(selectedItem.name,
                            localPlaylistManager.deletePlaylist(selectedItem.uid));
                    dialog.dismiss();
                })
                .create()
                .show();
    }

    private void showMergeDialog(final ArrayList<StreamEntity> streams) {
        if (activity == null || disposables == null) {
            return;
        }
        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.getRoot().getContext().setTheme(ThemeHelper.getDialogTheme(requireContext()));
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.merge_playlists)
                .setMessage(R.string.delete_playlist_warning)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.merge, (dialog, i) -> {
        final String name = dialogBinding.dialogEditText.getText().toString();
        final Toast successToast = Toast.makeText(getActivity(),
                R.string.playlist_creation_success,
                Toast.LENGTH_SHORT);

        localPlaylistManager.createPlaylist(name, streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> successToast.show());
        localPlaylistManager.deleteMultiPlaylists(checkedList)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> { /*Do nothing on success*/ });

        deselectAll();
    }).show();
    }

    private void showDeleteDialog(final String name, final Single<Integer> deleteReactor) {
        if (activity == null || disposables == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) ->
                        disposables.add(deleteReactor
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(ignored -> { /*Do nothing on success*/
                                    deselectAll(); }, throwable ->
                                        showError(new ErrorInfo(throwable,
                                                UserAction.REQUESTED_BOOKMARK,
                                                "Deleting playlist")))))
                .setNegativeButton(R.string.cancel, null)
                .show();

    }

    private void changeLocalPlaylistName(final long id, final String name) {
        if (localPlaylistManager == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + id + "] "
                    + "with new name=[" + name + "] items");
        }

        localPlaylistManager.renamePlaylist(id, name);
        final Disposable disposable = localPlaylistManager.renamePlaylist(id, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> { /*Do nothing on success*/ }, throwable -> showError(
                        new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK,
                                "Changing playlist name")));
        disposables.add(disposable);
    }

    private void printSelectedList() {
        String names = "Selected: ";
        for (int i = 0; i < checkedList.size(); i++) {
            names = names + ", " + checkedList.get(i).name;
        }
        debugText.setText(names);
    }

    private void deselectAll() {
        merger = false;
        final Button multiSelect = activity.findViewById(R.id.multiButton);
        multiSelect.setText("select");
        activity.findViewById(R.id.deleteButton).setVisibility(View.INVISIBLE);
        activity.findViewById(R.id.mergeButton).setVisibility(View.INVISIBLE);
        checkedList.clear();
        printSelectedList();
    }
}

