package org.schabi.newpipe.settings.domain.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.take
import org.schabi.newpipe.settings.domain.repositories.HistoryRecordRepository

class DeleteCompleteStreamStateHistory(
    private val historyRecordRepository: HistoryRecordRepository,
) {
    suspend operator fun invoke(
        dispatcher: CoroutineDispatcher,
        onError: (Throwable) -> Unit,
        onSuccess: () -> Unit,
    ) = historyRecordRepository.deleteWholeStreamHistory(dispatcher).catch {
        onError(it)
    }.take(1).collect {
        onSuccess()
    }
}
