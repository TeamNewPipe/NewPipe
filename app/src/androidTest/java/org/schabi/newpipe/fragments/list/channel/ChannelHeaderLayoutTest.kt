package org.schabi.newpipe.fragments.list.channel

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.views.ChannelAppBarLayout

@RunWith(AndroidJUnit4::class)
class ChannelHeaderLayoutTest {
    @Test
    fun headerControlsStayVisibleAcrossOrientationsAndBannerStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)

            assertHeaderAtSize(context, 1080, 2400, showBanner = true)
            assertHeaderAtSize(context, 1080, 2400, showBanner = false)
            assertHeaderAtSize(context, 2400, 1080, showBanner = true)
            assertHeaderAtSize(context, 2400, 1080, showBanner = false)
        }
    }

    private fun assertHeaderAtSize(
        context: Context,
        widthPixels: Int,
        heightPixels: Int,
        showBanner: Boolean
    ) {
        val root = LayoutInflater.from(context)
            .inflate(R.layout.fragment_channel, FrameLayout(context), false)
        val appBar = root.findViewById<AppBarLayout>(R.id.app_bar_layout)
        val bannerContainer = root.findViewById<View>(R.id.channel_banner_container)
        val banner = root.findViewById<ImageView>(R.id.channel_banner_image)

        assertTrue(appBar is ChannelAppBarLayout)
        assertTrue(bannerContainer is FrameLayout)
        assertEquals(View.GONE, bannerContainer.visibility)
        measureAndLayout(root, widthPixels, heightPixels)

        if (showBanner) {
            // Channel banners arrive asynchronously with ChannelInfo. Exercise the real transition
            // from no reserved banner space to a newly visible, measured banner.
            bannerContainer.visibility = View.VISIBLE
            measureAndLayout(root, widthPixels, heightPixels)
        }

        val metadataRow = root.findViewById<View>(R.id.channel_metadata_row)
        val avatar = root.findViewById<ImageView>(R.id.channel_avatar_view)
        val title = root.findViewById<TextView>(R.id.channel_title_view)
        title.text = "Sabine Hossenfelder and the science of everything"
        measureAndLayout(root, widthPixels, heightPixels)
        assertEquals(2, title.maxLines)
        assertTrue(title.lineCount in 1..2)
        val subscriberCount = root.findViewById<View>(R.id.channel_subscriber_view)
        val subscriptionActions =
            root.findViewById<View>(R.id.channel_subscription_actions)
        val subscribeButton = root.findViewById<View>(R.id.channel_subscribe_button)

        if (showBanner) {
            assertVisibleAndMeasured(banner)
            assertVisibleAndMeasured(bannerContainer)
            assertEquals(ImageView.ScaleType.CENTER_CROP, banner.scaleType)
            assertEquals(bannerContainer.height, banner.height)
            assertTrue(metadataRow.top >= bannerContainer.bottom)
        } else {
            assertEquals(View.GONE, bannerContainer.visibility)
            assertEquals(0, metadataRow.top)
        }

        val metadataLayoutParams = metadataRow.layoutParams as AppBarLayout.LayoutParams
        assertEquals(0, metadataLayoutParams.scrollFlags)
        assertTrue(appBar.totalScrollRange <= bannerContainer.height)
        assertTrue(appBar.height - appBar.totalScrollRange >= metadataRow.height)
        assertTrue(metadataRow.bottom <= appBar.height)
        assertEquals(ImageView.ScaleType.CENTER_CROP, avatar.scaleType)
        assertTrue(subscriptionActions.right < metadataRow.width)

        listOf(avatar, title, subscriberCount, subscriptionActions, subscribeButton).forEach {
            assertVisibleAndMeasured(it)
            assertContainedIn(metadataRow, it)
        }
    }

    private fun measureAndLayout(root: View, widthPixels: Int, heightPixels: Int) {
        val width = View.MeasureSpec.makeMeasureSpec(widthPixels, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(heightPixels, View.MeasureSpec.EXACTLY)
        root.measure(width, height)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun assertVisibleAndMeasured(view: View) {
        assertEquals(View.VISIBLE, view.visibility)
        assertTrue(view.width > 0)
        assertTrue(view.height > 0)
    }

    private fun assertContainedIn(container: View, child: View) {
        assertTrue(child.left >= 0)
        assertTrue(child.top >= 0)
        assertTrue(child.right <= container.width)
        assertTrue(child.bottom <= container.height)
    }
}
