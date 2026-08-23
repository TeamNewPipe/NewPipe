package org.schabi.newpipe.youtube

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.SharedWebViewRuntime
import org.schabi.newpipe.extractor.services.youtube.YoutubePoTokenResult
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object LocalDomPoTokenProvider {
    private lateinit var appContext: Context
    private val initializationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YoutubePoTokenWarmup").apply { isDaemon = true }
    }
    private val initializationLock = Any()
    @Volatile
    private var initializationTask: FutureTask<MintState>? = null

    fun warmUp() {
        ensureInitializationTask()
    }

    fun getPlayerPoToken(videoId: String): YoutubePoTokenResult {
        val state = getState()
        val token = when (state.bootstrap.binding) {
            YoutubePoTokenBinding.CONTENT -> state.session.mint(videoId)
            YoutubePoTokenBinding.SESSION -> requireNotNull(state.sessionPoToken).clone()
            YoutubePoTokenBinding.NONE -> throw SabrProtocolException(
                "YouTube home does not enable a supported PO token binding",
            )
        }
        return YoutubePoTokenResult(
            state.bootstrap.visitorData,
            state.bootstrap.clientVersion,
            Base64.getUrlEncoder().withoutPadding().encodeToString(token),
        )
    }

    fun invalidate() {
        val sessionToClose: PersistentMintSession
        synchronized(initializationLock) {
            val task = initializationTask ?: return
            if (!task.isDone || task.isCancelled) return
            val state = try {
                task.get()
            } catch (_: Exception) {
                return
            }
            initializationTask = null
            sessionToClose = state.session
        }
        sessionToClose.close()
        Log.i(TAG, "Invalidated rejected PO token minter")
        warmUp()
    }

    private fun getState(): MintState {
        while (true) {
            val task = ensureInitializationTask()
            val state = try {
                task.get()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SabrProtocolException("Global PO token initialization interrupted", error)
            } catch (error: ExecutionException) {
                synchronized(initializationLock) {
                    if (initializationTask === task) {
                        initializationTask = null
                    }
                }
                val cause = error.cause ?: error
                throw SabrProtocolException(
                    "Global PO token initialization failed: ${cause.message}",
                    cause,
                )
            }
            if (!state.session.isExpired()) {
                return state
            }
            synchronized(initializationLock) {
                if (initializationTask === task) {
                    initializationTask = null
                    state.session.close()
                }
            }
        }
    }

    private fun ensureInitializationTask(): FutureTask<MintState> {
        initializationTask?.let { return it }
        synchronized(initializationLock) {
            initializationTask?.let { return it }
            val task = FutureTask {
                val loginCookies = PreferenceManager.getDefaultSharedPreferences(appContext)
                    .getString(appContext.getString(R.string.youtube_cookies_key), null)
                    ?.takeIf(String::isNotBlank)
                val loggedIn = loginCookies != null
                val bootstrap = fetchHomeBootstrap(loginCookies)
                if (bootstrap.binding == YoutubePoTokenBinding.NONE) {
                    throw SabrProtocolException(
                        "YouTube home does not enable a supported PO token binding",
                    )
                }
                val session = PersistentMintSession.create(
                    appContext,
                    bootstrap,
                )
                try {
                    val sessionPoToken = if (bootstrap.binding == YoutubePoTokenBinding.SESSION) {
                        val sessionBinding = if (loggedIn) {
                            bootstrap.dataSyncId ?: throw SabrProtocolException(
                                "Authenticated YouTube home has no Data Sync ID",
                            )
                        } else {
                            bootstrap.visitorData
                        }
                        session.mint(sessionBinding)
                    } else {
                        null
                    }
                    Log.i(
                        TAG,
                        "Global PO minter ready client=${bootstrap.clientName} " +
                            "version=${bootstrap.clientVersion} " +
                            "binding=${bootstrap.binding}",
                    )
                    MintState(bootstrap, session, sessionPoToken)
                } catch (error: Throwable) {
                    session.close()
                    throw error
                }
            }
            initializationTask = task
            initializationExecutor.execute(task)
            return task
        }
    }

    private fun fetchHomeBootstrap(loginCookies: String?): YoutubePageAttestationBootstrap {
        val downloader = DownloaderImpl.getInstance()
            ?: throw SabrProtocolException("DownloaderImpl is not initialized")
        val response = downloader.get(
            YOUTUBE_HOME,
            mapOf(
                "Accept-Language" to listOf("en-US"),
                "Cookie" to listOf(loginCookies ?: ANONYMOUS_COOKIE),
                "User-Agent" to listOf(SharedWebViewRuntime.USER_AGENT),
            ),
        )
        if (response.responseCode() != 200) {
            throw SabrProtocolException(
                "YouTube home initialization failed: ${response.responseCode()}",
            )
        }
        return parseYoutubePageAttestationBootstrap(response.responseBody())
    }

    private data class MintState(
        val bootstrap: YoutubePageAttestationBootstrap,
        val session: PersistentMintSession,
        val sessionPoToken: ByteArray?,
    )

    @JvmStatic
    fun initialize(context: Context) {
        synchronized(initializationLock) {
            if (!::appContext.isInitialized) {
                appContext = context.applicationContext
            }
        }
        warmUp()
    }

    private const val TAG = "YoutubeGlobalPoToken"
    private const val YOUTUBE_HOME = "https://www.youtube.com"
    private const val ANONYMOUS_COOKIE = "PREF=hl=en&gl=US"
}

