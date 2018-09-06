package org.schabi.newpipe.settings.tabs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SelectChannelFragment;
import org.schabi.newpipe.settings.SelectKioskFragment;
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.settings.tabs.Tab.typeFrom;

public class ChooseTabsFragment extends Fragment {

    private TabsManager tabsManager;
    private List<Tab> tabList = new ArrayList<>();
    public ChooseTabsFragment.SelectedTabsAdapter selectedTabsAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tabsManager = TabsManager.getManager(requireContext());
        updateTabList();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_choose_tabs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initButton(rootView);

        RecyclerView listSelectedTabs = rootView.findViewById(R.id.selectedTabs);
        listSelectedTabs.setLayoutManager(new LinearLayoutManager(requireContext()));

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listSelectedTabs);

        selectedTabsAdapter = new SelectedTabsAdapter(requireContext(), itemTouchHelper);
        listSelectedTabs.setAdapter(selectedTabsAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTitle();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChanges();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    private final int MENU_ITEM_RESTORE_ID = 123456;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        final MenuItem restoreItem = menu.add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE, R.string.restore_defaults);
        restoreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        final int restoreIcon = ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_restore_defaults);
        restoreItem.setIcon(AppCompatResources.getDrawable(requireContext(), restoreIcon));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_ITEM_RESTORE_ID) {
            restoreDefaults();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateTabList() {
        tabList.clear();
        tabList.addAll(tabsManager.getTabs());
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) actionBar.setTitle(R.string.main_page_content);
        }
    }

    private void saveChanges() {
        tabsManager.saveTabs(tabList);
    }

    private void restoreDefaults() {
        new AlertDialog.Builder(requireContext(), ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    tabsManager.resetTabs();
                    updateTabList();
                    selectedTabsAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void initButton(View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.addTabsButton);
        fab.setOnClickListener(v -> {
            final ChooseTabListItem[] availableTabs = getAvailableTabs(requireContext());

            if (availableTabs.length == 0) {
                //Toast.makeText(requireContext(), "No available tabs", Toast.LENGTH_SHORT).show();
                return;
            }

            Dialog.OnClickListener actionListener = (dialog, which) -> {
                final ChooseTabListItem selected = availableTabs[which];
                addTab(selected.tabId);
            };

            new AddTabDialog(requireContext(), availableTabs, actionListener)
                    .show();
        });
    }

    private void addTab(final Tab tab) {
        tabList.add(tab);
        selectedTabsAdapter.notifyDataSetChanged();
    }

    private void addTab(int tabId) {
        final Tab.Type type = typeFrom(tabId);

        if (type == null) {
            ErrorActivity.reportError(requireContext(), new IllegalStateException("Tab id not found: " + tabId), null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none", "Choosing tabs on settings", 0));
            return;
        }

        switch (type) {
            case KIOSK: {
                SelectKioskFragment selectFragment = new SelectKioskFragment();
                selectFragment.setOnSelectedLisener((serviceId, kioskId, kioskName) ->
                        addTab(new Tab.KioskTab(serviceId, kioskId)));
                selectFragment.show(requireFragmentManager(), "select_kiosk");
                return;
            }
            case CHANNEL: {
                SelectChannelFragment selectFragment = new SelectChannelFragment();
                selectFragment.setOnSelectedLisener((serviceId, url, name) ->
                        addTab(new Tab.ChannelTab(serviceId, url, name)));
                selectFragment.show(requireFragmentManager(), "select_channel");
                return;
            }
            default:
                addTab(type.getTab());
                break;
        }
    }

    public ChooseTabListItem[] getAvailableTabs(Context context) {
        final ArrayList<ChooseTabListItem> returnList = new ArrayList<>();

        for (Tab.Type type : Tab.Type.values()) {
            final Tab tab = type.getTab();
            switch (type) {
                case BLANK:
                    if (!tabList.contains(tab)) {
                        returnList.add(new ChooseTabListItem(tab.getTabId(), getString(R.string.blank_page_summary),
                                tab.getTabIconRes(context)));
                    }
                    break;
                case KIOSK:
                    returnList.add(new ChooseTabListItem(tab.getTabId(), getString(R.string.kiosk_page_summary),
                            ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_hot)));
                    break;
                case CHANNEL:
                    returnList.add(new ChooseTabListItem(tab.getTabId(), getString(R.string.channel_page_summary),
                            tab.getTabIconRes(context)));
                    break;
                default:
                    if (!tabList.contains(tab)) {
                        returnList.add(new ChooseTabListItem(context, tab));
                    }
                    break;
            }
        }

        return returnList.toArray(new ChooseTabListItem[0]);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

    private class SelectedTabsAdapter extends RecyclerView.Adapter<ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder> {
        private ItemTouchHelper itemTouchHelper;
        private final LayoutInflater inflater;

        SelectedTabsAdapter(Context context, ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        public void swapItems(int fromPosition, int toPosition) {
            Collections.swap(tabList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.list_choose_tabs, parent, false);
            return new ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder holder, int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return tabList.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {
            private AppCompatImageView tabIconView;
            private TextView tabNameView;
            private ImageView handle;

            TabViewHolder(View itemView) {
                super(itemView);

                tabNameView = itemView.findViewById(R.id.tabName);
                tabIconView = itemView.findViewById(R.id.tabIcon);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(int position, TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));

                final Tab tab = tabList.get(position);
                final Tab.Type type = Tab.typeFrom(tab.getTabId());

                if (type == null) {
                    return;
                }

                String tabName = tab.getTabName(requireContext());
                switch (type) {
                    case BLANK:
                        tabName = requireContext().getString(R.string.blank_page_summary);
                        break;
                    case KIOSK:
                        tabName = NewPipe.getNameOfService(((Tab.KioskTab) tab).getKioskServiceId()) + "/" + tabName;
                        break;
                    case CHANNEL:
                        tabName = NewPipe.getNameOfService(((Tab.ChannelTab) tab).getChannelServiceId()) + "/" + tabName;
                        break;
                }


                tabNameView.setText(tabName);
                tabIconView.setImageResource(tab.getTabIconRes(requireContext()));
            }

            @SuppressLint("ClickableViewAccessibility")
            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && getItemCount() > 1) {
                            itemTouchHelper.startDrag(item);
                            return true;
                        }
                    }
                    return false;
                };
            }
        }
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
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
                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                int position = viewHolder.getAdapterPosition();
                tabList.remove(position);
                selectedTabsAdapter.notifyItemRemoved(position);

                if (tabList.isEmpty()) {
                    tabList.add(Tab.Type.BLANK.getTab());
                    selectedTabsAdapter.notifyItemInserted(0);
                }
            }
        };
    }
}
