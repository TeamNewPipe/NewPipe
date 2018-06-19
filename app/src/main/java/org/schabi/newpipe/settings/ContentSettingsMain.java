package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.local.holder.LocalPlaylistStreamItemHolder;
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
        initButton(rootView);

        usedTabsView = rootView.findViewById(R.id.usedTabs);
        usedTabsView.setLayoutManager(new LinearLayoutManager(getContext()));
        usedAdapter = new ContentSettingsMain.UsedAdapter();


        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(usedTabsView);
        usedAdapter.setOnItemSelectedListener(itemTouchHelper);

        usedTabsView.setAdapter(usedAdapter);
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

    private void tabNames() {
        allTabs[0] = getString(R.string.blank_page_summary);
        allTabs[1] = getString(R.string.kiosk_page_summary);
        allTabs[2] = getString(R.string.subscription_page_summary);
        allTabs[3] = getString(R.string.feed_page_summary);
        allTabs[4] = getString(R.string.tab_bookmarks);
        allTabs[5] = getString(R.string.title_activity_history);
        allTabs[6] = getString(R.string.channel_page_summary);
    }

    private void initButton(View rootView) {
        FloatingActionButton fab = rootView.findViewById(R.id.floatingActionButton);
        fab.setImageResource(ThemeHelper.getIconByAttr(R.attr.ic_add, getContext()));
        fab.setOnClickListener(v -> {
            ContentSettingsMainDialog contentSettingsMainDialog = new ContentSettingsMainDialog();
            contentSettingsMainDialog.setOnAddListener(ContentSettingsMain.this::addTab);
            contentSettingsMainDialog.show(getFragmentManager(), "select_channel");
        });

        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        int color = typedValue.data;
        fab.setBackgroundTintList(ColorStateList.valueOf(color));
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

    public class UsedAdapter extends RecyclerView.Adapter<ContentSettingsMain.UsedAdapter.TabViewHolder>{
        // ... code from gist

        private ItemTouchHelper itemTouchHelper;

        public void setOnItemSelectedListener(ItemTouchHelper mItemTouchHelper) {
            itemTouchHelper = mItemTouchHelper;
        }

        public void swapItems(int fromPosition, int toPosition) {
            String temp = usedTabs.get(fromPosition);
            usedTabs.set(fromPosition, usedTabs.get(toPosition));
            usedTabs.set(toPosition, temp);
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
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return usedTabs.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {

            TextView text;
            View view;
            CardView cardView;
            ImageView handle;

            public TabViewHolder(View itemView) {
                super(itemView);

                text = itemView.findViewById(R.id.tabName);
                cardView = itemView.findViewById(R.id.layoutCard);
                handle = itemView.findViewById(R.id.handle);
                view = itemView;
            }

            void bind(int position, ContentSettingsMain.UsedAdapter.TabViewHolder holder) {

                handle.setImageResource(ThemeHelper.getIconByAttr(R.attr.drag_handle, getContext()));
                handle.setOnTouchListener(getOnTouchListener(holder));

                view.setOnLongClickListener(getOnLongClickListener(holder));

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

            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    view.performClick();
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if(itemTouchHelper != null) itemTouchHelper.startDrag(item);
                    }
                    return false;
                };
            }

            private View.OnLongClickListener getOnLongClickListener(TabViewHolder holder) {
                return (view) -> {
                    int position = holder.getAdapterPosition();
                    usedTabs.remove(position);
                    notifyItemRemoved(position);
                    saveChanges();
                    return false;
                };
            }
        }
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public int interpolateOutOfBoundsScroll(RecyclerView recyclerView, int viewSize,
                                                    int viewSizeOutOfBounds, int totalSize,
                                                    long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(12,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source,
                                  RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType() ||
                        usedAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                usedAdapter.swapItems(sourceIndex, targetIndex);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
        };
    }
}
