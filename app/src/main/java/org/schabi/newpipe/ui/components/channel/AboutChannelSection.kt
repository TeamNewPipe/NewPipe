package org.schabi.newpipe.ui.components.channel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.ui.components.metadata.ImageMetadataItem
import org.schabi.newpipe.ui.components.metadata.MetadataItem
import org.schabi.newpipe.ui.components.metadata.TagsSection
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID

@Composable
fun AboutChannelSection(channelInfo: ParcelableChannelInfo) {
    // This tab currently holds little information, so a lazy column isn't needed here.
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val (serviceId, description, count, avatars, banners, tags) = channelInfo

        if (description.isNotEmpty()) {
            Text(text = description)
        }

        if (count != StreamExtractor.UNKNOWN_SUBSCRIBER_COUNT) {
            MetadataItem(
                title = R.string.metadata_subscribers,
                value = Localization.shortCount(LocalContext.current, count)
            )
        }

        if (avatars.isNotEmpty()) {
            ImageMetadataItem(R.string.metadata_avatars, avatars)
        }

        if (banners.isNotEmpty()) {
            ImageMetadataItem(R.string.metadata_banners, banners)
        }

        if (tags.isNotEmpty()) {
            TagsSection(serviceId, tags)
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutChannelSectionPreview() {
    val images = listOf(
        Image("https://example.com/image_low.png", 16, 16, ResolutionLevel.LOW),
        Image("https://example.com/image_mid.png", 32, 32, ResolutionLevel.MEDIUM)
    )
    val info = ParcelableChannelInfo(
        serviceId = NO_SERVICE_ID,
        description = "This is an example description",
        subscriberCount = 10,
        avatars = images,
        banners = images,
        tags = listOf("Tag 1", "Tag 2")
    )

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AboutChannelSection(info)
        }
    }
}
