package org.schabi.newpipe.settings.tabs;

import static org.schabi.newpipe.settings.tabs.Tab.typeFrom;
import static org.schabi.newpipe.util.ServiceHelper.getNameOfServiceById;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.settings.SelectChannelFragment;
import org.schabi.newpipe.settings.SelectKioskFragment;
import org.schabi.newpipe.settings.SelectPlaylistFragment;
import org.schabi.newpipe.settings.SelectFeedGroupFragment;
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooseTabsFragment extends Fragment {
    private TabsManager tabsManager;

    private final List<Tab> tabList = new ArrayList<>();
    private ChooseTabsFragment.SelectedTabsAdapter selectedTabsAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tabsManager = TabsManager.getManager(requireContext());
        updateTabList();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_choose_tabs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initButton(rootView);

        final RecyclerView listSelectedTabs = rootView.findViewById(R.id.selectedTabs);
        listSelectedTabs.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listSelectedTabs);

        selectedTabsAdapter = new SelectedTabsAdapter(requireContext(), itemTouchHelper);
        listSelectedTabs.setAdapter(selectedTabsAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(),
                getString(R.string.main_page_content));
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChanges();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_chooser_fragment, menu);
        menu.findItem(R.id.menu_item_restore_default).setOnMenuItemClickListener(item -> {
            restoreDefaults();
            return true;
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateTabList() {
        tabList.clear();
        tabList.addAll(tabsManager.getTabs());
    }

    private void saveChanges() {
        tabsManager.saveTabs(tabList);
    }

    private void restoreDefaults() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    tabsManager.resetTabs();
                    updateTabList();
                    selectedTabsAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void initButton(final View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.addTabsButton);
        fab.setOnClickListener(v -> {
            final ChooseTabListItem[] availableTabs = getAvailableTabs(requireContext());

            if (availableTabs.length == 0) {
                //Toast.makeText(requireContext(), "No available tabs", Toast.LENGTH_SHORT).show();
                return;
            }

            final Dialog.OnClickListener actionListener = (dialog, which) -> {
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

    private void addTab(final int tabId) {
        final Tab.Type type = typeFrom(tabId);

        if (type == null) {
            ErrorUtil.showSnackbar(this,
                    new ErrorInfo(new IllegalStateException("Tab id not found: " + tabId),
                            UserAction.SOMETHING_ELSE, "Choosing tabs on settings"));
            return;
        }

        switch (type) {
            case KIOSK:
                final SelectKioskFragment selectKioskFragment = new SelectKioskFragment();
                selectKioskFragment.setOnSelectedListener((serviceId, kioskId, kioskName) ->
                        addTab(new Tab.KioskTab(serviceId, kioskId)));
                selectKioskFragment.show(getParentFragmentManager(), "select_kiosk");
                return;
            case CHANNEL:
                final SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
                selectChannelFragment.setOnSelectedListener((serviceId, url, name) ->
                        addTab(new Tab.ChannelTab(serviceId, url, name)));
                selectChannelFragment.show(getParentFragmentManager(), "select_channel");
                return;
            case PLAYLIST:
                final SelectPlaylistFragment selectPlaylistFragment = new SelectPlaylistFragment();
                selectPlaylistFragment.setOnSelectedListener(
                        new SelectPlaylistFragment.OnSelectedListener() {
                            @Override
                            public void onLocalPlaylistSelected(final long id, final String name) {
                                addTab(new Tab.PlaylistTab(id, name));
                            }

                            @Override
                            public void onRemotePlaylistSelected(
                                    final int serviceId, final String url, final String name) {
                                addTab(new Tab.PlaylistTab(serviceId, url, name));
                            }
                        });
                selectPlaylistFragment.show(getParentFragmentManager(), "select_playlist");
                return;
            case FEEDGROUP:
                final SelectFeedGroupFragment selectFeedGroupFragment =
                        new SelectFeedGroupFragment();
                selectFeedGroupFragment.setOnSelectedListener(
                        (groupId, name, iconId) ->
                                addTab(new Tab.FeedGroupTab(groupId, name, iconId)));
                selectFeedGroupFragment.show(getParentFragmentManager(), "select_feed_group");
                return;
            default:
                addTab(type.getTab());
                break;
        }
    }

    private ChooseTabListItem[] getAvailableTabs(final Context context) {
        final ArrayList<ChooseTabListItem> returnList = new ArrayList<>();

        for (final Tab.Type type : Tab.Type.values()) {
            final Tab tab = type.getTab();
            switch (type) {
                case BLANK:
                    if (!tabList.contains(tab)) {
                        returnList.add(new ChooseTabListItem(tab.getTabId(),
                                getString(R.string.blank_page_summary),
                                tab.getTabIconRes(context)));
                    }
                    break;
                case KIOSK:
                    returnList.add(new ChooseTabListItem(tab.getTabId(),
                            getString(R.string.kiosk_page_summary),
                            R.drawable.ic_whatshot));
                    break;
                case CHANNEL:
                    returnList.add(new ChooseTabListItem(tab.getTabId(),
                            getString(R.string.channel_page_summary),
                            tab.getTabIconRes(context)));
                    break;
                case DEFAULT_KIOSK:
                    if (!tabList.contains(tab)) {
                        returnList.add(new ChooseTabListItem(tab.getTabId(),
                                getString(R.string.default_kiosk_page_summary),
                                R.drawable.ic_whatshot));
                    }
                    break;
                case PLAYLIST:
                    returnList.add(new ChooseTabListItem(tab.getTabId(),
                            getString(R.string.playlist_page_summary),
                            tab.getTabIconRes(context)));
                    break;
                case FEEDGROUP:
                    returnList.add(new ChooseTabListItem(tab.getTabId(),
                            getString(R.string.feed_group_page_summary),
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

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(12,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder source,
                                  @NonNull final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || selectedTabsAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getBindingAdapterPosition();
                final int targetIndex = target.getBindingAdapterPosition();
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
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                final int position = viewHolder.getBindingAdapterPosition();
                tabList.remove(position);
                selectedTabsAdapter.notifyItemRemoved(position);

                if (tabList.isEmpty()) {
                    tabList.add(Tab.Type.BLANK.getTab());
                    selectedTabsAdapter.notifyItemInserted(0);
                }
            }
        };
    }

    private class SelectedTabsAdapter
            extends RecyclerView.Adapter<ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder> {
        private final LayoutInflater inflater;
        private final ItemTouchHelper itemTouchHelper;

        SelectedTabsAdapter(final Context context, final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        public void swapItems(final int fromPosition, final int toPosition) {
            Collections.swap(tabList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder onCreateViewHolder(
                @NonNull final ViewGroup parent, final int viewType) {
            final View view = inflater.inflate(R.layout.list_choose_tabs, parent, false);
            return new ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull final ChooseTabsFragment.SelectedTabsAdapter.TabViewHolder holder,
                final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return tabList.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {
            private final AppCompatImageView tabIconView;
            private final TextView tabNameView;
            private final ImageView handle;

            TabViewHolder(final View itemView) {
                super(itemView);

                tabNameView = itemView.findViewById(R.id.tabName);
                tabIconView = itemView.findViewById(R.id.tabIcon);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));

                final Tab tab = tabList.get(position);
                final Tab.Type type = Tab.typeFrom(tab.getTabId());

                if (type == null) {
                    return;
                }

                tabNameView.setText(getTabName(type, tab));
                tabIconView.setImageResource(tab.getTabIconRes(requireContext()));
            }

            private String getTabName(@NonNull final Tab.Type type, @NonNull final Tab tab) {
                switch (type) {
                    case BLANK:
                        return getString(R.string.blank_page_summary);
                    case DEFAULT_KIOSK:
                        return getString(R.string.default_kiosk_page_summary);
                    case KIOSK:
                        return getNameOfServiceById(((Tab.KioskTab) tab).getKioskServiceId())
                                + "/" + tab.getTabName(requireContext());
                    case CHANNEL:
                        return getNameOfServiceById(((Tab.ChannelTab) tab).getChannelServiceId())
                                + "/" + tab.getTabName(requireContext());
                    case PLAYLIST:
                        final int serviceId = ((Tab.PlaylistTab) tab).getPlaylistServiceId();
                        final String serviceName = serviceId == -1
                                ? getString(R.string.local)
                                : getNameOfServiceById(serviceId);
                        return serviceName + "/" + tab.getTabName(requireContext());
                    case FEEDGROUP:
                        return getString(R.string.feed_groups_header_title)
                                + "/" + ((Tab.FeedGroupTab) tab).getFeedGroupName();
                    default:
                        return tab.getTabName(requireContext());
                }
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
}
