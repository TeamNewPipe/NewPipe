package org.schabi.newpipe.fragments.list.search;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Button;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.util.ServiceHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("checkstyle:ParameterNumber")
final class SearchFilterDialog {
    interface Listener {
        void onSearchFiltersApplied(@NonNull String contentFilter,
                                    @NonNull List<Integer> sortFilters);
    }

    private final Context context;
    private final StreamingService service;
    private final LinearLayout content;
    private final LinearLayout sortContent;
    private final List<FilterItem> contentFilters;
    private final Set<Integer> selectedSortFilters;
    private FilterItem selectedContentFilter;
    private ChipGroup contentFilterGroup;
    private final SearchFilterPresets presets;
    private final boolean datesSupported;
    private final boolean musicOnly;
    private EditText afterInput;
    private EditText beforeInput;
    private TextView datePreview;
    private Button dateHeading;
    private String after;
    private String before;
    private LinearLayout presetsContent;

    interface AdvancedListener {
        void onSearchFiltersApplied(String contentFilter, List<Integer> sortFilters,
                                    String after, String before);
    }

    interface DurationListener {
        void onSearchFiltersApplied(String contentFilter, List<Integer> sortFilters,
                                    String after, String before, int order,
                                    long minimum, long maximum);
    }

    private final DurationListener advancedListener;
    private int durationOrder;
    private long minimumDuration;
    private long maximumDuration;
    private EditText minimumInput;
    private EditText maximumInput;
    private ChipGroup durationChips;