private class PersistentMintSession private constructor(
    context: Context,
    private val initialization: InitWaiter,
    private val bootstrap: YoutubePageAttestationBootstrap,
) : Closeable {
    private val runtime = SharedWebViewRuntime.get(context.applicationContext)
    private val sessionId = runtime.registerSabrLocalDomCallbacks(Callbacks())
    private val tokenWaiters = mutableMapOf<String, TokenWaiter>()
    @Volatile
    private var closed = false
    @Volatile
    private var expiresAtMs = Long.MAX_VALUE

    private fun loadScriptAndInitialize() {
        try {
            runtime.ensureReady(INIT_TIMEOUT_MS, "Local DOM PO token initialization")
            runtime.evaluateJavascriptBlocking(
                runtime.loadAsset(ASSET) + "\ntrue",
                INIT_TIMEOUT_MS,
                "Local DOM BotGuard helper injection",
            )
            downloadAndRunBotguard()
        } catch (error: Throwable) {
            failInitialization(error)
        }
    }

    @Synchronized
    @Throws(SabrProtocolException::class)
    fun mint(identifier: String): ByteArray {
        if (closed) {
            throw SabrProtocolException("Local DOM PO token session is closed")
        }
        val waiter = TokenWaiter()
        synchronized(tokenWaiters) {
            tokenWaiters[identifier] = waiter
        }
        val posted = runtime.evaluateJavascript(
            "pipeplaySabrObtainPoToken(" + jsonString(sessionId) + ", " +
                jsonString(identifier) + ", " + stringToSabrU8(identifier) + ");",
            null,
        ) { error -> onTokenError(identifier, error) }
        if (!posted) {
            synchronized(tokenWaiters) {
                tokenWaiters.remove(identifier)
            }
            throw SabrProtocolException("Could not post Local DOM PO token generation")
        }
        try {
            if (!waiter.latch.await(TOKEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                synchronized(tokenWaiters) {
                    tokenWaiters.remove(identifier)
                }
                throw SabrProtocolException("Local DOM PO token generation timed out")
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            synchronized(tokenWaiters) {
                tokenWaiters.remove(identifier)
            }
            throw SabrProtocolException("Local DOM PO token generation interrupted", error)
        }
        waiter.error.get()?.let {
            throw SabrProtocolException("Local DOM PO token generation failed: ${it.message}", it)
        }
        val token = waiter.token.get()
        if (token == null || token.isEmpty()) {
            throw SabrProtocolException("Local DOM PO token generation returned no token")
        }
        return token
    }

    fun isExpired(): Boolean {
        return closed || System.currentTimeMillis() >= expiresAtMs - EXPIRY_MARGIN_MS
    }

    override fun close() {
        closed = true
        runtime.unregisterSabrLocalDomCallbacks(sessionId)
        synchronized(tokenWaiters) {
            tokenWaiters.values.forEach {
                it.error.set(SabrProtocolException("Local DOM PO token session closed"))
                it.latch.countDown()
            }
            tokenWaiters.clear()
        }
        runtime.evaluateJavascript(
            "pipeplaySabrDeleteSession(" + jsonString(sessionId) + ");",
            null,
            null,
        )
    }

    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        contentType: String = "application/json+protobuf",
        extraHeaders: Map<String, List<String>> = emptyMap(),
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        Thread({
            try {
                val downloader = DownloaderImpl.getInstance()
                    ?: throw SabrProtocolException("DownloaderImpl is not initialized")
                val response = downloader.post(
                    url,
                    mapOf(
                        "User-Agent" to listOf(SharedWebViewRuntime.USER_AGENT),
                        "Accept" to listOf("application/json"),
                        "Content-Type" to listOf(contentType),
                        "x-goog-api-key" to listOf(LOCAL_DOM_GOOGLE_API_KEY),
                        "x-user-agent" to listOf("grpc-web-javascript/0.1"),
                    ) + extraHeaders,
                    data.toByteArray(),
                )
                if (response.responseCode() != 200) {
                    throw SabrProtocolException(
                        "Local DOM BotGuard request failed: ${response.responseCode()}",
                    )
                }
                onSuccess(response.responseBody())
            } catch (error: Throwable) {
                onError(error)
            }
        }, "SabrLocalDomPoTokenJnn").start()
    }

    private fun makeBotguardGetRequest(
        url: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        Thread({
            try {
                val downloader = DownloaderImpl.getInstance()
                    ?: throw SabrProtocolException("DownloaderImpl is not initialized")
                val response = downloader.get(
                    url,
                    mapOf(
                        "User-Agent" to listOf(SharedWebViewRuntime.USER_AGENT),
                        "Accept" to listOf("*/*"),
                    ),
                )
                if (response.responseCode() != 200) {
                    throw SabrProtocolException(
                        "Local DOM BotGuard GET failed: ${response.responseCode()}",
                    )
                }
                onSuccess(response.responseBody())
            } catch (error: Throwable) {
                onError(error)
            }
        }, "SabrLocalDomPoTokenJnnGet").start()
    }

    private fun failInitialization(error: Throwable) {
        initialization.error.compareAndSet(null, error)
        initialization.latch.countDown()
        close()
    }

    private fun completeInitialization() {
        initialization.session.compareAndSet(null, this)
        initialization.latch.countDown()
    }

    private fun onTokenResult(identifier: String, poTokenU8: String) {
        val waiter = synchronized(tokenWaiters) {
            tokenWaiters.remove(identifier)
        } ?: return
        try {
            waiter.token.set(csvU8ToByteArray(poTokenU8))
        } catch (error: Throwable) {
            waiter.error.set(error)
        } finally {
            waiter.latch.countDown()
        }
    }

    private fun onTokenError(identifier: String, error: Throwable) {
        val waiter = synchronized(tokenWaiters) {
            tokenWaiters.remove(identifier)
        } ?: return
        waiter.error.set(error)
        waiter.latch.countDown()
    }

    private fun downloadAndRunBotguard() {
        val challenge = bootstrap.challenge
        val inlineInterpreter = challenge.interpreterJavascript
        if (inlineInterpreter != null) {
            runBotguard(challenge, inlineInterpreter)
        } else {
            makeBotguardGetRequest(
                requireNotNull(challenge.interpreterUrl),
                onSuccess = { runBotguard(challenge, it) },
                onError = ::failInitialization,
            )
        }
    }

    private fun runBotguard(
        challenge: SabrAttChallengeData,
        interpreterJavascript: String,
    ) {
        runtime.evaluateJavascript(
            "pipeplaySabrRunBotguard(" + jsonString(sessionId) + ", " +
                jsonString(bootstrap.eventId) + ", " +
                buildSabrAttChallengeData(challenge, interpreterJavascript) + ");",
            null,
        ) { error -> failInitialization(error) }
    }

    private fun onRunBotguardResult(botguardResponse: String) {
        makeBotguardServiceRequest(
            "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
            onSuccess = { body ->
                try {
                    val integrityTokenData = parseSabrIntegrityTokenData(body)
                    val integrityToken = integrityTokenData.first
                    expiresAtMs = System.currentTimeMillis() +
                        TimeUnit.SECONDS.toMillis(integrityTokenData.second)
                    runtime.evaluateJavascript(
                        "pipeplaySabrCreateMinter(" + jsonString(sessionId) + ", " +
                            integrityToken + ");",
                        null,
                    ) { error -> failInitialization(error) }
                } catch (error: Throwable) {
                    failInitialization(error)
                }
            },
            onError = ::failInitialization,
        )
    }

    private inner class Callbacks : SharedWebViewRuntime.SabrLocalDomCallbacks {
        override fun onJsInitializationError(error: String) {
            failInitialization(SabrProtocolException(error))
        }

        override fun onRunBotguardResult(botguardResponse: String) {
            this@PersistentMintSession.onRunBotguardResult(botguardResponse)
        }

        override fun onMinterReady() {
            completeInitialization()
        }

        override fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
            onTokenResult(identifier, poTokenU8)
        }

        override fun onObtainPoTokenError(identifier: String, error: String) {
            onTokenError(identifier, SabrProtocolException(error))
        }
    }

    private class TokenWaiter {
        val latch = CountDownLatch(1)
        val token = AtomicReference<ByteArray>()
        val error = AtomicReference<Throwable>()
    }

    private class InitWaiter {
        val latch = CountDownLatch(1)
        val session = AtomicReference<PersistentMintSession>()
        val error = AtomicReference<Throwable>()
    }

    companion object {
        private const val ASSET = "sabr_po_token.js"
        private const val TOKEN_TIMEOUT_MS = 30_000L
        private const val INIT_TIMEOUT_MS = 60_000L
        private const val EXPIRY_MARGIN_MS = 60_000L
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

        @Throws(SabrProtocolException::class)
        fun create(
            context: Context,
            bootstrap: YoutubePageAttestationBootstrap,
        ): PersistentMintSession {
            val initialization = InitWaiter()
            val session = PersistentMintSession(
                context,
                initialization,
                bootstrap,
            )
            session.loadScriptAndInitialize()
            try {
                if (!initialization.latch.await(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    session.close()
                    throw SabrProtocolException("Local DOM PO token initialization timed out")
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                session.close()
                throw SabrProtocolException("Local DOM PO token initialization interrupted", error)
            }
            initialization.error.get()?.let {
                throw SabrProtocolException(
                    "Local DOM PO token initialization failed: ${it.message}",
                    it,
                )
            }
            return initialization.session.get()
                ?: throw SabrProtocolException(
                    "Local DOM PO token initialization returned no result",
                )
        }
    }
}

private const val LOCAL_DOM_GOOGLE_API_KEY =
    "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

private fun jsonString(value: String): String {
    return buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}

private fun buildSabrAttChallengeData(
    challengeData: SabrAttChallengeData,
    interpreterJavascript: String,
): String {
    return JsonWriter.string(
        JsonObject.builder()
            .`object`("interpreterJavascript")
            .value(
                "privateDoNotAccessOrElseSafeScriptWrappedValue",
                interpreterJavascript,
            )
            .end()
            .value("program", challengeData.program)
            .value("globalName", challengeData.globalName)
            .done(),
    )
}

private fun parseSabrIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JsonParser.array().from(rawIntegrityTokenData)
    return base64ToU8(integrityTokenData.getString(0)) to integrityTokenData.getLong(1)
}

private fun stringToSabrU8(value: String): String {
    return newUint8Array(value.toByteArray())
}

private fun csvU8ToByteArray(value: String): ByteArray {
    if (value.isBlank()) {
        return ByteArray(0)
    }
    return value.split(",").map { it.toUByte().toByte() }.toByteArray()
}

private fun base64ToU8(base64: String): String {
    return newUint8Array(base64ToByteArray(base64))
}

private fun newUint8Array(contents: ByteArray): String {
    return "new Uint8Array([" + contents.joinToString(separator = ",") {
        it.toUByte().toString()
    } + "])"
}

private fun base64ToByteArray(base64: String): ByteArray {
    val normalized = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')
    return Base64.getDecoder().decode(normalized)
}
