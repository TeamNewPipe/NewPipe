package org.schabi.newpipe.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseFilterListActivity extends AppCompatActivity {
    protected RecyclerView recyclerView;
    protected FilterAdapter adapter;
    protected List<String> filterItems = new ArrayList<>();
    protected List<String> filteredItems = new ArrayList<>();
    protected SharedPreferences sharedPreferences;

    protected abstract String getPreferenceKey();
    protected abstract String getEmptyViewText();
    protected abstract String getAddDialogTitle();
    protected abstract String getActivityTitle();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_list);

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getActivityTitle());
        }

        TextView emptyView = findViewById(R.id.empty_view);
        emptyView.setText(getEmptyViewText());

        recyclerView = findViewById(R.id.filter_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        loadFilterItems();

        // Initialize filteredItems with all items
        filteredItems.addAll(filterItems);

        adapter = new FilterAdapter(filteredItems, emptyView);
        recyclerView.setAdapter(adapter);

        // Add swipe to delete functionality
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeToDeleteCallback());
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Setup FAB
        FloatingActionButton fab = findViewById(R.id.fab_add_filter);
        fab.setOnClickListener(v -> showAddFilterDialog());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filter_list_menu, menu);

        // Setup search functionality
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterList(newText);
                return true;
            }
        });

        return true;
    }

    private void filterList(String query) {
        filteredItems.clear();

        if (query.isEmpty()) {
            filteredItems.addAll(filterItems);
        } else {
            String lowercaseQuery = query.toLowerCase();
            for (String item : filterItems) {
                if (item.toLowerCase().contains(lowercaseQuery)) {
                    filteredItems.add(item);
                }
            }
        }

        adapter.notifyDataSetChanged();
        adapter.updateEmptyViewVisibility();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void loadFilterItems() {
        filterItems.clear();
        Set<String> items = sharedPreferences.getStringSet(getPreferenceKey(), new HashSet<>());
        filterItems.addAll(items);
    }

    protected void saveFilterItems() {
        Set<String> items = new HashSet<>(filterItems);
        sharedPreferences.edit()
                .putStringSet(getPreferenceKey(), items)
                .apply();
        ServiceHelper.initServices(this);
    }

    private void showAddFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getAddDialogTitle());

        View view = getLayoutInflater().inflate(R.layout.dialog_add_filter, null);
        EditText input = view.findViewById(R.id.filter_input);
        builder.setView(view);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String newFilter = input.getText().toString().trim();
            if (!newFilter.isEmpty() && !filterItems.contains(newFilter)) {
                filterItems.add(newFilter);
                // Also add to filtered list if it matches current filter
                if (filteredItems.size() == filterItems.size() - 1) {
                    filteredItems.add(newFilter);
                    adapter.notifyItemInserted(filteredItems.size() - 1);
                } else {
                    // Re-filter the list to ensure consistency
                    SearchView searchView = findViewById(R.id.action_search);
                    if (searchView != null && !searchView.getQuery().toString().isEmpty()) {
                        filterList(searchView.getQuery().toString());
                    } else {
                        filteredItems.add(newFilter);
                        adapter.notifyItemInserted(filteredItems.size() - 1);
                    }
                }
                adapter.updateEmptyViewVisibility();
                saveFilterItems();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {
        private final List<String> items;
        private final TextView emptyView;

        FilterAdapter(List<String> items, TextView emptyView) {
            this.items = items;
            this.emptyView = emptyView;
            updateEmptyViewVisibility();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_filter, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void updateEmptyViewVisibility() {
            if (items.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.filter_text);
            }
        }
    }

    private class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
        SwipeToDeleteCallback() {
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();
            String itemToRemove = filteredItems.get(position);

            // Remove from filtered list
            filteredItems.remove(position);
            adapter.notifyItemRemoved(position);

            // Also remove from main list
            filterItems.remove(itemToRemove);

            adapter.updateEmptyViewVisibility();
            saveFilterItems();
        }
    }
}
