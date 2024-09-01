package org.schabi.newpipe.settings.domain.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.error.usecases.OpenErrorActivity

class DeleteWatchHistory(
    private val deletePlaybackStates: DeletePlaybackStates,
    private val deleteCompleteStreamStateHistory: DeleteCompleteStreamStateHistory,
    private val removeOrphanedRecords: RemoveOrphanedRecords,
    private val openErrorActivity: OpenErrorActivity,
) {
    suspend operator fun invoke(
        onDeletePlaybackStates: () -> Unit,
        onDeleteWholeStreamHistory: () -> Unit,
        onRemoveOrphanedRecords: () -> Unit,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) = coroutineScope {
        launch {
            deletePlaybackStates(
                dispatcher,
                onError = { error ->
                    openErrorActivity(
                        ErrorInfo(
                            error,
                            UserAction.DELETE_FROM_HISTORY,
                            "Delete playback states"
                        )
                    )
                },
                onSuccess = onDeletePlaybackStates
            )
        }
        launch {
            deleteCompleteStreamStateHistory(
                dispatcher,
                onError = { error ->
                    openErrorActivity(
                        ErrorInfo(
                            error,
                            UserAction.DELETE_FROM_HISTORY,
                            "Delete from history"
                        )
                    )
                },
                onSuccess = onDeleteWholeStreamHistory
            )
        }
        launch {
            removeOrphanedRecords(
                dispatcher,
                onError = { error ->
                    openErrorActivity(
                        ErrorInfo(
                            error,
                            UserAction.DELETE_FROM_HISTORY,
                            "Clear orphaned records"
                        )
                    )
                },
                onSuccess = onRemoveOrphanedRecords
            )
        }
    }
}
