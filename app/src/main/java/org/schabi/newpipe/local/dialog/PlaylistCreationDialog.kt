package org.schabi.newpipe.local.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.functions.Consumer
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.util.ThemeHelper

class PlaylistCreationDialog() : PlaylistDialog() {
    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    ////////////////////////////////////////////////////////////////////////// */
    public override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (getStreamEntities() == null) {
            return super.onCreateDialog(savedInstanceState)
        }
        val dialogBinding: DialogEditTextBinding = DialogEditTextBinding.inflate(getLayoutInflater())
        dialogBinding.getRoot().getContext().setTheme(ThemeHelper.getDialogTheme(requireContext()))
        dialogBinding.dialogEditText.setHint(R.string.name)
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT)
        val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(requireContext(),
                ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.create_playlist)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, DialogInterface.OnClickListener({ dialogInterface: DialogInterface?, i: Int ->
                    val name: String = dialogBinding.dialogEditText.getText().toString()
                    val playlistManager: LocalPlaylistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()))
                    val successToast: Toast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT)
                    playlistManager.createPlaylist(name, getStreamEntities())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(Consumer<List<Long?>?>({ longs: List<Long?>? -> successToast.show() }))
                }))
        return dialogBuilder.create()
    }

    companion object {
        /**
         * Create a new instance of [PlaylistCreationDialog].
         *
         * @param streamEntities    a list of [StreamEntity] to be added to playlists
         * @return a new instance of [PlaylistCreationDialog]
         */
        fun newInstance(streamEntities: List<StreamEntity?>?): PlaylistCreationDialog {
            val dialog: PlaylistCreationDialog = PlaylistCreationDialog()
            dialog.setStreamEntities(streamEntities)
            return dialog
        }
    }
}
