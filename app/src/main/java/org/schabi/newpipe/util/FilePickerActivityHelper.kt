/*
 * SPDX-FileCopyrightText: 2018 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.loader.content.Loader
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.nononsenseapps.filepicker.AbstractFilePickerFragment
import com.nononsenseapps.filepicker.FilePickerFragment
import org.schabi.newpipe.R
import java.io.File

open class FilePickerActivityHelper : com.nononsenseapps.filepicker.FilePickerActivity() {
    private var currentFragment: CustomFilePickerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (ThemeHelper.isLightThemeSelected(this)) {
            setTheme(R.style.FilePickerThemeLight)
        } else {
            setTheme(R.style.FilePickerThemeDark)
        }
        super.onCreate(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val fragment = currentFragment
        // If at top most level, normal behaviour
        if (fragment == null || fragment.isBackTop) {
            super.onBackPressed()
        } else {
            // Else go up
            fragment.goUp()
        }
    }

    override fun getFragment(
        startPath: String?,
        mode: Int,
        allowMultiple: Boolean,
        allowCreateDir: Boolean,
        allowExistingFile: Boolean,
        singleClick: Boolean
    ): AbstractFilePickerFragment<File> {
        val fragment = CustomFilePickerFragment()
        fragment.setArgs(
            startPath ?: Environment.getExternalStorageDirectory().path,
            mode,
            allowMultiple,
            allowCreateDir,
            allowExistingFile,
            singleClick
        )
        currentFragment = fragment
        return fragment
    }

    class CustomFilePickerFragment : FilePickerFragment() {
        private var customToast: Toast? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val viewHolder = super.onCreateViewHolder(parent, viewType)

            (viewHolder.itemView.findViewById<View>(android.R.id.text1) as? TextView)?.apply {
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.file_picker_items_text_size)
                )
            }

            return viewHolder
        }

        override fun onClickOk(view: View) {
            if (mode == MODE_NEW_FILE && newFileName.isEmpty()) {
                customToast?.cancel()
                customToast = Toast.makeText(
                    activity, R.string.file_name_empty_error,
                    Toast.LENGTH_SHORT
                ).apply { show() }
                return
            }

            super.onClickOk(view)
        }

        override fun isItemVisible(file: File): Boolean {
            return if (file.isDirectory && file.isHidden) {
                true
            } else {
                super.isItemVisible(file)
            }
        }

        private val backTop: File
            get() {
                val args = arguments ?: return Environment.getExternalStorageDirectory()

                val path = args.getString(KEY_START_PATH, "/") ?: "/"
                return if (path.contains(Environment.getExternalStorageDirectory().path)) {
                    Environment.getExternalStorageDirectory()
                } else {
                    File(path)
                }
            }

        val isBackTop: Boolean
            get() = (compareFiles(mCurrentPath, backTop) == 0) ||
                    (compareFiles(mCurrentPath, File("/")) == 0)

        override fun onLoadFinished(loader: Loader<SortedList<File>>, data: SortedList<File>?) {
            super.onLoadFinished(loader, data)
            layoutManager.scrollToPosition(0)
        }
    }

    companion object {
        @JvmStatic
        fun isOwnFileUri(context: Context, uri: Uri): Boolean {
            val authority = uri.authority ?: return false
            return authority.startsWith(context.packageName)
        }
    }
}
