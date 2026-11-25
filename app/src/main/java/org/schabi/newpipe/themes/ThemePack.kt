package org.schabi.newpipe.themes

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONObject
import org.schabi.newpipe.settings.appearance.CustomThemeColor

/** Versioned, data-only theme recipe. No resource paths, URLs, or executable content. */
data class ThemePack(
    val name: String,
    val author: String,
    val mode: String = "system",
    val night: String = "dark",
    val lightAccent: String = "wizestream",
    val darkAccent: String = "wizestream",
    val version: Int = 1,
    val glass: ThemeGlass? = null,
    val player: ThemePlayer = ThemePlayer(),
    val skin: String? = null,
    val icons: Map<String, String> = emptyMap(),
    val assets: Map<String, ThemeAsset> = emptyMap()
) {
    fun encode(): String = JSONObject().apply {
        require(version in 1..3) { "Unsupported version" }
        require(version >= 2 || (glass == null && player == ThemePlayer() && skin == null && icons.isEmpty() && assets.isEmpty())) {
            "Extensions require version 2 or later"
        }
        put("format", "wizestream-theme")
        put("version", version)
        put("name", name)
        put("author", author)
        put("mode", mode)
        put("night", night)
        put("lightAccent", normalizeAccent(lightAccent, version))
        put("darkAccent", normalizeAccent(darkAccent, version))
        if (version >= 2) {
            glass?.let {
                put(
                    "glass",
                    JSONObject().put("blur", it.blur).put("tint", it.tint).put("radius", it.radius).apply {
                        if (it.scrollUnderBars) put("scrollUnderBars", true)
                    }
                )
            }
            put(
                "player",
                JSONObject().put("layout", player.layout).apply {
                    player.background?.let { put("background", it) }
                    player.artwork?.let { put("artwork", it) }
                }
            )
            skin?.let { put("skin", it) }
            put("icons", JSONObject(icons))
            put("assets", JSONObject(assets.mapValues { it.value.base64 }))
        }
    }.toString(2)

    companion object {
        const val MAX_BYTES = 512 * 1024
        val accents = setOf("wizestream", "neutral", "green", "blue", "cyan", "purple", "yellow", "orange", "pink", "red", "follow_system")
        private val fields = setOf("format", "version", "name", "author", "mode", "night", "lightAccent", "darkAccent", "glass", "player", "skin", "icons", "assets")

        fun read(input: InputStream): ThemePack {
            val buffer = ByteArray(MAX_BYTES + 1)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count < 0) break
                if (count == 0) {
                    val single = input.read()
                    if (single < 0) break
                    buffer[size++] = single.toByte()
                } else {
                    size += count
                }
            }
            require(size <= MAX_BYTES) { "Theme pack exceeds 512 KiB" }
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            return parse(decoder.decode(ByteBuffer.wrap(buffer, 0, size)).toString())
        }

        fun parse(text: String): ThemePack {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val json = JsonReader(StringReader(text)).use { reader ->
                reader.isLenient = false
                val result = readObject(reader, 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected trailing content" }
                result
            }
            require(json.keys().asSequence().all { it in fields }) { "Unknown field" }
            require(json.get("format") == "wizestream-theme") { "Unsupported format" }
            val version = json.get("version") as? Int ?: error("Expected integer version")
            require(version in 1..3) { "Unsupported version" }
            if (version == 1) {
                require(text.toByteArray(Charsets.UTF_8).size <= 16 * 1024)
                require(listOf("glass", "player", "skin", "icons", "assets").none(json::has))
            }
            fun string(key: String, fallback: String? = null): String {
                if (!json.has(key) && fallback != null) return fallback
                return json.get(key) as? String ?: error("Expected text: $key")
            }
            fun label(key: String): String = string(key).also {
                require(it.isNotBlank() && it.length <= 80 && it.none { c -> c.isISOControl() || c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' }) {
                    "Invalid $key"
                }
            }
            val mode = string("mode", "system").also { require(it in setOf("system", "light", "dark", "black", "eye_comfort")) }
            val night = string("night", "dark").also { require(it in setOf("dark", "black")) }
            val light = normalizeAccent(string("lightAccent", "wizestream"), version)
            val dark = normalizeAccent(string("darkAccent", "wizestream"), version)
            val extensions = ThemeExtensions.parse(json)
            return ThemePack(
                label("name"), label("author"), mode, night, light, dark, version,
                extensions.glass, extensions.player, extensions.skin, extensions.icons, extensions.assets
            ).also { require(it.encode().toByteArray(Charsets.UTF_8).size <= if (version == 1) 16 * 1024 else MAX_BYTES) }
        }

        private fun normalizeAccent(value: String, version: Int): String {
            if (value in accents) return value
            require(version >= 3 && value.length == 7) { "Custom accent seeds require version 3" }
            return requireNotNull(CustomThemeColor.normalize(value)) { "Expected a six-digit #RRGGBB accent seed" }
        }

        private fun readObject(reader: JsonReader, depth: Int): JSONObject {
            require(depth <= 2) { "Theme nesting is too deep" }
            val result = JSONObject()
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(!result.has(key)) { "Duplicate field: $key" }
                val value: Any = when (reader.peek()) {
                    JsonToken.STRING -> reader.nextString()

                    JsonToken.NUMBER -> reader.nextString().let {
                        require(it.matches(Regex("0|[1-9][0-9]{0,3}"))) { "Expected bounded integer" }
                        it.toInt()
                    }

                    JsonToken.BOOLEAN -> reader.nextBoolean()

                    JsonToken.BEGIN_OBJECT -> readObject(reader, depth + 1)

                    else -> error("Unsupported value: $key")
                }
                result.put(key, value)
            }
            reader.endObject()
            return result
        }
    }
}
