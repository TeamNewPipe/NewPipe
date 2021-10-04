package org.schabi.newpipe.settings.drawer_items;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.settings.SelectChannelFragment;
import org.schabi.newpipe.settings.SelectKioskFragment;
import org.schabi.newpipe.settings.SelectPlaylistFragment;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooseDrawerItemsFragment extends Fragment {
    private static final int MENU_ITEM_RESTORE_ID = 123456;

    private DrawerItemManager drawerItemManager;

    private final List<DrawerItem> drawerItemList = new ArrayList<>();
    private SelectedDrawerItemsAdapter selectedDrawerItemsAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        drawerItemManager = DrawerItemManager.getManager(requireContext());
        updateDrawerItemList();

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

        final RecyclerView listSelectedDrawerItems = rootView.findViewById(R.id.selectedTabs);
        listSelectedDrawerItems.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listSelectedDrawerItems);

        selectedDrawerItemsAdapter =
                new SelectedDrawerItemsAdapter(requireContext(), itemTouchHelper);
        listSelectedDrawerItems.setAdapter(selectedDrawerItemsAdapter);
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

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        final MenuItem restoreItem = menu.add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE,
                R.string.restore_defaults);
        restoreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        restoreItem.setIcon(AppCompatResources.getDrawable(requireContext(),
                R.drawable.ic_settings_backup_restore));
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

    private void updateDrawerItemList() {
        drawerItemList.clear();
        drawerItemList.addAll(drawerItemManager.getDrawerItems());
    }

    private void initButton(final View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.addTabsButton);
        fab.setOnClickListener(v -> {
            final AddDrawerItemDialog.ChooseDrawerItemListItem[] availabledrawerItems =
                    getAvailableDrawerItems(requireContext());

            if (availabledrawerItems.length == 0) {
                return;
            }

            final Dialog.OnClickListener actionListener = ((dialog, which) -> {
               final AddDrawerItemDialog.ChooseDrawerItemListItem selected
                       = availabledrawerItems[which];
               addDrawerItem(selected.drawerItemId);
            });

            new AddDrawerItemDialog(requireContext(), availabledrawerItems, actionListener).show();
        });
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.panel_content);
            }
        }
    }

    private void restoreDefaults() {
        new AlertDialog.Builder(requireContext(), ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    drawerItemManager.resetDrawerItems();
                    updateDrawerItemList();
                    selectedDrawerItemsAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void addDrawerItem(final DrawerItem drawerItem) {
        if (drawerItemList.get(0).getDrawerItemId() == DrawerItem.ITEM_ID_BLANK) {
            drawerItemList.remove(0);
        }
        drawerItemList.add(drawerItem);
        selectedDrawerItemsAdapter.notifyDataSetChanged();
    }

    private void addDrawerItem(final int drawerItemId) {
        final DrawerItem.Type type = DrawerItem.typeFrom(drawerItemId);

        if (type == null) {
            ErrorActivity.reportErrorInSnackbar(requireContext(),
                    new ErrorInfo(
                            new IllegalStateException("DrawerItem id not found: " + drawerItemId),
                            UserAction.SOMETHING_ELSE, "Choosing DrawerItems on settings"));
            return;
        }

        switch (type) {
            case KIOSK:
                final SelectKioskFragment selectKioskFragment = new SelectKioskFragment();
                selectKioskFragment.setOnSelectedListener((serviceId, kioskId, kioskName) ->
                        addDrawerItem(new DrawerItem.KioskDrawerItem(serviceId, kioskId)));
                selectKioskFragment.show(getParentFragmentManager(), "select_kiosk");
                return;
            case CHANNEL:
                final SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
                selectChannelFragment.setOnSelectedListener((serviceId, url, name) ->
                        addDrawerItem(new DrawerItem.ChannelDrawerItem(serviceId, url, name)));
                selectChannelFragment.show(getParentFragmentManager(), "select_channel");
                return;
            case PLAYLIST:
                final SelectPlaylistFragment selectPlaylistFragment = new SelectPlaylistFragment();
                selectPlaylistFragment.setOnSelectedListener(
                        new SelectPlaylistFragment.OnSelectedListener() {
                            @Override
                            public void onLocalPlaylistSelected(final long id, final String name) {
                                addDrawerItem(new DrawerItem.PlaylistDrawerItem(id, name));
                            }

                            @Override
                            public void onRemotePlaylistSelected(
                                    final int serviceId, final String url, final String name) {
                                addDrawerItem(
                                        new DrawerItem.PlaylistDrawerItem(serviceId, url, name));
                            }
                        });
                selectPlaylistFragment.show(getParentFragmentManager(), "select_playlist");
                return;
            default:
                addDrawerItem(type.getDrawerItem());
                break;
        }
    }

    private void saveChanges() {
        drawerItemManager.saveDrawerItems(drawerItemList);
    }

    private AddDrawerItemDialog.ChooseDrawerItemListItem[] getAvailableDrawerItems(
            final Context context) {
        final ArrayList<AddDrawerItemDialog.ChooseDrawerItemListItem> returnList
                = new ArrayList<>();

        for (final DrawerItem.Type type : DrawerItem.Type.values()) {
            final DrawerItem drawerItem = type.getDrawerItem();
            switch (type) {
                case BLANK:
                    //dont show blank pages
                    break;
                case DOWNLOADS:
                    returnList.add(new AddDrawerItemDialog.ChooseDrawerItemListItem(
                            drawerItem.getDrawerItemId(),
                            getString(R.string.download),
                            drawerItem.getDrawerItemIconRes(context)));
                    break;
                case KIOSK:
                    returnList.add(new AddDrawerItemDialog.ChooseDrawerItemListItem(
                            drawerItem.getDrawerItemId(),
                            getString(R.string.kiosk_page_summary),
                            R.drawable.ic_whatshot));
                    break;
                case CHANNEL:
                    returnList.add(new AddDrawerItemDialog.ChooseDrawerItemListItem(
                            drawerItem.getDrawerItemId(),
                            getString(R.string.channel_page_summary),
                            drawerItem.getDrawerItemIconRes(context)));
                    break;
                case DEFAULT_KIOSK:
                    returnList.add(new AddDrawerItemDialog.ChooseDrawerItemListItem(
                            drawerItem.getDrawerItemId(),
                            getString(R.string.default_kiosk_page_summary),
                            R.drawable.ic_whatshot));
                    break;
                case PLAYLIST:
                    returnList.add(new AddDrawerItemDialog.ChooseDrawerItemListItem(
                            drawerItem.getDrawerItemId(),
                            getString(R.string.playlist_page_summary),
                            drawerItem.getDrawerItemIconRes(context)));
                    break;
                default:
                    if (!drawerItemList.contains(drawerItem)) {
                        returnList.add(
                                new AddDrawerItemDialog
                                        .ChooseDrawerItemListItem(context, drawerItem));
                    }
                    break;
            }
        }

        return returnList.toArray(new AddDrawerItemDialog.ChooseDrawerItemListItem[0]);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public int interpolateOutOfBoundsScroll(final RecyclerView recyclerView,
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
            public boolean onMove(final RecyclerView recyclerView,
                                  final RecyclerView.ViewHolder source,
                                  final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || selectedDrawerItemsAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                selectedDrawerItemsAdapter.swapItems(sourceIndex, targetIndex);
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
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) {
                final int position = viewHolder.getAdapterPosition();
                drawerItemList.remove(position);
                selectedDrawerItemsAdapter.notifyItemRemoved(position);

                if (drawerItemList.isEmpty()) {
                    drawerItemList.add(DrawerItem.Type.BLANK.getDrawerItem());
                    selectedDrawerItemsAdapter.notifyItemInserted(0);
                }
            }
        };
    }

    private class SelectedDrawerItemsAdapter
            extends RecyclerView.Adapter<SelectedDrawerItemsAdapter.TabViewHolder> {
        private final LayoutInflater inflater;
        private ItemTouchHelper itemTouchHelper;

        SelectedDrawerItemsAdapter(final Context context, final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        public void swapItems(final int fromPosition, final int toPosition) {
            Collections.swap(drawerItemList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public SelectedDrawerItemsAdapter.TabViewHolder onCreateViewHolder(
                @NonNull final ViewGroup parent, final int viewType) {
            final View view = inflater.inflate(R.layout.list_choose_tabs, parent, false);
            return new SelectedDrawerItemsAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull final SelectedDrawerItemsAdapter.TabViewHolder holder,
                final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return drawerItemList.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {
            private AppCompatImageView drawerItemIconView;
            private TextView drawerItemNameView;
            private ImageView handle;

            TabViewHolder(final View itemView) {
                super(itemView);

                drawerItemNameView = itemView.findViewById(R.id.tabName);
                drawerItemIconView = itemView.findViewById(R.id.tabIcon);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));

                final DrawerItem drawerItem = drawerItemList.get(position);
                final DrawerItem.Type type = DrawerItem.typeFrom(drawerItem.getDrawerItemId());

                if (type == null) {
                    return;
                }

                final String drawerItemName;
                switch (type) {
                    case BLANK:
                        drawerItemName = getString(R.string.blank_page_summary);
                        break;
                    case DEFAULT_KIOSK:
                        drawerItemName = getString(R.string.default_kiosk_page_summary);
                        break;
                    case KIOSK:
                        drawerItemName =
                                NewPipe.getNameOfService(((DrawerItem.KioskDrawerItem) drawerItem)
                                .getKioskServiceId()) + "/"
                                + drawerItem.getDrawerItemName(requireContext());
                        break;
                    case CHANNEL:
                        drawerItemName =
                                NewPipe.getNameOfService(((DrawerItem.ChannelDrawerItem) drawerItem)
                                .getChannelServiceId()) + "/"
                                + drawerItem.getDrawerItemName(requireContext());
                        break;
                    case PLAYLIST:
                        final int serviceId = ((DrawerItem.PlaylistDrawerItem) drawerItem)
                                .getPlaylistServiceId();
                        final String serviceName = serviceId == -1
                                ? getString(R.string.local)
                                : NewPipe.getNameOfService(serviceId);
                        drawerItemName =
                                serviceName + "/" + drawerItem.getDrawerItemName(requireContext());
                        break;
                    default:
                        drawerItemName = drawerItem.getDrawerItemName(requireContext());
                        break;
                }

                drawerItemNameView.setText(drawerItemName);
                drawerItemIconView.setImageResource(
                        drawerItem.getDrawerItemIconRes(requireContext()));
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
