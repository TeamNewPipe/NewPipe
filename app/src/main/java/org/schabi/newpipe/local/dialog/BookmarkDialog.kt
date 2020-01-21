package org.schabi.newpipe.local.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.EditText
import org.schabi.newpipe.R

class BookmarkDialog(
        context: Context,
        private val playlistName: String,
        val listener: OnClickListener)
    : Dialog(context) {

    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_bookmark)
        initListeners()
    }

    private fun initListeners() {
        editText = findViewById(R.id.playlist_name_edit_text);
        editText.setText(playlistName)

        findViewById<Button>(R.id.bookmark_delete).setOnClickListener {
            listener.onDeleteClicked()
            dismiss()
        }
        findViewById<Button>(R.id.bookmark_cancel).setOnClickListener {
            dismiss()
        }
        findViewById<Button>(R.id.bookmark_save).setOnClickListener {
            listener.onSaveClicked(editText.text.toString())
            dismiss()
        }
    }

    interface OnClickListener {
        fun onDeleteClicked()
        fun onSaveClicked(name: String)
    }
}