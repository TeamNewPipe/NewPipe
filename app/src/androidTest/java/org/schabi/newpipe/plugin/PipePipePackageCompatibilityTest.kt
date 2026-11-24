/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.plugin

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcel
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.DexClassLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfoItemsCollector
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemsCollector
import org.schabi.newpipe.extractor.plugin.PluginContext
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginLoader
import org.schabi.newpipe.extractor.plugin.PluginManifest
import org.schabi.newpipe.extractor.plugin.PluginPackage
import org.schabi.newpipe.extractor.plugin.PluginPreferences
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.PluginObjectInputStream
import org.schabi.newpipe.util.PluginStateClassLoader
import org.schabi.newpipe.util.SerializedCache

/**
 * Executes an unmodified, publisher-signed package against the installed Android host.
 * Preparation downloads pinned bytes into test-only generated assets; all requests made
 * by the plugin during these tests are answered by OfflineDownloader, never the network.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PipePipePackageCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val assets get() = instrumentation.context.assets
    private lateinit var previousDownloader: Downloader
    private lateinit var previousPreferences: Map<String, *>
    private lateinit var previousInstallState: Map<String, *>
    private lateinit var previousRuntime: RuntimeSnapshot
    private var previousFiles = emptySet<String>()
    private var previousCodeCache = emptySet<String>()
    private val pluginPreferences = mutableMapOf<String, Map<String, *>>()
    private var initialized = false
    private val offline = OfflineDownloader()

    @Before
    fun prepareIsolatedRuntime() {
        previousDownloader = NewPipe.getDownloader()
        previousPreferences = PreferenceManager.getDefaultSharedPreferences(context).all.toMap()
        previousInstallState = context.getSharedPreferences("plugins", Context.MODE_PRIVATE).all.toMap()
        previousRuntime = RuntimeSnapshot.capture()
        previousFiles = packageDirectory().listFiles().orEmpty().mapTo(mutableSetOf()) { it.name }
        previousCodeCache = codeCacheDirectory().listFiles().orEmpty().mapTo(mutableSetOf()) { it.name }
        initialized = true
        resetRuntime()
        // Keep existing archives out of manager cleanup while the registry is isolated.
        runtimeSet().addAll(previousFiles)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences("plugins", Context.MODE_PRIVATE).edit().clear().commit()
        ProfileManager.getActiveProfileId(context)
        PluginManager.setEnabled(context, true)
        NewPipe.init(offline, NewPipe.getPreferredLocalization(), NewPipe.getPreferredContentCountry())
    }

    @After
    fun restoreRuntime() {
        if (!initialized) return
        resetRuntime()
        packageDirectory().listFiles().orEmpty().filter { it.name !in previousFiles }.forEach { it.delete() }
        codeCacheDirectory().listFiles().orEmpty().filter { it.name !in previousCodeCache }.forEach { it.deleteRecursively() }
        pluginPreferences.forEach { (file, values) ->
            restorePreferences(context.getSharedPreferences(file, Context.MODE_PRIVATE), values)
        }
        restorePreferences(context.getSharedPreferences("plugins", Context.MODE_PRIVATE), previousInstallState)
        restorePreferences(PreferenceManager.getDefaultSharedPreferences(context), previousPreferences)
        previousRuntime.restore()
        NewPipe.init(previousDownloader, NewPipe.getPreferredLocalization(), NewPipe.getPreferredContentCountry())
    }

    @Test
    fun publishedPackageLoadsAndRestoresPluginOwnedSavedState() {
        val fixture = loadTwitch()
        assertEquals(59_460_859, fixture.service.serviceId)
        assertEquals("Twitch", fixture.service.serviceInfo.name)
        assertTrue(fixture.service.javaClass.classLoader is DexClassLoader)
        assertEquals("live", fixture.service.kioskList.defaultKioskId)
        assertTrue(fixture.service.streamLHFactory.acceptUrl("https://twitch.tv/videos/9001"))
        assertEquals((0..10).toList(), ServiceList.builtIn().map { it.serviceId })
        assertSame(fixture.service, NewPipe.getService(59_460_859))
        assertEquals(12, ServiceList.all().map { it.serviceId }.toSet().size)
        assertNotNull(PluginManager.getIcon(context, fixture.service.serviceId))
        assertFalse(fixture.file.canWrite())

        val enumClass = requireNotNull(PluginManager.findPluginClass("mohr.jonas.twitch.data.TwitchChannelTabLinkType"))
        val value = requireNotNull(enumClass.enumConstants).first { it.toString() == "LIVE" } as Serializable
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
        }.toByteArray()
        assertThrows(ClassNotFoundException::class.java) {
            ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        }
        val restored = PluginObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertSame(value, restored)
        val cache = SerializedCache.getInstance()
        val key = requireNotNull(cache.put(value, Serializable::class.java))
        assertSame(value, cache.take(key, Serializable::class.java))
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(Bundle().apply { putSerializable("plugin-tab", value) })
            parcel.setDataPosition(0)
            val bundle = requireNotNull(parcel.readBundle(PluginStateClassLoader.withPlugins(javaClass.classLoader)))
            @Suppress("DEPRECATION")
            assertSame(value, bundle.getSerializable("plugin-tab"))
        } finally {
            parcel.recycle()
        }

        fixture.preferences.sharedPreferences.edit().putString("fixture_saved_setting", "keep").commit()
        PluginManager.uninstall(context, fixture.entry.pluginId)
        assertTrue(PluginManager.getInstalled(context).isEmpty())
        assertEquals(fixture.entry.publicKey, PluginManager.trustedPublisher(context, fixture.entry.pluginId))
        assertEquals("keep", fixture.preferences.getString("fixture_saved_setting"))
        assertTrue("A loaded archive remains until restart for lazy class resolution", fixture.file.isFile)
        assertSame(fixture.service, ServiceList.getById(fixture.service.serviceId))
        assertTrue(offline.requests.isEmpty())
    }

    @Test
    fun pluginRuntimeProvidesPlatformTypedHelpersWithoutRewritingSignedPackage() {
        val fixture = loadTwitch()
        val pluginLoader = requireNotNull(fixture.service.javaClass.classLoader)
        val runtimeLoader = PluginRuntimeLoader.parentForPlugins(context)
        assertSame(runtimeLoader, pluginLoader.parent)

        // Resolve through the platform loader so minSdk23 desugaring cannot replace these types.
        val platformStream = platformClass("java.util.stream.Stream")
        val streamHelper = Class.forName("j$.util.stream.Stream\$-EL", true, pluginLoader)
        assertEquals("java.util.stream.Stream", platformStream.name)
        assertSame(runtimeLoader, streamHelper.classLoader)
        assertEquals("java.util.List", streamHelper.getMethod("toList", platformStream).returnType.name)
        val values = arrayOf<Any?>("First lesson", null, "Last lesson")
        val list = platformStreamToList(pluginLoader, values)
        assertEquals(values.toList(), list)
        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) { (list as MutableList<Any?>).add("Extra lesson") }

        val platformCollection = platformClass("java.util.Collection")
        val platformIntFunction = platformClass("java.util.function.IntFunction")
        val collectionHelper = Class.forName("j$.util.Collection\$-EL", true, pluginLoader)
        assertSame(runtimeLoader, collectionHelper.classLoader)
        val toArray = collectionHelper.getMethod("toArray", platformCollection, platformIntFunction)
        assertEquals(Array<Any>::class.java, toArray.returnType)
        val generator = Proxy.newProxyInstance(
            ClassLoader.getSystemClassLoader(),
            arrayOf(platformIntFunction)
        ) { proxy, method, arguments ->
            when (method.name) {
                "apply" -> arrayOfNulls<String>(requireNotNull(arguments)[0] as Int)
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Platform String array generator"
                else -> throw AssertionError("Unexpected IntFunction method: $method")
            }
        }
        val array = toArray.invoke(null, list, generator) as Array<*>
        assertEquals(Array<String>::class.java, array.javaClass)
        assertArrayEquals(values, array)

        val hostDuration = Duration.ofSeconds(42)
        assertFalse(PluginRuntimeLoader.ownsClassName(hostDuration.javaClass.name))
        assertSame(hostDuration.javaClass, Class.forName("j$.time.Duration", false, pluginLoader))
        assertSame(hostDuration.javaClass, Class.forName("j$.time.Duration", false, runtimeLoader))

        // The compatibility runtime is separate: no plugin DEX or signature input is patched.
        assertArrayEquals(assets.open("twitch-5.ppp").use { it.readBytes() }, fixture.file.readBytes())
        PluginPackage.verify(fixture.file, fixture.entry.sha256, fixture.entry.signature, fixture.entry.publicKey)
        assertTrue(offline.requests.isEmpty())
    }

    @Test
    fun serializedCollectionsPreserveSharedHostIdentityAcrossSavedStateReaders() {
        val fixture = loadTwitch()
        val pluginLoader = requireNotNull(fixture.service.javaClass.classLoader)
        val runtimeLoader = PluginRuntimeLoader.parentForPlugins(context)
        val hostFactory = Class.forName("j$.util.DesugarCollections", true, PluginManager::class.java.classLoader)
        val pluginFactory = Class.forName("j$.util.DesugarCollections", true, pluginLoader)
        assertSame(hostFactory, pluginFactory)
        assertFalse(PluginRuntimeLoader.ownsClassName(hostFactory.name))
        val unmodifiableList = hostFactory.getMethod("unmodifiableList", platformClass("java.util.List"))
        val hostList = unmodifiableList.invoke(null, arrayListOf("Saved host lesson", null)) as List<*>
        val pluginList = pluginFactory.getMethod("unmodifiableList", platformClass("java.util.List"))
            .invoke(null, arrayListOf("Saved plugin lesson", null)) as List<*>
        assertSame(hostList.javaClass, pluginList.javaClass)
        assertFalse(PluginRuntimeLoader.ownsClassName(hostList.javaClass.name))

        // Stream.toList returns a platform wrapper; DesugarCollections wrappers are shared too.
        val streamList = platformStreamToList(pluginLoader, arrayOf("First lesson", null, "Last lesson"))
        val hostFactoryList = java.util.List.of("Saved host lesson", "Another host lesson")
        for (list in listOf(hostList, pluginList, streamList, hostFactoryList)) {
            assertTrue("The saved list must be serializable", list is Serializable)
            assertFalse(list.javaClass.classLoader === runtimeLoader)
            assertSavedStateRoundTripsPreserveTypeAndContents(list as Serializable)

            // A matching component class is necessary to prevent ArrayStoreException on restore.
            val array = java.lang.reflect.Array.newInstance(list.javaClass, 1)
            java.lang.reflect.Array.set(array, 0, list)
            if (list.javaClass == hostList.javaClass) assertFalse(PluginRuntimeLoader.ownsClassName(array.javaClass.name))
            assertSavedStateRoundTripsPreserveTypeAndContents(array as Serializable)
        }
        assertSavedStateRoundTripsPreserveTypeAndContents(Duration.ofSeconds(42))
        assertTrue(offline.requests.isEmpty())
    }

    @Test
    fun publishedPluginCollectorsUseCompatibleHostDefaults() {
        val fixture = loadTwitch()
        val handler = SearchQueryHandler(SEARCH_URL, SEARCH_URL, "fixture", emptyList(), emptyList())
        val outer = fixture.service.getSearchExtractor(handler)
        val id = fixture.service.serviceId
        val channel = ChannelInfoItem(id, "https://twitch.tv/fixture", "Fixture channel").apply {
            description = "Saved channel"
            subscriberCount = 123
            thumbnailUrl = "https://images.example.invalid/avatar.png"
        }
        val stream = StreamInfoItem(id, STREAM_URL, "Fixture live lesson", StreamType.LIVE_STREAM).apply {
            uploaderName = "Fixture streamer"
            duration = -1
            viewCount = 456
            thumbnailUrl = "https://images.example.invalid/live.png"
        }
        val playlist = PlaylistInfoItem(id, "https://twitch.tv/directory/category/fixture", "Fixture category").apply {
            uploaderName = "Fixture streamer"
            streamCount = -1
            thumbnailUrl = "https://images.example.invalid/category.png"
        }
        val channels = ChannelInfoItemsCollector(id)
        channels.commit(actualCollectorExtractor(fixture, 1, outer, channel) as ChannelInfoItemExtractor)
        val streams = StreamInfoItemsCollector(id)
        streams.commit(actualCollectorExtractor(fixture, 2, outer, stream) as StreamInfoItemExtractor)
        val playlists = PlaylistInfoItemsCollector(id)
        playlists.commit(actualCollectorExtractor(fixture, 4, outer, playlist) as PlaylistInfoItemExtractor)
        assertTrue(channels.errors.toString(), channels.errors.isEmpty())
        assertTrue(streams.errors.toString(), streams.errors.isEmpty())
        assertTrue(playlists.errors.toString(), playlists.errors.isEmpty())
        assertEquals("Fixture channel", channels.items.single().name)
        assertEquals("Fixture live lesson", streams.items.single().name)
        assertEquals("Fixture category", playlists.items.single().name)
        assertEquals(123L, channels.items.single().subscriberCount)
        assertEquals(456L, streams.items.single().viewCount)
        assertTrue(offline.requests.isEmpty())
    }

    @Test
    fun publishedPluginExtractsSearchItemsWithoutNetwork() {
        val fixture = loadTwitch()
        val query = fixture.service.searchQHFactory.fromQuery("fixture course", emptyList(), emptyList())
        assertTrue(query.url, query.url.contains("term=fixture+course"))
        val info = SearchInfo.getInfo(fixture.service, query)
        assertTrue(info.errors.toString(), info.errors.isEmpty())
        assertEquals(
            listOf("Fixture channel", "Fixture live lesson", "Fixture category", "Fixture recording"),
            info.relatedItems.map { it.name }
        )
        assertEquals(1, info.relatedItems.count { it is ChannelInfoItem })
        assertEquals(2, info.relatedItems.count { it is StreamInfoItem })
        assertEquals(1, info.relatedItems.count { it is PlaylistInfoItem })
        assertTrue(info.relatedItems.all { it.serviceId == fixture.service.serviceId })
        assertEquals(listOf("home", "search"), offline.requests)
    }

    @Test
    fun publishedPluginExtractsDefaultKioskWithoutNetwork() {
        val fixture = loadTwitch()
        val extractor = requireNotNull(fixture.service.kioskList.defaultKioskExtractor)
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        assertTrue(info.errors.toString(), info.errors.isEmpty())
        assertEquals("live", info.name)
        val item = info.relatedItems.single()
        assertEquals("Fixture live lesson", item.name)
        assertEquals("Fixture streamer", item.uploaderName)
        assertEquals(456L, item.viewCount)
        assertEquals(StreamType.LIVE_STREAM, item.streamType)
        assertEquals(fixture.service.serviceId, item.serviceId)
        assertEquals(listOf("home", "kiosk"), offline.requests)
    }

    @Test
    fun publishedPluginBuildsPlayableHlsStreamInfoWithoutNetwork() {
        val fixture = loadTwitch()
        val info = StreamInfo.getInfo(fixture.service, STREAM_URL)
        assertTrue(info.errors.toString(), info.errors.isEmpty())
        assertEquals("Fixture live lesson", info.name)
        assertEquals("Fixture streamer", info.uploaderName)
        assertEquals(StreamType.LIVE_STREAM, info.streamType)
        assertEquals(HLS_URL, info.hlsUrl)
        assertEquals(HLS_URL, info.videoStreams.single().content)
        assertEquals(DeliveryMethod.HLS, info.videoStreams.single().deliveryMethod)
        assertEquals(fixture.service.serviceId, info.serviceId)
        assertEquals(listOf("home", "stream", "channel", "sidebar", "token", "hls"), offline.requests)
    }

    @Test
    fun nativePluginUsesProfileContextAndRejectsPipePipeOnlyContext() {
        val nativePresent = assets.list("").orEmpty().contains("native-example.ppp")
        val requireNative = InstrumentationRegistry.getArguments().getString("requireNativePluginFixture") == "true"
        assertTrue("The native compatibility job must generate native-example.ppp", nativePresent || !requireNative)
        assumeTrue("Native example fixture is generated only by compatibility jobs", nativePresent)
        val profile = requireNotNull(ProfileManager.createProfile(context, "Native fixture"))
        val fixture = loadFixture("native-example.ppp", "native-index.json", NATIVE_REPOSITORY) { preferences ->
            preferences.sharedPreferences.edit().putString("base_url", "https://example.invalid/profile-fixture").commit()
        }
        assertEquals("wizestream", fixture.manifest.host)
        assertEquals("2.0", fixture.manifest.apiVersion)
        assertEquals(1_772_767_428, fixture.service.serviceId)
        assertEquals("https://example.invalid/profile-fixture", fixture.service.baseUrl)
        assertEquals(profile.id, fixture.service.javaClass.getMethod("getBoundProfileId").invoke(fixture.service))
        val pipePipeContext = object : PluginContext {
            override fun getPluginId(): String = fixture.manifest.pluginId
            override fun getPreferences(): PluginPreferences = fixture.preferences
        }
        assertThrows(PluginException::class.java) {
            PluginLoader.createService(
                requireNotNull(fixture.service.javaClass.classLoader),
                fixture.manifest,
                fixture.service.serviceId,
                pipePipeContext
            )
        }
        assertTrue(offline.requests.isEmpty())
    }

    private fun loadTwitch(): LoadedFixture {
        val fixture = loadFixture("twitch-5.ppp", "plugin-fixtures/twitch-5-index.json", TWITCH_REPOSITORY)
        assertEquals(5, fixture.manifest.versionCode)
        assertEquals("1.2.1", fixture.manifest.versionName)
        assertEquals("BHXTB1DqhMt9/MVbz63M5/YPOkEtuDPh72ueB4DqUsc=", fixture.entry.publicKey)
        return fixture
    }

    // Class.forName literals can be rewritten to desugared types in the minified test APK.
    private fun platformClass(name: String): Class<*> = ClassLoader.getSystemClassLoader().loadClass(name)

    private fun platformStreamToList(loader: ClassLoader, values: Array<Any?>): List<*> {
        val streamType = platformClass("java.util.stream.Stream")
        val stream = platformClass("java.util.Arrays").getMethod("stream", Array<Any>::class.java)
            .invoke(null, values as Any)
        val helper = Class.forName("j$.util.stream.Stream\$-EL", true, loader)
        return helper.getMethod("toList", streamType).invoke(null, stream) as List<*>
    }

    private fun serializationRoundTrip(value: Serializable): Any {
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
        }.toByteArray()
        return PluginObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
    }

    private fun assertSavedStateRoundTripsPreserveTypeAndContents(value: Serializable) {
        val parcel = Parcel.obtain()
        try {
            // Parcel writes a bare List using its list protocol, even after putSerializable.
            // Enclose it like a field in a saved extractor snapshot to exercise Java serialization.
            parcel.writeBundle(Bundle().apply { putSerializable("saved-value", SavedStateEnvelope(value)) })
            parcel.setDataPosition(0)
            val bundle = requireNotNull(parcel.readBundle(PluginStateClassLoader.withPlugins(javaClass.classLoader)))

            @Suppress("DEPRECATION")
            val parcelValue = (requireNotNull(bundle.getSerializable("saved-value")) as SavedStateEnvelope).value
            for ((reader, restored) in listOf("ObjectInputStream" to serializationRoundTrip(value), "Bundle" to parcelValue)) {
                assertSame("$reader must preserve the serialized field's concrete class", value.javaClass, restored.javaClass)
                if (value is Array<*>) {
                    val restoredArray = restored as Array<*>
                    assertArrayEquals(value, restoredArray)
                    value.indices.forEach { index ->
                        val element = value[index]
                        if (element != null) assertSame(element.javaClass, restoredArray[index]?.javaClass)
                    }
                } else {
                    assertEquals(value, restored)
                }
            }
        } finally {
            parcel.recycle()
        }
    }

    private class SavedStateEnvelope(val value: Serializable) : Serializable

    private fun loadFixture(
        packageAsset: String,
        catalogAsset: String,
        repository: String,
        configure: (AndroidPluginPreferences) -> Unit = {}
    ): LoadedFixture {
        val catalog = assets.open(catalogAsset).bufferedReader().use { it.readText() }
        val entry = PluginRepository.parse(repository, catalog).plugins.single()
        val bytes = try {
            assets.open(packageAsset).use { it.readBytes() }
        } catch (missing: IOException) {
            throw AssertionError("Run scripts/prepare-plugin-test-fixtures.py before building Android tests", missing)
        }
        val profileId = ProfileManager.getActiveProfileId(context)
        val preferencesFile = AndroidPluginPreferences.fileName(entry.pluginId, profileId)
        val shared = context.getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        pluginPreferences.putIfAbsent(preferencesFile, shared.all.toMap())
        shared.edit().clear().commit()
        val preferences = AndroidPluginPreferences(context, entry.pluginId, emptyList(), profileId)
        configure(preferences)
        PluginManager.installDownloaded(context, repository, entry, bytes)
        val installed = PluginManager.getInstalled(context).single()
        val file = File(packageDirectory(), installed.fileName)
        PluginPackage.verify(file, entry.sha256, entry.signature, entry.publicKey)
        val manifest = PluginPackage.readManifest(file)
        PluginManager.loadInstalledPlugins(context)
        assertTrue("Plugin failed to load: ${PluginManager.getLoadError(entry.pluginId)}", PluginManager.isLoaded(entry.pluginId))
        val service = requireNotNull(ServiceList.getById(PluginManager.serviceIdFor(manifest)))
        return LoadedFixture(service, manifest, entry, file, preferences)
    }

    /** Real anonymous classes from the published DEX, independent of URL-building behavior. */
    private fun actualCollectorExtractor(fixture: LoadedFixture, number: Int, outer: Any, item: InfoItem): Any {
        val type = Class.forName(
            "mohr.jonas.twitch.extractors.TwitchSearchExtractor\$$number",
            true,
            fixture.service.javaClass.classLoader
        )
        val constructor = type.declaredConstructors.single()
        constructor.isAccessible = true
        val arguments = constructor.parameterTypes.map { parameter ->
            when {
                parameter.isInstance(item) -> item
                parameter.isInstance(outer) -> outer
                else -> throw AssertionError("Unexpected published extractor constructor parameter: $parameter")
            }
        }.toTypedArray()
        return constructor.newInstance(*arguments)
    }

    private data class LoadedFixture(
        val service: StreamingService,
        val manifest: PluginManifest,
        val entry: PluginRepository.Entry,
        val file: File,
        val preferences: AndroidPluginPreferences
    )

    private fun packageDirectory() = File(context.filesDir, "plugins")

    private fun codeCacheDirectory() = File(context.codeCacheDir, "plugins")

    private inner class OfflineDownloader : Downloader() {
        val requests = mutableListOf<String>()

        override fun execute(request: Request): Response {
            val url = request.url()
            if (url == "https://twitch.tv") {
                requests += "home"
                return Response(
                    200,
                    "OK",
                    mapOf("set-cookie" to listOf("unique_id_durable=fixture-device; Path=/")),
                    "",
                    byteArrayOf(),
                    url
                )
            }
            if (url.startsWith("https://usher.ttvnw.net/api/v2/channel/hls/")) {
                requests += "hls"
                return response(request, "twitch-master.m3u8")
            }
            if (url == "https://gql.twitch.tv/gql") {
                val body = request.dataToSend()?.toString(Charsets.UTF_8).orEmpty()
                val kind = when {
                    body.contains("SearchResultsPage_SearchResults") -> "search"
                    body.contains("HomeOfflineCarousel") -> "channel"
                    body.contains("SideNav") -> "sidebar"
                    body.contains("streamPlaybackAccessToken") -> "token"
                    body.contains("streams(first:") -> "kiosk"
                    body.contains("user(login:") -> "stream"
                    else -> throw AssertionError("Unexpected offline Twitch GraphQL operation: $body")
                }
                requests += kind
                return response(request, "twitch-$kind.json")
            }
            throw AssertionError("Unexpected network request in offline plugin test: ${request.httpMethod()} $url")
        }

        private fun response(request: Request, name: String): Response {
            val bytes = assets.open("plugin-fixtures/$name").use { it.readBytes() }
            return Response(200, "OK", emptyMap(), bytes.toString(Charsets.UTF_8), bytes, request.url())
        }

        override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall = throw AssertionError("Unexpected asynchronous request in offline plugin test")
    }

    /** Test-only restoration; no production reset/install-verification bypass is exposed. */
    private data class RuntimeSnapshot(
        val startupAttempted: Boolean,
        val profile: Any?,
        val services: List<StreamingService>,
        val maps: Map<String, Map<Any, Any>>,
        val loaders: List<Any>,
        val files: Set<Any>
    ) {
        fun restore() {
            services.forEach(ServiceList::register)
            maps.forEach { (name, values) -> runtimeMap(name).putAll(values) }
            runtimeList().addAll(loaders)
            runtimeSet().addAll(files)
            field("startupAttempted").setBoolean(null, startupAttempted)
            field("loadedProfileId").set(null, profile)
        }

        companion object {
            fun capture() = RuntimeSnapshot(
                field("startupAttempted").getBoolean(null),
                field("loadedProfileId").get(null),
                ServiceList.all().filter { it.serviceId >= ServiceList.FIRST_DYNAMIC_PLUGIN_ID },
                MAP_FIELDS.associateWith { runtimeMap(it).toMap() },
                runtimeList().toList(),
                runtimeSet().toSet()
            )
        }
    }

    companion object {
        private const val TWITCH_REPOSITORY = "https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/index.json"
        private const val NATIVE_REPOSITORY = "https://example.invalid/native-fixtures/index.json"
        private const val SEARCH_URL = "https://twitch.tv/search?term=fixture"
        private const val STREAM_URL = "https://twitch.tv/fixture?_type=stream&_channelId=42&_pagination=eyJvZmZzZXQiOjAsInNpemUiOjIwfQ=="
        private const val HLS_URL = "https://media.example.invalid/live/720p.m3u8"
        private val MAP_FIELDS = listOf("loaded", "loadedPackageHashes", "icons", "loadErrors")

        private fun field(name: String): Field = PluginManager::class.java.getDeclaredField(name).apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        private fun runtimeMap(name: String): MutableMap<Any, Any> = field(name).get(null) as MutableMap<Any, Any>

        @Suppress("UNCHECKED_CAST")
        private fun runtimeList(): MutableList<Any> = field("classLoaders").get(null) as MutableList<Any>

        @Suppress("UNCHECKED_CAST")
        private fun runtimeSet(): MutableSet<Any> = field("loadedFiles").get(null) as MutableSet<Any>

        private fun resetRuntime() {
            ServiceList.all().filter { it.serviceId >= ServiceList.FIRST_DYNAMIC_PLUGIN_ID }
                .forEach { ServiceList.unregister(it.serviceId) }
            MAP_FIELDS.forEach { runtimeMap(it).clear() }
            runtimeList().clear()
            runtimeSet().clear()
            field("startupAttempted").setBoolean(null, false)
            field("loadedProfileId").set(null, null)
        }

        private fun restorePreferences(preferences: SharedPreferences, values: Map<String, *>) {
            val editor = preferences.edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit())
        }
    }
}
