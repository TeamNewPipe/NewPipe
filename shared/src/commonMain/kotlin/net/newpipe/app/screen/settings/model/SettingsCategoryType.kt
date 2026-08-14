package net.newpipe.app.screen.settings.model

import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.content
import newpipe.shared.generated.resources.ic_bug_report
import newpipe.shared.generated.resources.ic_cloud_download
import newpipe.shared.generated.resources.ic_file_download
import newpipe.shared.generated.resources.ic_headset
import newpipe.shared.generated.resources.ic_history
import newpipe.shared.generated.resources.ic_language
import newpipe.shared.generated.resources.ic_notifications
import newpipe.shared.generated.resources.ic_palette
import newpipe.shared.generated.resources.ic_settings_backup_restore
import newpipe.shared.generated.resources.notifications
import newpipe.shared.generated.resources.settings_category_appearance_title
import newpipe.shared.generated.resources.settings_category_backup_restore_title
import newpipe.shared.generated.resources.settings_category_debug_title
import newpipe.shared.generated.resources.settings_category_downloads_title
import newpipe.shared.generated.resources.settings_category_history_title
import newpipe.shared.generated.resources.settings_category_updates_title
import newpipe.shared.generated.resources.settings_category_video_audio_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Categories on the settings home screen.
 */
enum class SettingsCategoryType(
    val title: StringResource,
    val icon: DrawableResource
) {
    VIDEO_AUDIO(Res.string.settings_category_video_audio_title, Res.drawable.ic_headset),
    DOWNLOADS(Res.string.settings_category_downloads_title, Res.drawable.ic_file_download),
    APPEARANCE(Res.string.settings_category_appearance_title, Res.drawable.ic_palette),
    HISTORY(Res.string.settings_category_history_title, Res.drawable.ic_history),
    CONTENT(Res.string.content, Res.drawable.ic_language),
    NOTIFICATIONS(Res.string.notifications, Res.drawable.ic_notifications),
    UPDATES(Res.string.settings_category_updates_title, Res.drawable.ic_cloud_download),
    BACKUP_RESTORE(Res.string.settings_category_backup_restore_title, Res.drawable.ic_settings_backup_restore),
    DEBUG(Res.string.settings_category_debug_title, Res.drawable.ic_bug_report)
}
