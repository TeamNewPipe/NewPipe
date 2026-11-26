package org.schabi.newpipe.views

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import java.time.Duration
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.profiles.ProfileManager

/** Covers diagnostic behavior and real view lifecycles, not the affected device's GPU output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = Application::class)
class DetailRenderingDiagnosticsTest {
    private lateinit var controller: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private var containerId = 0
    private var previousProfile: String? = null

    @Before
    fun setUp() {
        DetailRenderingDiagnostics.clear()
        controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = controller.get()
        activity.setTheme(R.style.DarkTheme)
        controller.setup()
        containerId = View.generateViewId()
        activity.setContentView(FrameLayout(activity).apply { id = containerId })
        controller.visible()
        previousProfile = PreferenceManager.getDefaultSharedPreferences(activity).getString(ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY, null)
    }

    @After
    fun tearDown() {
        DetailRenderingDiagnostics.clear()
        controller.pause().stop().destroy()
        PreferenceManager.getDefaultSharedPreferences(activity).edit().apply {
            if (previousProfile == null) remove(ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY) else putString(ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY, previousProfile)
        }.commit()
    }

    @Test
    fun requiresOptInAndCapturesOnlyViewStateWithoutChangingRenderingOrClipboard() {
        val fragment = showDetails()
        advance(1_000)
        assertNull(DetailRenderingDiagnostics.report())
        val binding = requireNotNull(fragment.binding)
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val originalClipboard = clipboard.primaryClip
        val originalLayer = binding.detailContentRootLayout.layerType
        val originalAlpha = binding.detailVideoTitleView.alpha

        armAndReturnToDetails()
        advance(500)
        val first = latestViews()
        assertEquals(SECRET_TITLE.length, first.getJSONObject("title").getInt("textLength"))
        assertEquals(originalLayer, first.getJSONObject("detailPanel").getInt("layer"))
        assertEquals(View.LAYER_TYPE_SOFTWARE, originalLayer)
        assertFalse(first.getJSONObject("videoSurface").getBoolean("present"))
        assertEquals(0, first.getJSONObject("playerPlaceholder").getInt("children"))

        binding.detailVideoTitleView.alpha = 0.4f
        binding.detailSubChannelTextView.visibility = View.INVISIBLE
        (binding.root.parent as View).alpha = 0.6f
        CapturedSurfaceView(activity).use { surface ->
            surface.id = R.id.surfaceView
            binding.playerPlaceholder.addView(surface, FrameLayout.LayoutParams(200, 100))
            layout()
            val previousSamples = sampleCount()
            advance(500)
            assertEquals("capturing", report().getString("state"))
            assertEquals(previousSamples + 1, sampleCount())
            val changed = latestViews()
            assertEquals(0.4, changed.getJSONObject("title").getDouble("alpha"), 0.0001)
            assertEquals(View.INVISIBLE, changed.getJSONObject("channel").getInt("visibility"))
            assertEquals(0.6, changed.getJSONArray("ancestors").getJSONObject(0).getDouble("alpha"), 0.0001)
            assertTrue(changed.getJSONObject("videoSurface").getBoolean("present"))
            assertTrue(changed.getJSONObject("videoSurface").getBoolean("surfaceValid"))

            assertEquals(originalLayer, binding.detailContentRootLayout.layerType)
            assertEquals(View.LAYER_TYPE_NONE, binding.playerPlaceholder.layerType)
            assertEquals(View.LAYER_TYPE_NONE, surface.layerType)
            assertEquals(SECRET_TITLE, binding.detailVideoTitleView.text.toString())
            assertEquals(1f, originalAlpha, 0f)
            assertEquals(0.4f, binding.detailVideoTitleView.alpha, 0f)
            assertEquals(View.INVISIBLE, binding.detailSubChannelTextView.visibility)
            assertEquals(originalClipboard, clipboard.primaryClip)
            val text = requireNotNull(DetailRenderingDiagnostics.report())
            for (secret in listOf(SECRET_TITLE, SECRET_CHANNEL, SECRET_DESCRIPTION, SECRET_TAG)) assertFalse(text.contains(secret))
        }
    }

    @Test
    fun recreatedViewsReplaceTheSamplerAndPausedOrDestroyedViewsStopSampling() {
        val fragment = showDetails()
        armAndReturnToDetails()
        advance(500)
        val manager = activity.supportFragmentManager
        repeat(2) {
            val old = requireNotNull(fragment.binding)
            manager.beginTransaction().detach(fragment).commitNow()
            val stoppedCount = sampleCount()
            advance(1_000)
            assertEquals(stoppedCount, sampleCount())
            old.detailVideoTitleView.alpha = 0.1f

            manager.beginTransaction().attach(fragment).commitNow()
            val fresh = requireNotNull(fragment.binding)
            assertNotSame(old.root, fresh.root)
            fresh.detailVideoTitleView.alpha = 0.7f
            layout()
            val resumedCount = sampleCount()
            advance(500)
            assertEquals(resumedCount + 1, sampleCount())
            assertEquals(0.7, latestViews().getJSONObject("title").getDouble("alpha"), 0.0001)
        }

        controller.pause()
        val pausedCount = sampleCount()
        advance(1_000)
        assertEquals(pausedCount, sampleCount())
        controller.resume()
        assertTrue(sampleCount() > pausedCount)
        manager.beginTransaction().remove(fragment).commitNow()
        val removedCount = sampleCount()
        advance(1_000)
        assertEquals(removedCount, sampleCount())
    }

    @Test
    fun captureIsBoundedAndClearingCancelsItsPendingSamples() {
        val fragment = showDetails()
        armAndReturnToDetails()
        repeat(60) { sample ->
            requireNotNull(fragment.binding).detailVideoTitleView.alpha = if (sample % 2 == 0) 0.8f else 0.9f
            advance(500)
        }
        advance(5_000)
        assertEquals("completed", report().getString("state"))
        assertEquals(60, sampleCount())
        assertTrue(report().getJSONArray("frames").length() <= 60)
        assertTrue(requireNotNull(DetailRenderingDiagnostics.report()).length <= 128_000)
        advance(5_000)
        assertEquals(60, sampleCount())

        armAndReturnToDetails()
        assertEquals(1, sampleCount())
        DetailRenderingDiagnostics.clear()
        advance(1_000)
        assertNull(DetailRenderingDiagnostics.report())
    }

    @Test
    fun profileChangesStopCaptureWithoutExportingProfileIdentifiers() {
        showDetails()
        armAndReturnToDetails()
        val count = sampleCount()
        val newProfile = "private-diagnostic-profile-identifier"
        PreferenceManager.getDefaultSharedPreferences(activity).edit().putString(ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY, newProfile).commit()
        advance(500)
        assertEquals("profile_changed", report().getString("state"))
        assertEquals(count, sampleCount())
        assertFalse(requireNotNull(DetailRenderingDiagnostics.report()).contains(newProfile))
        advance(1_000)
        assertEquals(count, sampleCount())
    }

    @Test
    fun unusedArmingExpiresWithoutSamplingAViewOpenedLater() {
        assertTrue(DetailRenderingDiagnostics.startCapture(activity))
        advance(121_000)
        showDetails()
        advance(500)
        assertEquals("expired", report().getString("state"))
        assertEquals(0, sampleCount())
    }

    @Test
    @Config(sdk = [28, 30])
    fun otherAndroidVersionsCannotEnableTheDiagnostic() {
        showDetails()
        assertFalse(DetailRenderingDiagnostics.isAvailable())
        assertFalse(DetailRenderingDiagnostics.startCapture(activity))
        advance(1_000)
        assertNull(DetailRenderingDiagnostics.report())
    }

    private fun showDetails() = DetailsFragment().also {
        activity.supportFragmentManager.beginTransaction().add(containerId, it).commitNow()
        layout()
    }

    private fun armAndReturnToDetails() {
        controller.pause()
        assertTrue(DetailRenderingDiagnostics.startCapture(activity))
        controller.resume()
    }

    private fun layout() {
        activity.window.decorView.apply {
            measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun advance(milliseconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    private fun report() = JSONObject(requireNotNull(DetailRenderingDiagnostics.report()))
    private fun sampleCount() = report().getInt("sampleCount")
    private fun latestViews(): JSONObject = report().getJSONArray("frames").let { it.getJSONObject(it.length() - 1).getJSONObject("views") }

    /** Robolectric's default holder returns null instead of Android's persistent Surface object. */
    private class CapturedSurfaceView(context: Context) : SurfaceView(context), AutoCloseable {
        private val capturedTexture = SurfaceTexture(0)
        private val capturedSurface = Surface(capturedTexture)
        private val originalHolder = super.getHolder()
        private val capturedHolder = object : SurfaceHolder by originalHolder {
            override fun getSurface(): Surface = capturedSurface
        }

        override fun getHolder(): SurfaceHolder = capturedHolder

        override fun close() {
            capturedSurface.release()
            capturedTexture.release()
        }
    }

    class DetailsFragment : Fragment() {
        var binding: FragmentVideoDetailBinding? = null
            private set

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FragmentVideoDetailBinding.inflate(inflater, container, false).also {
            binding = it
            it.detailVideoTitleView.text = SECRET_TITLE
            it.detailSubChannelTextView.text = SECRET_CHANNEL
            it.detailContentRootHiding.visibility = View.VISIBLE
            it.detailVideoTitleView.contentDescription = SECRET_DESCRIPTION
            it.root.tag = SECRET_TAG
            DetailTextRendering.apply(it.detailContentRootLayout)
        }.root

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            DetailRenderingDiagnostics.observe(this, view)
        }

        override fun onDestroyView() {
            binding = null
            super.onDestroyView()
        }
    }

    companion object {
        private const val SECRET_TITLE = "Private episode https://example.invalid/watch?token=private-token"
        private const val SECRET_CHANNEL = "Private channel name"
        private const val SECRET_DESCRIPTION = "Private accessibility description"
        private const val SECRET_TAG = "Private arbitrary view tag"
    }
}
