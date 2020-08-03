package org.schabi.newpipe.settings.extensions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ManageFingerprintsFragment extends Fragment {
    private List<String> fingerprintsList = new ArrayList<>();
    private FingerprintsAdapter fingerprintsAdapter = null;

    private SharedPreferences prefs;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        updateFingerprintsList();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_manage_fingerprints, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initButton(rootView);

        final RecyclerView listFingerprints
                = rootView.findViewById(R.id.fingerprints);
        listFingerprints.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listFingerprints);

        fingerprintsAdapter = new FingerprintsAdapter(requireContext(), itemTouchHelper);
        listFingerprints.setAdapter(fingerprintsAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTitle();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void updateFingerprintsList() {
        fingerprintsList.clear();

        fingerprintsList = new ArrayList<>(prefs.getStringSet(getString(R.string.fingerprints_key),
                new HashSet<>()));

        if (fingerprintsAdapter != null) {
            fingerprintsAdapter.notifyDataSetChanged();
        }
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.manage_fingerprints_title);
            }
        }
    }

    private void initButton(final View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.add_fingerprint_button);
        fab.setOnClickListener(v -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            final EditText input = new EditText(getContext());
            input.setHint(R.string.fingerprint_placeholder);
            builder.setView(input)
                    .setTitle(R.string.add_fingerprint_dialog)
                    .setPositiveButton(R.string.finish, (DialogInterface d, int id) -> {
                        final String fingerprint = input.getText().toString().replace(":", "")
                                .toUpperCase();
                        if (fingerprint.matches("[0-9A-F]{64}")) {
                            fingerprintsList.add(fingerprint);
                            prefs.edit().putStringSet(getString(R.string.fingerprints_key),
                                    new HashSet<>(fingerprintsList)).commit();
                            updateFingerprintsList();
                        } else {
                            Toast.makeText(getContext(), R.string.invalid_fingerprint,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }).setNegativeButton(R.string.cancel, (DialogInterface d, int id) -> {
                        d.cancel();
                    }).show();
        });
    }

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
                return false;
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
                fingerprintsList.remove(position);
                prefs.edit().putStringSet(getString(R.string.fingerprints_key),
                        new HashSet<>(fingerprintsList)).commit();
                updateFingerprintsList();
            }
        };
    }

    private class FingerprintsAdapter
            extends RecyclerView.Adapter<FingerprintsAdapter.FingerprintViewHolder> {
        private final LayoutInflater inflater;
        private ItemTouchHelper itemTouchHelper;

        FingerprintsAdapter(final Context context, final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public FingerprintViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                        final int viewType) {
            final View view = inflater.inflate(R.layout.list_manage_fingerprints, parent, false);
            return new FingerprintViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final FingerprintViewHolder holder,
                                     final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return fingerprintsList.size();
        }

        class FingerprintViewHolder extends RecyclerView.ViewHolder {
            private TextView fingerprintView;
            private ImageView handle;

            FingerprintViewHolder(final View itemView) {
                super(itemView);

                fingerprintView = itemView.findViewById(R.id.fingerprint);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final FingerprintViewHolder holder) {
                fingerprintView.setText(fingerprintsList.get(position));
                handle.setOnTouchListener(getOnTouchListener(holder));
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
