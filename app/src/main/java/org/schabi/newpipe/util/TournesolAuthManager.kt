package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.DownloaderImpl

object TournesolAuthManager {
    private const val TAG = "TournesolAuthManager"
    private const val SHARED_PREF_AUTH_STATE = "tournesol_auth_state"
    private const val SHARED_PREF_USERNAME = "tournesol_username"
    private const val SHARED_PREF_COMPARISON_COUNT = "tournesol_comparison_count"
    private const val SHARED_PREF_DAILY_BASELINE_DATE = "tournesol_daily_baseline_date"
    private const val SHARED_PREF_DAILY_BASELINE_COUNT = "tournesol_daily_baseline_count"
    private const val AUTH_STATE_PREFS_NAME = "tournesol_auth_state_prefs"
    private const val AUTH_URL = "https://api.tournesol.app/o/authorize/"
    private const val TOKEN_URL = "https://api.tournesol.app/o/token/"
    private const val OAUTH_SCOPE = "read write groups"
    private const val DEFAULT_MIN_TTL_MS = 2 * 60 * 1000L

    private fun getAuthStatePrefs(context: Context): SharedPreferences? {
        val appContext = context.applicationContext
        val encryptedPrefs = try {
            createEncryptedAuthPrefs(appContext)
        } catch (firstFailure: Exception) {
            Log.w(TAG, "Encrypted auth prefs are unreadable. Resetting auth storage.", firstFailure)
            clearAuthStateStorage(appContext)
            try {
                createEncryptedAuthPrefs(appContext)
            } catch (secondFailure: Exception) {
                Log.e(TAG, "Failed to recreate encrypted auth prefs after reset", secondFailure)
                null
            }
        } ?: return null

        migrateLegacyAuthState(appContext, encryptedPrefs)

        return encryptedPrefs
    }

    private fun createEncryptedAuthPrefs(appContext: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            AUTH_STATE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearAuthStateStorage(appContext: Context) {
        runCatching {
            appContext.getSharedPreferences(AUTH_STATE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        runCatching {
            appContext.deleteSharedPreferences(AUTH_STATE_PREFS_NAME)
        }
        runCatching {
            val prefsFile = File(
                appContext.applicationInfo.dataDir,
                "shared_prefs/$AUTH_STATE_PREFS_NAME.xml"
            )
            if (prefsFile.exists()) {
                prefsFile.delete()
            }
        }
    }

    private fun migrateLegacyAuthState(
        context: Context,
        encryptedPrefs: SharedPreferences
    ) {
        val legacyPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!legacyPrefs.contains(SHARED_PREF_AUTH_STATE)) {
            return
        }

        val legacyValue = legacyPrefs.getString(SHARED_PREF_AUTH_STATE, null)
        if (!legacyValue.isNullOrBlank() && !encryptedPrefs.contains(SHARED_PREF_AUTH_STATE)) {
            encryptedPrefs.edit().putString(SHARED_PREF_AUTH_STATE, legacyValue).apply()
        }
        legacyPrefs.edit().remove(SHARED_PREF_AUTH_STATE).apply()
    }

    fun saveUsername(context: Context, username: String) {
        val prefs = getAuthStatePrefs(context) ?: return
        prefs.edit().putString(SHARED_PREF_USERNAME, username).apply()
    }

    fun getUsername(context: Context): String? {
        val prefs = getAuthStatePrefs(context) ?: return null
        return prefs.getString(SHARED_PREF_USERNAME, null)?.takeIf { it.isNotBlank() }
    }

    fun saveComparisonCount(context: Context, count: Int) {
        val prefs = getAuthStatePrefs(context) ?: return
        prefs.edit().putInt(SHARED_PREF_COMPARISON_COUNT, count).apply()
    }

    fun getComparisonCount(context: Context): Int? {
        val prefs = getAuthStatePrefs(context) ?: return null
        return if (prefs.contains(SHARED_PREF_COMPARISON_COUNT)) {
            prefs.getInt(SHARED_PREF_COMPARISON_COUNT, 0)
        } else {
            null
        }
    }

    fun incrementComparisonCount(context: Context) {
        val current = getComparisonCount(context) ?: return
        saveComparisonCount(context, current + 1)
    }

    fun getDailyComparisons(context: Context): Int {
        val prefs = getAuthStatePrefs(context) ?: return 0
        val currentCount = getComparisonCount(context) ?: return 0
        val today = java.time.LocalDate.now().toString()
        val storedDate = prefs.getString(SHARED_PREF_DAILY_BASELINE_DATE, null)
        if (storedDate == today) {
            val baseline = prefs.getInt(SHARED_PREF_DAILY_BASELINE_COUNT, currentCount)
            return (currentCount - baseline).coerceAtLeast(0)
        }
        // New day: reset baseline to current count
        prefs.edit()
            .putString(SHARED_PREF_DAILY_BASELINE_DATE, today)
            .putInt(SHARED_PREF_DAILY_BASELINE_COUNT, currentCount)
            .apply()
        return 0
    }

    fun clearAuthState(context: Context) {
        val prefs = getAuthStatePrefs(context) ?: return
        prefs.edit()
            .remove(SHARED_PREF_AUTH_STATE)
            .remove(SHARED_PREF_USERNAME)
            .remove(SHARED_PREF_COMPARISON_COUNT)
            .remove(SHARED_PREF_DAILY_BASELINE_DATE)
            .remove(SHARED_PREF_DAILY_BASELINE_COUNT)
            .apply()
    }

    fun saveAuthState(context: Context, authState: AuthState) {
        val prefs = getAuthStatePrefs(context) ?: return
        prefs.edit().putString(SHARED_PREF_AUTH_STATE, authState.jsonSerializeString()).apply()
    }

    fun saveAuthState(context: Context, tokenResponse: TokenResponse) {
        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_URL),
            android.net.Uri.parse(TOKEN_URL)
        )
        val authState = AuthState(config)
        authState.update(tokenResponse, null)
        saveAuthState(context, authState)
    }

