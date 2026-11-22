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
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.schabi.newpipe.learning.LearningMode;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.settings.SelectChannelFragment;
import org.schabi.newpipe.settings.SelectKioskFragment;
import org.schabi.newpipe.settings.SelectPlaylistFragment;
import org.schabi.newpipe.settings.SelectFeedGroupFragment;
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.Disposable;

public class ChooseTabsFragment extends Fragment {
    private TabsManager tabsManager;

    private final List<Tab> tabList = new ArrayList<>();
    private ChooseTabsFragment.SelectedTabsAdapter selectedTabsAdapter;
    private Disposable homeGroupIconSubscription;

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
        stopObservingGroupIcons();
        homeGroupIconSubscription = HomeTabGroupIconSync.observe(
                requireContext(), () -> tabList, () -> {
                    if (selectedTabsAdapter != null && getView() != null) {
                        selectedTabsAdapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void onPause() {
        stopObservingGroupIcons();
        super.onPause();
        saveChanges();
    }

    @Override
    public void onDestroyView() {
        stopObservingGroupIcons();
        super.onDestroyView();
    }

    private void stopObservingGroupIcons() {
        if (homeGroupIconSubscription != null) {
            homeGroupIconSubscription.dispose();
            homeGroupIconSubscription = null;
        }
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
        menu.findItem(R.id.menu_item_startup_tab).setOnMenuItemClickListener(item -> {
            chooseStartupTab();
            return true;
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void chooseStartupTab() {
        final String[] names = new String[tabList.size() + 1];
        names[0] = getString(R.string.home_startup_tab_first);
        for (int i = 0; i < tabList.size(); i++) {
            names[i + 1] = tabList.get(i).getTabName(requireContext());
        }
        final int selected = HomeStartupTab.preferredIndex(requireContext(), tabList) + 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.home_startup_tab_title)
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    HomeStartupTab.save(requireContext(),
                            which == 0 ? null : tabList.get(which - 1));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateTabList() {
        tabList.clear();
        tabList.addAll(tabsManager.getTabs());
    }

    private void saveChanges() {
        tabsManager.saveTabs(tabList);
    }

    private void restoreDefaults() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    for (final Tab.Type type : Tab.Type.values()) {
                        type.getTab().setCustomIconId(Tab.DEFAULT_ICON_ID);
                    }
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
                if (selected.tab != null) {
                    addTab(selected.tab);
                } else {
                    addTab(selected.tabId);
                }
            };

            SearchableTabChooser.show(requireContext(), availableTabs, actionListener);
        });
    }

    private void addTab(final Tab tab) {
        if (tabList.contains(tab)) {
            return;
        }
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
                final Tab tab = type.getTab();
                tab.setCustomIconId(Tab.DEFAULT_ICON_ID);
                addTab(tab);
                break;
        }
    }

    private ChooseTabListItem[] getAvailableTabs(final Context context) {
        final ArrayList<ChooseTabListItem> returnList = new ArrayList<>();

        final ArrayList<ChooseTabListItem> localSections = new ArrayList<>();
        addSingletonTabIfAvailable(context, localSections, Tab.Type.FEED);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.RECOMMENDATIONS);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.SUBSCRIPTIONS);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.BOOKMARKS);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.DOWNLOADS);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.LOCAL_MEDIA);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.HISTORY);
        addSingletonTabIfAvailable(context, localSections, Tab.Type.BLANK);
        appendGroup(returnList, R.string.add_tab_local_sections, localSections);

        final ArrayList<ChooseTabListItem> currentService = new ArrayList<>();
        addSingletonTabIfAvailable(context, currentService, Tab.Type.DEFAULT_KIOSK);
        addCurrentServiceKiosks(context, currentService);
        appendGroup(returnList, R.string.add_tab_current_service, currentService);

        final ArrayList<ChooseTabListItem> customContent = new ArrayList<>();
        if (LearningMode.isEnabled(context)) {
            addSingletonTabIfAvailable(context, customContent, Tab.Type.LEARNING);
        }
        addGenericTab(customContent, Tab.Type.KIOSK, getString(R.string.kiosk_page_summary),
                R.drawable.ic_whatshot);
        addGenericTab(customContent, Tab.Type.FEEDGROUP,
                getString(R.string.feed_group_page_summary),
                Tab.Type.FEEDGROUP.getTab().getTabIconRes(context));
        addGenericTab(customContent, Tab.Type.CHANNEL, getString(R.string.channel_page_summary),
                Tab.Type.CHANNEL.getTab().getTabIconRes(context));
        addGenericTab(customContent, Tab.Type.PLAYLIST, getString(R.string.playlist_page_summary),
                Tab.Type.PLAYLIST.getTab().getTabIconRes(context));
        appendGroup(returnList, R.string.add_tab_custom_content, customContent);

        return returnList.toArray(new ChooseTabListItem[0]);
    }

    private void appendGroup(final ArrayList<ChooseTabListItem> returnList,
                             final int headerStringRes,
                             final ArrayList<ChooseTabListItem> groupItems) {
        if (!groupItems.isEmpty()) {
            returnList.add(ChooseTabListItem.header(getString(headerStringRes)));
            returnList.addAll(groupItems);
        }
    }

    private void addSingletonTabIfAvailable(final Context context,
                                            final ArrayList<ChooseTabListItem> returnList,
                                            final Tab.Type type) {
        final Tab tab = type.getTab();
        if (tabList.contains(tab)) {
            return;
        }
        if (type == Tab.Type.BLANK) {
            returnList.add(new ChooseTabListItem(tab.getTabId(),
                    getString(R.string.blank_page_summary), tab.getTabIconRes(context)));
        } else if (type == Tab.Type.DEFAULT_KIOSK) {
            returnList.add(new ChooseTabListItem(tab.getTabId(),
                    getString(R.string.default_kiosk_page_summary),
                    getString(R.string.default_kiosk_follows_selected_service),
                    R.drawable.ic_whatshot));
        } else {
            returnList.add(new ChooseTabListItem(context, tab));
        }
    }

    private void addGenericTab(final ArrayList<ChooseTabListItem> returnList,
                               final Tab.Type type,
                               final String title,
                               final int icon) {
        returnList.add(new ChooseTabListItem(type.getTab().getTabId(), title, icon));
    }

    private void addCurrentServiceKiosks(final Context context,
                                         final ArrayList<ChooseTabListItem> returnList) {
        final int selectedServiceId = ServiceHelper.getSelectedServiceId(context);
        try {
            final StreamingService service = NewPipe.getService(selectedServiceId);
            final String defaultKioskId = service.getKioskList().getDefaultKioskId();
            final boolean defaultKioskSelected = tabList.contains(Tab.Type.DEFAULT_KIOSK.getTab());
            for (final String kioskId : service.getKioskList().getAvailableKiosks()) {
                if (defaultKioskSelected && kioskId.equals(defaultKioskId)) {
                    continue;
                }
                final Tab.KioskTab kioskTab = new Tab.KioskTab(selectedServiceId, kioskId);
                if (!tabList.contains(kioskTab)) {
                    returnList.add(new ChooseTabListItem(kioskTab,
                            KioskTranslator.getTranslatedKioskName(kioskId, context),
                            getNameOfServiceById(selectedServiceId),
                            KioskTranslator.getKioskIcon(kioskId)));
                }
            }
        } catch (final ExtractionException e) {
            ErrorUtil.showSnackbar(this, new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading current service kiosks for home customization"));
        }
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
            if (fromPosition < 0 || toPosition < 0
                    || fromPosition >= tabList.size() || toPosition >= tabList.size()) {
                return;
            }
            final Tab moved = tabList.remove(fromPosition);
            tabList.add(toPosition, moved);
            notifyItemMoved(fromPosition, toPosition);
        }

        private void removeItem(final int position) {
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            tabList.remove(position);
            notifyItemRemoved(position);
            if (tabList.isEmpty()) {
                tabList.add(Tab.Type.BLANK.getTab());
                notifyItemInserted(0);
            }
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
            private final ImageView remove;

            TabViewHolder(final View itemView) {
                super(itemView);

                tabNameView = itemView.findViewById(R.id.tabName);
                tabIconView = itemView.findViewById(R.id.tabIcon);
                handle = itemView.findViewById(R.id.handle);
                remove = itemView.findViewById(R.id.remove);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));
                handle.setOnClickListener(v -> showMoveDialog());
                remove.setOnClickListener(v -> removeItem(getBindingAdapterPosition()));

                final Tab tab = tabList.get(position);
                final Tab.Type type = Tab.typeFrom(tab.getTabId());

                if (type == null) {
                    return;
                }

                tabNameView.setText(getTabName(type, tab));
                tabIconView.setImageResource(tab.getTabIconRes(requireContext()));
                tabIconView.setContentDescription(getString(R.string.customize_tab_icon));
                tabIconView.setOnClickListener(v -> HomeTabIconPicker.show(
                        requireContext(), tab, () -> {
                            final int adapterPosition = getBindingAdapterPosition();
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                notifyItemChanged(adapterPosition);
                            }
                            saveChanges();
                        }));
            }

            private void showMoveDialog() {
                final int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || tabList.size() < 2) {
                    return;
                }
                final Tab selected = tabList.get(position);
                final List<String> labels = new ArrayList<>();
                final List<Integer> offsets = new ArrayList<>();
                if (position > 0) {
                    labels.add(getString(R.string.move_home_section_up));
                    offsets.add(-1);
                }
                if (position < tabList.size() - 1) {
                    labels.add(getString(R.string.move_home_section_down));
                    offsets.add(1);
                }
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(tabNameView.getText())
                        .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                            final int current = tabList.indexOf(selected);
                            if (current < 0) {
                                return;
                            }
                            swapItems(current, current + offsets.get(which));
                            saveChanges();
                            handle.post(handle::requestFocus);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
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

