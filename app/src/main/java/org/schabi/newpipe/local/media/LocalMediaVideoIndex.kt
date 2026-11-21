/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import java.util.Locale

enum class LocalMediaVideoCategory { VIDEOS, FOLDERS }

object LocalMediaVideoIndex {
    fun folders(
        items: List<LocalMediaItem>,
        unknownFolder: String,
        kind: LocalMediaGroupKind = LocalMediaGroupKind.VIDEO_FOLDER,
        showParentForDuplicates: Boolean = true
    ): List<LocalMediaGroup> = items.groupBy { item ->
        "${item.volumeName}:${item.relativePath}"
    }.map { (key, groupedItems) ->
        val first = groupedItems.first()
        LocalMediaGroup(
            stableKey = "folder:$key",
            title = first.folder.ifBlank { unknownFolder },
            subtitle = folderSubtitle(first),
            items = groupedItems.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaItem::title)
            ),
            thumbnailUri = groupedItems.firstNotNullOfOrNull(LocalMediaItem::thumbnailUri),
            kind = kind
        )
    }.let { groups ->
        if (showParentForDuplicates) disambiguateDuplicates(groups) else groups
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LocalMediaGroup::title))

    private fun disambiguateDuplicates(groups: List<LocalMediaGroup>): List<LocalMediaGroup> {
        val byTitle = groups.groupBy { it.title.lowercase(Locale.ROOT) }
        return groups.map { group ->
            val duplicates = byTitle[group.title.lowercase(Locale.ROOT)].orEmpty()
            if (duplicates.size <= 1) return@map group
            val groupItem = group.items.firstOrNull() ?: return@map group
            val candidate = groupItem.relativePath.trimEnd('/')
                .substringBeforeLast('/', missingDelimiterValue = "")
                .substringAfterLast('/')
                .ifBlank { groupItem.volumeName }
            val sameParent = duplicates.count { duplicate ->
                duplicate.items.firstOrNull()?.let { item ->
                    item.relativePath.trimEnd('/')
                        .substringBeforeLast('/', missingDelimiterValue = "")
                        .substringAfterLast('/')
                        .ifBlank { item.volumeName }
                        .equals(candidate, ignoreCase = true)
                } == true
            } > 1
            val suffix = if (sameParent) group.subtitle else candidate
            if (suffix.isBlank()) group else group.copy(title = "${group.title} ($suffix)")
        }
    }

    private fun folderSubtitle(item: LocalMediaItem): String = listOf(
        item.volumeName,
        item.relativePath
    ).filter(String::isNotBlank).joinToString(" • ")
}
