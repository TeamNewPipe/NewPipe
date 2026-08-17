package org.schabi.newpipe.local.bookmark

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager

/**
 * Takes care of remote and local playlists at once, hence "merged".
 */
object MergedPlaylistManager {

    @JvmStatic
    fun getMergedOrderedPlaylists(
        localPlaylistManager: LocalPlaylistManager,
        remotePlaylistManager: RemotePlaylistManager
    ): Flow<List<PlaylistLocalItem>> {
        return combine(
            localPlaylistManager.getPlaylists(),
            remotePlaylistManager.playlists
        ) { local, remote ->
            merge(local, remote)
        }
    }

    /**
     * Merge localPlaylists and remotePlaylists by the display index.
     * If two items have the same display index, sort them in `CASE_INSENSITIVE_ORDER`.
     *
     * @param localPlaylists  local playlists, already sorted by display index
     * @param remotePlaylists remote playlists, already sorted by display index
     * @return merged playlists
     */
    @JvmStatic
    fun merge(
        localPlaylists: List<PlaylistMetadataEntry>,
        remotePlaylists: List<PlaylistRemoteEntity>
    ): List<PlaylistLocalItem> {
        // This algorithm is similar to the merge operation in merge sort.
        val result = ArrayList<PlaylistLocalItem>(localPlaylists.size + remotePlaylists.size)
        val itemsWithSameIndex = ArrayList<PlaylistLocalItem>()

        var i = 0
        var j = 0
        while (i < localPlaylists.size) {
            while (j < remotePlaylists.size) {
                val remoteIndex = remotePlaylists[j].displayIndex ?: 0L
                val localIndex = localPlaylists[i].displayIndex ?: 0L
                if (remoteIndex <= localIndex) {
                    addItem(result, remotePlaylists[j], itemsWithSameIndex)
                    j++
                } else {
                    break
                }
            }
            addItem(result, localPlaylists[i], itemsWithSameIndex)
            i++
        }
        while (j < remotePlaylists.size) {
            addItem(result, remotePlaylists[j], itemsWithSameIndex)
            j++
        }
        addItemsWithSameIndex(result, itemsWithSameIndex)

        return result
    }

    private fun addItem(
        result: MutableList<PlaylistLocalItem>,
        item: PlaylistLocalItem,
        itemsWithSameIndex: MutableList<PlaylistLocalItem>
    ) {
        if (itemsWithSameIndex.isNotEmpty() &&
            itemsWithSameIndex[0].displayIndex != item.displayIndex
        ) {
            // The new item has a different display index, add previous items with same
            // index to the result.
            addItemsWithSameIndex(result, itemsWithSameIndex)
            itemsWithSameIndex.clear()
        }
        itemsWithSameIndex.add(item)
    }

    private fun addItemsWithSameIndex(
        result: MutableList<PlaylistLocalItem>,
        itemsWithSameIndex: MutableList<PlaylistLocalItem>
    ) {
        itemsWithSameIndex.sortWith(
            compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.orderingName }
        )
        result.addAll(itemsWithSameIndex)
    }
}
