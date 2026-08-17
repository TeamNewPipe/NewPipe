package org.schabi.newpipe.ui.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.work.*
import org.schabi.newpipe.R
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportInput
import org.schabi.newpipe.local.subscription.workers.SubscriptionImportWorker

@Composable
fun ImportConfirmationDialog(
    input: SubscriptionImportInput,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = { Text(stringResource(R.string.import_network_expensive_warning)) },
        confirmButton = {
            TextButton(onClick = {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                
                val req = OneTimeWorkRequest.Builder(SubscriptionImportWorker::class.java)
                    .setInputData(input.toData())
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        SubscriptionImportWorker.WORK_NAME,
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        req
                    )
                onDismiss()
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
