package org.schabi.newpipe.settings

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import java.io.IOException
import java.net.URI
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend

/**
 * Explicit, one-shot discovery from the official Invidious directory.
 *
 * Discovery never changes preferences and deliberately excludes non-HTTPS, unhealthy, and
 * non-API instances. Companion/media origins remain a separate trust decision.
 */
object InvidiousInstanceDirectory {
    private const val DIRECTORY_URL = "https://api.invidious.io/instances.json"
    private const val MAX_RESPONSE_CHARS = 512_000
    private const val MIN_UPTIME_PERCENT = 90.0

    data class Entry(
        val url: String,
        val host: String,
        val flag: String,
        val region: String,
        val uptime: Double
    )

    @JvmStatic
    @Throws(IOException::class, ExtractionException::class)
    fun load(): List<Entry> {
        val response = NewPipe.getDownloader().get(DIRECTORY_URL)
        if (response.responseCode() !in 200..299) {
            throw ExtractionException(
                "The official Invidious directory returned HTTP ${response.responseCode()}"
            )
        }
        if (response.getHeader("Content-Type")?.contains("text/html", ignoreCase = true) == true) {
            throw ExtractionException("The official Invidious directory returned HTML")
        }
        val body = response.responseBody()
        if (body.length > MAX_RESPONSE_CHARS) {
            throw ExtractionException("The official Invidious directory response is too large")
        }
        return parse(body)
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun parse(body: String): List<Entry> {
        val directory = try {
            JsonParser.array().from(body)
        } catch (error: JsonParserException) {
            throw ExtractionException("Invalid official Invidious directory response", error)
        }

        val entries = mutableListOf<Entry>()
        for (item in directory) {
            val pair = item as? JsonArray ?: continue
            if (pair.size < 2) continue
            val metadata = pair[1] as? JsonObject ?: continue
            val monitor = metadata.getObject("monitor") ?: continue
            val ssl = monitor.getObject("ssl") ?: continue
            val uptime = monitor.getDouble("uptime", -1.0)

            if (metadata.getString("type", "") != "https" ||
                !metadata.getBoolean("api", false) ||
                monitor.getBoolean("down", true) ||
                !monitor.getBoolean("enabled", false) ||
                !monitor.getBoolean("published", false) ||
                monitor.getInt("last_status", 0) !in 200..399 ||
                !ssl.getBoolean("valid", false) ||
                !uptime.isFinite() ||
                uptime < MIN_UPTIME_PERCENT
            ) {
                continue
            }

            val normalized = try {
                InvidiousBackend.normalizeInstance(metadata.getString("uri", ""))
            } catch (_: IllegalArgumentException) {
                continue
            }
            val host = URI.create(normalized).host ?: continue
            if (entries.any { it.url == normalized }) continue

            entries += Entry(
                url = normalized,
                host = host,
                flag = metadata.getString("flag", "").take(8),
                region = metadata.getString("region", "")
                    .uppercase()
                    .takeIf { it.matches(Regex("[A-Z]{2}")) }
                    .orEmpty(),
                uptime = uptime
            )
        }
        return entries
    }
}
