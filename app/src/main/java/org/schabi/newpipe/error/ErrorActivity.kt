package org.schabi.newpipe.error

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import org.schabi.newpipe.util.ThemeHelper

class ErrorActivity : ComponentActivity() {
    private lateinit var errorInfo: ErrorInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setDayNightMode(this)

        errorInfo = IntentCompat.getParcelableExtra(intent, ERROR_INFO, ErrorInfo::class.java)!!

        setContent {
            ErrorScreen(errorInfo = errorInfo, onBack = { finish() })
        }
    }

    @Composable
    fun ErrorScreen(
        errorInfo: ErrorInfo,
        onBack: () -> Unit
    ) {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title = { Text("Error Report") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Text("Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = "Sorry, an error occurred.", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = errorInfo.userAction.message, style = MaterialTheme.typography.titleMedium)
                Text(text = errorInfo.request ?: "No request info", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = errorInfo.stackTraces.joinToString("\n"),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { /* Email */ }) { Text("Email") }
                    Button(onClick = { /* GitHub */ }) { Text("GitHub") }
                }
            }
        }
    }

    companion object {
        const val ERROR_INFO = "error_info"
    }
}
