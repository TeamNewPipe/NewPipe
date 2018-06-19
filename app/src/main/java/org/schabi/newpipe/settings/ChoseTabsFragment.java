package org.schabi.newpipe.settings;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChoseTabsFragment extends Fragment {

    public ChoseTabsFragment.SelectedTabsAdapter selectedTabsAdapter;

    RecyclerView selectedTabsView;

    List<String> selectedTabs = new ArrayList<>();
    private String saveString;
    public String[] availableTabs = new String[7];

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ((AppCompatActivity)getContext()).getSupportActionBar().setTitle(R.string.main_page_content);
        return inflater.inflate(R.layout.fragment_chose_tabs, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        tabNames();
        initUsedTabs();
        initButton(rootView);

        selectedTabsView = rootView.findViewById(R.id.usedTabs);
        selectedTabsView.setLayoutManager(new LinearLayoutManager(getContext()));
        selectedTabsAdapter = new ChoseTabsFragment.SelectedTabsAdapter();


        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(selectedTabsView);
        selectedTabsAdapter.setOnItemSelectedListener(itemTouchHelper);

        selectedTabsView.setAdapter(selectedTabsAdapter);
    }

    private void saveChanges() {
        StringBuilder save = new StringBuilder();
        if(selectedTabs.size()==0) {
            save = new StringBuilder("0");
        } else {
            for(String s: selectedTabs) {
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
        selectedTabs.addAll(Arrays.asList(tabs));
    }

    private void tabNames() {
        availableTabs[0] = getString(R.string.blank_page_summary);
        availableTabs[1] = getString(R.string.kiosk_page_summary);
        availableTabs[2] = getString(R.string.subscription_page_summary);
        availableTabs[3] = getString(R.string.feed_page_summary);
        availableTabs[4] = getString(R.string.tab_bookmarks);
        availableTabs[5] = getString(R.string.title_activity_history);
        availableTabs[6] = getString(R.string.channel_page_summary);
    }

    private void initButton(View rootView) {
        FloatingActionButton fab = rootView.findViewById(R.id.floatingActionButton);
        fab.setImageResource(ThemeHelper.getIconByAttr(R.attr.ic_add, getContext()));
        fab.setOnClickListener(v -> {
            Dialog.OnClickListener onClickListener = (dialog, which) -> addTab(which);

            new AddTabsDialog(getContext(),
                    getString(R.string.tab_chose),
                    availableTabs,
                    onClickListener)
                    .show();
        });

        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        int color = typedValue.data;
        fab.setBackgroundTintList(ColorStateList.valueOf(color));
    }


    private void addTab(int position) {
        if(position!=6) {
            selectedTabs.add(String.valueOf(position));
            selectedTabsAdapter.notifyDataSetChanged();
            saveChanges();
        } else {
            SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
            selectChannelFragment.setOnSelectedLisener((String url, String name, int service) -> {
                selectedTabs.add(position+"\t"+url+"\t"+name+"\t"+service);
                selectedTabsAdapter.notifyDataSetChanged();
                saveChanges();
            });
            selectChannelFragment.show(getFragmentManager(), "select_channel");
        }
    }

    public class SelectedTabsAdapter extends RecyclerView.Adapter<ChoseTabsFragment.SelectedTabsAdapter.TabViewHolder>{
        private ItemTouchHelper itemTouchHelper;

        public void setOnItemSelectedListener(ItemTouchHelper mItemTouchHelper) {
            itemTouchHelper = mItemTouchHelper;
        }

        public void swapItems(int fromPosition, int toPosition) {
            String temp = selectedTabs.get(fromPosition);
            selectedTabs.set(fromPosition, selectedTabs.get(toPosition));
            selectedTabs.set(toPosition, temp);
            notifyItemMoved(fromPosition, toPosition);
            saveChanges();
        }

        @Override
        public ChoseTabsFragment.SelectedTabsAdapter.TabViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            LayoutInflater inflater = LayoutInflater.from(getContext());
            View view = inflater.inflate(R.layout.viewholder_chose_tabs, parent, false);
            return new ChoseTabsFragment.SelectedTabsAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChoseTabsFragment.SelectedTabsAdapter.TabViewHolder holder, int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return selectedTabs.size();
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

            void bind(int position, ChoseTabsFragment.SelectedTabsAdapter.TabViewHolder holder) {

                handle.setImageResource(ThemeHelper.getIconByAttr(R.attr.drag_handle, getContext()));
                handle.setOnTouchListener(getOnTouchListener(holder));

                view.setOnLongClickListener(getOnLongClickListener(holder));

                if(selectedTabs.get(position).startsWith("6\t")) {
                    String channelInfo[] = selectedTabs.get(position).split("\t");
                    String channelName = "";
                    if(channelInfo.length==4)   channelName = channelInfo[2];
                    String textToSet = availableTabs[6]+": "+channelName;
                    text.setText(textToSet);
                } else {
                    text.setText(availableTabs[Integer.parseInt(selectedTabs.get(position))]);
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
                    selectedTabs.remove(position);
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
                        selectedTabsAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                selectedTabsAdapter.swapItems(sourceIndex, targetIndex);
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
