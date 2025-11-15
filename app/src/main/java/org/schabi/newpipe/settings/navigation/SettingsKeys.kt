package org.schabi.newpipe.settings.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface SettingsKey : NavKey

@Serializable
data object SettingsHome : SettingsKey

@Serializable
data object PlayerSettings : SettingsKey

@Serializable
data object BehaviourSettings : SettingsKey

@Serializable
data object DownloadSettings : SettingsKey

@Serializable
data object LookFeelSettings : SettingsKey

@Serializable
data object HistoryCacheSettings : SettingsKey

@Serializable
data object ContentSettings : SettingsKey

@Serializable
data object FeedSettings : SettingsKey

@Serializable
data object ServicesSettings : SettingsKey

@Serializable
data object LanguageSettings : SettingsKey

@Serializable
data object BackupRestoreSettings : SettingsKey

@Serializable
data object UpdateSettings : SettingsKey

@Serializable
data object DebugSettings : SettingsKey
