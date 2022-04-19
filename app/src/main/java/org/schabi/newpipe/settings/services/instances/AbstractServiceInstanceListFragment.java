package org.schabi.newpipe.settings.services.instances;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentInstanceListBinding;
import org.schabi.newpipe.databinding.InstanceTypeFloatingItemBinding;
import org.schabi.newpipe.databinding.ItemInstanceBinding;
import org.schabi.newpipe.extractor.instance.Instance;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.services.InstanceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class AbstractServiceInstanceListFragment<I extends Instance> extends Fragment {
    protected static final String TAG = "AbsServiceInstanceLFrag";
    protected static final int TYPES_CONTAINER_ANIMATION_DURATION = 500;

    @StringRes
    protected final int title;

    protected final List<? extends InstanceTypeCreator<? extends I>> instanceTypeCreators;
    protected final InstanceManager<I> manager;

    protected final List<I> currentInstances = new ArrayList<>();
    protected I selectedInstance;

    protected FragmentInstanceListBinding binding;

    protected InstanceListAdapter instanceListAdapter;

    protected CompositeDisposable disposables = new CompositeDisposable();

    protected AbstractServiceInstanceListFragment(
            @StringRes final int title,
            @NonNull final InstanceManager<I> manager,
            @NonNull final List<? extends InstanceTypeCreator<? extends I>> instanceTypeCreators
    ) {
        this.title = title;
        this.manager = manager;
        this.instanceTypeCreators = instanceTypeCreators;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        selectedInstance = manager.getCurrentInstance();
        reloadInstanceListFromManager();

        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            @Nullable final ViewGroup container,
            @Nullable final Bundle savedInstanceState
    ) {
        binding = FragmentInstanceListBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull final View view,
            @Nullable final Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding.instances.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(binding.instances);

        instanceListAdapter = new InstanceListAdapter(itemTouchHelper);
        binding.instances.setAdapter(instanceListAdapter);

        binding.addInstanceButton.setOnClickListener(v -> onFABCreateButtonClicked());
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getString(title));
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChanges();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) {
            disposables.clear();
        }
        disposables = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_chooser_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_item_restore_default) {
            restoreDefaults();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void saveChanges() {
        manager.saveInstanceList(currentInstances, requireContext());
    }

    protected void selectInstance(final I instance) {
        selectedInstance = manager.saveCurrentInstance(instance, requireContext());
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, true)
                .apply();
    }

    private void reloadInstanceListFromManager() {
        currentInstances.clear();
        currentInstances.addAll(manager.getInstanceList(requireContext()));
    }

    protected void restoreDefaults() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    manager.restoreDefaults(requireContext());
                    reloadInstanceListFromManager();
                    instanceListAdapter.notifyDataSetChanged();
                })
                .show();
    }

    protected void onFABCreateButtonClicked() {
        final List<InstanceTypeCreator<? extends I>> availableInstanceTypeCreators =
                instanceTypeCreators.stream()
                        .filter(c -> c.canNewInstanceBeCreated(currentInstances))
                        .collect(Collectors.toList());

        binding.instanceTypesContainer.removeAllViews();
        // Only one instance type creator available -> Directly create instance on click
        if (availableInstanceTypeCreators.size() == 1) {
            availableInstanceTypeCreators.get(0)
                    .createNewInstance(requireContext(), currentInstances, this::addInstance);
            return;
        }

        final Runnable closeTypeContainersAndReset = () -> {
            ViewUtils.animate(
                    binding.instanceTypesContainer,
                    false,
                    TYPES_CONTAINER_ANIMATION_DURATION);
            ViewUtils.animateRotation(
                    binding.addInstanceButton,
                    TYPES_CONTAINER_ANIMATION_DURATION,
                    0);

            binding.addInstanceButton.setOnClickListener(v2 -> onFABCreateButtonClicked());
        };

        for (final InstanceTypeCreator<? extends I> config : availableInstanceTypeCreators) {
            final InstanceTypeFloatingItemBinding itemBinding =
                    InstanceTypeFloatingItemBinding.inflate(LayoutInflater.from(requireContext()));

            itemBinding.desc.setText(config.instanceServiceName());
            itemBinding.floatingActionButton.setImageResource(config.icon());
            itemBinding.floatingActionButton.setOnClickListener(v -> {
                config.createNewInstance(requireContext(), currentInstances, this::addInstance);
                closeTypeContainersAndReset.run();
            });

            binding.instanceTypesContainer.addView(itemBinding.getRoot());
        }

        ViewUtils.slideUp(
                binding.instanceTypesContainer,
                TYPES_CONTAINER_ANIMATION_DURATION,
                0,
                1F);
        ViewUtils.animateRotation(
                binding.addInstanceButton,
                TYPES_CONTAINER_ANIMATION_DURATION,
                135);
        binding.addInstanceButton.setOnClickListener(v -> closeTypeContainersAndReset.run());
    }

    protected void addInstance(final I createdInstance) {
        binding.loadingProgressBar.setVisibility(View.VISIBLE);
        final Disposable disposable = Single.fromCallable(() -> {
            createdInstance.fetchMetadata();
            return createdInstance;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(instance -> {
                    binding.loadingProgressBar.setVisibility(View.GONE);
                    add(instance);
                }, e -> {
                    binding.loadingProgressBar.setVisibility(View.GONE);
                    if (MainActivity.DEBUG) {
                        Log.w(TAG, "Failed to validate instance", e);
                    }
                    Toast.makeText(getActivity(),
                            requireContext().getString(
                                    R.string.could_not_validate_instance,
                                    e.getMessage() != null ? e.getMessage() : "no message"),
                            Toast.LENGTH_LONG).show();
                });
        disposables.add(disposable);
    }

    protected void add(final I instance) {
        currentInstances.add(instance);
        instanceListAdapter.notifyDataSetChanged();
    }

    protected ItemTouchHelper.SimpleCallback getItemTouchCallback() {
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
                final int minimumAbsVelocity = Math.max(12, Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder source,
                                  @NonNull final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || instanceListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getBindingAdapterPosition();
                final int targetIndex = target.getBindingAdapterPosition();
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
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                final int position = viewHolder.getBindingAdapterPosition();
                // do not allow swiping the selected instance
                if (currentInstances.get(position).getUrl().equals(selectedInstance.getUrl())) {
                    instanceListAdapter.notifyItemChanged(position);
                    return;
                }
                currentInstances.remove(position);
                instanceListAdapter.notifyItemRemoved(position);

                if (currentInstances.isEmpty()) {
                    currentInstances.add(selectedInstance);
                    instanceListAdapter.notifyItemInserted(0);
                }
            }
        };
    }

    @DrawableRes
    protected int getIconForInstance(final I instance) {
        return instanceTypeCreators.stream()
                .filter(c -> c.createdClass().isInstance(instance))
                .map(InstanceTypeCreator::icon)
                .findFirst()
                .orElse(R.drawable.ic_placeholder_circle);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

    protected class InstanceListAdapter
            extends RecyclerView.Adapter<InstanceListAdapter.TabViewHolder> {
        private final ItemTouchHelper itemTouchHelper;
        private RadioButton lastChecked;

        InstanceListAdapter(final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
        }

        public void swapItems(final int fromPosition, final int toPosition) {
            Collections.swap(currentInstances, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public InstanceListAdapter.TabViewHolder onCreateViewHolder(
                @NonNull final ViewGroup parent,
                final int viewType
        ) {
            return new InstanceListAdapter.TabViewHolder(
                    ItemInstanceBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final InstanceListAdapter.TabViewHolder holder,
                                     final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return currentInstances.size();
        }

        protected class TabViewHolder extends RecyclerView.ViewHolder {

            private final ItemInstanceBinding binding;

            TabViewHolder(@NonNull final ItemInstanceBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final InstanceListAdapter.TabViewHolder holder) {
                binding.handle.setOnTouchListener(getOnTouchListener(holder));

                final I instance = currentInstances.get(position);
                binding.instanceName.setText(instance.getName());
                binding.instanceUrl.setText(instance.getUrl());
                binding.selectInstanceRB.setOnCheckedChangeListener(null);
                if (selectedInstance.getUrl().equals(instance.getUrl())) {
                    if (lastChecked != null && lastChecked != binding.selectInstanceRB) {
                        lastChecked.setChecked(false);
                    }
                    binding.selectInstanceRB.setChecked(true);
                    lastChecked = binding.selectInstanceRB;
                }
                binding.selectInstanceRB.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectInstance(instance);
                        if (lastChecked != null && lastChecked != binding.selectInstanceRB) {
                            lastChecked.setChecked(false);
                        }
                        lastChecked = binding.selectInstanceRB;
                    }
                });
                binding.instanceIcon.setImageResource(getIconForInstance(instance));
            }

            @SuppressLint("ClickableViewAccessibility")
            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN
                            && itemTouchHelper != null
                            && getItemCount() > 1) {
                        itemTouchHelper.startDrag(item);
                        return true;
                    }
                    return false;
                };
            }
        }
    }
}
