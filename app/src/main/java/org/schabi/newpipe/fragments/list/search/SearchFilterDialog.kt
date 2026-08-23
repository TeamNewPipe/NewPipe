package org.schabi.newpipe.fragments.list.search

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.filter.FilterGroup
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.ThemeHelper

class SearchFilterDialog : DialogFragment() {

    interface Callback {
        fun onSearchFilterSelected(
            serviceId: Int,
            contentFilterId: Int,
            sortFilterIds: ArrayList<Int>
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val serviceIds = requireArguments().getIntegerArrayList(ARG_SERVICE_IDS) ?: arrayListOf()
        val selectedServiceId = requireArguments().getInt(ARG_SELECTED_SERVICE_ID)
        val selectedContentFilterId = requireArguments().getInt(ARG_SELECTED_CONTENT_FILTER_ID)
        val selectedSortFilterIds = requireArguments()
            .getIntegerArrayList(ARG_SELECTED_SORT_FILTER_IDS) ?: arrayListOf()
        val searchQuery = requireArguments().getString(ARG_SEARCH_QUERY).orEmpty()
        val callback = parentFragment as? Callback ?: activity as? Callback
        val context = requireContext()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SearchFilterTheme(context) {
                    FilterCard(
                        serviceIds = serviceIds,
                        initialServiceId = selectedServiceId,
                        initialContentFilterId = selectedContentFilterId,
                        initialSortFilterIds = selectedSortFilterIds,
                        searchQuery = searchQuery,
                        context = context,
                        onApply = { serviceId, contentFilterId, sortFilterIds ->
                            callback?.onSearchFilterSelected(
                                serviceId,
                                contentFilterId,
                                ArrayList(sortFilterIds)
                            )
                            dismiss()
                        },
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "SearchFilterDialog"
        const val YOUTUBE_MUSIC_SERVICE_ID = -100
        const val YOUTUBE_MUSIC_SERVICE_NAME = "YouTube Music"
        private const val ARG_SERVICE_IDS = "service_ids"
        private const val ARG_SELECTED_SERVICE_ID = "selected_service_id"
        private const val ARG_SELECTED_CONTENT_FILTER_ID = "selected_content_filter_id"
        private const val ARG_SELECTED_SORT_FILTER_IDS = "selected_sort_filter_ids"
        private const val ARG_SEARCH_QUERY = "search_query"

        @JvmStatic
        fun newInstance(
            serviceIds: ArrayList<Int>,
            selectedServiceId: Int,
            selectedContentFilterId: Int,
            selectedSortFilterIds: ArrayList<Int>,
            searchQuery: String
        ): SearchFilterDialog {
            return SearchFilterDialog().apply {
                arguments = Bundle().apply {
                    putIntegerArrayList(ARG_SERVICE_IDS, serviceIds)
                    putInt(ARG_SELECTED_SERVICE_ID, selectedServiceId)
                    putInt(ARG_SELECTED_CONTENT_FILTER_ID, selectedContentFilterId)
                    putIntegerArrayList(ARG_SELECTED_SORT_FILTER_IDS, selectedSortFilterIds)
                    putString(ARG_SEARCH_QUERY, searchQuery)
                }
            }
        }
    }
}

@Composable
private fun SearchFilterTheme(
    context: Context,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(context) {
        when (resolveSearchFilterThemeMode(context)) {
            SearchFilterThemeMode.LIGHT -> SearchFilterLightColorScheme
            SearchFilterThemeMode.DARK -> SearchFilterDarkColorScheme
            SearchFilterThemeMode.BLACK -> SearchFilterBlackColorScheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

private enum class SearchFilterThemeMode {
    LIGHT,
    DARK,
    BLACK
}

private fun resolveSearchFilterThemeMode(context: Context): SearchFilterThemeMode {
    val resources = context.resources
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    val selectedTheme = preferences.getString(
        context.getString(R.string.theme_key),
        resources.getString(R.string.default_theme_value)
    )

    return when (selectedTheme) {
        resources.getString(R.string.light_theme_key) -> SearchFilterThemeMode.LIGHT
        resources.getString(R.string.black_theme_key) -> SearchFilterThemeMode.BLACK
        resources.getString(R.string.auto_device_theme_key) -> {
            if (!ThemeHelper.isDeviceDarkThemeEnabled(context)) {
                SearchFilterThemeMode.LIGHT
            } else {
                val selectedNightTheme = preferences.getString(
                    context.getString(R.string.night_theme_key),
                    resources.getString(R.string.default_night_theme_value)
                )
                if (selectedNightTheme == resources.getString(R.string.black_theme_key)) {
                    SearchFilterThemeMode.BLACK
                } else {
                    SearchFilterThemeMode.DARK
                }
            }
        }
        else -> SearchFilterThemeMode.DARK
    }
}

private val SearchFilterLightColorScheme = lightColorScheme()

private val SearchFilterDarkColorScheme = darkColorScheme()

private val SearchFilterBlackColorScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFF1F1F1F),
    outline = Color(0xFF3A3A3A)
)

private data class FilterUiState(
    val serviceId: Int,
    val contentFilters: List<FilterItem>,
    val selectedContentFilterId: Int,
    val sortGroups: List<FilterGroup>,
    val selectedSortFilterIds: Set<Int>
)

private data class PersistedFilterSelection(
    val contentFilterId: Int,
    val sortFilterIds: Set<Int>
)

private fun getPersistedFilterSelection(
    context: Context,
    serviceId: Int
): PersistedFilterSelection {
    return PersistedFilterSelection(
        contentFilterId = SearchFragment.getPersistedSearchContentFilterId(context, serviceId, -1),
        sortFilterIds = SearchFragment.getPersistedSearchSortFilterIds(context, serviceId).toSet()
    )
}

private fun createState(
    serviceId: Int,
    selectedContentFilterId: Int,
    selectedSortFilterIds: Set<Int>
): FilterUiState {
    val actualServiceId = resolveActualServiceId(serviceId)
    val service = NewPipe.getService(actualServiceId)
    val contentFilters = service.searchQHFactory.availableContentFilter.filterGroups
        .flatMap { it.filterItems.asList() }
        .filter {
            when {
                isYouTubeMusicService(serviceId) -> isYouTubeMusicFilter(it)
                actualServiceId == ServiceList.YouTube.serviceId -> !isYouTubeMusicFilter(it)
                else -> true
            }
        }
    val fallbackContentFilterId = contentFilters.firstOrNull()?.identifier ?: -1
    val currentContentFilterId = contentFilters.firstOrNull {
        it.identifier == selectedContentFilterId
    }?.identifier ?: fallbackContentFilterId
    val sortGroups = service.searchQHFactory
        .getContentFilterSortFilterVariant(currentContentFilterId)
        ?.filterGroups
        ?.toList()
        ?: emptyList()
    return FilterUiState(
        serviceId = serviceId,
        contentFilters = contentFilters,
        selectedContentFilterId = currentContentFilterId,
        sortGroups = sortGroups,
        selectedSortFilterIds = normalizeSortSelection(sortGroups, selectedSortFilterIds)
    )
}

private fun normalizeSortSelection(
    sortGroups: List<FilterGroup>,
    selectedSortFilterIds: Set<Int>
): Set<Int> {
    val validIds = sortGroups.flatMap { it.filterItems.asList() }.map { it.identifier }.toSet()
    val normalized = selectedSortFilterIds.filterTo(mutableSetOf()) { validIds.contains(it) }
    sortGroups.filter { it.onlyOneCheckable && it.filterItems.isNotEmpty() }.forEach { group ->
        val selected = group.filterItems.firstOrNull { normalized.contains(it.identifier) }
        if (selected == null) {
            normalized.add(group.filterItems.first().identifier)
        } else {
            group.filterItems.filter { it.identifier != selected.identifier }.forEach {
                normalized.remove(it.identifier)
            }
        }
    }
    return normalized
}

private fun resetSortSelection(sortGroups: List<FilterGroup>): Set<Int> {
    val reset = mutableSetOf<Int>()
    sortGroups.filter { it.onlyOneCheckable && it.filterItems.isNotEmpty() }.forEach { group ->
        reset.add(group.filterItems.first().identifier)
    }
    return reset
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun FilterCard(
    serviceIds: List<Int>,
    initialServiceId: Int,
    initialContentFilterId: Int,
    initialSortFilterIds: List<Int>,
    searchQuery: String,
    context: android.content.Context,
    onApply: (Int, Int, Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember {
        mutableStateOf(
            createState(
                initialServiceId,
                initialContentFilterId,
                initialSortFilterIds.toSet()
            )
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                IntegratedServiceHeader(
                    serviceIds = serviceIds,
                    selectedServiceId = state.serviceId,
                    onServiceChange = { serviceId ->
                        val persistedSelection = getPersistedFilterSelection(context, serviceId)
                        state = createState(
                            serviceId,
                            persistedSelection.contentFilterId,
                            persistedSelection.sortFilterIds
                        )
                    }
                )

                androidx.compose.material3.Divider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    FilterContent(
                        state = state,
                        context = context,
                        onContentFilterChange = { filterId ->
                            state = createState(state.serviceId, filterId, emptySet())
                        },
                        onSortFilterToggle = { filterGroup, filterItem ->
                            val selected = state.selectedSortFilterIds.toMutableSet()
                            if (filterGroup.onlyOneCheckable) {
                                filterGroup.filterItems.forEach { selected.remove(it.identifier) }
                                selected.add(filterItem.identifier)
                            } else if (selected.contains(filterItem.identifier)) {
                                selected.remove(filterItem.identifier)
                            } else {
                                selected.add(filterItem.identifier)
                            }
                            state = state.copy(
                                selectedSortFilterIds = normalizeSortSelection(
                                    state.sortGroups,
                                    selected
                                )
                            )
                        }
                    )
                }

                androidx.compose.material3.Divider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = {
                        state = state.copy(
                            selectedSortFilterIds = resetSortSelection(state.sortGroups)
                        )
                    }) {
                        Text(stringResource(R.string.playback_reset))
                    }
                    FilledTonalButton(onClick = {
                        onApply(
                            state.serviceId,
                            state.selectedContentFilterId,
                            state.selectedSortFilterIds
                        )
                    }) {
                        Text(
                            stringResource(
                                if (searchQuery.isEmpty()) R.string.done else R.string.perform_search
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegratedServiceHeader(
    serviceIds: List<Int>,
    selectedServiceId: Int,
    onServiceChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var headerWidthPx by remember { mutableStateOf(0) }
    val selectedServiceName = remember(selectedServiceId) {
        getServiceDisplayName(selectedServiceId)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerWidthPx = it.size.width }
                    .clickable { expanded = !expanded },
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (if (serviceIds.size > 1) 28 else 0).dp)
                    ) {
                        Text(
                            text = selectedServiceName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        if (serviceIds.size > 1) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .rotate(if (expanded) 180f else 0f)
                                    .width(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(density) { headerWidthPx.toDp() })
            ) {
                serviceIds.forEach { serviceId ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = getServiceDisplayName(serviceId),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        onClick = {
                            onServiceChange(serviceId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun getServiceDisplayName(serviceId: Int): String {
    if (isYouTubeMusicService(serviceId)) {
        return SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_NAME
    }
    val service = NewPipe.getService(serviceId)
    return service.serviceInfo.name + if (ServiceHelper.isBeta(service)) " (Legacy)" else ""
}

private fun resolveActualServiceId(serviceId: Int): Int {
    return if (isYouTubeMusicService(serviceId)) ServiceList.YouTube.serviceId else serviceId
}

private fun isYouTubeMusicService(serviceId: Int): Boolean {
    return serviceId == SearchFilterDialog.YOUTUBE_MUSIC_SERVICE_ID
}

private fun isYouTubeMusicFilter(filterItem: FilterItem): Boolean {
    return filterItem.name.startsWith("music_")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterContent(
    state: FilterUiState,
    context: android.content.Context,
    onContentFilterChange: (Int) -> Unit,
    onSortFilterToggle: (FilterGroup, FilterItem) -> Unit
) {
    val initialExpandedGroups = remember(state.selectedSortFilterIds, state.sortGroups) {
        state.sortGroups.filter { group ->
            group.filterItems.any { filterItem ->
                state.selectedSortFilterIds.contains(filterItem.identifier)
                        && (!group.onlyOneCheckable
                        || filterItem.identifier != group.filterItems.firstOrNull()?.identifier)
            }
        }.mapNotNull { it.groupName }.toSet()
    }
    var expandedGroups by remember(state.serviceId, state.selectedContentFilterId) {
        mutableStateOf(initialExpandedGroups)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.search_type),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        state.contentFilters.forEach { contentFilter ->
            val isSelected = state.selectedContentFilterId == contentFilter.identifier
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContentFilterChange(contentFilter.identifier) },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onContentFilterChange(contentFilter.identifier) }
                    )
                    Text(
                        text = ServiceHelper.getTranslatedFilterString(contentFilter.name, context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        state.sortGroups.forEach { filterGroup ->
            val groupName = filterGroup.groupName ?: return@forEach
            val isExpanded = expandedGroups.contains(groupName)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    expandedGroups = if (isExpanded) {
                        expandedGroups - groupName
                    } else {
                        expandedGroups + groupName
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ServiceHelper.getTranslatedFilterString(groupName, context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(
                            if (isExpanded) R.string.collapse else R.string.expand
                        ),
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    filterGroup.filterItems.toList().chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { filterItem ->
                                FilterChip(
                                    selected = state.selectedSortFilterIds.contains(filterItem.identifier),
                                    onClick = { onSortFilterToggle(filterGroup, filterItem) },
                                    label = {
                                        Text(
                                            text = ServiceHelper.getTranslatedFilterString(
                                                filterItem.name,
                                                context
                                            ),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(2 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
