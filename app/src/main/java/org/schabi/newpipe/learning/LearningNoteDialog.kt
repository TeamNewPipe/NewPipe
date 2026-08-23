package org.schabi.newpipe.learning

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.databinding.DialogLearningNoteBinding

object LearningNoteDialog {
    @JvmStatic
    fun show(
        context: Context,
        initialTimestampMillis: Long,
        existing: LearningNoteEntity? = null,
        onSave: LearningNoteSaveListener
    ) {
        val binding = DialogLearningNoteBinding.inflate(LayoutInflater.from(context))
        binding.learningNoteTimestamp.setText(
            LearningNoteTime.format(existing?.timestampMillis ?: initialTimestampMillis)
        )
        binding.learningNoteText.setText(existing?.noteText.orEmpty())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.learning_note_add else R.string.learning_note_edit)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val timestamp = LearningNoteTime.parse(
                    binding.learningNoteTimestamp.text?.toString().orEmpty()
                )
                val noteText = binding.learningNoteText.text?.toString()?.trim().orEmpty()

                binding.learningNoteTimestampLayout.error = if (timestamp == null) {
                    context.getString(R.string.learning_note_timestamp_error)
                } else {
                    null
                }
                binding.learningNoteTextLayout.error = when {
                    noteText.isEmpty() -> context.getString(R.string.learning_note_empty_error)
                    noteText.length > LearningNoteManager.MAX_NOTE_LENGTH -> context.getString(
                        R.string.learning_note_too_long_error,
                        LearningNoteManager.MAX_NOTE_LENGTH
                    )
                    else -> null
                }

                if (timestamp != null && noteText.isNotEmpty() &&
                    noteText.length <= LearningNoteManager.MAX_NOTE_LENGTH
                ) {
                    onSave.onSave(timestamp, noteText)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
