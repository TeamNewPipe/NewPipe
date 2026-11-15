package org.schabi.newpipe.fragments.list.channel

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSubscriptionButtonLayoutTest {
    @Test
    fun `subscription text can grow without clipping`() {
        val layout = Files.readString(
            Paths.get("src/main/res/layout/fragment_channel.xml")
        )
        val button = layout.substringAfter("android:id=\"@+id/channel_subscribe_button\"")
            .substringBefore("/>")

        assertTrue(button.contains("android:layout_width=\"wrap_content\""))
        assertTrue(button.contains("android:layout_height=\"wrap_content\""))
        assertEquals(1, Regex("android:minHeight=\"48dp\"").findAll(button).count())
    }
}
