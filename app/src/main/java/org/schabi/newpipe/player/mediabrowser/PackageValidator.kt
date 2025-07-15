/*
 * Copyright 2018 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// THIS FILE WAS TAKEN FROM UAMP, EXCEPT FOR THINGS RELATED TO THE WHITELIST. UPDATE IT WHEN NEEDED.
// https://github.com/android/uamp/blob/329a21b63c247e9bd35f6858d4fc0e448fa38603/common/src/main/java/com/example/android/uamp/media/PackageValidator.kt

package org.schabi.newpipe.player.mediabrowser

import android.Manifest.permission.MEDIA_CONTENT_CONTROL
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
import android.content.pm.PackageManager
import android.os.Process
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import org.schabi.newpipe.BuildConfig
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Validates that the calling package is authorized to browse a [MediaBrowserServiceCompat].
 *
 * The list of allowed signing certificates and their corresponding package names is defined in
 * res/xml/allowed_media_browser_callers.xml.
 *
 * If you want to add a new caller to allowed_media_browser_callers.xml and you don't know
 * its signature, this class will print to logcat (INFO level) a message with the proper
 * xml tags to add to allow the caller.
 *
 * For more information, see res/xml/allowed_media_browser_callers.xml.
 */
internal class PackageValidator(context: Context) {
    private val context: Context = context.applicationContext
    private val packageManager: PackageManager = this.context.packageManager
    private val platformSignature: String = getSystemSignature()
    private val callerChecked = mutableMapOf<String, Pair<Int, Boolean>>()

    /**
     * Checks whether the caller attempting to connect to a [MediaBrowserServiceCompat] is known.
     * See [MusicService.onGetRoot] for where this is utilized.
     *
     * @param callingPackage The package name of the caller.
     * @param callingUid The user id of the caller.
     * @return `true` if the caller is known, `false` otherwise.
     */
    fun isKnownCaller(callingPackage: String, callingUid: Int): Boolean {
        // If the caller has already been checked, return the previous result here.
        val (checkedUid, checkResult) = callerChecked[callingPackage] ?: Pair(0, false)
        if (checkedUid == callingUid) {
            return checkResult
        }

        /**
         * Because some of these checks can be slow, we save the results in [callerChecked] after
         * this code is run.
         *
         * In particular, there's little reason to recompute the calling package's certificate
         * signature (SHA-256) each call.
         *
         * This is safe to do as we know the UID matches the package's UID (from the check above),
         * and app UIDs are set at install time. Additionally, a package name + UID is guaranteed to
         * be constant until a reboot. (After a reboot then a previously assigned UID could be
         * reassigned.)
         */

        // Build the caller info for the rest of the checks here.
        val callerPackageInfo = buildCallerInfo(callingPackage)
            ?: throw IllegalStateException("Caller wasn't found in the system?")

        // Verify that things aren't ... broken. (This test should always pass.)
        if (callerPackageInfo.uid != callingUid) {
            throw IllegalStateException("Caller's package UID doesn't match caller's actual UID?")
        }

        val callerSignature = callerPackageInfo.signature

        val isCallerKnown = when {
            // If it's our own app making the call, allow it.
            callingUid == Process.myUid() -> true
            // If the system is making the call, allow it.
            callingUid == Process.SYSTEM_UID -> true
            // If the app was signed by the same certificate as the platform itself, also allow it.
            callerSignature == platformSignature -> true
            /**
             * [MEDIA_CONTENT_CONTROL] permission is only available to system applications, and
             * while it isn't required to allow these apps to connect to a
             * [MediaBrowserServiceCompat], allowing this ensures optimal compatability with apps
             * such as Android TV and the Google Assistant.
             */
            callerPackageInfo.permissions.contains(MEDIA_CONTENT_CONTROL) -> true
            /**
             * If the calling app has a notification listener it is able to retrieve notifications
             * and can connect to an active [MediaSessionCompat].
             *
             * It's not required to allow apps with a notification listener to
             * connect to your [MediaBrowserServiceCompat], but it does allow easy compatibility
             * with apps such as Wear OS.
             */
            NotificationManagerCompat.getEnabledListenerPackages(this.context)
                .contains(callerPackageInfo.packageName) -> true

            // If none of the previous checks succeeded, then the caller is unrecognized.
            else -> false
        }

        if (!isCallerKnown) {
            logUnknownCaller(callerPackageInfo)
        }

        // Save our work for next time.
        callerChecked[callingPackage] = Pair(callingUid, isCallerKnown)
        return isCallerKnown
    }

