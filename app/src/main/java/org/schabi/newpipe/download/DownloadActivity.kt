package org.schabi.newpipe.download

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityDownloaderBinding
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.FocusOverlayView
import us.shandian.giga.service.DownloadManagerService
import us.shandian.giga.ui.fragment.MissionsFragment

class DownloadActivity() : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Service
        val i: Intent = Intent()
        i.setClass(this, DownloadManagerService::class.java)
        startService(i)
        Localization.assureCorrectAppLanguage(this)
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        val downloaderBinding: ActivityDownloaderBinding = ActivityDownloaderBinding.inflate(getLayoutInflater())
        setContentView(downloaderBinding.getRoot())
        setSupportActionBar(downloaderBinding.toolbarLayout.toolbar)
        val actionBar: ActionBar? = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.downloads_title)
            actionBar.setDisplayShowTitleEnabled(true)
        }
        getWindow().getDecorView().getViewTreeObserver()
                .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    public override fun onGlobalLayout() {
                        updateFragments()
                        getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(this)
                    }
                })
        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.Companion.setupFocusObserver(this)
        }
    }

    private fun updateFragments() {
        val fragment: MissionsFragment = MissionsFragment()
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, fragment, MISSIONS_FRAGMENT_TAG)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit()
    }

    public override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater: MenuInflater = getMenuInflater()
        inflater.inflate(R.menu.download_menu, menu)
        return true
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val MISSIONS_FRAGMENT_TAG: String = "fragment_tag"
    }
}
