package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Manager for handling blocked channels.
 * Stores blocked channel IDs in SharedPreferences and provides methods to add, remove, and check blocked channels.
 */
object BlockedChannelsManager {
    private const val BLOCKED_CHANNELS_KEY = "blocked_channels"
    private const val DELIMITER = ","

    /**
     * Get the SharedPreferences instance
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Get the set of blocked channel IDs
     */
    fun getBlockedChannelIds(context: Context): Set<String> {
        val prefs = getPreferences(context)
        val blockedString = prefs.getString(BLOCKED_CHANNELS_KEY, "") ?: ""
        return if (blockedString.isEmpty()) {
            emptySet()
        } else {
            blockedString.split(DELIMITER).toSet()
        }
    }

    /**
     * Check if a channel is blocked
     *
     * @param context Application context
     * @param channelUrl The channel URL to check
     * @return true if the channel is blocked, false otherwise
     */
    fun isChannelBlocked(context: Context, channelUrl: String?): Boolean {
        if (channelUrl.isNullOrEmpty()) {
            return false
        }
        return getBlockedChannelIds(context).contains(channelUrl)
    }

    /**
     * Block a channel
     *
     * @param context Application context
     * @param channelUrl The channel URL to block
     * @param channelName The channel name (for logging/debugging)
     */
    fun blockChannel(context: Context, channelUrl: String, channelName: String? = null) {
        val blockedChannels = getBlockedChannelIds(context).toMutableSet()
        blockedChannels.add(channelUrl)
        saveBlockedChannels(context, blockedChannels)
    }

    /**
     * Unblock a channel
     *
     * @param context Application context
     * @param channelUrl The channel URL to unblock
     */
    fun unblockChannel(context: Context, channelUrl: String) {
        val blockedChannels = getBlockedChannelIds(context).toMutableSet()
        blockedChannels.remove(channelUrl)
        saveBlockedChannels(context, blockedChannels)
    }

    /**
     * Get all blocked channels as a list of channel URLs
     *
     * @param context Application context
     * @return List of blocked channel URLs
     */
    fun getBlockedChannelsList(context: Context): List<String> {
        return getBlockedChannelIds(context).toList()
    }

    /**
     * Clear all blocked channels
     *
     * @param context Application context
     */
    fun clearAllBlockedChannels(context: Context) {
        getPreferences(context).edit()
            .remove(BLOCKED_CHANNELS_KEY)
            .apply()
    }

    /**
     * Save the blocked channels set to SharedPreferences
     */
    private fun saveBlockedChannels(context: Context, blockedChannels: Set<String>) {
        val blockedString = blockedChannels.joinToString(DELIMITER)
        getPreferences(context).edit()
            .putString(BLOCKED_CHANNELS_KEY, blockedString)
            .apply()
    }
}
