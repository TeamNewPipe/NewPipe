/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.InputStream
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.DialogEditTextBinding
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginRepository
import org.schabi.newpipe.plugin.PluginInstallPolicy
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.plugin.PluginSourceResolver
import org.schabi.newpipe.plugin.PreparedLocalPluginCatalog
import org.schabi.newpipe.plugin.ResolvedPluginSource

/** Source selection and consent only. The shared installer verifies every selected package. */
internal class PluginSourceActions(
    private val fragment: Fragment,
    private val knownRepositories: () -> Map<String, Result<PluginRepository>>,
    private val onChanged: () -> Unit,
    private val onBusyChanged: () -> Unit,
    private val installRemote: (String, PluginRepository.Entry) -> Unit,
    private val installLocal: (String, PluginRepository.Entry, Uri) -> Unit,
    private val resolve: (String) -> List<ResolvedPluginSource> = {
        PluginSourceResolver.resolve(it, PluginManager::fetchSourceText)
    },
    private val openInput: (Uri) -> InputStream = {
        fragment.requireContext().contentResolver.openInputStream(it)
            ?: throw PluginException("Cannot read the selected file")
    }
) {
    private enum class Picker { REPOSITORY, PACKAGE, COMPANION }

    /** Only small document references survive recreation; package bytes never enter saved state. */
    private data class Request(
        val picker: Picker,
        val packageUri: Uri? = null,
        val source: String? = null,
        val pluginId: String? = null,
        val sha256: String? = null,
        val publicKey: String? = null
    )

    private data class LocalChoice(
        val source: String,
        val entry: PluginRepository.Entry,
        val label: String
    )

    private data class LocalInspection(val packageUri: Uri, val choices: List<LocalChoice>)

    private val work = CompositeDisposable()
    private val dialogs = mutableSetOf<AlertDialog>()
    private var request: Request? = null
    private var working = false
    val isBusy: Boolean get() = working || request != null

    private val repositoryPicker = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        receiveDocument(Picker.REPOSITORY, it)
    }
    private val packagePicker = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        receiveDocument(Picker.PACKAGE, it)
    }
    private val companionPicker = fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        receiveDocument(Picker.COMPANION, it)
    }

    fun showAddRepository() = showUrlDialog(addRepository = true)

    fun showInstallFromProject() = showUrlDialog(addRepository = false)

    fun addRepository(url: String) {
        if (isBusy || !canAct()) return
        resolveUrl(url, addRepository = true)
    }

    fun chooseLocalPackage(source: String? = null, entry: PluginRepository.Entry? = null) {
        if (isBusy || !canAct()) return
        launch(Request(Picker.PACKAGE, source = source, pluginId = entry?.pluginId, sha256 = entry?.sha256, publicKey = entry?.publicKey))
    }

    fun saveState(outState: Bundle) {
        request?.let {
            outState.putBundle(
                STATE_KEY,
                Bundle().apply {
                    putString("picker", it.picker.name)
                    putString("package", it.packageUri?.toString())
                    putString("source", it.source)
                    putString("plugin", it.pluginId)
                    putString("hash", it.sha256)
                    putString("publisher", it.publicKey)
                }
            )
        }
    }

    fun restoreState(savedState: Bundle?) {
        val state = savedState?.getBundle(STATE_KEY) ?: return
        val picker = runCatching { Picker.valueOf(state.getString("picker").orEmpty()) }.getOrNull() ?: return
        request = Request(
            picker,
            state.getString("package")?.let(Uri::parse),
            state.getString("source"),
            state.getString("plugin"),
            state.getString("hash"),
            state.getString("publisher")
        )
    }

    fun onDestroyView() {
        work.clear()
        working = false
        // A pending system picker is restored separately. An unaccepted dialog is canceled.
        dialogs.toList().forEach(AlertDialog::dismiss)
        dialogs.clear()
    }

    private fun showUrlDialog(addRepository: Boolean) {
        if (isBusy || !canAct()) return
        val binding = DialogEditTextBinding.inflate(fragment.layoutInflater)
        binding.dialogEditText.hint = fragment.getString(R.string.plugins_source_url_hint)
        binding.dialogEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        val builder = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(if (addRepository) R.string.plugins_add_repository else R.string.plugins_install_project)
            .setMessage(if (addRepository) R.string.plugins_add_repository_help else R.string.plugins_install_project_help)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.plugins_source_continue, null)
        if (addRepository) {
            builder.setNeutralButton(R.string.plugins_from_storage) { _, _ ->
                if (canAct()) launch(Request(Picker.REPOSITORY))
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!canAct()) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                val url = binding.dialogEditText.text.toString().trim()
                val validation = runCatching { PluginInstallPolicy.requireHttps(url) }
                if (validation.isFailure) {
                    binding.dialogEditText.error = fragment.getString(R.string.plugins_source_https_only)
                    return@setOnClickListener
                }
                dialog.dismiss()
                resolveUrl(url, addRepository)
            }
        }
        show(dialog)
    }

    private fun resolveUrl(url: String, addRepository: Boolean) {
        background({ resolve(url) }) { sources ->
            choose(
                fragment.getString(R.string.plugins_choose_repository),
                sources,
                { "${it.repository.name}\n${it.repository.url}" }
            ) { source ->
                if (addRepository) {
                    confirmRepository(source.repository)
                } else {
                    chooseRemotePlugin(source)
                }
            }
        }
    }

    private fun confirmRepository(repository: PluginRepository, local: PreparedLocalPluginCatalog? = null) {
        if (!canAct()) return
        val source = local?.source ?: repository.url
        if (source in PluginManager.getRepositories(fragment.requireContext())) {
            message(fragment.getString(R.string.plugins_repository_already_added))
            return
        }
        val label = if (local != null) fragment.getString(R.string.plugins_local_repository_source, local.name) else source
        val accept = {
            background({
                val context = fragment.requireContext().applicationContext
                requireAccess()
                if (local != null) {
                    PluginManager.addLocalRepository(context, local)
                } else {
                    PluginManager.addRepository(context, source)
                }
            }) { onChanged() }
        }
        // The built-in identity is the exact catalog URL, never the untrusted display name.
        if (source == PluginManager.BUILTIN_REPOSITORY) {
            accept()
            return
        }
        show(
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.plugins_unofficial_repository_title)
                .setMessage(fragment.getString(R.string.plugins_unofficial_repository_warning, repository.name, label))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.plugins_add_repository) { _, _ -> if (canAct()) accept() }
                .create()
        )
    }

    private fun chooseRemotePlugin(source: ResolvedPluginSource) {
        val entries = visibleEntries(source.entries)
        choose(fragment.getString(R.string.plugins_choose_plugin), entries, ::entryLabel) { entry ->
            val label = "${source.repository.url}\n${entry.packageUrl}"
            confirmDirectInstall(entry, label) { installRemote(source.repository.url, entry) }
        }
    }

    private fun launch(next: Request) {
        if (!canAct()) return
        request = next
        onBusyChanged()
        try {
            when (next.picker) {
                Picker.REPOSITORY -> repositoryPicker.launch(arrayOf("*/*"))
                Picker.PACKAGE -> packagePicker.launch(arrayOf("*/*"))
                Picker.COMPANION -> companionPicker.launch(arrayOf("*/*"))
            }
        } catch (error: Exception) {
            request = null
            onBusyChanged()
            message(fragment.getString(R.string.plugins_file_picker_failed, error.message ?: error.javaClass.simpleName))
        }
    }

    private fun receiveDocument(picker: Picker, uri: Uri?) {
        val selected = request
        if (selected?.picker != picker) return
        request = null
        if (!canAct()) return
        onBusyChanged()
        if (uri == null) return
        when (picker) {
            Picker.REPOSITORY -> background({
                openInput(uri).use { PluginManager.prepareLocalCatalog(it, displayName(uri)) }
            }) { catalog -> confirmRepository(catalog.repository, catalog) }

            Picker.PACKAGE -> inspectPackage(uri, selected, null)

            Picker.COMPANION -> selected.packageUri?.let { inspectPackage(it, selected, uri) }
        }
    }

    private fun inspectPackage(packageUri: Uri, selected: Request, catalogUri: Uri?) {
        val context = fragment.requireContext().applicationContext
        val repositories = knownRepositories().toMap()
        background({
            val fileName = displayName(packageUri)
            openInput(packageUri).use { input ->
                PluginManager.prepareLocalPackage(context, input).use { prepared ->
                    val catalog = catalogUri?.let { uri ->
                        openInput(uri).use { PluginManager.prepareLocalCatalog(it, displayName(uri)) }
                    }
                    val choices = when {
                        catalog != null -> catalog.entries.filter(prepared::matches).map {
                            LocalChoice(catalog.source, it, "${catalog.name}\n$fileName")
                        }

                        selected.source != null -> {
                            val repository = PluginManager.fetchRepository(context, selected.source)
                            repository.plugins.filter {
                                it.pluginId == selected.pluginId && it.sha256 == selected.sha256 &&
                                    it.publicKey == selected.publicKey && prepared.matches(it)
                            }.map { LocalChoice(selected.source, it, "${repository.name}\n$fileName") }
                        }

                        else -> repositories.flatMap { (source, result) ->
                            result.getOrNull()?.let { repository ->
                                repository.plugins.filter(prepared::matches).map {
                                    val label = if (PluginManager.isLocalRepository(source)) repository.name else source
                                    LocalChoice(source, it, "$label\n$fileName")
                                }
                            }.orEmpty()
                        }
                    }.distinctBy { listOf(it.entry.pluginId, it.entry.sha256, it.entry.publicKey) }
                    if (choices.isEmpty() && (catalog != null || selected.source != null)) {
                        throw PluginException(context.getString(R.string.plugins_local_catalog_mismatch))
                    }
                    LocalInspection(packageUri, choices)
                }
            }
        }) { inspection ->
            if (inspection.choices.isEmpty()) {
                show(
                    MaterialAlertDialogBuilder(fragment.requireContext())
                        .setTitle(R.string.plugins_choose_companion_title)
                        .setMessage(R.string.plugins_choose_companion_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.plugins_choose_index) { _, _ ->
                            if (canAct()) launch(Request(Picker.COMPANION, packageUri = packageUri))
                        }
                        .create()
                )
            } else {
                val visible = visibleEntries(inspection.choices.map { it.entry }).toSet()
                choose(
                    fragment.getString(R.string.plugins_choose_plugin),
                    inspection.choices.filter { it.entry in visible },
                    { entryLabel(it.entry) + "\n" + it.label }
                ) { choice ->
                    val label = fragment.getString(R.string.plugins_local_file_source, choice.label)
                    confirmDirectInstall(choice.entry, label) {
                        installLocal(choice.source, choice.entry, inspection.packageUri)
                    }
                }
            }
        }
    }

    private fun confirmDirectInstall(entry: PluginRepository.Entry, label: String, accept: () -> Unit) {
        if (!canAct()) return
        show(
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.plugins_direct_install_title)
                .setMessage(fragment.getString(R.string.plugins_direct_install_warning, entryLabel(entry), label))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.install) { _, _ -> if (canAct()) accept() }
                .create()
        )
    }

    private fun visibleEntries(entries: List<PluginRepository.Entry>): List<PluginRepository.Entry> {
        val context = fragment.requireContext()
        val showNsfw = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(fragment.getString(R.string.plugins_show_nsfw_key), false)
        return entries.filter { !it.isNsfw || showNsfw }
    }

    private fun entryLabel(entry: PluginRepository.Entry): String = "${entry.name} ${entry.versionName}\n${entry.pluginId}"

    private fun <T> choose(title: String, items: List<T>, label: (T) -> String, accept: (T) -> Unit) {
        if (!canAct()) return
        when (items.size) {
            0 -> message(fragment.getString(R.string.plugins_source_no_plugins))

            1 -> accept(items.single())

            else -> show(
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(title)
                    .setItems(items.map(label).toTypedArray()) { _, which -> if (canAct()) accept(items[which]) }
                    .setNegativeButton(R.string.cancel, null)
                    .create()
            )
        }
    }

    private fun <T : Any> background(task: () -> T, accept: (T) -> Unit) {
        if (!canAct()) return
        working = true
        onBusyChanged()
        work.add(
            Single.fromCallable {
                requireAccess()
                task()
            }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    working = false
                    if (canAct()) {
                        onBusyChanged()
                        accept(result)
                    }
                }, { error ->
                    working = false
                    if (canAct()) {
                        onBusyChanged()
                        message(fragment.getString(R.string.plugins_source_failed, error.message ?: error.javaClass.simpleName))
                    }
                })
        )
    }

    private fun requireAccess() {
        val context = fragment.requireContext()
        if (!PluginManager.isEnabled(context)) throw PluginException(context.getString(R.string.plugins_source_disabled))
        if (!PluginManager.canManage(context)) throw PluginException(context.getString(R.string.plugins_parent_access))
    }

    private fun canAct(): Boolean {
        if (!fragment.isAdded || fragment.view == null) return false
        return runCatching { requireAccess() }.onFailure {
            message(it.message ?: fragment.getString(R.string.plugins_source_disabled))
        }.isSuccess
    }

    private fun displayName(uri: Uri): String = runCatching {
        fragment.requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: fragment.getString(R.string.plugins_from_storage)

    private fun show(dialog: AlertDialog) {
        dialogs += dialog
        dialog.setOnDismissListener { dialogs -= dialog }
        dialog.show()
    }

    private fun message(text: String) {
        fragment.context?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val STATE_KEY = "plugin_source_picker"
    }
}
