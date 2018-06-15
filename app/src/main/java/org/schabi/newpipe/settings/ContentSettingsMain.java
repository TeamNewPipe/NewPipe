package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ContentSettingsMain extends Fragment {

    public ContentSettingsMain.UsedAdapter usedAdapter;

    RecyclerView usedTabsView;

    List<String> usedTabs = new ArrayList<>();
    private String saveString;
    public String[] allTabs = new String[7];

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((AppCompatActivity)getContext()).getSupportActionBar().setTitle(R.string.main_page_content);
        return inflater.inflate(R.layout.dialog_contentsettings, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        tabNames();
        initUsedTabs();
        initAddButton(rootView);

        usedTabsView = rootView.findViewById(R.id.usedTabs);
        usedTabsView.setLayoutManager(new LinearLayoutManager(getContext()));
        usedAdapter = new ContentSettingsMain.UsedAdapter();
        usedTabsView.setAdapter(usedAdapter);
        usedTabsView.addItemDecoration(new ContentSettingsMain.DividerItemDecoration(getActivity()));

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(usedAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(usedTabsView);
    }

    private void saveChanges() {
        StringBuilder save = new StringBuilder();
        if(usedTabs.size()==0) {
            save = new StringBuilder("0");
        } else {
            for(String s: usedTabs) {
                save.append(s);
                save.append("\n");
            }
        }

        saveString = save.toString();
    }

    @Override
    public void onPause() {
        saveChanges();
        SharedPreferences sharedPreferences  = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("saveUsedTabs", saveString);
        editor.commit();
        super.onPause();
    }

    private void initUsedTabs() {
        String save = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext()).getString("saveUsedTabs", "1\n2\n4\n");
        String tabs[] = save.trim().split("\n");
        usedTabs.addAll(Arrays.asList(tabs));
    }

    private void initAddButton(View rootView) {
        Button addButton = rootView.findViewById(R.id.buttonAdd);
        addButton.setBackgroundResource(ThemeHelper.getIconByAttr(R.attr.ic_add, getActivity()));
        addButton.setOnClickListener(v -> {
            ContentSettingsMainDialog contentSettingsMainDialog = new ContentSettingsMainDialog();
            contentSettingsMainDialog.setOnAddListener((int position) -> {
                addTab(position);
            });
            contentSettingsMainDialog.show(getFragmentManager(), "select_channel");
        });
    }

    private void tabNames() {
        allTabs[0] = getString(R.string.blank_page_summary);
        allTabs[1] = getString(R.string.kiosk_page_summary);
        allTabs[2] = getString(R.string.subscription_page_summary);
        allTabs[3] = getString(R.string.feed_page_summary);
        allTabs[4] = getString(R.string.tab_bookmarks);
        allTabs[5] = getString(R.string.title_activity_history);
        allTabs[6] = getString(R.string.channel_page_summary);
    }

    private void addTab(int position) {
        if(position!=6) {
            usedTabs.add(String.valueOf(position));
            usedAdapter.notifyDataSetChanged();
            saveChanges();
        } else {
            SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
            selectChannelFragment.setOnSelectedLisener((String url, String name, int service) -> {
                usedTabs.add(position+"\t"+url+"\t"+name+"\t"+service);
                usedAdapter.notifyDataSetChanged();
                saveChanges();
            });
            selectChannelFragment.show(getFragmentManager(), "select_channel");
        }
    }

    public class UsedAdapter extends RecyclerView.Adapter<ContentSettingsMain.UsedAdapter.TabViewHolder>
            implements ItemTouchHelperAdapter {
        // ... code from gist
        @Override
        public void onItemDismiss(int position) {
            usedTabs.remove(position);
            notifyItemRemoved(position);
            saveChanges();
        }

        @Override
        public void onItemMove(int fromPosition, int toPosition) {
            if (fromPosition < toPosition) {
                for (int i = fromPosition; i < toPosition; i++) {
                    Collections.swap(usedTabs, i, i + 1);
                }
            } else {
                for (int i = fromPosition; i > toPosition; i--) {
                    Collections.swap(usedTabs, i, i - 1);
                }
            }
            notifyItemMoved(fromPosition, toPosition);
            saveChanges();
        }

        @Override
        public ContentSettingsMain.UsedAdapter.TabViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            LayoutInflater inflater = LayoutInflater.from(getContext());
            View view = inflater.inflate(R.layout.dialog_contentsettingtab, parent, false);
            return new ContentSettingsMain.UsedAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContentSettingsMain.UsedAdapter.TabViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return usedTabs.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {

            TextView text;
            Button remove;
            Button up;
            Button down;

            public TabViewHolder(View itemView) {
                super(itemView);

                text = itemView.findViewById(R.id.tabName);
            }

            void bind(int position) {
                if(usedTabs.get(position).startsWith("6\t")) {
                    String channelInfo[] = usedTabs.get(position).split("\t");
                    String channelName = "";
                    if(channelInfo.length==4)   channelName = channelInfo[2];
                    String textToSet = allTabs[6]+": "+channelName;
                    text.setText(textToSet);
                } else {
                    text.setText(allTabs[Integer.parseInt(usedTabs.get(position))]);
                }
            }
        }
    }

    public class DividerItemDecoration extends RecyclerView.ItemDecoration {

        private final int[] ATTRS = new int[]{android.R.attr.listDivider};

        private Drawable divider;

        public DividerItemDecoration(Context context) {
            final TypedArray styledAttributes = context.obtainStyledAttributes(ATTRS);
            divider = styledAttributes.getDrawable(0);
            styledAttributes.recycle();
        }


        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            int left = parent.getPaddingLeft();
            int right = parent.getWidth() - parent.getPaddingRight();

            int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);

                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();

                int top = child.getBottom() + params.bottomMargin;
                int bottom = top + divider.getIntrinsicHeight();

                divider.setBounds(left, top, right, bottom);
                divider.draw(c);
            }
        }
    }

    public class SimpleItemTouchHelperCallback extends ItemTouchHelper.Callback {

        private final ItemTouchHelperAdapter mAdapter;

        public SimpleItemTouchHelperCallback(ItemTouchHelperAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
            return makeMovementFlags(dragFlags, swipeFlags);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                              RecyclerView.ViewHolder target) {
            mAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            mAdapter.onItemDismiss(viewHolder.getAdapterPosition());
        }
    }

    public interface ItemTouchHelperAdapter {

        void onItemMove(int fromPosition, int toPosition);

        void onItemDismiss(int position);
    }
}
