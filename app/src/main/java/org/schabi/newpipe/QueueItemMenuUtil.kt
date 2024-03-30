package org.schabi.newpipe

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.FragmentManager
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.download.DownloadDialog
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.local.dialog.PlaylistDialog
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.util.List
import java.util.function.Consumer

object QueueItemMenuUtil {
    fun openPopupMenu(playQueue: PlayQueue?,
                      item: PlayQueueItem,
                      view: View?,
                      hideDetails: Boolean,
                      fragmentManager: FragmentManager?,
                      context: Context) {
        val themeWrapper: ContextThemeWrapper = ContextThemeWrapper(context, R.style.DarkPopupMenu)
        val popupMenu: PopupMenu = PopupMenu(themeWrapper, view)
        popupMenu.inflate(R.menu.menu_play_queue_item)
        if (hideDetails) {
            popupMenu.getMenu().findItem(R.id.menu_item_details).setVisible(false)
        }
        popupMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener({ menuItem: MenuItem ->
            when (menuItem.getItemId()) {
                R.id.menu_item_remove -> {
                    val index: Int = playQueue!!.indexOf(item)
                    playQueue.remove(index)
                    return@setOnMenuItemClickListener true
                }

                R.id.menu_item_details -> {
                    // playQueue is null since we don't want any queue change
                    NavigationHelper.openVideoDetail(context, item.getServiceId(),
                            item.getUrl(), item.getTitle(), null,
                            false)
                    return@setOnMenuItemClickListener true
                }

                R.id.menu_item_append_playlist -> {
                    PlaylistDialog.Companion.createCorrespondingDialog(
                            context,
                            List.of<StreamEntity?>(StreamEntity(item)),
                            Consumer<PlaylistDialog>({ dialog: PlaylistDialog ->
                                dialog.show(
                                        (fragmentManager)!!,
                                        "QueueItemMenuUtil@append_playlist"
                                )
                            })
                    )
                    return@setOnMenuItemClickListener true
                }

                R.id.menu_item_channel_details -> {
                    SparseItemUtil.fetchUploaderUrlIfSparse(context, item.getServiceId(),
                            item.getUrl(), item.getUploaderUrl(),  // An intent must be used here.
                            // Opening with FragmentManager transactions is not working,
                            // as PlayQueueActivity doesn't use fragments.
                            Consumer({ uploaderUrl: String? ->
                                NavigationHelper.openChannelFragmentUsingIntent(
                                        context, item.getServiceId(), uploaderUrl, item.getUploader()
                                )
                            }))
                    return@setOnMenuItemClickListener true
                }

                R.id.menu_item_share -> {
                    ShareUtils.shareText(context, item.getTitle(), item.getUrl(),
                            item.getThumbnails())
                    return@setOnMenuItemClickListener true
                }

                R.id.menu_item_download -> {
                    SparseItemUtil.fetchStreamInfoAndSaveToDatabase(context, item.getServiceId(), item.getUrl(),
                            Consumer({ info: StreamInfo ->
                                val downloadDialog: DownloadDialog = DownloadDialog(context,
                                        info)
                                downloadDialog.show((fragmentManager)!!, "downloadDialog")
                            }))
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }))
        popupMenu.show()
    }
}
