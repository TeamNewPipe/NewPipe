/* SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from PipePipeClient 43ef494c1a1792a774c223e1982ff1e794c49017.
 */
package org.schabi.newpipe.plugin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.CookieJar
import okhttp3.Request
import org.schabi.newpipe.App
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginLoader
import org.schabi.newpipe.extractor.plugin.PluginManifest
import org.schabi.newpipe.extractor.plugin.PluginPackage
import org.schabi.newpipe.extractor.plugin.PluginPreferences
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.extractor.plugin.WizeStreamPluginApi
import org.schabi.newpipe.extractor.plugin.WizeStreamPluginContext
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager

/**
 * Device-wide verified plugin installation. Loaded code and its captured profile remain fixed
 * for one process; install, update, removal and profile changes require a process restart.
 */
object PluginManager {
    private const val TAG = "PluginManager"
    private const val STATE_FILE = "plugins"
    private const val KEY_INSTALLED = "installed"
    private const val KEY_REPOSITORIES = "repositories"
    private const val KEY_PUBLISHERS = "trusted_publishers"
    private const val MAX_PACKAGE_SIZE = 32L * 1024 * 1024
    private val mutationLock = Any()
    private val validPluginId = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}")

    const val BUILTIN_REPOSITORY =
        "https://github.com/InfinityLoop1308/PipePipe-builtin-plugins/releases/download/repo/index.json"
    const val AWESOME_REPOSITORY =
        "https://raw.githubusercontent.com/InfinityLoop1308/awesome-pipepipe-plugins/main/index.json"

    data class InstalledPlugin(
        val pluginId: String,
        val name: String,
        val versionCode: Int,
        val versionName: String,
        val publicKey: String,
        val repositoryUrl: String,
        val fileName: String,
        val author: String,
        val sha256: String,
        val signature: String,
        val host: String = "pipepipe"
    )

    private val loaded = ConcurrentHashMap<Int, PluginManifest>()
    private val loadedPackageHashes = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<Int, Bitmap>()
    private val classLoaders = CopyOnWriteArrayList<Pair<PluginManifest, ClassLoader>>()
    private val loadedFiles = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val loadErrors = ConcurrentHashMap<String, String>()

    @Volatile private var installedStateError: String? = null

    @Volatile private var startupAttempted = false

    @get:JvmStatic
    @Volatile var loadedProfileId: String? = null
        private set

    private fun state(context: Context) = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)
    private fun pluginDir(context: Context) = File(context.filesDir, "plugins")
    private fun codeCacheDir(context: Context, pluginId: String) = File(context.codeCacheDir, "plugins/$pluginId")
    private fun builtIns() = ServiceList.builtIn().associate { it.serviceId to it.serviceInfo.name }

    @JvmStatic
    fun isPlatformSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    @JvmStatic
    fun isEnabled(context: Context): Boolean = isPlatformSupported() &&
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.plugins_enabled_key), false)

    @JvmStatic
    fun canManage(context: Context): Boolean = !KidsMode.requiresParentAccess(context)

    private fun requireManagementAccess(context: Context) {
        if (!canManage(context)) throw PluginException(context.getString(R.string.plugins_parent_access))
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        requireManagementAccess(context)
        if (enabled && !isPlatformSupported()) {
            throw PluginException(context.getString(R.string.plugins_android_version))
        }
        check(
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.plugins_enabled_key), enabled).commit()
        )
    }

    @JvmStatic
    fun requiresProfileRestart(context: Context): Boolean = PluginInstallPolicy.needsProfileRestart(
        loadedProfileId,
        ProfileManager.getActiveProfileId(context),
        loaded.isNotEmpty()
    )

    fun isRestartRequired(context: Context): Boolean = requiresProfileRestart(context) ||
        PluginInstallPolicy.needsPackageRestart(
            isEnabled(context),
            getInstalled(context).associate { it.pluginId to it.sha256 },
            loadedPackageHashes
        )

    fun compatibilityError(entry: PluginRepository.Entry): String? = runCatching {
        PluginInstallPolicy.requireCompatible(entry, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
    }.exceptionOrNull()?.let { it.message ?: "Incompatible plugin" }

    @JvmStatic
    fun serviceIdFor(manifest: PluginManifest): Int = PluginInstallPolicy.serviceId(manifest.pluginId)

    @JvmStatic
    fun builtInServiceName(pluginId: String, name: String): String? = PluginInstallPolicy.builtInName(pluginId, name, builtIns())

    @JvmStatic
    @JvmOverloads
    fun getInstalled(context: Context = App.instance): List<InstalledPlugin> {
        return runCatching {
            val json = state(context).getString(KEY_INSTALLED, null) ?: "[]"
            val array = JsonParser.array().from(json)
            (0 until array.size).map { index ->
                val o = array.getObject(index)
                val id = o.getString("pluginId")
                val fileName = o.getString("fileName")
                require(
                    validPluginId.matches(id) && fileName.startsWith("$id-") &&
                        fileName.endsWith(".ppp") && !fileName.contains('/') && !fileName.contains('\\')
                )
                InstalledPlugin(
                    id, o.getString("name"), o.getInt("versionCode"),
                    o.getString("versionName"), o.getString("publicKey"),
                    o.getString("repositoryUrl"), fileName, o.getString("author", ""),
                    o.getString("sha256"), o.getString("signature"), o.getString("host", "pipepipe")
                )
            }.also { installedStateError = null }
        }.getOrElse {
            Log.e(TAG, "Cannot read plugin installation state", it)
            installedStateError = it.message ?: "Cannot read plugin installation state"
            emptyList()
        }
    }

    fun getStateError(): String? = installedStateError

    private fun checkedInstalled(context: Context): List<InstalledPlugin> = getInstalled(context).also {
        installedStateError?.let { error -> throw PluginException(error) }
    }

    private fun trustedPublishers(context: Context): JsonObject {
        val json = state(context).getString(KEY_PUBLISHERS, "{}")
        return JsonParser.`object`().from(json)
    }

    fun trustedPublisher(context: Context, pluginId: String): String? = try {
        trustedPublishers(context).getString(pluginId, null)
    } catch (e: Exception) {
        // An unreadable pin must never be treated as a new, untrusted first installation.
        Log.e(TAG, "Cannot read trusted plugin publishers", e)
        "invalid-publisher-state"
    }

    private fun saveInstalled(context: Context, plugins: List<InstalledPlugin>, pin: Pair<String, String>? = null) {
        checkedInstalled(context)
        val array = JsonArray()
        plugins.forEach { plugin ->
            array.add(
                JsonObject().apply {
                    put("pluginId", plugin.pluginId)
                    put("name", plugin.name)
                    put("versionCode", plugin.versionCode)
                    put("versionName", plugin.versionName)
                    put("publicKey", plugin.publicKey)
                    put("repositoryUrl", plugin.repositoryUrl)
                    put("fileName", plugin.fileName)
                    put("author", plugin.author)
                    put("sha256", plugin.sha256)
                    put("signature", plugin.signature)
                    put("host", plugin.host)
                }
            )
        }
        val editor = state(context).edit().putString(KEY_INSTALLED, JsonWriter.string(array))
        if (pin != null) {
            val publishers = trustedPublishers(context).apply { put(pin.first, pin.second) }
            editor.putString(KEY_PUBLISHERS, JsonWriter.string(publishers))
        }
        if (!editor.commit()) throw IOException("Cannot save plugin installation state")
    }

    @JvmStatic
    fun getRepositories(context: Context): List<String> = (listOf(BUILTIN_REPOSITORY) + userRepositories(context)).distinct()

    private fun userRepositories(context: Context): List<String> = runCatching {
        val array = JsonParser.array().from(state(context).getString(KEY_REPOSITORIES, "[]"))
        (0 until array.size).map { PluginInstallPolicy.requireHttps(array.getString(it)) }
    }.getOrDefault(emptyList())

    @JvmStatic
    fun addRepository(context: Context, url: String) = synchronized(mutationLock) {
        requireManagementAccess(context)
        val canonical = PluginInstallPolicy.requireHttps(url.trim())
        val updated = (userRepositories(context) + canonical).distinct().filter { it != BUILTIN_REPOSITORY }
        check(state(context).edit().putString(KEY_REPOSITORIES, JsonWriter.string(JsonArray(updated))).commit())
    }

    @JvmStatic
    fun removeRepository(context: Context, url: String) = synchronized(mutationLock) {
        requireManagementAccess(context)
        val updated = userRepositories(context).filter { it != url }
        check(state(context).edit().putString(KEY_REPOSITORIES, JsonWriter.string(JsonArray(updated))).commit())
    }

    /** Call after NewPipe.init and before any services or saved extractor objects are used. */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun loadInstalledPlugins(context: Context, safeMode: Boolean = false) {
        if (startupAttempted) return
        startupAttempted = true
        if (!isEnabled(context)) return
        if (safeMode) {
            getInstalled(context).forEach { loadErrors[it.pluginId] = context.getString(R.string.plugins_safe_mode) }
            return
        }
        loadedProfileId = ProfileManager.getActiveProfileId(context)
        val installed = getInstalled(context)
        if (installedStateError != null) return
        for (plugin in installed) {
            try {
                load(context, plugin)
            } catch (e: Throwable) {
                // An external LinkageError must not prevent built-in services from starting.
                Log.e(TAG, "Cannot load plugin ${plugin.pluginId}", e)
                loadErrors[plugin.pluginId] = e.message ?: e.javaClass.simpleName
            }
        }
        cleanupUnusedPackages(context)
    }

    private fun load(context: Context, plugin: InstalledPlugin) {
        val file = File(pluginDir(context), plugin.fileName)
        if (!file.isFile) throw PluginException("Package file missing")
        PluginInstallPolicy.checkPublisher(trustedPublisher(context, plugin.pluginId), plugin.publicKey)
        PluginPackage.verify(file, plugin.sha256, plugin.signature, plugin.publicKey)
        val manifest = PluginPackage.readManifest(file)
        PluginInstallPolicy.requireCompatible(manifest, plugin.sha256, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
        if (manifest.pluginId != plugin.pluginId || manifest.name != plugin.name ||
            manifest.versionCode != plugin.versionCode || manifest.host != plugin.host
        ) {
            throw PluginException("Package does not match installed plugin")
        }
        builtInServiceName(manifest.pluginId, manifest.name)?.let {
            throw PluginException("$it is already built into WizeStream")
        }
        if (!PluginPackage.hasDex(file)) throw PluginException("Package has no Android code")
        if (!file.setReadOnly()) throw IOException("Cannot protect plugin package")
        val id = serviceIdFor(manifest)
        if (ServiceList.getById(id) != null) throw PluginException("Service id is already registered")
        val optimized = codeCacheDir(context, plugin.pluginId).apply { mkdirs() }
        val parent = if (manifest.host == WizeStreamPluginApi.HOST) {
            javaClass.classLoader
        } else {
            PluginRuntimeLoader.parentForPlugins(context)
        }
        val loader = DexClassLoader(file.absolutePath, optimized.absolutePath, null, parent)
        val prefs = AndroidPluginPreferences(context, manifest.pluginId, manifest.settings, loadedProfileId!!)
        val service = PluginLoader.createService(
            loader,
            manifest,
            id,
            object : WizeStreamPluginContext {
                override fun getPluginId(): String = manifest.pluginId
                override fun getPreferences(): PluginPreferences = prefs
                override fun getHostVersionCode(): Int = BuildConfig.VERSION_CODE
                override fun getHostVersionName(): String = BuildConfig.VERSION_NAME
                override fun getProfileId(): String = prefs.profileId
            }
        )
        ServiceList.register(service)
        loaded[id] = manifest
        loadedPackageHashes[plugin.pluginId] = plugin.sha256
        classLoaders.add(manifest to loader)
        loadedFiles.add(plugin.fileName)
        loadIcon(file, manifest, id)
    }

    private fun loadIcon(file: File, manifest: PluginManifest, id: Int) {
        runCatching {
            val bytes = PluginPackage.readIcon(file, manifest) ?: return
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..PluginManifest.ICON_MAX_DIMENSION ||
                bounds.outHeight !in 1..PluginManifest.ICON_MAX_DIMENSION
            ) {
                return
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { icons[id] = it }
        }.onFailure { Log.w(TAG, "Ignoring invalid plugin icon", it) }
    }

    @JvmStatic
    fun getIcon(context: Context, serviceId: Int): Drawable? = icons[serviceId]?.let {
        BitmapDrawable(context.resources, it)
    }

    @JvmStatic fun getLoadedManifest(serviceId: Int): PluginManifest? = loaded[serviceId]

    @JvmStatic fun isPluginService(service: StreamingService): Boolean = loaded.containsKey(service.serviceId)
    fun getLoadedManifests(): List<PluginManifest> = loaded.values.toList()
    fun getLoadError(pluginId: String): String? = loadErrors[pluginId]
    fun isLoaded(pluginId: String): Boolean = loaded.values.any { it.pluginId == pluginId }
    fun getServiceIdByPluginId(pluginId: String): Int? = loaded.entries.firstOrNull { it.value.pluginId == pluginId }?.key

    @JvmStatic
    fun findPluginClass(name: String): Class<*>? {
        for ((_, loader) in classLoaders) {
            try {
                return Class.forName(name, false, loader)
            } catch (_: ClassNotFoundException) { }
        }
        return null
    }

    @get:JvmStatic
    val hostAndPluginsClassLoader: ClassLoader = object : ClassLoader(PluginManager::class.java.classLoader) {
        override fun findClass(name: String): Class<*> = PluginRuntimeLoader.findRuntimeClass(name) ?: findPluginClass(name) ?: throw ClassNotFoundException(name)
    }

    fun fetchRepository(url: String): PluginRepository = PluginRepository.parse(
        PluginInstallPolicy.requireHttps(url),
        download(url, 2L * 1024 * 1024).toString(Charsets.UTF_8)
    )

    @Throws(IOException::class, PluginException::class)
    fun install(context: Context, repositoryUrl: String, entry: PluginRepository.Entry) {
        requireManagementAccess(context)
        if (!isEnabled(context)) throw PluginException("Plugins are disabled")
        builtInServiceName(entry.pluginId, entry.name)?.let {
            throw PluginException(context.getString(R.string.plugin_already_builtin, it))
        }
        PluginInstallPolicy.requireCompatible(entry, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
        PluginInstallPolicy.checkPublisher(trustedPublisher(context, entry.pluginId), entry.publicKey)
        val bytes = download(entry.packageUrl, MAX_PACKAGE_SIZE)
        installDownloaded(context, repositoryUrl, entry, bytes)
    }

    /** Also used by instrumentation tests with signed fixture bytes; never executes a package. */
    internal fun installDownloaded(
        context: Context,
        repositoryUrl: String,
        entry: PluginRepository.Entry,
        bytes: ByteArray
    ) = synchronized(mutationLock) {
        requireManagementAccess(context)
        if (!isEnabled(context)) throw PluginException("Plugins are disabled")
        PluginInstallPolicy.requireHttps(repositoryUrl)
        PluginInstallPolicy.requireHttps(entry.packageUrl)
        PluginInstallPolicy.requireCompatible(entry, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
        if (bytes.size > MAX_PACKAGE_SIZE) throw PluginException("Package is too large")
        val existing = checkedInstalled(context).firstOrNull { it.pluginId == entry.pluginId }
        PluginInstallPolicy.checkPublisher(trustedPublisher(context, entry.pluginId) ?: existing?.publicKey, entry.publicKey)
        if (existing != null && entry.versionCode <= existing.versionCode) {
            throw PluginException("Only a newer plugin version can replace the installed version")
        }
        val dir = pluginDir(context).apply { if (!isDirectory && !mkdirs()) throw IOException("Cannot create plugin directory") }
        val temporary = File.createTempFile("install-", ".tmp", dir)
        try {
            FileOutputStream(temporary).use { output ->
                if (!temporary.setReadOnly()) throw IOException("Cannot protect plugin package")
                output.write(bytes)
                output.fd.sync()
            }
            PluginPackage.verify(temporary, entry.sha256, entry.signature, entry.publicKey)
            val manifest = PluginPackage.readManifest(temporary)
            PluginInstallPolicy.checkManifest(entry, manifest, builtIns())
            PluginInstallPolicy.requireCompatible(manifest, entry.sha256, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)
            if (!PluginPackage.hasDex(temporary)) throw PluginException("Package has no Android code")
            val id = serviceIdFor(manifest)
            val conflict = getInstalled(context).firstOrNull {
                it.pluginId != manifest.pluginId && (
                    PluginInstallPolicy.serviceId(it.pluginId) == id ||
                        it.name.trim().equals(manifest.name.trim(), ignoreCase = true)
                    )
            }
            if (conflict != null) throw PluginException("Service is already supplied by ${conflict.name}")
            val fileName = "${manifest.pluginId}-${manifest.versionCode}-${entry.sha256.take(16)}.ppp"
            val target = File(dir, fileName)
            if (target.exists()) {
                PluginPackage.verify(target, entry.sha256, entry.signature, entry.publicKey)
            } else if (!temporary.renameTo(target)) {
                throw IOException("Cannot save plugin package")
            }
            // Old loaded files stay until process restart, including lazily resolved classes.
            val updated = getInstalled(context).filter { it.pluginId != entry.pluginId } + InstalledPlugin(
                manifest.pluginId, manifest.name, manifest.versionCode, manifest.versionName,
                entry.publicKey, repositoryUrl, fileName, manifest.author, entry.sha256, entry.signature,
                manifest.host
            )
            requireManagementAccess(context)
            saveInstalled(context, updated, entry.pluginId to entry.publicKey)
        } finally {
            temporary.delete()
        }
    }

    fun uninstall(context: Context, pluginId: String) = synchronized(mutationLock) {
        requireManagementAccess(context)
        saveInstalled(context, checkedInstalled(context).filter { it.pluginId != pluginId })
        // Deliberately keep publisher pins, profile preferences and all saved service data.
        cleanupUnusedPackages(context)
    }

    private fun cleanupUnusedPackages(context: Context) {
        val keep = getInstalled(context).map { it.fileName }.toSet() + loadedFiles
        if (installedStateError != null) return
        pluginDir(context).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun download(url: String, maxBytes: Long): ByteArray {
        var current = PluginInstallPolicy.requireHttps(url)
        val client = DownloaderImpl.getInstance().client.newBuilder().apply {
            // Catalog/package requests never receive a logged-in service's cookies or interceptors.
            interceptors().clear()
            networkInterceptors().clear()
            cookieJar(CookieJar.NO_COOKIES)
            followRedirects(false)
            followSslRedirects(false)
        }.build()
        repeat(6) {
            val request = Request.Builder().url(current).header("User-Agent", DownloaderImpl.USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    val location = response.header("Location") ?: throw IOException("Redirect has no destination")
                    val target = response.request.url.resolve(location) ?: throw IOException("Invalid redirect")
                    current = PluginInstallPolicy.requireHttps(target.toString())
                } else {
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $current")
                    val body = response.body ?: throw IOException("Empty response")
                    if (body.contentLength() > maxBytes) throw IOException("Response is too large")
                    val source = body.source()
                    if (source.request(maxBytes + 1)) throw IOException("Response is too large")
                    return source.readByteArray()
                }
            }
        }
        throw IOException("Too many redirects")
    }
}