    private SearchFilterDialog(@NonNull final Context context,
                               @NonNull final StreamingService service,
                               @NonNull final String[] currentContentFilters,
                               @NonNull final int[] currentSortFilters,
                               final boolean musicOnly,
                               final String after, final String before,
                               final int order, final long minimum, final long maximum,
                               @NonNull final DurationListener listener) {
        this.context = context;
        this.service = service;
        this.musicOnly = musicOnly;
        this.advancedListener = listener;
        this.after = after;
        this.before = before;
        durationOrder = order;
        minimumDuration = minimum;
        maximumDuration = maximum;
        presets = new SearchFilterPresets(context, service.getServiceId(), musicOnly);
        datesSupported = service.getServiceId() == ServiceList.YouTube.getServiceId()
                && !musicOnly;
        contentFilters = getContentFilters(service, musicOnly);
        selectedContentFilter = findByName(contentFilters,
                currentContentFilters.length == 0 ? null : currentContentFilters[0]);
        if (selectedContentFilter == null && !contentFilters.isEmpty()) {
            selectedContentFilter = contentFilters.get(0);
        }
        selectedSortFilters = new LinkedHashSet<>();
        for (final int currentSortFilter : currentSortFilters) {
            selectedSortFilters.add(currentSortFilter);
        }

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(8));
        sortContent = new LinearLayout(context);
        sortContent.setOrientation(LinearLayout.VERTICAL);
    }

    static void show(@NonNull final Context context,
                     @NonNull final StreamingService service,
                     @NonNull final String[] currentContentFilters,
                     @NonNull final int[] currentSortFilters,
                     final boolean musicOnly,
                     @NonNull final Listener listener) {
        showAdvanced(context, service, currentContentFilters, currentSortFilters, musicOnly,
                "", "", (selectedContent, selectedSort, after, before) ->
                        listener.onSearchFiltersApplied(selectedContent, selectedSort));
    }

    static void showAdvanced(@NonNull final Context context,
                             @NonNull final StreamingService service,
                             @NonNull final String[] currentContentFilters,
                             @NonNull final int[] currentSortFilters,
                             final boolean musicOnly, final String after, final String before,
                             @NonNull final AdvancedListener listener) {
        showExtended(context, service, currentContentFilters, currentSortFilters, musicOnly,
                after, before, 0, 0, 0, (selected, sort, start, end, order, minimum, maximum) ->
                        listener.onSearchFiltersApplied(selected, sort, start, end));
    }

    static void showExtended(@NonNull final Context context,
                             @NonNull final StreamingService service,
                             @NonNull final String[] currentContentFilters,
                             @NonNull final int[] currentSortFilters,
                             final boolean musicOnly, final String after, final String before,
                             final int order, final long minimum, final long maximum,
                             @NonNull final DurationListener listener) {
        new SearchFilterDialog(context, service, currentContentFilters, currentSortFilters,
                musicOnly, after, before, order, minimum, maximum, listener).show();
    }

    static boolean hasFilters(@Nullable final StreamingService service) {
        if (service == null || service.getSearchQHFactory() == null) {
            return false;
        }
        final Filter content = service.getSearchQHFactory().getAvailableContentFilter();
        final Filter sort = service.getSearchQHFactory().getAvailableSortFilter();
        return !flatten(content).isEmpty() || !flatten(sort).isEmpty();
    }

    @NonNull
    static List<FilterItem> getContentFilters(@NonNull final StreamingService service,
                                              final boolean musicOnly) {
        return filterContentTypes(
                flatten(service.getSearchQHFactory().getAvailableContentFilter()), musicOnly);
    }

    private void show() {
        buildPresets();
        buildContentFilters();
        if (datesSupported) {
            buildDates();
        }
        rebuildSortFilters(false);
        buildDuration();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.addView(content);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.search_filters)
                .setView(scrollView)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset, null)
                .setPositiveButton(R.string.search_filter_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(view -> reset());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (!validateOptions()) {
                    return;
                }
                advancedListener.onSearchFiltersApplied(selectedContentFilter == null
                                ? "" : selectedContentFilter.getName(),
                        new ArrayList<>(selectedSortFilters), after, before,
                        durationOrder, minimumDuration, maximumDuration);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void buildContentFilters() {
        if (contentFilters.isEmpty()) {
            content.addView(sortContent);
            return;
        }
        contentFilterGroup = new ChipGroup(context);
        contentFilterGroup.setSingleSelection(true);
        contentFilterGroup.setSelectionRequired(true);
        for (final FilterItem filter : contentFilters) {
            final Chip chip = filterChip(filter);
            if (!musicOnly && service.getServiceId() == ServiceList.YouTube.getServiceId()
                    && filter.getName().startsWith("music_")) {
                chip.setText(context.getString(R.string.search_filter_source_label,
                        context.getString(R.string.youtube_music), translated(filter.getName())));
            }
            chip.setChecked(filter == selectedContentFilter);
            contentFilterGroup.addView(chip);
        }
        contentFilterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            final View checked = group.findViewById(checkedIds.get(0));
            selectedContentFilter = (FilterItem) checked.getTag();
            rebuildSortFilters(false);
        });
        addSection(content, context.getString(R.string.search_filter_content_type),
                contentFilterGroup, "", true);
        content.addView(sortContent);
    }

    private void rebuildSortFilters(final boolean contentTypeChanged) {
        sortContent.removeAllViews();
        final Filter sortVariant = selectedContentFilter == null
                ? service.getSearchQHFactory().getAvailableSortFilter()
                : service.getSearchQHFactory().getContentFilterSortFilterVariant(
                        selectedContentFilter.getIdentifier());
        final List<FilterGroup> groups = groups(sortVariant);
        normalizeSortFilters(groups, selectedSortFilters, contentTypeChanged);

        for (final FilterGroup group : groups) {
            if (group.filterItems.length == 0) {
                continue;
            }
            if (group.onlyOneCheckable) {
                addExclusiveGroup(group);
            } else {
                addMultiChoiceGroup(group);
            }
        }
    }

    private Chip filterChip(final FilterItem filter) {
        final Chip chip = (Chip) LayoutInflater.from(context).inflate(
                R.layout.item_search_music_filter_chip, content, false);
        chip.setId(View.generateViewId());
        chip.setText(translated(filter.getName()));
        chip.setTag(filter);
        return chip;
    }

    private void addExclusiveGroup(@NonNull final FilterGroup group) {
        addChipGroup(group, true);
    }

    private void addMultiChoiceGroup(@NonNull final FilterGroup group) {
        addChipGroup(group, false);
    }

    private void addChipGroup(final FilterGroup group, final boolean exclusive) {
        final ChipGroup chips = new ChipGroup(context);
        chips.setSingleSelection(exclusive);
        chips.setSelectionRequired(exclusive);
        for (final FilterItem filter : group.filterItems) {
            final Chip chip = filterChip(filter);
            chip.setChecked(selectedSortFilters.contains(filter.getIdentifier()));
            chips.addView(chip);
        }
        final Button heading = addSection(sortContent, translated(group.groupName), chips,
                selectedChipNames(chips), false);
        chips.setOnCheckedStateChangeListener((view, checkedIds) -> {
            for (final FilterItem filter : group.filterItems) {
                selectedSortFilters.remove(filter.getIdentifier());
            }
            for (final int id : checkedIds) {
                selectedSortFilters.add(((FilterItem) view.findViewById(id).getTag())
                        .getIdentifier());
            }
            heading.setText(sectionTitle(translated(group.groupName), selectedChipNames(chips)));
        });
    }

    private String selectedChipNames(final ChipGroup chips) {
        final List<String> selected = new ArrayList<>();
        for (final int id : chips.getCheckedChipIds()) {
            selected.add(((Chip) chips.findViewById(id)).getText().toString());
        }
        return String.join(", ", selected);
    }

    private void buildPresets() {
        presetsContent = new LinearLayout(context);
        presetsContent.setOrientation(LinearLayout.VERTICAL);
        addSection(content, context.getString(R.string.search_filter_presets),
                presetsContent, "", false);
        refreshPresets();
    }

    private void refreshPresets() {
        presetsContent.removeAllViews();
        final ChipGroup chips = new ChipGroup(context);
        for (final SearchFilterPresets.Preset preset : presets.read()) {
            final Chip chip = filterChip(new FilterItem(0, preset.getName()));
            chip.setText(preset.getName());
            chip.setCheckable(false);
            chip.setOnClickListener(view -> new MaterialAlertDialogBuilder(context)
                    .setTitle(preset.getName())
                    .setItems(new CharSequence[]{context.getString(R.string.search_preset_load),
                            context.getString(R.string.search_preset_update),
                            context.getString(R.string.delete)}, (dialog, which) -> {
                        if (which == 0) {
                            loadPreset(preset);
                        } else if (which == 1 && validateOptions()) {
                            savePreset(preset.getName());
                        } else if (which == 2) {
                            presets.delete(preset.getName());
                            refreshPresets();
                        }
                    }).show());
            chips.addView(chip);
        }
        final Button add = new Button(context);
        add.setText(R.string.search_preset_add);
        add.setOnClickListener(view -> {
            if (!validateOptions()) {
                return;
            }
            final EditText name = new EditText(context);
            name.setSingleLine(true);
            name.setHint(R.string.search_preset_name);
            final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.search_preset_add).setView(name)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.save, null).create();
            dialog.setOnShowListener(ignored -> dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(button -> {
                        final String value = name.getText().toString().trim();
                        if (value.isEmpty()) {
                            name.setError(context.getString(R.string.search_preset_name));
                        } else {
                            savePreset(value);
                            dialog.dismiss();
                        }
                    }));
            dialog.show();
        });
        presetsContent.addView(chips);
        presetsContent.addView(add);
    }

    private void savePreset(final String name) {
        final List<String> filters = flatten(service.getSearchQHFactory().getAvailableSortFilter())
                .stream().filter(item -> selectedSortFilters.contains(item.getIdentifier()))
                .map(FilterItem::getName).collect(Collectors.toList());
        presets.save(new SearchFilterPresets.Preset(name,
                selectedContentFilter == null ? "" : selectedContentFilter.getName(),
                filters, after, before, durationOrder, minimumDuration, maximumDuration));
        refreshPresets();
    }

    private void loadPreset(final SearchFilterPresets.Preset preset) {
        final FilterItem selected = findByName(contentFilters, preset.getContent());
        if (selected != null && contentFilterGroup != null) {
            selectedContentFilter = selected;
            for (int index = 0; index < contentFilterGroup.getChildCount(); index++) {
                final Chip chip = (Chip) contentFilterGroup.getChildAt(index);
                if (chip.getTag() == selected) {
                    chip.setChecked(true);
                }
            }
        }
        selectedSortFilters.clear();
        for (final FilterItem filter : flatten(
                service.getSearchQHFactory().getAvailableSortFilter())) {
            if (preset.getFilters().contains(filter.getName())) {
                selectedSortFilters.add(filter.getIdentifier());
            }
        }
        rebuildSortFilters(false);
        durationOrder = preset.getOrder();
        if (durationOrder < 0 || durationOrder > 2) {
            durationOrder = 0;
        }
        ((Chip) durationChips.getChildAt(durationOrder)).setChecked(true);
        minimumInput.setText(SearchResultOrdering.secondsToHours(preset.getMinimum()));
        maximumInput.setText(SearchResultOrdering.secondsToHours(preset.getMaximum()));
        if (datesSupported) {
            afterInput.setText(preset.getAfter());
            beforeInput.setText(preset.getBefore());
        }
    }

    private void buildDuration() {
        final LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        addSection(content, context.getString(R.string.search_duration_order), body, "",
                durationOrder != 0 || minimumDuration > 0 || maximumDuration > 0);
        final TextView help = new TextView(context);
        help.setText(R.string.search_duration_help);
        body.addView(help);
        durationChips = new ChipGroup(context);
        durationChips.setSingleSelection(true);
        durationChips.setSelectionRequired(true);
        final int[] labels = {R.string.search_service_order, R.string.search_longest_first,
                R.string.search_shortest_first};
        for (int index = 0; index < labels.length; index++) {
            final Chip chip = filterChip(new FilterItem(index, ""));
            chip.setText(labels[index]);
            chip.setChecked(index == durationOrder);
            durationChips.addView(chip);
        }
        durationChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                durationOrder = ((FilterItem) group.findViewById(checkedIds.get(0)).getTag())
                        .getIdentifier();
            }
        });
        body.addView(durationChips);
        minimumInput = dateInput(body, R.string.search_minimum_hours,
                SearchResultOrdering.secondsToHours(minimumDuration));
        maximumInput = dateInput(body, R.string.search_maximum_hours,
                SearchResultOrdering.secondsToHours(maximumDuration));
        minimumInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        maximumInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    private boolean validateOptions() {
        if (!validateDates()) {
            return false;
        }
        try {
            minimumDuration = SearchResultOrdering.hoursToSeconds(
                    minimumInput.getText().toString());
            maximumDuration = SearchResultOrdering.hoursToSeconds(
                    maximumInput.getText().toString());
            SearchResultOrdering.validate(durationOrder, minimumDuration, maximumDuration);
            return true;
        } catch (final IllegalArgumentException exception) {
            ((View) minimumInput.getParent()).setVisibility(View.VISIBLE);
            minimumInput.setError(context.getString(R.string.search_duration_invalid));
            minimumInput.requestFocus();
            return false;
        }
    }

    private void buildDates() {
        final LinearLayout dateContent = new LinearLayout(context);
        dateContent.setOrientation(LinearLayout.VERTICAL);
        dateHeading = addSection(content, context.getString(R.string.search_custom_dates),
                dateContent, "", !after.isEmpty() || !before.isEmpty());
        final TextView help = new TextView(context);
        help.setText(R.string.search_date_help);
        dateContent.addView(help);
        afterInput = dateInput(dateContent, R.string.search_after_date, after);
        beforeInput = dateInput(dateContent, R.string.search_before_date, before);
        datePreview = new TextView(context);
        dateContent.addView(datePreview);
        final TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start,
                                          final int count, final int extra) { }
            @Override
            public void onTextChanged(final CharSequence s, final int start,
                                      final int beforeCount, final int count) {
                updateDatePreview();
            }
            @Override
            public void afterTextChanged(final Editable editable) { }
        };
        afterInput.addTextChangedListener(watcher);
        beforeInput.addTextChangedListener(watcher);
        updateDatePreview();
    }

    private EditText dateInput(final LinearLayout parent, final int hint, final String value) {
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        parent.addView(input);
        return input;
    }

    private void updateDatePreview() {
        final List<String> labels = new ArrayList<>();
        if (!afterInput.getText().toString().trim().isEmpty()) {
            labels.add(context.getString(R.string.search_after_label,
                    afterInput.getText().toString().trim()));
        }
        if (!beforeInput.getText().toString().trim().isEmpty()) {
            labels.add(context.getString(R.string.search_before_label,
                    beforeInput.getText().toString().trim()));
        }
        dateHeading.setText(sectionTitle(context.getString(R.string.search_custom_dates),
                String.join(" / ", labels)));
        try {
            datePreview.setText(SearchDateRange.query("", afterInput.getText().toString(),
                    beforeInput.getText().toString(), LocalDate.now()));
        } catch (final IllegalArgumentException exception) {
            datePreview.setText(R.string.search_date_invalid);
        } catch (final java.time.DateTimeException exception) {
            datePreview.setText(R.string.search_date_invalid);
        }
    }

    private boolean validateDates() {
        if (!datesSupported) {
            after = "";
            before = "";
            return true;
        }
        after = afterInput.getText().toString().trim();
        before = beforeInput.getText().toString().trim();
        try {
            SearchDateRange.query("", after, before, LocalDate.now());
            return true;
        } catch (final IllegalArgumentException | java.time.DateTimeException exception) {
            ((View) afterInput.getParent()).setVisibility(View.VISIBLE);
            afterInput.setError(context.getString(R.string.search_date_invalid));
            afterInput.requestFocus();
            return false;
        }
    }

    private void reset() {
        selectedContentFilter = contentFilters.isEmpty() ? null : contentFilters.get(0);
        selectedSortFilters.clear();
        if (contentFilterGroup != null && contentFilterGroup.getChildCount() > 0) {
            ((Chip) contentFilterGroup.getChildAt(0)).setChecked(true);
        }
        rebuildSortFilters(true);
        ((Chip) durationChips.getChildAt(0)).setChecked(true);
        minimumInput.setText("");
        maximumInput.setText("");
        if (datesSupported) {
            afterInput.setText("");
            beforeInput.setText("");
        }
    }

    static void normalizeSortFilters(@NonNull final List<FilterGroup> groups,
                                     @NonNull final Set<Integer> selected,
                                     final boolean contentTypeChanged) {
        final Set<Integer> normalized = new LinkedHashSet<>();
        for (final FilterGroup group : groups) {
            if (group.filterItems.length == 0) {
                continue;
            }
            if (group.onlyOneCheckable) {
                FilterItem selectedItem = null;
                for (final FilterItem filter : group.filterItems) {
                    if (selected.contains(filter.getIdentifier())) {
                        selectedItem = filter;
                        break;
                    }
                }
                normalized.add((selectedItem == null || contentTypeChanged
                        ? group.filterItems[0] : selectedItem).getIdentifier());
            } else {
                for (final FilterItem filter : group.filterItems) {
                    if (selected.contains(filter.getIdentifier())) {
                        normalized.add(filter.getIdentifier());
                    }
                }
            }
        }
        selected.clear();
        selected.addAll(normalized);
    }

    private Button addSection(final LinearLayout parent, final String title, final View body,
                            final String summary, final boolean expanded) {
        final Button heading = new Button(context);
        heading.setText(sectionTitle(title, summary));
        heading.setAllCaps(false);
        heading.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        heading.setTypeface(null, Typeface.BOLD);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        heading.setOnClickListener(view -> body.setVisibility(
                body.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        parent.addView(heading);
        parent.addView(body);
        return heading;
    }

    private String sectionTitle(final String title, final String summary) {
        return summary.isEmpty() ? title : title + ": " + summary;
    }

    private String translated(@Nullable final String name) {
        return name == null ? "" : ServiceHelper.getTranslatedFilterString(name, context);
    }

    private int dp(final int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static List<FilterGroup> groups(@Nullable final Filter filter) {
        if (filter == null || filter.getFilterGroups() == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(filter.getFilterGroups());
    }

    @NonNull
    private static List<FilterItem> flatten(@Nullable final Filter filter) {
        final List<FilterItem> items = new ArrayList<>();
        for (final FilterGroup group : groups(filter)) {
            items.addAll(Arrays.asList(group.filterItems));
        }
        return items;
    }

    @NonNull
    static List<FilterItem> filterContentTypes(@NonNull final List<FilterItem> items,
                                               final boolean musicOnly) {
        if (!musicOnly) {
            return items;
        }
        final List<FilterItem> musicItems = new ArrayList<>();
        for (final FilterItem item : items) {
            if (item.getName().startsWith("music_")) {
                musicItems.add(item);
            }
        }
        return musicItems;
    }

    @Nullable
    private static FilterItem findByName(@NonNull final List<FilterItem> items,
                                         @Nullable final String name) {
        for (final FilterItem item : items) {
            if (item.getName().equals(name)) {
                return item;
            }
        }
        return null;
    }
}
