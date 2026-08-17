package org.schabi.newpipe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.urlfinder.UrlFinder

class RouterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeHelper.setDayNightMode(this)

        val url = getUrl(intent)
        if (url == null) {
            finish()
            return
        }

        setContent {
            RouterScreen(url = url, onChoiceSelected = { choice ->
                handleChoice(url, choice)
            })
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun RouterScreen(url: String, onChoiceSelected: (String) -> Unit) {
        var isLoading by remember { mutableStateOf(true) }
        var linkType by remember { mutableStateOf(LinkType.NONE) }

        LaunchedEffect(url) {
            withContext(Dispatchers.IO) {
                try {
                    val service = NewPipe.getServiceByUrl(url)
                    linkType = service.getLinkTypeByUrl(url)
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    isLoading = false
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(text = "Open with NewPipe", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                val choices = listOf("Show Info", "Video Player", "Background Player", "Popup Player")
                choices.forEach { choice ->
                    ListItem(
                        headlineContent = { Text(choice) },
                        modifier = Modifier.clickable { onChoiceSelected(choice) }
                    )
                }
            }
        }
    }

    private fun handleChoice(url: String, choice: String) {
        lifecycleScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) {
                    NavigationHelper.getIntentByLink(this@RouterActivity, url)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@RouterActivity, "Error opening link", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun getUrl(intent: Intent): String? {
        return intent.data?.toString() ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            UrlFinder.firstUrlFromInput(it)
        }
    }
}
