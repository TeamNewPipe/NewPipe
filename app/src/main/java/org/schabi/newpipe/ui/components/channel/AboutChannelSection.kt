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
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.ui.components.metadata.ImageMetadataItem
import org.schabi.newpipe.ui.components.metadata.MetadataItem
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.util.image.ImageStrategy

@Composable
fun AboutChannelSection(channelInfo: ChannelInfo) {
    // This tab currently holds little information, so a lazy column isn't needed here.
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val description = channelInfo.description
        if (!description.isNullOrEmpty()) {
            Text(text = description)
        }

        val count = channelInfo.subscriberCount
        if (count != -1L) {
            MetadataItem(
                title = R.string.metadata_subscribers,
                value = Localization.shortCount(LocalContext.current, count)
            )
        }

        ImageStrategy.choosePreferredImage(channelInfo.avatars)?.let {
            ImageMetadataItem(R.string.metadata_avatars, channelInfo.avatars, it)
        }

        ImageStrategy.choosePreferredImage(channelInfo.banners)?.let {
            ImageMetadataItem(R.string.metadata_banners, channelInfo.banners, it)
        }

        if (channelInfo.tags.isNotEmpty()) {
            TagsSection(channelInfo.serviceId, channelInfo.tags)
        }
    }
}

@Preview(name = "Light mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutChannelSectionPreview() {
    val info = ChannelInfo(NO_SERVICE_ID, "", "", "", "")
    info.description = "This is an example description"
    info.subscriberCount = 10

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AboutChannelSection(info)
        }
    }
}
