package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader

object ExtractorEngineHelper {
    private const val TAG = "ExtractorEngineHelper"

    enum class Engine {
        NEWPIPE,
        PIPEPIPE
    }

    @JvmStatic
    fun parsePreferenceValue(
        value: String?,
        newPipeValue: String,
        pipePipeValue: String
    ): Engine = when (value) {
        pipePipeValue -> Engine.PIPEPIPE
        newPipeValue -> Engine.NEWPIPE
        else -> Engine.NEWPIPE
    }

    @JvmStatic
    fun getSelectedEngine(context: Context): Engine = getSelectedEngine(
        context,
        PreferenceManager.getDefaultSharedPreferences(context)
    )

    @JvmStatic
    fun getSelectedEngine(context: Context, preferences: SharedPreferences): Engine {
        val newPipeValue = context.getString(R.string.extractor_engine_newpipe_value)
        val selectedValue = preferences.getString(
            context.getString(R.string.extractor_engine_key),
            context.getString(R.string.extractor_engine_default_value)
        )
        return parsePreferenceValue(
            selectedValue,
            newPipeValue,
            context.getString(R.string.extractor_engine_pipepipe_value)
        ).also { engine ->
            if (engine == Engine.NEWPIPE && selectedValue != null && selectedValue != newPipeValue) {
                Log.w(TAG, "Unknown extractor engine preference '$selectedValue'; using NewPipe Extractor")
            }
        }
    }

    @JvmStatic
    fun initSelectedEngine(context: Context, downloader: Downloader) {
        val localization = Localization.getPreferredLocalization(context)
        val contentCountry = Localization.getPreferredContentCountry(context)
        when (getSelectedEngine(context)) {
            Engine.NEWPIPE -> NewPipe.init(downloader, localization, contentCountry)

            Engine.PIPEPIPE -> {
                Log.w(
                    TAG,
                    "PipePipe Extractor cannot be initialized from the current classpath " +
                        "because the verified PipePipeExtractor source uses the same " +
                        "org.schabi.newpipe.extractor packages/classes as NewPipe Extractor; " +
                        "falling back to NewPipe Extractor"
                )
                try {
                    NewPipe.init(downloader, localization, contentCountry)
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Fallback NewPipe Extractor initialization failed", throwable)
                    throw throwable
                }
            }
        }
    }
}