    /**
     * Logs an info level message with details of how to add a caller to the allowed callers list
     * when the app is debuggable.
     */
    private fun logUnknownCaller(callerPackageInfo: CallerPackageInfo) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "Unknown caller $callerPackageInfo")
        }
    }

    /**
     * Builds a [CallerPackageInfo] for a given package that can be used for all the
     * various checks that are performed before allowing an app to connect to a
     * [MediaBrowserServiceCompat].
     */
    private fun buildCallerInfo(callingPackage: String): CallerPackageInfo? {
        val packageInfo = getPackageInfo(callingPackage) ?: return null

        val appName = packageInfo.applicationInfo?.loadLabel(packageManager).toString()
        val uid = packageInfo.applicationInfo?.uid ?: -1
        val signature = getSignature(packageInfo)

        val requestedPermissions = packageInfo.requestedPermissions
        val permissionFlags = packageInfo.requestedPermissionsFlags
        val activePermissions = mutableSetOf<String>()
        if (permissionFlags != null) {
            requestedPermissions?.forEachIndexed { index, permission ->
                if (permissionFlags[index] and REQUESTED_PERMISSION_GRANTED != 0) {
                    activePermissions += permission
                }
            }
        }

        return CallerPackageInfo(appName, callingPackage, uid, signature, activePermissions.toSet())
    }

    /**
     * Looks up the [PackageInfo] for a package name.
     * This requests both the signatures (for checking if an app is on the allow list) and
     * the app's permissions, which allow for more flexibility in the allow list.
     *
     * @return [PackageInfo] for the package name or null if it's not found.
     */
    @Suppress("deprecation")
    @SuppressLint("PackageManagerGetSignatures")
    private fun getPackageInfo(callingPackage: String): PackageInfo? =
        packageManager.getPackageInfo(
            callingPackage,
            PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS
        )

    /**
     * Gets the signature of a given package's [PackageInfo].
     *
     * The "signature" is a SHA-256 hash of the public key of the signing certificate used by
     * the app.
     *
     * If the app is not found, or if the app does not have exactly one signature, this method
     * returns `null` as the signature.
     */
    @Suppress("deprecation")
    private fun getSignature(packageInfo: PackageInfo): String? =
        if (packageInfo.signatures == null || packageInfo.signatures!!.size != 1) {
            // Security best practices dictate that an app should be signed with exactly one (1)
            // signature. Because of this, if there are multiple signatures, reject it.
            null
        } else {
            val certificate = packageInfo.signatures!![0].toByteArray()
            getSignatureSha256(certificate)
        }

    /**
     * Finds the Android platform signing key signature. This key is never null.
     */
    private fun getSystemSignature(): String =
        getPackageInfo(ANDROID_PLATFORM)?.let { platformInfo ->
            getSignature(platformInfo)
        } ?: throw IllegalStateException("Platform signature not found")

    /**
     * Creates a SHA-256 signature given a certificate byte array.
     */
    private fun getSignatureSha256(certificate: ByteArray): String {
        val md: MessageDigest
        try {
            md = MessageDigest.getInstance("SHA256")
        } catch (noSuchAlgorithmException: NoSuchAlgorithmException) {
            Log.e(TAG, "No such algorithm: $noSuchAlgorithmException")
            throw RuntimeException("Could not find SHA256 hash algorithm", noSuchAlgorithmException)
        }
        md.update(certificate)

        // This code takes the byte array generated by `md.digest()` and joins each of the bytes
        // to a string, applying the string format `%02x` on each digit before it's appended, with
        // a colon (':') between each of the items.
        // For example: input=[0,2,4,6,8,10,12], output="00:02:04:06:08:0a:0c"
        return md.digest().joinToString(":") { String.format("%02x", it) }
    }

    /**
     * Convenience class to hold all of the information about an app that's being checked
     * to see if it's a known caller.
     */
    private data class CallerPackageInfo(
        val name: String,
        val packageName: String,
        val uid: Int,
        val signature: String?,
        val permissions: Set<String>
    )
}

private const val TAG = "PackageValidator"
private const val ANDROID_PLATFORM = "android"
