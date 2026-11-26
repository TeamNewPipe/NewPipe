package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.notification.PendingStreamNotification
import org.schabi.newpipe.database.notification.PendingStreamNotificationDAO
import org.schabi.newpipe.error.CrashRecoveryManager
import org.schabi.newpipe.profiles.ProfileManager

/** Serializes acknowledgement and notification projection. Feed transactions never take [lock]. */
object StickyNotificationCoordinator {
    internal const val RESTORE_WORK = "sticky_stream_notifications_restore"
    private val lock = Any()
    private var observedPreferences: SharedPreferences? = null
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        StickyNotificationSettings.ensureGeneration(appContext)
        if (observedPreferences !== preferences) {
            preferenceListener?.let { observedPreferences?.unregisterOnSharedPreferenceChangeListener(it) }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val settingsChanged = key == StickyNotificationSettings.ENABLED_KEY ||
                    key == appContext.getString(R.string.enable_streams_notifications)
                if (settingsChanged && !StickyNotificationSettings.isEnabled(appContext)) {
                    // Also invalidate changes made outside the settings screen, such as imports.
                    StickyNotificationSettings.invalidate(appContext)
                }
                if (settingsChanged || key == StickyNotificationSettings.GENERATION_KEY ||
                    key == ProfileManager.ACTIVE_PROFILE_ID_PREFERENCE_KEY
                ) {
                    // ProfileManager may own its monitor here. Never query Room or acquire the
                    // coordinator monitor synchronously from a preference callback.
                    requestReconcile(appContext)
                }
            }
            observedPreferences = preferences
            preferenceListener = listener
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }
        requestReconcile(appContext)
    }

    fun requestReconcile(context: Context) {
        if (CrashRecoveryManager.isSafeModeActive(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            RESTORE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<StickyNotificationRestoreWorker>().build()
        )
    }

    /** Restore/close work is offline. New-alert artwork is prepared outside the close monitor. */
    internal fun reconcile(
        context: Context,
        silent: Boolean = true,
        alertChannels: Set<Pair<Int, String>> = emptySet()
    ) {
        val appContext = context.applicationContext
        if (silent || alertChannels.isEmpty()) {
            synchronized(lock) { reconcileLocked(appContext, silent = true) }
            return
        }
        val snapshot = synchronized(lock) { snapshotLocked(appContext) } ?: return
        // A notification broadcast must be able to persist Close immediately while a slow image
        // request is running. Neither Room nor the acknowledgement monitor is held here.
        val artwork = StickyNotificationRenderer.prepareArtwork(appContext, snapshot.session.profileId, snapshot.pending, alertChannels)
        synchronized(lock) {
            // Re-read the database after decoding. An item closed during that wait must never be
            // reposted, and an off/on operation or profile switch invalidates the whole snapshot.
            reconcileLocked(appContext, silent = false, alertChannels, artwork, snapshot.session)
        }
    }

    private fun reconcileLocked(
        context: Context,
        silent: Boolean,
        alertChannels: Set<Pair<Int, String>> = emptySet(),
        artwork: Map<Pair<Int, String>, Bitmap> = emptyMap(),
        expectedSession: StickyNotificationSession? = null
    ) {
        val snapshot = snapshotLocked(context) ?: return
        if (expectedSession != null && snapshot.session != expectedSession) return
        StickyNotificationRenderer.render(context, snapshot.session.profileId, snapshot.pending, silent, alertChannels, artwork)
        if (!StickyNotificationSettings.isCurrent(context, snapshot.session)) {
            StickyNotificationRenderer.cancelAll(context)
        }
    }

    private fun snapshotLocked(context: Context): Projection? {
        if (CrashRecoveryManager.isSafeModeActive(context)) {
            StickyNotificationRenderer.cancelAll(context)
            return null
        }
        val database = NewPipeDatabase.getInstance(context)
        val dao = database.pendingStreamNotificationDAO()
        database.runInTransaction {
            // Read preferences after acquiring the Room transaction. A new generation's feed
            // commit cannot interleave between this snapshot and the obsolete-row cleanup.
            val state = StickyNotificationSettings.read(context)
            dao.generations().filter { !state.enabled || it != state.generation }
                .forEach(dao::deleteGeneration)
        }
        val state = StickyNotificationSettings.read(context)
        val generation = state.generation
        if (!state.enabled) {
            StickyNotificationRenderer.cancelAll(context)
            return null
        }
        val profileId = ProfileManager.getActiveProfileId(context)
        val session = StickyNotificationSession(profileId, generation)
        if (!canOpen(context, profileId, generation) ||
            !NotificationHelper.areNotificationsEnabledOnDevice(context)
        ) {
            StickyNotificationRenderer.cancelAll(context)
            return null
        }
        val pending = dao.pending(profileId, generation)
        // A profile/switch may have changed while Room was read. Never render a stale snapshot.
        if (!StickyNotificationSettings.isCurrent(context, session)) return null
        return Projection(session, pending)
    }

    /** Content PendingIntents never switch profiles or bypass a parent's lock. */
    fun canOpen(context: Context, profileId: String, generation: String): Boolean = StickyNotificationSettings.isCurrent(context, StickyNotificationSession(profileId, generation)) &&
        ProfileManager.getProfile(context, profileId) != null && !CrashRecoveryManager.isSafeModeActive(context)

    fun closeItem(
        context: Context,
        profileId: String,
        generation: String,
        serviceId: Int,
        streamUrl: String
    ): Completable = close(context, profileId, generation) {
        it.closeItem(profileId, generation, serviceId, streamUrl)
    }

    fun closeChannel(
        context: Context,
        profileId: String,
        generation: String,
        serviceId: Int,
        channelUrl: String
    ): Completable = close(context, profileId, generation) {
        it.closeChannel(profileId, generation, serviceId, channelUrl)
    }

    fun closeAll(context: Context, profileId: String, generation: String): Completable = close(context, profileId, generation) { it.closeAll(profileId, generation) }

    private fun close(
        context: Context,
        profileId: String,
        generation: String,
        acknowledge: (PendingStreamNotificationDAO) -> Unit
    ): Completable = Completable.fromAction {
        synchronized(lock) {
            if (!canOpen(context, profileId, generation)) return@synchronized
            val database = NewPipeDatabase.getInstance(context)
            // Commit first. A crash between this transaction and cancellation must still restore
            // the acknowledged state, and a repeated PendingIntent is harmless.
            database.runInTransaction {
                if (StickyNotificationSettings.isCurrent(context, StickyNotificationSession(profileId, generation))) {
                    acknowledge(database.pendingStreamNotificationDAO())
                }
            }
            reconcileLocked(context.applicationContext, silent = true)
        }
    }.subscribeOn(Schedulers.io())

    internal fun resetForTests() {
        preferenceListener?.let { observedPreferences?.unregisterOnSharedPreferenceChangeListener(it) }
        preferenceListener = null
        observedPreferences = null
    }

    private data class Projection(val session: StickyNotificationSession, val pending: List<PendingStreamNotification>)
}
