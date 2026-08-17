package org.schabi.newpipe.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.util.SliderStrategy

@Composable
fun PlaybackParameterDialog(
    initialTempo: Float,
    initialPitch: Float,
    initialSkipSilence: Boolean,
    onParametersChanged: (Float, Float, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var tempo by remember { mutableStateOf(initialTempo) }
    var pitch by remember { mutableStateOf(initialPitch) }
    var skipSilence by remember { mutableStateOf(initialSkipSilence) }
    
    val minVal = 0.1f
    val maxVal = 3.0f
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Parameters") },
        text = {
            Column {
                Text(text = "Speed: ${PlayerHelper.formatSpeed(tempo.toDouble())}")
                Slider(
                    value = tempo,
                    onValueChange = { 
                        tempo = it
                        onParametersChanged(tempo, pitch, skipSilence)
                    },
                    valueRange = minVal..maxVal
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(text = "Pitch: ${PlayerHelper.formatPitch(pitch.toDouble())}")
                Slider(
                    value = pitch,
                    onValueChange = { 
                        pitch = it
                        onParametersChanged(tempo, pitch, skipSilence)
                    },
                    valueRange = minVal..maxVal
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = skipSilence,
                        onCheckedChange = { 
                            skipSilence = it
                            onParametersChanged(tempo, pitch, skipSilence)
                        }
                    )
                    Text("Skip Silence")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                tempo = 1.0f
                pitch = 1.0f
                skipSilence = false
                onParametersChanged(tempo, pitch, skipSilence)
            }) {
                Text("Reset")
            }
        }
    )
}
