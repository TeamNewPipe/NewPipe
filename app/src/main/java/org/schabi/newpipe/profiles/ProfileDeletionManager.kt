package org.schabi.newpipe.profiles

import android.content.Context
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.learning.LearningReminders
import org.schabi.newpipe.local.bookmark.PlaylistCategories
import org.schabi.newpipe.local.feed.notifications.StickyNotificationCoordinator

object ProfileDeletionManager {
    // Delete only data that has become profile-scoped so later phases can extend cleanup safely.
    @JvmStatic
    fun deleteProfile(context: Context, profileId: String): Completable {
        if (!ProfileManager.canDeleteProfile(context, profileId)) {
            return Completable.error(
                IllegalArgumentException("Choose another default profile before deleting this profile")
            )
        }

        val appContext = context.applicationContext
        return Completable.fromAction {
            val finishDeletion = checkNotNull(ProfileManager.prepareProfileDeletion(appContext, profileId))

            // Delete optional archive data before mutating the Room profile tables.
            org.schabi.newpipe.local.feed.archive.forgetProfileFeedArchive(appContext, profileId)
            val database = NewPipeDatabase.getInstance(appContext)
            database.runInTransaction {
                database.pendingStreamNotificationDAO().deleteProfile(profileId)
                database.savedSearchFeedDAO().deleteAllForProfile(profileId)
                database.playlistRemoteDAO().deleteAllForProfile(profileId)
                database.playlistDAO().deleteAllForProfile(profileId)
                database.streamHistoryDAO().deleteAllForProfile(profileId)
                database.streamStateDAO().deleteAllForProfile(profileId)
                database.searchHistoryDAO().deleteAllForProfile(profileId)
                database.learningSessionDAO().deleteAllForProfile(profileId)
                database.feedGroupDAO().deleteAllForProfile(profileId)
                database.subscriptionDAO().deleteAllForProfile(profileId)
                // A profile removal is local, unlike deleting its individual records. Keep the
                // journals intact and publish the durable profile tombstone before another Room
                // transaction can mistake the missing rows for deletions to send to a peer.
                check(finishDeletion()) {
                    "Profile metadata no longer exists"
                }
            }
            PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .remove(PlaylistCategories.preferenceKey(profileId))
                .apply()
            org.schabi.newpipe.local.tv.TvPlaylistStore(appContext, profileId).delete()
            database.streamDAO().deleteOrphans()
            LearningReminders.cancelNotification(appContext, profileId)
            LearningReminders.initialize(appContext)
            StickyNotificationCoordinator.requestReconcile(appContext)
        }.subscribeOn(Schedulers.io())
    }
}
