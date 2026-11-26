/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.plugin.ResolvedPluginSource
import org.schabi.newpipe.profiles.KidsMode
import org.schabi.newpipe.profiles.ProfileManager

/** Tests consent and document routing; installer verification remains a separate integration gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PluginSourceActionsTest {
    private lateinit var context: Context
    private lateinit var controller: ActivityController<TestActivity>
    private lateinit var io: TestScheduler
    private lateinit var fixture: Session
    private lateinit var previousPreferences: Map<SharedPreferences, Map<String, *>>
    private lateinit var previousFiles: Map<File, Set<String>>
    private val activity get() = controller.get()
    private val fragment get() = activity.supportFragmentManager.findFragmentByTag(SOURCE_TAG) as SourceFragment
    private val actions get() = fragment.actions
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        previousPreferences = listOf(
            preferences,
            context.getSharedPreferences("plugins", Context.MODE_PRIVATE),
            context.getSharedPreferences(KidsMode.PREFERENCES, Context.MODE_PRIVATE)
        ).associateWith { it.all.toMap() }
        previousFiles = listOf(
            File(context.filesDir, "plugin-repositories"),
            File(context.cacheDir, "plugin-imports")
        ).associateWith { directory -> directory.listFiles().orEmpty().mapTo(mutableSetOf()) { it.name } }
        previousPreferences.keys.forEach { it.edit().clear().commit() }
        preferences.edit().putBoolean(context.getString(R.string.plugins_enabled_key), true).commit()
        KidsMode.lock()
        io = TestScheduler()
        RxJavaPlugins.setIoSchedulerHandler { io }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        fixture = Session(signedFixture())
        if (PluginManager.isPlatformSupported()) {
            fixture.signed.bytes.inputStream().use { input ->
                PluginManager.prepareLocalPackage(context, input).use { prepared ->
                    val entry = fixture.signed.repository().plugins.single()
                    assertEquals(PLUGIN_ID, prepared.manifest.pluginId)
                    assertTrue("Signed fixture must match its catalog before exercising the UI", prepared.matches(entry))
                }
            }
        }
        currentSession = fixture
        controller = Robolectric.buildActivity(TestActivity::class.java).setup().visible()
        mount(SourceFragment(), SOURCE_TAG)
    }

    @After
    fun tearDown() {
        try {
            if (::controller.isInitialized) controller.pause().stop().destroy()
            KidsMode.lock()
            previousFiles.forEach { (directory, before) ->
                directory.listFiles().orEmpty().filter { it.name !in before }.forEach { it.delete() }
            }
            previousPreferences.forEach { (preferences, values) -> restorePreferences(preferences, values) }
        } finally {
            currentSession = null
            RxJavaPlugins.reset()
            RxAndroidPlugins.reset()
        }
    }

    @Test
    fun unofficialRepositoriesAndCommunityShortcutRequireTheirOwnConsent() {
        for (url in listOf(CATALOG_URL, PluginManager.AWESOME_REPOSITORY)) {
            val before = PluginManager.getRepositories(context)
            actions.addRepository(url)
            drain()
            val warning = latestDialog()
            assertEquals(
                context.getString(R.string.plugins_unofficial_repository_warning, CATALOG_NAME, url),
                message(warning)
            )
            assertEquals(before, PluginManager.getRepositories(context))
            click(warning, AlertDialog.BUTTON_NEGATIVE)
            assertEquals(before, PluginManager.getRepositories(context))

            actions.addRepository(url)
            drain()
            click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
            drain()
            assertEquals(before + url, PluginManager.getRepositories(context))
        }
        assertEquals(2, fixture.changed)
        assertTrue(fixture.remoteInstalls.isEmpty())
        assertTrue(fixture.localInstalls.isEmpty())
    }

    @Test
    fun directProjectInstallRequiresSourceWarningWithoutAddingItsRepository() {
        fixture.resolve = { listOf(ResolvedPluginSource(fixture.signed.repository())) }
        val before = PluginManager.getRepositories(context)
        openProject()
        val warning = latestDialog()
        assertEquals(
            context.getString(
                R.string.plugins_direct_install_warning,
                "Fixture plugin 1.0\n" + PLUGIN_ID,
                CATALOG_URL + "\n" + PACKAGE_URL
            ),
            message(warning)
        )
        click(warning, AlertDialog.BUTTON_NEGATIVE)
        assertTrue(fixture.remoteInstalls.isEmpty())
        assertEquals(before, PluginManager.getRepositories(context))

        openProject()
        click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
        assertEquals(1, fixture.remoteInstalls.size)
        assertEquals(CATALOG_URL, fixture.remoteInstalls.single().source)
        assertEquals(PLUGIN_ID, fixture.remoteInstalls.single().entry.pluginId)
        assertEquals(before, PluginManager.getRepositories(context))
    }

    @Test
    fun localRepositoryPickerAndWarningCanBeCanceledBeforeSavingASnapshot() {
        val before = PluginManager.getRepositories(context)
        openRepositoryPicker()
        returnDocument(nextPicker(), null, Activity.RESULT_CANCELED)
        assertFalse(actions.isBusy)
        assertTrue(fixture.opened.isEmpty())
        assertEquals(before, PluginManager.getRepositories(context))

        openRepositoryPicker()
        returnDocument(nextPicker(), CATALOG_URI)
        drain()
        val warning = latestDialog()
        assertEquals(
            context.getString(
                R.string.plugins_unofficial_repository_warning,
                CATALOG_NAME,
                context.getString(R.string.plugins_local_repository_source, CATALOG_NAME)
            ),
            message(warning)
        )
        click(warning, AlertDialog.BUTTON_NEGATIVE)
        assertEquals(before, PluginManager.getRepositories(context))

        openRepositoryPicker()
        returnDocument(nextPicker(), CATALOG_URI)
        drain()
        click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
        drain()
        val source = (PluginManager.getRepositories(context) - before.toSet()).single()
        assertTrue(PluginManager.isLocalRepository(source))
        assertEquals(CATALOG_NAME, PluginManager.fetchRepository(context, source).name)
        assertEquals(1, fixture.changed)
        assertTrue(fixture.localInstalls.isEmpty())
    }

    @Test
    fun localPackageMatchesConfiguredMetadataButStillRequiresDirectInstallConsent() {
        val repository = fixture.signed.repository()
        PluginManager.addRepository(context, CATALOG_URL)
        fixture.repositories = mapOf(CATALOG_URL to Result.success(repository))
        val before = PluginManager.getRepositories(context)
        actions.chooseLocalPackage()
        returnDocument(nextPicker(), PACKAGE_URI)
        drain()
        val warning = latestDialog()
        assertTrue(message(warning).contains(context.getString(R.string.plugins_local_file_source, CATALOG_URL + "\nfixture.ppp")))
        assertFalse(File(context.cacheDir, "plugin-imports").listFiles().orEmpty().any())
        click(warning, AlertDialog.BUTTON_NEGATIVE)
        assertTrue(fixture.localInstalls.isEmpty())

        actions.chooseLocalPackage()
        returnDocument(nextPicker(), PACKAGE_URI)
        drain()
        click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
        val install = fixture.localInstalls.single()
        assertEquals(CATALOG_URL, install.source)
        assertEquals(PLUGIN_ID, install.entry.pluginId)
        assertEquals(PACKAGE_URI, install.uri)
        assertEquals(before, PluginManager.getRepositories(context))
    }

    @Test
    fun companionPickerRestoresThePackageReferenceAfterActivityRecreation() {
        val before = PluginManager.getRepositories(context)
        val oldFragment = fragment
        actions.chooseLocalPackage()
        returnDocument(nextPicker(), PACKAGE_URI)
        drain()
        assertEquals(context.getString(R.string.plugins_choose_companion_message), message(latestDialog()))
        click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
        val request = nextPicker()
        controller.recreate()
        assertNotSame(oldFragment, fragment)
        assertTrue(actions.isBusy)
        returnDocument(request, CATALOG_URI)
        drain()
        val warning = latestDialog()
        assertTrue(message(warning).contains(CATALOG_NAME))
        assertTrue(message(warning).contains("fixture.ppp"))
        assertFalse(message(warning).contains("local-import.invalid"))
        assertTrue(fixture.localInstalls.isEmpty())
        click(warning, AlertDialog.BUTTON_POSITIVE)
        val install = fixture.localInstalls.single()
        assertEquals(fragment, install.owner)
        assertEquals(PACKAGE_URI, install.uri)
        assertTrue(PluginManager.isLocalRepository(install.source))
        assertEquals(before, PluginManager.getRepositories(context))
    }

    @Test
    fun aSelectedLocalCatalogEntryKeepsItsSourceAndIdentityAcrossRecreation() {
        val catalog = fixture.signed.catalog.byteInputStream().use { PluginManager.prepareLocalCatalog(it) }
        val source = PluginManager.addLocalRepository(context, catalog)
        val entry = catalog.entries.single()
        actions.chooseLocalPackage(source, entry)
        val request = nextPicker()
        controller.recreate()
        returnDocument(request, PACKAGE_URI)
        drain()
        click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
        val install = fixture.localInstalls.single()
        assertEquals(source, install.source)
        assertEquals(entry.pluginId, install.entry.pluginId)
        assertEquals(entry.sha256, install.entry.sha256)
        assertEquals(PACKAGE_URI, install.uri)
        assertTrue(fixture.resolved.isEmpty())
    }

    @Test
    fun canceledMissingAndUnreadableDocumentsLeaveTheFlowReadyToRetry() {
        for (result in listOf(Activity.RESULT_CANCELED, Activity.RESULT_OK)) {
            actions.chooseLocalPackage()
            returnDocument(nextPicker(), null, result)
            assertFalse(actions.isBusy)
        }
        assertTrue(fixture.opened.isEmpty())
        fixture.openFailure = SecurityException("Document permission expired")
        actions.chooseLocalPackage()
        returnDocument(nextPicker(), PACKAGE_URI)
        drain()
        assertFalse(actions.isBusy)
        assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("Document permission expired"))
        assertTrue(fixture.localInstalls.isEmpty())
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))

        activity.launchFailure = ActivityNotFoundException("No document picker")
        actions.chooseLocalPackage()
        assertFalse(actions.isBusy)
        assertTrue(ShadowToast.getTextOfLatestToast().toString().contains("No document picker"))
    }

    @Test
    fun confirmationAndPickerResultsRecheckEnabledAndParentAccess() {
        actions.addRepository(CATALOG_URL)
        drain()
        val warning = latestDialog()
        preferences.edit().putBoolean(context.getString(R.string.plugins_enabled_key), false).commit()
        click(warning, AlertDialog.BUTTON_POSITIVE)
        drain()
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))

        preferences.edit().putBoolean(context.getString(R.string.plugins_enabled_key), true).commit()
        actions.chooseLocalPackage()
        val request = nextPicker()
        lockParentAccess()
        returnDocument(request, PACKAGE_URI)
        drain()
        assertFalse(actions.isBusy)
        assertTrue(fixture.opened.isEmpty())
        assertTrue(fixture.localInstalls.isEmpty())
        assertEquals(context.getString(R.string.plugins_parent_access), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun destroyingTheViewCancelsLookupAndAnUnacceptedWarning() {
        actions.addRepository(CATALOG_URL)
        assertTrue(actions.isBusy)
        val owner = fragment
        activity.supportFragmentManager.beginTransaction().detach(owner).commitNow()
        drain()
        assertTrue(fixture.resolved.isEmpty())
        assertFalse(owner.actions.isBusy)
        activity.supportFragmentManager.beginTransaction().attach(owner).commitNow()
        actions.addRepository(CATALOG_URL)
        drain()
        val warning = latestDialog()
        activity.supportFragmentManager.beginTransaction().detach(owner).commitNow()
        assertFalse(warning.isShowing)
        click(warning, AlertDialog.BUTTON_POSITIVE)
        drain()
        assertEquals(listOf(PluginManager.BUILTIN_REPOSITORY), PluginManager.getRepositories(context))
        assertEquals(0, fixture.changed)
    }

    @Test
    fun aPendingDocumentWaitsForTheReattachedViewBeforeShowingConsent() {
        fixture.repositories = mapOf(CATALOG_URL to Result.success(fixture.signed.repository()))
        actions.chooseLocalPackage()
        val request = nextPicker()
        val owner = fragment
        activity.supportFragmentManager.beginTransaction().detach(owner).commitNow()
        returnDocument(request, PACKAGE_URI)
        drain()
        assertTrue(fixture.opened.isEmpty())
        activity.supportFragmentManager.beginTransaction().attach(owner).commitNow()
        drain()
        assertTrue(latestDialog().isShowing)
        assertTrue(fixture.localInstalls.isEmpty())
        click(latestDialog(), AlertDialog.BUTTON_POSITIVE)
        assertEquals(1, fixture.localInstalls.size)
    }

    @Test
    fun realPreferenceActionsAreFirstGatedAndDisableWhileThePickerIsOpen() {
        val screen = PluginSettingsFragment()
        mount(screen, "settings")
        val firstSource = requireNotNull(screen.findPreference<Preference>(SOURCE_KEYS.first()))
        assertEquals(screen.preferenceScreen.getPreference(2), firstSource.parent)
        SOURCE_KEYS.forEach { assertTrue(requireNotNull(screen.findPreference<Preference>(it)).isEnabled) }
        val file = requireNotNull(screen.findPreference<Preference>("plugins_install_file"))
        file.onPreferenceClickListener!!.onPreferenceClick(file)
        val request = nextPicker()
        SOURCE_KEYS.forEach { assertFalse(requireNotNull(screen.findPreference<Preference>(it)).isEnabled) }
        returnDocument(request, null, Activity.RESULT_CANCELED)
        SOURCE_KEYS.forEach { assertTrue(requireNotNull(screen.findPreference<Preference>(it)).isEnabled) }

        val before = PluginManager.getRepositories(context)
        val shortcut = requireNotNull(screen.findPreference<Preference>("plugins_add_awesome_repository"))
        shortcut.onPreferenceClickListener!!.onPreferenceClick(shortcut)
        // The lookup is queued, so the old immediate-add shortcut would fail this assertion.
        assertEquals(before, PluginManager.getRepositories(context))
        assertFalse(requireNotNull(screen.findPreference<Preference>("plugins_add_awesome_repository")).isEnabled)
        activity.supportFragmentManager.beginTransaction().detach(screen).commitNow()
        preferences.edit().putBoolean(context.getString(R.string.plugins_enabled_key), false).commit()
        activity.supportFragmentManager.beginTransaction().attach(screen).commitNow()
        SOURCE_KEYS.forEach { assertNull(screen.findPreference<Preference>(it)) }

        activity.supportFragmentManager.beginTransaction().detach(screen).commitNow()
        preferences.edit().putBoolean(context.getString(R.string.plugins_enabled_key), true).commit()
        lockParentAccess()
        activity.supportFragmentManager.beginTransaction().attach(screen).commitNow()
        SOURCE_KEYS.forEach { assertNull(screen.findPreference<Preference>(it)) }
        assertNull(screen.findPreference<Preference>("plugins_add_awesome_repository"))
        assertFalse(requireNotNull(screen.findPreference<SwitchPreferenceCompat>(context.getString(R.string.plugins_enabled_key))).isEnabled)
    }

    @Test
    @Config(sdk = [23])
    fun restoredEnablePreferenceCannotExposeSourceActionsOnAndroidSix() {
        actions.showAddRepository()
        actions.showInstallFromProject()
        actions.chooseLocalPackage()
        assertFalse(actions.isBusy)
        assertTrue(fixture.resolved.isEmpty())
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        val screen = PluginSettingsFragment()
        mount(screen, "settings")
        SOURCE_KEYS.forEach { assertNull(screen.findPreference<Preference>(it)) }
        assertFalse(requireNotNull(screen.findPreference<SwitchPreferenceCompat>(context.getString(R.string.plugins_enabled_key))).isEnabled)
    }

    private fun openProject() {
        actions.showInstallFromProject()
        // OnShow installs the validated positive-button listener on the next main-loop turn.
        shadowOf(Looper.getMainLooper()).idle()
        val input = latestDialog()
        input.findViewById<EditText>(R.id.dialogEditText)!!.setText(PROJECT_URL)
        click(input, AlertDialog.BUTTON_POSITIVE)
        drain()
    }

    private fun openRepositoryPicker() {
        actions.showAddRepository()
        click(latestDialog(), AlertDialog.BUTTON_NEUTRAL)
    }

    private fun nextPicker(): Int {
        val request = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(request)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        return request.requestCode
    }

    private fun returnDocument(request: Int, uri: Uri?, result: Int = Activity.RESULT_OK) {
        activity.activityResultRegistry.dispatchResult(request, result, uri?.let { Intent().setData(it) })
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun drain() {
        io.triggerActions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun mount(value: Fragment, tag: String) {
        activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, value, tag).commitNow()
    }

    private fun latestDialog(): AlertDialog {
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(
            "Expected a visible consent or input dialog; last toast=${ShadowToast.getTextOfLatestToast()}, " +
                "resolved=${fixture.resolved}, opened=${fixture.opened}",
            dialog is AlertDialog && dialog.isShowing
        )
        return dialog as AlertDialog
    }

    private fun message(dialog: AlertDialog) = dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()

    private fun click(dialog: AlertDialog, button: Int) {
        dialog.getButton(button).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun lockParentAccess() {
        assertTrue(KidsMode.setPin(context, "1234"))
        KidsMode.grantParentAccess()
        assertTrue(KidsMode.setEnabled(context, ProfileManager.getActiveProfileId(context), true))
        KidsMode.lock()
        assertFalse(PluginManager.canManage(context))
    }

    @Suppress("UNCHECKED_CAST")
    private fun restorePreferences(preferences: SharedPreferences, values: Map<String, *>) {
        preferences.edit().apply {
            clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value as Set<String>)
                }
            }
        }.commit()
    }

    class TestActivity : AppCompatActivity() {
        var launchFailure: RuntimeException? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.LightTheme)
            super.onCreate(savedInstanceState)
            setContentView(FrameLayout(this).apply { id = R.id.settings_fragment_holder })
        }

        override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
            launchFailure?.let { throw it }
            super.startActivityForResult(intent, requestCode, options)
        }
    }

    class SourceFragment : Fragment() {
        private val session get() = requireNotNull(currentSession)
        internal val actions = PluginSourceActions(
            fragment = this,
            knownRepositories = { session.repositories },
            onChanged = { session.changed++ },
            onBusyChanged = {},
            installRemote = { source, entry -> session.remoteInstalls += Install(this, source, entry) },
            installLocal = { source, entry, uri -> session.localInstalls += Install(this, source, entry, uri) },
            resolve = { source ->
                session.resolved += source
                session.resolve(source)
            },
            openInput = { uri ->
                session.opened += uri
                session.openFailure?.let { throw it }
                requireNotNull(session.documents[uri]).inputStream()
            }
        )

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            actions.restoreState(savedInstanceState)
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext())

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            actions.saveState(outState)
        }

        override fun onDestroyView() {
            actions.onDestroyView()
            super.onDestroyView()
        }
    }

    private data class Install(val owner: SourceFragment, val source: String, val entry: PluginRepository.Entry, val uri: Uri? = null)

    private class Session(val signed: SignedFixture) {
        var changed = 0
        var openFailure: RuntimeException? = null
        val opened = mutableListOf<Uri>()
        val resolved = mutableListOf<String>()
        val remoteInstalls = mutableListOf<Install>()
        val localInstalls = mutableListOf<Install>()
        var repositories: Map<String, Result<PluginRepository>> = emptyMap()
        var resolve: (String) -> List<ResolvedPluginSource> = { listOf(ResolvedPluginSource(signed.repository(it))) }
        val documents = mapOf(CATALOG_URI to signed.catalog.toByteArray(), PACKAGE_URI to signed.bytes)
    }

    private data class SignedFixture(val bytes: ByteArray, val catalog: String) {
        fun repository(url: String = CATALOG_URL): PluginRepository = PluginRepository.parse(url, catalog)
    }

    /** Authentic digest/signature metadata; this fixture's placeholder DEX is never executed. */
    private fun signedFixture(): SignedFixture {
        val manifest = JsonObject().apply {
            put("pluginId", PLUGIN_ID)
            put("name", "Fixture plugin")
            put("author", CATALOG_NAME)
            put("versionCode", 1)
            put("versionName", "1.0")
            put("apiVersion", "1.0")
            put("entryClass", "org.example.FixturePlugin")
        }
        val bytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { output ->
                for ((name, content) in mapOf("plugin.json" to JsonWriter.string(manifest).toByteArray(), "classes.dex" to byteArrayOf(1, 2, 3))) {
                    output.putNextEntry(ZipEntry(name))
                    output.write(content)
                    output.closeEntry()
                }
            }
            buffer.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val key = Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0)
        val signature = Ed25519Signer().run {
            init(true, key)
            update(digest, 0, digest.size)
            Base64.toBase64String(generateSignature())
        }
        val entry = JsonObject().apply {
            putAll(manifest)
            put("package", PACKAGE_URL)
            put("sha256", Hex.toHexString(digest))
            put("signature", signature)
            put("publicKey", Base64.toBase64String(key.generatePublicKey().encoded))
        }
        val catalog = JsonWriter.string(
            JsonObject().apply {
                put("formatVersion", 1)
                put("name", CATALOG_NAME)
                put("plugins", JsonArray(listOf(entry)))
            }
        )
        return SignedFixture(bytes, catalog)
    }

    private companion object {
        const val SOURCE_TAG = "source"
        const val CATALOG_NAME = "Fixture publisher"
        const val PLUGIN_ID = "org.example.source.fixture"
        const val CATALOG_URL = "https://example.org/plugins/index.json"
        const val PACKAGE_URL = "https://example.org/plugins/fixture.ppp"
        const val PROJECT_URL = "https://github.com/example/plugins"
        val CATALOG_URI: Uri = Uri.parse("content://plugin-test/index.json")
        val PACKAGE_URI: Uri = Uri.parse("content://plugin-test/fixture.ppp")
        val SOURCE_KEYS = listOf("plugins_add_repository", "plugins_install_project", "plugins_install_file")
        var currentSession: Session? = null
    }
}
