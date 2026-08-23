package org.schabi.newpipe.local.subscription.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.local.feed.FeedDatabaseManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class FeedGroupSelectionDialog extends DialogFragment {
    private static final String KEY_SUBSCRIPTION_ID = "subscription_id";

    private long subscriptionId;
    private List<FeedGroupEntity> allGroups = new ArrayList<>();
    private Set<Long> selectedGroupIds = new HashSet<>();
    private Set<Long> originalGroupIds = new HashSet<>();
    private GroupSelectionAdapter adapter;
    private CompositeDisposable disposables = new CompositeDisposable();
    private FeedDatabaseManager feedDatabaseManager;
    private boolean isDataLoaded = false;

    public static FeedGroupSelectionDialog newInstance(long subscriptionId) {
        FeedGroupSelectionDialog dialog = new FeedGroupSelectionDialog();
        Bundle args = new Bundle();
        args.putLong(KEY_SUBSCRIPTION_ID, subscriptionId);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        subscriptionId = getArguments().getLong(KEY_SUBSCRIPTION_ID);
        feedDatabaseManager = new FeedDatabaseManager(requireContext());

        // Initialize fresh state
        allGroups = new ArrayList<>();
        selectedGroupIds = new HashSet<>();
        originalGroupIds = new HashSet<>();
        isDataLoaded = false;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_feed_group_selection, null);

        setupRecyclerView(view);
        setupButtons(view);
        loadDataSynchronously();

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    private void setupRecyclerView(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.groups_recycler_view);
        adapter = new GroupSelectionAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupButtons(View view) {
        Button cancelButton = view.findViewById(R.id.cancel_button);
        Button saveButton = view.findViewById(R.id.save_button);

        cancelButton.setOnClickListener(v -> dismiss());
        saveButton.setOnClickListener(v -> saveChanges());
    }

    private void loadDataSynchronously() {
        // Load all data in a single chain to avoid race conditions
        Disposable loadDisposable = feedDatabaseManager.groups()
                .firstOrError()
                .flatMap(groups -> {
                    // Store groups
                    allGroups.clear();
                    allGroups.addAll(groups);

                    // Now load group memberships for each group
                    List<Single<GroupMembershipInfo>> membershipChecks = new ArrayList<>();

                    for (FeedGroupEntity group : groups) {
                        Single<GroupMembershipInfo> membershipCheck = feedDatabaseManager
                                .subscriptionIdsForGroup(group.getUid())
                                .firstOrError()
                                .map(subscriptionIds -> new GroupMembershipInfo(
                                        group.getUid(),
                                        subscriptionIds.contains(subscriptionId)
                                ));
                        membershipChecks.add(membershipCheck);
                    }

                    // Combine all membership checks
                    if (membershipChecks.isEmpty()) {
                        return Single.just(new ArrayList<GroupMembershipInfo>());
                    }

                    return Single.zip(membershipChecks, objects -> {
                        List<GroupMembershipInfo> results = new ArrayList<>();
                        for (Object obj : objects) {
                            results.add((GroupMembershipInfo) obj);
                        }
                        return results;
                    });
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        membershipInfos -> {
                            // Clear and populate selected groups
                            selectedGroupIds.clear();
                            originalGroupIds.clear();

                            for (GroupMembershipInfo info : membershipInfos) {
                                if (info.isMember) {
                                    selectedGroupIds.add(info.groupId);
                                    originalGroupIds.add(info.groupId);
                                }
                            }

                            isDataLoaded = true;
                            adapter.notifyDataSetChanged();
                        },
                        throwable -> {
                            // Handle error
                            isDataLoaded = true;
                            adapter.notifyDataSetChanged();
                        }
                );

        disposables.add(loadDisposable);
    }

    private void saveChanges() {
        if (!isDataLoaded) {
            return; // Don't save if data isn't loaded yet
        }

        // Find groups to add and remove
        Set<Long> toAdd = new HashSet<>(selectedGroupIds);
        toAdd.removeAll(originalGroupIds);

        Set<Long> toRemove = new HashSet<>(originalGroupIds);
        toRemove.removeAll(selectedGroupIds);

        List<Single<Void>> operations = new ArrayList<>();

        // Add to new groups
        for (Long groupId : toAdd) {
            Single<Void> addOperation = feedDatabaseManager.subscriptionIdsForGroup(groupId)
                    .firstOrError()
                    .flatMapCompletable(currentIds -> {
                        List<Long> newIds = new ArrayList<>(currentIds);
                        if (!newIds.contains(subscriptionId)) {
                            newIds.add(subscriptionId);
                        }
                        return feedDatabaseManager.updateSubscriptionsForGroup(groupId, newIds);
                    })
                    .toSingle(() -> (Void) null);

            operations.add(addOperation);
        }

        // Remove from old groups
        for (Long groupId : toRemove) {
            Single<Void> removeOperation = feedDatabaseManager.subscriptionIdsForGroup(groupId)
                    .firstOrError()
                    .flatMapCompletable(currentIds -> {
                        List<Long> newIds = new ArrayList<>(currentIds);
                        newIds.remove(subscriptionId);
                        return feedDatabaseManager.updateSubscriptionsForGroup(groupId, newIds);
                    })
                    .toSingle(() -> (Void) null);

            operations.add(removeOperation);
        }

        if (operations.isEmpty()) {
            dismiss();
            return;
        }

        // Execute all operations
        Disposable saveDisposable = Single.zip(operations, objects -> (Void) null)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> dismiss(),
                        throwable -> {
                            // Handle error but still dismiss
                            dismiss();
                        }
                );

        disposables.add(saveDisposable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    // Helper class to store group membership information
    private static class GroupMembershipInfo {
        final long groupId;
        final boolean isMember;

        GroupMembershipInfo(long groupId, boolean isMember) {
            this.groupId = groupId;
            this.isMember = isMember;
        }
    }

    private class GroupSelectionAdapter extends RecyclerView.Adapter<GroupViewHolder> {
        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_feed_group_selection, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            if (position < allGroups.size()) {
                FeedGroupEntity group = allGroups.get(position);
                holder.bind(group);
            }
        }

        @Override
        public int getItemCount() {
            return allGroups.size();
        }
    }

    private class GroupViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView groupName;
        private boolean isBinding = false;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.group_checkbox);
            groupName = itemView.findViewById(R.id.group_name);
        }

        public void bind(FeedGroupEntity group) {
            isBinding = true;

            groupName.setText(group.getName());

            // Set checkbox state without triggering listener
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(selectedGroupIds.contains(group.getUid()));

            isBinding = false;

            // Set up click listeners
            View.OnClickListener clickListener = v -> {
                if (isBinding || !isDataLoaded) return;

                boolean newState = !checkBox.isChecked();
                checkBox.setChecked(newState);
                updateGroupSelection(group.getUid(), newState);
            };

            itemView.setOnClickListener(clickListener);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isBinding || !isDataLoaded) return;
                updateGroupSelection(group.getUid(), isChecked);
            });
        }

        private void updateGroupSelection(long groupId, boolean isSelected) {
            if (isSelected) {
                selectedGroupIds.add(groupId);
            } else {
                selectedGroupIds.remove(groupId);
            }
        }
    }
}
