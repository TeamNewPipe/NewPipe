/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.export

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.schabi.newpipe.sync.PortableSettingId
import org.schabi.newpipe.sync.PortableSettingValueType
import org.schabi.newpipe.sync.portableSettingSpecs

/** File operations use the same non-sensitive, typed settings allowlist as Device Sync. */
internal object SelectiveSettingsTransfer {
    private const val FORMAT_VERSION = 1
    private const val MAX_FILE_CHARS = 65_536
    private const val MAX_STRING_LENGTH = 1_024

    fun allowedIds(context: Context): List<PortableSettingId> {
        return portableSettingSpecs(context).map { it.id }.sortedBy { it.name }
    }

    fun export(
        context: Context,
        preferences: SharedPreferences,
        selected: Set<PortableSettingId>
    ): Pair<String, Int> {
        val values = JSONObject()
        for (spec in portableSettingSpecs(context)) {
            if (spec.id !in selected) continue
            val value = preferences.all[spec.preferenceKey] ?: continue
            val typed = typedValue(spec.id, value) ?: continue
            values.put(spec.id.name, typed)
        }
        val document = JSONObject()
            .put("format", "wizestream-selected-settings")
            .put("version", FORMAT_VERSION)
            .put("settings", values)
            .toString(2)
        require(document.length <= MAX_FILE_CHARS) { "Settings file too large" }
        return document to values.length()
    }

    /**
     * Validate everything before touching SharedPreferences. An imported file cannot write
     * unknown keys, protected identity data, tokens or values of the wrong type.
     */
    fun importSelected(
        context: Context,
        preferences: SharedPreferences,
        selected: Set<PortableSettingId>,
        document: String
    ): Int {
        require(document.length <= MAX_FILE_CHARS) { "Settings file too large" }
        val json = JSONObject(document)
        require(json.optString("format") == "wizestream-selected-settings")
        require(json.optInt("version") == FORMAT_VERSION)
        val values = json.getJSONObject("settings")
        val allowlist = portableSettingSpecs(context).associateBy { it.id.name }
        val updates = mutableListOf<Pair<String, Any>>()
        val keys = values.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val spec = allowlist[id] ?: error("Unrecognized setting: $id")
            if (spec.id !in selected) continue
            val node = values.getJSONObject(id)
            val actual = node.get("value")
            val typed: Any = when (spec.id.valueType) {
                PortableSettingValueType.BOOLEAN -> {
                    require(node.getString("type") == "BOOLEAN")
                    require(actual is Boolean)
                    actual
                }

                PortableSettingValueType.STRING -> {
                    require(node.getString("type") == "STRING")
                    require(actual is String && actual.isNotBlank() && actual.length <= MAX_STRING_LENGTH)
                    actual
                }

                PortableSettingValueType.FLOAT -> {
                    require(node.getString("type") == "FLOAT")
                    require(actual is Number && actual.toDouble().isFinite())
                    actual.toFloat().also { require(it.isFinite()) }
                }
            }
            updates += spec.preferenceKey to typed
        }
        val editor = preferences.edit()
        updates.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        check(editor.commit()) { "Unable to save selected settings" }
        return updates.size
    }

    fun reset(
        context: Context,
        preferences: SharedPreferences,
        selected: Set<PortableSettingId>
    ): Int {
        val specs = portableSettingSpecs(context).filter { it.id in selected }
        val editor = preferences.edit()
        specs.forEach { editor.remove(it.preferenceKey) }
        check(editor.commit()) { "Unable to reset selected settings" }
        return specs.size
    }

    private fun typedValue(id: PortableSettingId, value: Any): JSONObject? {
        return when (id.valueType) {
            PortableSettingValueType.BOOLEAN -> (value as? Boolean)?.let {
                JSONObject().put("type", "BOOLEAN").put("value", it)
            }

            PortableSettingValueType.STRING -> (value as? String)
                ?.takeIf { it.isNotBlank() && it.length <= MAX_STRING_LENGTH }
                ?.let { JSONObject().put("type", "STRING").put("value", it) }

            PortableSettingValueType.FLOAT -> (value as? Float)
                ?.takeIf { it.isFinite() }
                ?.let { JSONObject().put("type", "FLOAT").put("value", it.toDouble()) }
        }
    }
}
