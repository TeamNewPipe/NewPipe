/* SPDX-License-Identifier: GPL-3.0-or-later
 * Adapted from PipePipeClient 43ef494c1a1792a774c223e1982ff1e794c49017.
 */
package org.schabi.newpipe.settings

import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.plugin.PluginInstallPolicy
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.util.NavigationHelper

/**
 * Opt-in switch, repositories, installed and available plugins.
 *
 * Plugins are off by default and enabling them requires confirming a warning that states
 * they bypass store review (F-Droid inclusion policy, "explicit user consent").
 */
class PluginSettingsFragment : BasePreferenceFragment() {

    private val disposables = CompositeDisposable()
    private var repositories: Map<String, Result<PluginRepository>> = emptyMap()
    private var restartSnackbar: Snackbar? = null
    private var repositoryFetch: Disposable? = null
    private var refreshing = false
    private var installing = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.plugin_settings, rootKey)

        val enabledSwitch = requirePreference<SwitchPreferenceCompat>(R.string.plugins_enabled_key)
        enabledSwitch.isPersistent = false
        enabledSwitch.isChecked = PluginManager.isEnabled(requireContext())
        enabledSwitch.isEnabled = PluginManager.isPlatformSupported() && PluginManager.canManage(requireContext())
        if (!PluginManager.isPlatformSupported()) enabledSwitch.summary = getString(R.string.plugins_android_version)
        enabledSwitch.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                confirmEnable(enabledSwitch)
            } else {
                PluginManager.setEnabled(requireContext(), false)
                enabledSwitch.isChecked = false
                rebuild()
                showRestartHint()
            }
            false
        }
        requirePreference<SwitchPreferenceCompat>(R.string.plugins_show_nsfw_key).setOnPreferenceChangeListener { _, _ ->
            // the value is stored after this callback returns
            view?.post { rebuild() }
            true
        }
        rebuild()
        refreshRepositories()
    }

    override fun onDestroyView() {
        repositoryFetch?.dispose()
        refreshing = false
        disposables.clear()
        installing = false
        restartSnackbar?.dismiss()
        restartSnackbar = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
        if (PluginManager.isRestartRequired(requireContext())) showRestartHint()
        if (repositories.isEmpty() && !refreshing) refreshRepositories()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    private fun confirmEnable(enabledSwitch: SwitchPreferenceCompat) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.plugins_warning_title)
            .setMessage(R.string.plugins_warning_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.plugins_warning_accept) { _, _ ->
                PluginManager.setEnabled(requireContext(), true)
                enabledSwitch.isChecked = true
                rebuild()
                refreshRepositories()
                if (PluginManager.getInstalled(requireContext()).isNotEmpty()) {
                    showRestartHint()
                }
            }
            .show()
    }

    /** Recreate everything below the switch from the current state. */
    private fun rebuild() {
        val screen = preferenceScreen
        for (i in screen.preferenceCount - 1 downTo 0) {
            val pref = screen.getPreference(i)
            if (pref.key != getString(R.string.plugins_enabled_key) &&
                pref.key != getString(R.string.plugins_show_nsfw_key)
            ) {
                screen.removePreference(pref)
            }
        }
        val context = requireContext()
        requirePreference<SwitchPreferenceCompat>(R.string.plugins_show_nsfw_key).isVisible = PluginManager.isEnabled(context) && PluginManager.canManage(context)
        if (!PluginManager.isEnabled(context)) {
            return
        }

        if (!PluginManager.canManage(context)) {
            screen.addPreference(info(getString(R.string.plugins_parent_access)))
            return
        }
        val installed = PluginManager.getInstalled(context)
        PluginManager.getStateError()?.let { error ->
            screen.addPreference(info(getString(R.string.plugin_state_failed, error)))
            return
        }
        val installedCategory = category(R.string.plugins_installed)
        if (installed.isEmpty()) {
            installedCategory.addPreference(info(getString(R.string.plugins_none_installed)))
        }
        for (plugin in installed) {
            val error = PluginManager.getLoadError(plugin.pluginId)
            val available = availableEntries().firstOrNull {
                it.second.pluginId == plugin.pluginId && it.second.publicKey == plugin.publicKey
            }
            val updatable = available != null &&
                available.second.versionCode > plugin.versionCode &&
                PluginManager.compatibilityError(available.second) == null
            installedCategory.addPreference(
                Preference(context).apply {
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    title = plugin.name
                    summary = hostLine(plugin.host) + authorLine(plugin.author.ifEmpty { loadedAuthor(plugin.pluginId) }) + when {
                        error != null -> getString(R.string.plugin_status_error, plugin.versionName, error)

                        PluginManager.getServiceIdByPluginId(plugin.pluginId)?.let {
                            PluginManager.getLoadedManifest(it)?.versionCode
                        } == plugin.versionCode -> getString(R.string.plugin_status_loaded, plugin.versionName)

                        else -> getString(R.string.plugin_status_pending, plugin.versionName)
                    } + if (updatable) " · " + getString(R.string.plugin_update) + " " + available!!.second.versionName else ""
                    isEnabled = !installing
                    setOnPreferenceClickListener {
                        showInstalledActions(plugin, if (updatable) available else null)
                        true
                    }
                }
            )
        }

        val showNsfw = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(getString(R.string.plugins_show_nsfw_key), false)
        val availableCategory = category(R.string.plugins_available)
        val notInstalled = availableEntries()
            .filter { (_, entry) ->
                installed.none { it.pluginId == entry.pluginId && it.publicKey == entry.publicKey }
            }
            .filter { (_, entry) -> !entry.isNsfw || showNsfw }
        for ((repoUrl, entry) in notInstalled) {
            availableCategory.addPreference(
                Preference(context).apply {
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    title = entry.name + " " + entry.versionName
                    // An installed identity cannot be claimed by a different publisher.
                    val otherAuthor = installed.firstOrNull { it.pluginId == entry.pluginId }
                    val builtIn = PluginManager.builtInServiceName(entry.pluginId, entry.name)
                    val trusted = PluginManager.trustedPublisher(context, entry.pluginId)
                    val differentPublisher = trusted != null && trusted != entry.publicKey
                    val compatibilityError = PluginManager.compatibilityError(entry)
                    summary = hostLine(entry.host) + authorLine(entry.author) + when {
                        builtIn != null -> getString(R.string.plugin_already_builtin, builtIn)
                        differentPublisher -> getString(R.string.plugin_key_changed)
                        otherAuthor != null -> getString(R.string.plugin_installed_other_publisher)
                        compatibilityError != null -> compatibilityError
                        else -> entry.description ?: entry.pluginId
                    }
                    isEnabled = compatibilityError == null && otherAuthor == null && builtIn == null &&
                        !differentPublisher && !installing
                    setOnPreferenceClickListener {
                        confirmInstall(repoUrl, entry)
                        true
                    }
                }
            )
        }

        if (notInstalled.isEmpty()) {
            availableCategory.addPreference(
                info(
                    getString(
                        if (refreshing) R.string.plugins_loading else R.string.plugins_none_available
                    )
                )
            )
        }
        val repoCategory = category(R.string.plugins_repositories)
        repoCategory.addPreference(
            Preference(context).apply {
                title = getString(R.string.plugins_refresh)
                isIconSpaceReserved = false
                isEnabled = !refreshing && !installing
                setOnPreferenceClickListener {
                    refreshRepositories()
                    true
                }
            }
        )
        val repoUrls = PluginManager.getRepositories(context)
        if (repoUrls.isEmpty()) {
            repoCategory.addPreference(info(getString(R.string.plugins_no_repositories)))
        }
        for (url in repoUrls) {
            val result = repositories[url]
            repoCategory.addPreference(
                Preference(context).apply {
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    title = result?.getOrNull()?.name ?: url
                    summary = result?.exceptionOrNull()?.let {
                        getString(R.string.plugin_repository_failed, url, it.message ?: it.javaClass.simpleName)
                    } ?: url
                    setOnPreferenceClickListener {
                        if (url == PluginManager.BUILTIN_REPOSITORY) {
                            return@setOnPreferenceClickListener true
                        }
                        AlertDialog.Builder(context)
                            .setTitle(R.string.plugins_remove_repository)
                            .setMessage(url)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                PluginManager.removeRepository(context, url)
                                repositories = repositories - url
                                rebuild()
                            }
                            .show()
                        true
                    }
                }
            )
        }
        if (PluginManager.AWESOME_REPOSITORY !in repoUrls) {
            repoCategory.addPreference(
                Preference(context).apply {
                    isIconSpaceReserved = false
                    isSingleLineTitle = false
                    title = getString(R.string.plugins_add_awesome_repository)
                    summary = getString(R.string.plugins_add_awesome_repository_summary)
                    setOnPreferenceClickListener {
                        PluginManager.addRepository(context, PluginManager.AWESOME_REPOSITORY)
                        rebuild()
                        refreshRepositories()
                        true
                    }
                }
            )
        }
        repoCategory.addPreference(
            Preference(context).apply {
                isIconSpaceReserved = false
                title = getString(R.string.plugins_add_repository)
                setOnPreferenceClickListener {
                    showAddRepository()
                    true
                }
            }
        )
    }

    /** "by xxx" followed by a line break, or nothing when the author is unknown. */
    private fun authorLine(author: String?): String = if (author.isNullOrBlank()) "" else getString(R.string.plugin_author, author) + "\n"

    private fun hostLine(host: String): String = when (host) {
        "wizestream" -> getString(R.string.plugin_host_wizestream)
        "pipepipe" -> getString(R.string.plugin_host_pipepipe)
        else -> getString(R.string.plugin_host_other, host)
    } + "\n"

    private fun loadedAuthor(pluginId: String): String? = installedPluginServiceId(pluginId)?.let { PluginManager.getLoadedManifest(it)?.author }

    private fun availableEntries(): List<Pair<String, PluginRepository.Entry>> = PluginInstallPolicy.preferredEntries(
        repositories.flatMap { (url, result) ->
            result.getOrNull()?.plugins.orEmpty().map { url to it }
        },
        BuildConfig.VERSION_CODE,
        Build.VERSION.SDK_INT
    )

    private fun category(title: Int) = PreferenceCategory(requireContext()).also {
        it.layoutResource = R.layout.settings_category_header_layout
        it.title = getString(title)
        it.isIconSpaceReserved = false
        preferenceScreen.addPreference(it)
    }

    private fun info(text: String) = Preference(requireContext()).apply {
        summary = text
        isSelectable = false
        isIconSpaceReserved = false
        isSingleLineTitle = false
    }

    private fun refreshRepositories() {
        val context = requireContext()
        if (!PluginManager.isEnabled(context)) {
            return
        }
        val urls = PluginManager.getRepositories(context)
        repositoryFetch?.dispose()
        refreshing = true
        rebuild()
        repositoryFetch = Single.fromCallable {
            urls.associateWith { url -> runCatching { PluginManager.fetchRepository(url) } }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                if (!isAdded) return@subscribe
                refreshing = false
                repositories = result.filterKeys { it in PluginManager.getRepositories(requireContext()) }
                rebuild()
            }, {
                refreshing = false
                if (isAdded) rebuild()
            })
    }

    private fun showAddRepository() {
        val binding = DialogEditTextBinding.inflate(layoutInflater)
        binding.dialogEditText.hint = getString(R.string.plugins_repository_url_hint)
        binding.dialogEditText.inputType = InputType.TYPE_TEXT_VARIATION_URI
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.plugins_add_repository)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val url = binding.dialogEditText.text.toString().trim()
                try {
                    PluginManager.addRepository(requireContext(), url)
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: getString(R.string.plugins_repository_https_only), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                rebuild()
                refreshRepositories()
            }
            .show()
    }

    private fun confirmInstall(repoUrl: String, entry: PluginRepository.Entry) {
        AlertDialog.Builder(requireContext())
            .setTitle(entry.name)
            .setMessage(getString(R.string.plugin_install_from, entry.name, repoUrl))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.install) { _, _ -> install(repoUrl, entry) }
            .show()
    }

    private fun install(repoUrl: String, entry: PluginRepository.Entry) {
        val context = requireContext().applicationContext
        if (installing) return
        installing = true
        rebuild()
        Toast.makeText(context, getString(R.string.plugin_installing, entry.name), Toast.LENGTH_SHORT).show()
        disposables.add(
            Single.fromCallable { PluginManager.install(context, repoUrl, entry) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    installing = false
                    if (!isAdded) return@subscribe
                    rebuild()
                    showRestartHint()
                }, { e ->
                    installing = false
                    if (!isAdded) return@subscribe
                    rebuild()
                    Toast.makeText(
                        context,
                        getString(R.string.plugin_install_failed, entry.name, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                })
        )
    }

    private fun showInstalledActions(
        plugin: PluginManager.InstalledPlugin,
        update: Pair<String, PluginRepository.Entry>?
    ) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (update != null) {
            actions += getString(R.string.plugin_update) to { install(update.first, update.second) }
        }
        val serviceId = PluginManager.getInstalled(requireContext())
            .firstOrNull { it.pluginId == plugin.pluginId }
            ?.let { installedPluginServiceId(plugin.pluginId) }
        if (serviceId != null && PluginManager.getLoadedManifest(serviceId)?.let { it.settings.isNotEmpty() || it.login != null } == true) {
            actions += getString(R.string.settings) to {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, PluginServiceSettingsFragment.newInstance(serviceId))
                    .addToBackStack(null)
                    .commit()
            }
        }
        actions += getString(R.string.plugin_uninstall) to {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.plugin_uninstall_confirm, plugin.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.plugin_uninstall) { _, _ ->
                    runCatching { PluginManager.uninstall(requireContext(), plugin.pluginId) }
                        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    rebuild()
                    showRestartHint()
                }
                .show()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(plugin.name)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun installedPluginServiceId(pluginId: String): Int? = org.schabi.newpipe.extractor.ServiceList.all()
        .firstOrNull { PluginManager.getLoadedManifest(it.serviceId)?.pluginId == pluginId }
        ?.serviceId

    private fun showRestartHint() {
        val view = view ?: return
        if (restartSnackbar?.isShown == true) {
            return
        }
        restartSnackbar = Snackbar.make(view, R.string.plugin_restart_required, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.plugin_restart) { NavigationHelper.restartApp(requireActivity()) }
            .also { it.show() }
    }
}
