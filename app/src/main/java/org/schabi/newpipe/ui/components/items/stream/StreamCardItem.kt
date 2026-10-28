package org.schabi.newpipe.ui.components.items.stream

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.TournesolHelper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StreamCardItem(
    stream: Stream,
    showProgress: Boolean,
    isSelected: Boolean,
    onClick: (Stream) -> Unit = {},
    onLongClick: (Stream) -> Unit = {},
    onDismissPopup: () -> Unit = {}
) {
    Box {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onLongClick = { onLongClick(stream) },
                    onClick = { onClick(stream) }
                )
                .padding(top = 12.dp, start = 2.dp, end = 2.dp)
        ) {
            StreamThumbnail(
                stream = stream,
                showProgress = showProgress,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = stream.name,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2
                )

                Text(
                    text = stream.uploaderName,
                    style = MaterialTheme.typography.bodySmall
                )

                val tournesolScore = stream.tournesolScore
                val bestIcon = criteriaIcon(stream.tournesolBestCriteria)
                val worstIcon = criteriaIcon(stream.tournesolWorstCriteria)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tournesolScore != null) {
                        val isUnsafe = stream.tournesolUnsafeReasons.isNotEmpty()
                        val hasInsufficientReason = TournesolHelper.hasInsufficientReason(
                            stream.tournesolUnsafeReasons
                        )
                        Row(
                            modifier = Modifier.alpha(if (isUnsafe) 0.5f else 1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (hasInsufficientReason) {
                                Text(text = "\uD83C\uDF31", fontSize = 14.sp)
                            } else {
                                Image(
                                    painter = painterResource(R.drawable.logo_small),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = tournesolScore.toString(),
                                fontSize = 15.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD1B65C)
                            )
                        }
                    }
                    Text(
                        text = stream.detailText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.75f)
                    )
                    if (bestIcon != null || worstIcon != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.alpha(0.75f)
                        ) {
                            if (bestIcon != null) {
                                Text(text = "▲", fontSize = 9.sp, color = Color(0xFF6B9E6F))
                                Image(
                                    painter = painterResource(bestIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (worstIcon != null) {
                                Text(text = "▼", fontSize = 9.sp, color = Color(0xFFC07070))
                                Image(
                                    painter = painterResource(worstIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        StreamMenu(stream, isSelected, onDismissPopup)
    }
}

@DrawableRes
private fun criteriaIcon(criteria: String?): Int? = when (criteria) {
    "reliability" -> R.drawable.reliability
    "pedagogy" -> R.drawable.pedagogy
    "importance" -> R.drawable.importance
    "layman_friendly" -> R.drawable.layman_friendly
    "entertaining_relaxing" -> R.drawable.entertaining_relaxing
    "engaging" -> R.drawable.engaging
    "diversity_inclusion" -> R.drawable.diversity_inclusion
    "better_habits" -> R.drawable.better_habits
    "backfire_risk" -> R.drawable.backfire_risk
    else -> null
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamCardItemPreview(
    @PreviewParameter(StreamItemPreviewProvider::class) stream: Stream
) {
    AppTheme {
        Surface {
            StreamCardItem(stream, showProgress = false, isSelected = false)
        }
    }
}