    fun getAuthState(context: Context): AuthState? {
        val prefs = getAuthStatePrefs(context) ?: return null
        val json = prefs.getString(SHARED_PREF_AUTH_STATE, null) ?: return null
        return try {
            AuthState.jsonDeserialize(json)
        } catch (_: Exception) {
            null
        }
    }

    fun getValidAccessToken(
        context: Context,
        minTtlMs: Long = DEFAULT_MIN_TTL_MS
    ): Maybe<String> {
        val authState = getAuthState(context) ?: return Maybe.empty()
        val token = authState.accessToken
        val expirationTime = authState.accessTokenExpirationTime
        val now = System.currentTimeMillis()
        val needsRefresh = token.isNullOrBlank() ||
            (expirationTime != null && expirationTime <= now + minTtlMs)
        if (!needsRefresh) {
            return Maybe.just(token)
        }

        val refreshToken = authState.refreshToken
        if (refreshToken.isNullOrBlank()) {
            return Maybe.empty()
        }

        return performTokenRefresh(refreshToken)
            .flatMapMaybe { tokenResponse ->
                authState.update(tokenResponse, null)
                saveAuthState(context, authState)
                val refreshedToken = authState.accessToken
                if (refreshedToken.isNullOrBlank()) {
                    Maybe.empty()
                } else {
                    Maybe.just(refreshedToken)
                }
            }
    }

    fun performPasswordLogin(username: String, password: String): Single<TokenResponse> {
        val formBody = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", username)
            .add("password", password)
            .add("scope", OAUTH_SCOPE)
            .build()

        return performTokenRequestWithFallback(
            formBody = formBody,
            grantType = "password",
            refreshTokenFallback = null
        )
    }

    fun performTokenRefresh(refreshToken: String): Single<TokenResponse> {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", OAUTH_SCOPE)
            .build()

        return performTokenRequestWithFallback(
            formBody = formBody,
            grantType = "refresh_token",
            refreshTokenFallback = refreshToken
        )
    }

    private fun performTokenRequestWithFallback(
        formBody: FormBody,
        grantType: String,
        refreshTokenFallback: String?
    ): Single<TokenResponse> {
        val initialSecrets = TournesolSecretExtractor.getDefaultOrCachedSecrets()
        return performTokenRequest(
            clientId = initialSecrets.first,
            clientSecret = initialSecrets.second,
            formBody = formBody,
            grantType = grantType,
            refreshTokenFallback = refreshTokenFallback
        ).onErrorResumeNext { error ->
            if (error.message?.contains("invalid_client", ignoreCase = true) == true) {
                TournesolSecretExtractor().extractSecrets()
                    .flatMap { refreshedSecrets ->
                        performTokenRequest(
                            clientId = refreshedSecrets.first,
                            clientSecret = refreshedSecrets.second,
                            formBody = formBody,
                            grantType = grantType,
                            refreshTokenFallback = refreshTokenFallback
                        )
                    }
            } else {
                Single.error(error)
            }
        }
    }

    private fun performTokenRequest(
        clientId: String,
        clientSecret: String,
        formBody: FormBody,
        grantType: String,
        refreshTokenFallback: String?
    ): Single<TokenResponse> {
        return Single.create { emitter ->
            val baseClient = DownloaderImpl.getInstance()?.getClient() ?: OkHttpClient()
            val client = baseClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val authString = "$clientId:$clientSecret"
            val authHeader = "Basic " + Base64.encodeToString(
                authString.toByteArray(),
                Base64.NO_WRAP
            )

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val errorLabel = if (grantType == "password") "Login failed" else "Token refresh failed"

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val responseBody = response.body!!.string()
                        val json = JsonParser.`object`().from(responseBody)
                        emitter.onSuccess(
                            buildTokenResponse(
                                clientId = clientId,
                                grantType = grantType,
                                json = json,
                                refreshTokenFallback = refreshTokenFallback
                            )
                        )
                    } else {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        emitter.onError(IOException("$errorLabel: ${response.code} $errorBody"))
                    }
                }
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
    }

    private fun buildTokenResponse(
        clientId: String,
        grantType: String,
        json: JsonObject,
        refreshTokenFallback: String?
    ): TokenResponse {
        val accessToken = json.getString("access_token", "")
        if (accessToken.isBlank()) {
            throw IOException("Token response missing access_token")
        }

        val config = AuthorizationServiceConfiguration(
            android.net.Uri.parse(AUTH_URL),
            android.net.Uri.parse(TOKEN_URL)
        )
        val requestBuilder = TokenRequest.Builder(config, clientId)
            .setGrantType(grantType)
        if (grantType == "refresh_token" && !refreshTokenFallback.isNullOrBlank()) {
            requestBuilder.setRefreshToken(refreshTokenFallback)
        }
        val builder = TokenResponse.Builder(requestBuilder.build())

        builder.setAccessToken(accessToken)
        builder.setTokenType(json.getString("token_type", ""))
        if (json.has("expires_in")) {
            builder.setAccessTokenExpirationTime(
                System.currentTimeMillis() + (json.getLong("expires_in") * 1000)
            )
        }

        val refreshToken = json.getString("refresh_token", "").takeIf { it.isNotBlank() }
            ?: refreshTokenFallback
        if (!refreshToken.isNullOrBlank()) {
            builder.setRefreshToken(refreshToken)
        }

        val scope = json.getString("scope", "")
        if (scope.isNotBlank()) {
            builder.setScope(scope)
        }

        return builder.build()
    }
}
