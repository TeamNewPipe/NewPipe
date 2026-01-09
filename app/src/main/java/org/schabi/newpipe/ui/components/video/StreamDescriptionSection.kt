package org.schabi.newpipe.ui.components.video

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.ui.components.common.LazyColumnThemedScrollbar
import org.schabi.newpipe.ui.components.common.parseDescription
import org.schabi.newpipe.ui.components.metadata.MetadataItem
import org.schabi.newpipe.ui.components.metadata.TagsSection
import org.schabi.newpipe.ui.components.metadata.imageMetadataItem
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamDescriptionSection(streamInfo: StreamInfo) {
    var isSelectable by rememberSaveable { mutableStateOf(false) }
    val hasDescription = streamInfo.description != Description.EMPTY_DESCRIPTION
    val lazyListState = rememberLazyListState()

    LazyColumnThemedScrollbar(state = lazyListState) {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp)
                .nestedScroll(rememberNestedScrollInteropConnection()),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (streamInfo.uploadDate != null) Arrangement.SpaceBetween else Arrangement.End,
                ) {
                    streamInfo.uploadDate?.let {
                        val date = Localization.formatDate(it.offsetDateTime())
                        Text(
                            text = stringResource(R.string.upload_date_text, date),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (hasDescription) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                val tooltip = stringResource(
                                    if (isSelectable) R.string.description_select_disable
                                    else R.string.description_select_enable
                                )
                                PlainTooltip { Text(text = tooltip) }
                            },
                            state = rememberTooltipState()
                        ) {
                            val res = if (isSelectable) R.drawable.ic_close else R.drawable.ic_select_all
                            Image(
                                modifier = Modifier.clickable { isSelectable = !isSelectable },
                                painter = painterResource(res),
                                contentDescription = null
                            )
                        }
                    }
                }

                val density = LocalDensity.current
                AnimatedVisibility(
                    visible = isSelectable,
                    enter = slideInVertically {
                        with(density) { -40.dp.roundToPx() }
                    } + expandVertically(
                        expandFrom = Alignment.Top
                    ) + fadeIn(
                        initialAlpha = 0.3f
                    ),
                    exit = slideOutVertically() + shrinkVertically() + fadeOut()
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.description_select_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (hasDescription) {
                item {
                    val description = parseDescription(streamInfo.description, streamInfo.serviceId)

                    if (isSelectable) {
                        SelectionContainer {
                            Text(text = description, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            metadataItem(title = R.string.metadata_category, value = streamInfo.category)

            metadataItem(title = R.string.metadata_licence, value = streamInfo.licence)

            val privacy = streamInfo.privacy ?: StreamExtractor.Privacy.OTHER
            if (privacy != StreamExtractor.Privacy.OTHER) {
                item {
                    val message = when (privacy) {
                        StreamExtractor.Privacy.PUBLIC -> R.string.metadata_privacy_public
                        StreamExtractor.Privacy.UNLISTED -> R.string.metadata_privacy_unlisted
                        StreamExtractor.Privacy.PRIVATE -> R.string.metadata_privacy_private
                        StreamExtractor.Privacy.INTERNAL -> R.string.metadata_privacy_internal
                        else -> 0 // Never reached
                    }
                    MetadataItem(title = R.string.metadata_privacy, value = stringResource(message))
                }
            }

            val ageLimit = streamInfo.ageLimit
            if (ageLimit != StreamExtractor.NO_AGE_LIMIT) {
                item {
                    MetadataItem(title = R.string.metadata_age_limit, value = ageLimit.toString())
                }
            }

            streamInfo.languageInfo?.let {
                item {
                    MetadataItem(
                        title = R.string.metadata_language,
                        value = it.getDisplayLanguage(Localization.getAppLocale())
                    )
                }
            }

            metadataItem(title = R.string.metadata_support, value = streamInfo.supportInfo)

            metadataItem(title = R.string.metadata_host, value = streamInfo.host)

            imageMetadataItem(title = R.string.metadata_thumbnails, images = streamInfo.thumbnails)

            imageMetadataItem(
                title = R.string.metadata_uploader_avatars,
                images = streamInfo.uploaderAvatars
            )

            imageMetadataItem(
                title = R.string.metadata_subchannel_avatars,
                images = streamInfo.subChannelAvatars
            )

            if (streamInfo.tags.isNotEmpty()) {
                item {
                    TagsSection(serviceId = streamInfo.serviceId, tags = streamInfo.tags)
                }
            }
        }
    }
}

private fun LazyListScope.metadataItem(@StringRes title: Int, value: String) {
    if (value.isNotEmpty()) {
        item {
            MetadataItem(title, value)
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamDescriptionSectionPreview() {
    val info = StreamInfo(NO_SERVICE_ID, "", "", StreamType.VIDEO_STREAM, "", "", 0)
    info.uploadDate = DateWrapper(OffsetDateTime.now())
    info.description = Description("This is an <b>example</b> description", Description.HTML)

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            StreamDescriptionSection(info)
        }
    }
}
