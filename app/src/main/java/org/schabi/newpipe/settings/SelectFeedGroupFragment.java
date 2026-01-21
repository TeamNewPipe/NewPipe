package org.schabi.newpipe.settings;

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

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.error.ErrorUtil;

import java.util.List;
import java.util.Vector;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Christian Schabesberger on 26.09.17.
 * SelectChannelFragment.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class SelectFeedGroupFragment extends DialogFragment {
    private OnSelectedListener onSelectedListener;
    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;

    private List<FeedGroupEntity> feedGroups = new Vector<>();

    public void setOnSelectedListener(final OnSelectedListener listener) {
        onSelectedListener = listener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.select_feed_group_fragment, container, false);
        recyclerView = v.findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        final SelectFeedGroupAdapter feedGroupAdapter = new SelectFeedGroupAdapter();
        recyclerView.setAdapter(feedGroupAdapter);

        progressBar = v.findViewById(R.id.progressBar);
        emptyView = v.findViewById(R.id.empty_state_view);
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);


        final AppDatabase database = NewPipeDatabase.getInstance(requireContext());
        database.feedGroupDAO().getAll().toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getFeedGroupObserver());

        return v;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    private void clickedItem(final int position) {
        if (onSelectedListener != null) {
            final FeedGroupEntity entry = feedGroups.get(position);
            onSelectedListener
                    .onFeedGroupSelected(entry.getUid(), entry.getName(),
                            entry.getIcon().getDrawableResource());
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Item handling
    //////////////////////////////////////////////////////////////////////////*/

    private void displayFeedGroups(final List<FeedGroupEntity> newFeedGroups) {
        this.feedGroups = newFeedGroups;
        progressBar.setVisibility(View.GONE);
        if (newFeedGroups.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        recyclerView.setVisibility(View.VISIBLE);

    }

    private Observer<List<FeedGroupEntity>> getFeedGroupObserver() {
        return new Observer<List<FeedGroupEntity>>() {
            @Override
            public void onSubscribe(@NonNull final Disposable disposable) { }

            @Override
            public void onNext(@NonNull final List<FeedGroupEntity> newGroups) {
                displayFeedGroups(newGroups);
            }

            @Override
            public void onError(@NonNull final Throwable exception) {
                ErrorUtil.showUiErrorSnackbar(SelectFeedGroupFragment.this,
                        "Loading Feed Groups", exception);
            }

            @Override
            public void onComplete() { }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedListener {
        void onFeedGroupSelected(Long groupId, String name, int icon);
    }

    public interface OnCancelListener {
        void onCancel();
    }

    private final class SelectFeedGroupAdapter
            extends RecyclerView.Adapter<SelectFeedGroupAdapter.SelectFeedGroupItemHolder> {
        @NonNull
        @Override
        public SelectFeedGroupItemHolder onCreateViewHolder(final ViewGroup parent,
                                                          final int viewType) {
            final View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_feed_group_item, parent, false);
            return new SelectFeedGroupItemHolder(item);
        }

        @Override
        public void onBindViewHolder(final SelectFeedGroupItemHolder holder, final int position) {
            final FeedGroupEntity entry = feedGroups.get(position);
            holder.titleView.setText(entry.getName());
            holder.view.setOnClickListener(view -> clickedItem(position));
            holder.thumbnailView.setImageResource(entry.getIcon().getDrawableResource());
        }

        @Override
        public int getItemCount() {
            return feedGroups.size();
        }

        public class SelectFeedGroupItemHolder extends RecyclerView.ViewHolder {
            public final View view;
            final ImageView thumbnailView;
            final TextView titleView;
            SelectFeedGroupItemHolder(final View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
        }
    }
}
