package org.schabi.newpipe.local.bookmark

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.functions.BiFunction
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import java.lang.String
import java.util.Collections
import java.util.function.Function

/**
 * Takes care of remote and local playlists at once, hence "merged".
 */
object MergedPlaylistManager {
    fun getMergedOrderedPlaylists(
            localPlaylistManager: LocalPlaylistManager?,
            remotePlaylistManager: RemotePlaylistManager?): Flowable<List<PlaylistLocalItem>> {
        return Flowable.combineLatest(
                localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(), BiFunction<List<PlaylistMetadataEntry?>, List<PlaylistRemoteEntity?>, List<PlaylistLocalItem>>({ obj: List<PlaylistMetadataEntry?>?, localPlaylists: List<PlaylistRemoteEntity?>? -> merge(localPlaylists) }))
    }

    /**
     * Merge localPlaylists and remotePlaylists by the display index.
     * If two items have the same display index, sort them in `CASE_INSENSITIVE_ORDER`.
     *
     * @param localPlaylists  local playlists, already sorted by display index
     * @param remotePlaylists remote playlists, already sorted by display index
     * @return merged playlists
     */
    fun merge(
            localPlaylists: List<PlaylistMetadataEntry>,
            remotePlaylists: List<PlaylistRemoteEntity>): List<PlaylistLocalItem> {

        // This algorithm is similar to the merge operation in merge sort.
        val result: MutableList<PlaylistLocalItem> = ArrayList(
                localPlaylists.size + remotePlaylists.size)
        val itemsWithSameIndex: MutableList<PlaylistLocalItem> = ArrayList()
        var i: Int = 0
        var j: Int = 0
        while (i < localPlaylists.size) {
            while (j < remotePlaylists.size) {
                if ((remotePlaylists.get(j).getDisplayIndex()
                                <= localPlaylists.get(i).getDisplayIndex())) {
                    addItem(result, remotePlaylists.get(j), itemsWithSameIndex)
                    j++
                } else {
                    break
                }
            }
            addItem(result, localPlaylists.get(i), itemsWithSameIndex)
            i++
        }
        while (j < remotePlaylists.size) {
            addItem(result, remotePlaylists.get(j), itemsWithSameIndex)
            j++
        }
        addItemsWithSameIndex(result, itemsWithSameIndex)
        return result
    }

    private fun addItem(result: MutableList<PlaylistLocalItem>,
                        item: PlaylistLocalItem,
                        itemsWithSameIndex: MutableList<PlaylistLocalItem>) {
        if ((!itemsWithSameIndex.isEmpty()
                        && itemsWithSameIndex.get(0).getDisplayIndex() != item.getDisplayIndex())) {
            // The new item has a different display index, add previous items with same
            // index to the result.
            addItemsWithSameIndex(result, itemsWithSameIndex)
            itemsWithSameIndex.clear()
        }
        itemsWithSameIndex.add(item)
    }

    private fun addItemsWithSameIndex(result: MutableList<PlaylistLocalItem>,
                                      itemsWithSameIndex: List<PlaylistLocalItem>) {
        Collections.sort(itemsWithSameIndex,
                Comparator.comparing(Function({ obj: PlaylistLocalItem -> obj.getOrderingName() }),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
        result.addAll(itemsWithSameIndex)
    }
}
