package org.schabi.newpipe.settings;

import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xwray.groupie.GroupAdapter;
import com.xwray.groupie.GroupieViewHolder;
import com.xwray.groupie.Item;

import com.xwray.groupie.OnItemClickListener;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.local.subscription.SubscriptionViewModel;
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem;

import java.util.List;

public class SelectChannelGroupFragment extends DialogFragment {
    private OnSelectedListener onSelectedListener;
    private GroupAdapter<GroupieViewHolder> groupAdapter = new GroupAdapter<>();

    // Define the minimum item width in dp
    private static final int MIN_ITEM_WIDTH_DP = 80; // Adjust this value based on your item size
    private static final int MARGIN_DP = 8; // Margin between items in dp

    public interface OnSelectedListener {
        void onChannelGroupSelected(FeedGroupCardItem feedGroupCardItem);
    }

    public void setOnSelectedListener(OnSelectedListener listener) {
        this.onSelectedListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.select_channel_group_fragment, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.items_list);

        // Calculate dynamic column count
        int spanCount = calculateSpanCount();
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
        recyclerView.setLayoutManager(gridLayoutManager);

        // Add margin decoration
        int marginPx = dpToPx(MARGIN_DP);
        recyclerView.addItemDecoration(new GridSpacingItemDecoration(marginPx));
        recyclerView.setPadding(marginPx/2, marginPx/2, marginPx/2, marginPx/2);
        recyclerView.setClipToPadding(false);

        recyclerView.setAdapter(groupAdapter);

        // Use OnItemClickListener instead of OnClickGesture
        groupAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull Item item, @NonNull View view) {
                if (item instanceof FeedGroupCardItem && onSelectedListener != null) {
                    FeedGroupCardItem groupItem = (FeedGroupCardItem) item;
                    onSelectedListener.onChannelGroupSelected(groupItem);
                    dismiss();
                }
            }
        });

        SubscriptionViewModel viewModel = new ViewModelProvider(this)
                .get(SubscriptionViewModel.class);
        viewModel.getFeedGroupsLiveData().observe(this, this::handleFeedGroups);

        return view;
    }

    /**
     * Calculate the number of columns based on screen width and minimum item width
     */
    private int calculateSpanCount() {
        Resources resources = getResources();
        float screenWidthDp = resources.getConfiguration().screenWidthDp;
        int minItemWidthDp = MIN_ITEM_WIDTH_DP;

        // Calculate how many items can fit, with a minimum of 2 columns
        int spanCount = Math.max(3, (int) (screenWidthDp / minItemWidthDp));

        return spanCount;
    }

    /**
     * Convert dp to pixels
     */
    private int dpToPx(int dp) {
        return Math.round(getResources().getDisplayMetrics().density * dp);
    }

    /**
     * ItemDecoration to add spacing between grid items
     */
    /**
     * Simple symmetric ItemDecoration for grid
     */
    public static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int spacing;

        public GridSpacingItemDecoration(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int spanCount = ((GridLayoutManager) parent.getLayoutManager()).getSpanCount();

            // Add spacing to all sides except edges
            outRect.left = spacing / 2;
            outRect.right = spacing / 2;
            outRect.top = spacing / 2;
            outRect.bottom = spacing / 2;

            // Remove spacing from edges
            if (position % spanCount == 0) {
                outRect.left = 0; // First column
            }
            if (position % spanCount == spanCount - 1) {
                outRect.right = 0; // Last column
            }
            if (position < spanCount) {
                outRect.top = 0; // First row
            }
        }
    }


    private void handleFeedGroups(List<com.xwray.groupie.Group> groups) {
        groupAdapter.clear();

        // Add "All" group first
        groupAdapter.add(new FeedGroupCardItem(FeedGroupEntity.GROUP_ALL_ID,
                getString(R.string.all),
                org.schabi.newpipe.local.subscription.FeedGroupIcon.RSS));

        // Add other groups
        for (com.xwray.groupie.Group group : groups) {
            if (group instanceof FeedGroupCardItem) {
                groupAdapter.add(group);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
