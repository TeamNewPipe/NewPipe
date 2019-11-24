package org.schabi.newpipe.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

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
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.PeertubeHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class PeertubeInstanceListFragment extends Fragment {

    private List<PeertubeInstance> instanceList = new ArrayList<>();
    private PeertubeInstance selectedInstance;
    private String savedInstanceListKey;
    public InstanceListAdapter instanceListAdapter;

    private ProgressBar progressBar;
    private SharedPreferences sharedPreferences;

    private CompositeDisposable disposables = new CompositeDisposable();

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        savedInstanceListKey = getString(R.string.peertube_instance_list_key);
        selectedInstance = PeertubeHelper.getCurrentInstance();
        updateInstanceList();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_instance_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initButton(rootView);

        RecyclerView listInstances = rootView.findViewById(R.id.instances);
        listInstances.setLayoutManager(new LinearLayoutManager(requireContext()));

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listInstances);

        instanceListAdapter = new InstanceListAdapter(requireContext(), itemTouchHelper);
        listInstances.setAdapter(instanceListAdapter);

        progressBar = rootView.findViewById(R.id.loading_progress_bar);
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) disposables.clear();
        disposables = null;
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

    private void updateInstanceList() {
        instanceList.clear();
        instanceList.addAll(PeertubeHelper.getInstanceList(requireContext()));
    }

    private void selectInstance(PeertubeInstance instance) {
        selectedInstance = PeertubeHelper.selectInstance(instance, requireContext());
        sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, true).apply();
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) actionBar.setTitle(R.string.peertube_instance_url_title);
        }
    }

    private void saveChanges() {
        JsonStringWriter jsonWriter = JsonWriter.string().object().array("instances");
        for (PeertubeInstance instance : instanceList) {
            jsonWriter.object();
            jsonWriter.value("name", instance.getName());
            jsonWriter.value("url", instance.getUrl());
            jsonWriter.end();
        }
        String jsonToSave = jsonWriter.end().end().done();
        sharedPreferences.edit().putString(savedInstanceListKey, jsonToSave).apply();
    }

    private void restoreDefaults() {
        new AlertDialog.Builder(requireContext(), ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    sharedPreferences.edit().remove(savedInstanceListKey).apply();
                    selectInstance(PeertubeInstance.defaultInstance);
                    updateInstanceList();
                    instanceListAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void initButton(View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.addInstanceButton);
        fab.setOnClickListener(v -> {
            showAddItemDialog(requireContext());
        });
    }

    private void showAddItemDialog(Context c) {
        final EditText urlET = new EditText(c);
        urlET.setHint(R.string.peertube_instance_add_help);
        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(R.string.peertube_instance_add_title)
                .setIcon(R.drawable.place_holder_peertube)
                .setView(urlET)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.finish, (dialog1, which) -> {
                    String url = urlET.getText().toString();
                    addInstance(url);
                })
                .create();
        dialog.show();
    }

    private void addInstance(String url) {
        String cleanUrl = verifyUrl(url);
        if(null == cleanUrl) return;
        progressBar.setVisibility(View.VISIBLE);
        Disposable disposable = Single.fromCallable(() -> {
            PeertubeInstance instance = new PeertubeInstance(cleanUrl);
            instance.fetchInstanceMetaData();
            return instance;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe((instance) -> {
            progressBar.setVisibility(View.GONE);
            add(instance);
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(getActivity(), "failed to validate instance", Toast.LENGTH_SHORT).show();
        });
        disposables.add(disposable);
    }

    @Nullable
    private String verifyUrl(String url){
        // if protocol not present, add https
        if(!url.startsWith("http")){
            url = "https://" + url;
        }
        // remove trailing slash
        url = url.replaceAll("/$", "");
        // only allow https
        if (!url.startsWith("https://")) {
            Toast.makeText(getActivity(), "instance url should start with https://", Toast.LENGTH_SHORT).show();
            return null;
        }
        // only allow if not already exists
        for (PeertubeInstance instance : instanceList) {
            if (instance.getUrl().equals(url)) {
                Toast.makeText(getActivity(), "instance already exists", Toast.LENGTH_SHORT).show();
                return null;
            }
        }
        return url;
    }

    private void add(final PeertubeInstance instance) {
        instanceList.add(instance);
        instanceListAdapter.notifyDataSetChanged();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

    private class InstanceListAdapter extends RecyclerView.Adapter<InstanceListAdapter.TabViewHolder> {
        private ItemTouchHelper itemTouchHelper;
        private final LayoutInflater inflater;
        private RadioButton lastChecked;

        InstanceListAdapter(Context context, ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        public void swapItems(int fromPosition, int toPosition) {
            Collections.swap(instanceList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public InstanceListAdapter.TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.item_instance, parent, false);
            return new InstanceListAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull InstanceListAdapter.TabViewHolder holder, int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return instanceList.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {
            private AppCompatImageView instanceIconView;
            private TextView instanceNameView;
            private TextView instanceUrlView;
            private RadioButton instanceRB;
            private ImageView handle;

            TabViewHolder(View itemView) {
                super(itemView);

                instanceIconView = itemView.findViewById(R.id.instanceIcon);
                instanceNameView = itemView.findViewById(R.id.instanceName);
                instanceUrlView = itemView.findViewById(R.id.instanceUrl);
                instanceRB = itemView.findViewById(R.id.selectInstanceRB);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(int position, TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));

                final PeertubeInstance instance = instanceList.get(position);
                instanceNameView.setText(instance.getName());
                instanceUrlView.setText(instance.getUrl());
                instanceRB.setOnCheckedChangeListener(null);
                if (selectedInstance.getUrl().equals(instance.getUrl())) {
                    if (lastChecked != null && lastChecked != instanceRB) {
                        lastChecked.setChecked(false);
                    }
                    instanceRB.setChecked(true);
                    lastChecked = instanceRB;
                }
                instanceRB.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectInstance(instance);
                        if (lastChecked != null && lastChecked != instanceRB) {
                            lastChecked.setChecked(false);
                        }
                        lastChecked = instanceRB;
                    }
                });
                instanceIconView.setImageResource(R.drawable.place_holder_peertube);
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
                        instanceListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                instanceListAdapter.swapItems(sourceIndex, targetIndex);
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
                // do not allow swiping the selected instance
                if(instanceList.get(position).getUrl().equals(selectedInstance.getUrl())) {
                    instanceListAdapter.notifyItemChanged(position);
                    return;
                }
                instanceList.remove(position);
                instanceListAdapter.notifyItemRemoved(position);

                if (instanceList.isEmpty()) {
                    instanceList.add(selectedInstance);
                    instanceListAdapter.notifyItemInserted(0);
                }
            }
        };
    }
}
