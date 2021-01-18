package org.schabi.newpipe.settings.tabs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentChooseTabsBinding;
import org.schabi.newpipe.databinding.ListChooseTabsBinding;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.ErrorInfo;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SelectChannelFragment;
import org.schabi.newpipe.settings.SelectKioskFragment;
import org.schabi.newpipe.settings.SelectPlaylistFragment;
import org.schabi.newpipe.settings.tabs.AddTabDialog.ChooseTabListItem;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.settings.tabs.Tab.typeFrom;

public class ChooseTabsFragment extends Fragment {
    private static final int MENU_ITEM_RESTORE_ID = 123456;

    private TabsManager tabsManager;

    private final List<Tab> tabList = new ArrayList<>();
    private ChooseTabsFragment.SelectedTabsAdapter selectedTabsAdapter;

    private FragmentChooseTabsBinding binding;

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
        binding = FragmentChooseTabsBinding.bind(rootView);

        initButton();

        binding.selectedTabs.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(binding.selectedTabs);

        selectedTabsAdapter = new SelectedTabsAdapter(requireContext(), itemTouchHelper);
        binding.selectedTabs.setAdapter(selectedTabsAdapter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
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

        final MenuItem restoreItem = menu.add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE,
                R.string.restore_defaults);
        restoreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        final int restoreIcon = ThemeHelper.resolveResourceIdFromAttr(requireContext(),
                R.attr.ic_restore_defaults);
        restoreItem.setIcon(AppCompatResources.getDrawable(requireContext(), restoreIcon));
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
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

    private void initButton() {
        binding.addTabsButton.setOnClickListener(v -> {
            final ChooseTabListItem[] availableTabs = getAvailableTabs(requireContext());

            if (availableTabs.length == 0) {
                return;
            }

            final DialogInterface.OnClickListener actionListener = (dialog, which) -> {
                final ChooseTabListItem selected = availableTabs[which];
                addTab(selected.tabId);
            };

            new AddTabDialog(requireContext(), availableTabs, actionListener).show();
        });
    }

    private void addTab(final Tab tab) {
        tabList.add(tab);
        selectedTabsAdapter.notifyDataSetChanged();
    }

    private void addTab(final int tabId) {
        final Tab.Type type = typeFrom(tabId);

        if (type == null) {
            ErrorActivity.reportError(requireContext(),
                    new IllegalStateException("Tab id not found: " + tabId), null, null,
                    ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Choosing tabs on settings", 0));
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
                            ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_kiosk_hot)));
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
                                ThemeHelper.resolveResourceIdFromAttr(context,
                                        R.attr.ic_kiosk_hot)));
                    }
                    break;
                case PLAYLIST:
                    returnList.add(new ChooseTabListItem(tab.getTabId(),
                            getString(R.string.playlist_page_summary),
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
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                final int position = viewHolder.getAdapterPosition();
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
            return new ChooseTabsFragment.SelectedTabsAdapter
                    .TabViewHolder(ListChooseTabsBinding.inflate(inflater, parent, false));
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
            private final ListChooseTabsBinding binding;

            TabViewHolder(final ListChooseTabsBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final TabViewHolder holder) {
                binding.handle.setOnTouchListener(getOnTouchListener(holder));

                final Tab tab = tabList.get(position);
                final Tab.Type type = Tab.typeFrom(tab.getTabId());

                if (type == null) {
                    return;
                }

                final String tabName;
                switch (type) {
                    case BLANK:
                        tabName = getString(R.string.blank_page_summary);
                        break;
                    case DEFAULT_KIOSK:
                        tabName = getString(R.string.default_kiosk_page_summary);
                        break;
                    case KIOSK:
                        tabName = NewPipe.getNameOfService(((Tab.KioskTab) tab)
                                .getKioskServiceId()) + "/" + tab.getTabName(requireContext());
                        break;
                    case CHANNEL:
                        tabName = NewPipe.getNameOfService(((Tab.ChannelTab) tab)
                                .getChannelServiceId()) + "/" + tab.getTabName(requireContext());
                        break;
                    case PLAYLIST:
                        final int serviceId = ((Tab.PlaylistTab) tab).getPlaylistServiceId();
                        final String serviceName = serviceId == -1
                                ? getString(R.string.local)
                                : NewPipe.getNameOfService(serviceId);
                        tabName = serviceName + "/" + tab.getTabName(requireContext());
                        break;
                    default:
                        tabName = tab.getTabName(requireContext());
                        break;
                }

                binding.tabName.setText(tabName);
                binding.tabIcon.setImageResource(tab.getTabIconRes(requireContext()));
            }

            @SuppressLint("ClickableViewAccessibility")
            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN
                            && itemTouchHelper != null && getItemCount() > 1) {
                        itemTouchHelper.startDrag(item);
                        return true;
                    }
                    return false;
                };
            }
        }
    }
}
