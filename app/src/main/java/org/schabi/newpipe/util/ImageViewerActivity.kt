package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityImageViewerBinding
import org.schabi.newpipe.databinding.ItemImageViewerBinding
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.io.IOException


class ImageViewerActivity : AppCompatActivity() {

    companion object {
        private const val KEY_IMAGE_URLS = "urls"
        private const val KEY_SELECTED = "selected"

        @JvmStatic
        fun intent(context: Context, urls: Collection<String>, selected: Int = -1): Intent =
            Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(KEY_IMAGE_URLS, ArrayList(urls))
                if (selected > -1) putExtra(KEY_SELECTED, selected)
            }

        @JvmStatic
        fun intent(context: Context, pictures: Collection<Image>): Intent =
            intent(context, pictures.map { it.url }, 0)
    }

    private lateinit var binding: ActivityImageViewerBinding

    private var urls: List<String> = emptyList()
    private var current = -1

    private lateinit var fadeOutAnim: Animation
    private lateinit var fadeInAnim: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeStyle()

        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        urls = intent.getStringArrayListExtra(KEY_IMAGE_URLS).orEmpty().map { it.replace("http://", "https://") }
        current = intent.getIntExtra(KEY_SELECTED, urls.lastIndex)


        fadeInAnim = AlphaAnimation(0F, 1F).apply {
            interpolator = DecelerateInterpolator()
            duration = 400
        }

        fadeOutAnim = AlphaAnimation(1F, 0F).apply {
            interpolator = AccelerateInterpolator()
            duration = 400
        }

        setupViewPager()
        setupWindowsInsets()
        setupButtons()

        // Initial load
        binding.viewPager.setCurrentItem(current, false)
        updateCountIndicator(current)
    }

    @Suppress("DEPRECATION")
    private fun enableEdgeToEdgeStyle() {
        WindowCompat.setDecorFitsSystemWindows(window!!, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        WindowCompat.getInsetsController(window!!, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun setupWindowsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val marginSmall = resources.getDimensionPixelSize(R.dimen.margin_small)
            val marginNormal = resources.getDimensionPixelSize(R.dimen.margin_normal)
            binding.buttonClose.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + marginSmall
                rightMargin = insets.right + marginSmall
            }
            binding.countIndicator.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginNormal
                leftMargin = insets.left + marginNormal
            }
            binding.actions.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginSmall
                rightMargin = insets.right + marginSmall
            }
            windowInsets
        }
    }

    class ImageAdapter(private val urls: List<String>) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemImageViewerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.onBind(position, urls)
        }

        override fun getItemCount(): Int = urls.size

        class ViewHolder(val binding: ItemImageViewerBinding) : RecyclerView.ViewHolder(binding.root) {

            fun onBind(position: Int, urls: List<String>) {
                val context = itemView.context
                val url = urls[position]
                loadImages(context, url)
            }

            private fun loadImages(context: Context, url: String) {
                PicassoHelper.loadBanner(url).into(object : Target {
                    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                        binding.photoView.visibility = View.VISIBLE
                        binding.loadProgress.visibility = View.GONE
                        binding.photoView.setImageBitmap(bitmap)
                        binding.loadTip.visibility = View.GONE
                    }

                    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                        binding.photoView.visibility = View.INVISIBLE
                        binding.loadProgress.visibility = View.GONE
                        binding.loadText.visibility = View.VISIBLE
                        binding.loadTip.setOnClickListener {
                            loadImages(context, url) // retry
                        }
                    }

                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                        binding.photoView.visibility = View.INVISIBLE
                        binding.loadProgress.visibility = View.VISIBLE
                        binding.loadText.visibility = View.GONE
                        binding.loadTip.visibility = View.VISIBLE
                        binding.loadTip.setOnClickListener(null)
                    }

                })
            }
        }
    }

    private fun setupButtons() {
        binding.buttonClose.setOnClickListener { finish() }

        binding.countIndicator.setOnClickListener {
            binding.viewPager.currentItem = (current + 1).mod(urls.size)
        }

        binding.buttonShare.setOnClickListener { ShareUtils.shareText(it.context, urls[current], urls[current]) }
        binding.buttonOpenInBrowser.setOnClickListener { ShareUtils.openUrlInBrowser(it.context, urls[current]) }
        binding.buttonDownload.setOnClickListener { downloadAndSaveCurrent() }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ImageAdapter(urls)

        binding.viewPager.offscreenPageLimit = 1 // Keep one page on each side loaded
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                current = position
                updateCountIndicator(position)
            }
        })
    }

    private fun updateCountIndicator(position: Int) {
        @SuppressLint("SetTextI18n")
        binding.countIndicator.text = "${position + 1} / ${urls.size}"
        binding.countIndicator.visibility = View.VISIBLE
    }

    //region Save
    private fun downloadAndSaveCurrent() {
        val url = urls[current]
        val fileName = URLUtil.guessFileName(url, "", "image/*").substringBeforeLast("@")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, fileName)
            type = "image/*"
        }
        saveImage.launch(intent)
    }

    private val saveImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let {
                    saveImage(it.data!!, current)
                }
            }
        }

    private fun saveImage(uri: Uri, position: Int) {
        lifecycleScope.launch(SupervisorJob()) {
            PicassoHelper.loadOrigin(urls[position]).into(object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                    if (bitmap == null) return
                    try {
                        contentResolver.openOutputStream(uri)?.buffered(4096)?.use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        }
                        toast(R.string.success)
                    } catch (e: IOException) {
                        Log.e(javaClass.simpleName, "Failed to save", e)
                        toast(R.string.download_failed)
                    }
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    Log.e(javaClass.simpleName, "Failed to load", e)
                    toast(R.string.download_failed)
                }

                private fun toast(res: Int) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@ImageViewerActivity, res, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}

            })
        }
    }

    //endregion
}