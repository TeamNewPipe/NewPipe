/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.plugin

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.InterruptedIOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.CancellationException
import org.schabi.newpipe.extractor.plugin.PluginException
import org.schabi.newpipe.extractor.plugin.PluginRepository

/** A catalog to review, optionally restricted to the individual package the user requested. */
data class ResolvedPluginSource(
    val repository: PluginRepository,
    val entries: List<PluginRepository.Entry> = repository.plugins
)

/**
 * Discovers public catalogs, never installs code or persists a repository. The caller supplies the
 * existing bounded, cookie-free HTTPS transport. Packages still go through the signed installer.
 */
object PluginSourceResolver {
    private const val MAX_FETCHES = 16
    private const val MAX_CATALOGS = 8
    private const val RECENT_RELEASES = 5

    @JvmStatic
    fun resolve(input: String, fetchText: (String) -> String): List<ResolvedPluginSource> = Discovery(fetchText).resolve(https(input.trim()))

    private enum class Host { GITHUB, GITLAB }

    private data class Project(val host: Host, val path: String) {
        val webPath: String get() = encodePath(path)
        val api: String
            get() = when (host) {
                Host.GITHUB -> "https://api.github.com/repos/$webPath"
                Host.GITLAB -> "https://gitlab.com/api/v4/projects/${segment(path)}"
            }
    }

    private data class Source(
        val url: String,
        val project: Project? = null,
        val release: String? = null,
        val latest: Boolean = false,
        val catalog: Boolean = true,
        val packageUrl: Boolean = false
    )

    private class Discovery(val fetchText: (String) -> String) {
        private val fetched = linkedMapOf<String, String?>()
        private val catalogs = linkedSetOf<String>()
        private val packageAliases = linkedSetOf<String>()
        private var failure: Exception? = null

        fun resolve(url: String): List<ResolvedPluginSource> {
            val source = source(resolveApiFile(url))
            if (source.packageUrl) {
                packageAliases += https(url)
                packageAliases += source.url
                catalog(URI(source.url).resolve("index.json").toString())
            } else if (source.catalog) {
                catalog(releaseCatalog(source))
            }
            if (!source.catalog || source.packageUrl) {
                source.project?.let { discover(it, source.release, source.latest) }
            }

            val results = catalogs.mapNotNull { candidate ->
                attempt {
                    val text = read(candidate) ?: return@attempt null
                    val declaredEntries = JsonParser.`object`().from(text)["plugins"] as? JsonArray
                        ?: throw PluginException("Repository index must contain a plugins array")
                    val repository = PluginRepository.parse(candidate, text)
                    val entries = repository.plugins.filter { entry ->
                        val packageUrl = attempt { https(entry.packageUrl) }
                        packageUrl != null && entry.signature.isNotBlank() &&
                            (!source.packageUrl || packageUrl in packageAliases)
                    }
                    if (source.packageUrl && entries.isEmpty()) {
                        null
                    } else {
                        if (declaredEntries.isNotEmpty() && entries.isEmpty()) {
                            throw PluginException("Catalog contains no valid signed HTTPS plugin entries")
                        }
                        ResolvedPluginSource(repository, entries)
                    }
                }
            }
            if (results.isEmpty()) {
                if (source.catalog && !source.packageUrl && failure != null) {
                    throw PluginException("Cannot read plugin catalog: ${failure?.message}", failure)
                }
                val message = if (source.packageUrl) {
                    "No signed catalog entry matches this package URL. Import its index.json instead."
                } else {
                    "No plugin catalog found. Use a public index.json URL or a project with a catalog in its root or releases."
                }
                throw PluginException(message, failure)
            }
            return results
        }

        private fun discover(project: Project, requestedRelease: String?, latestOnly: Boolean) {
            val metadata = json(project.api)
            // A selected release is examined first; older rolling repository tags need not occur
            // in the first page of published releases.
            requestedRelease?.let { release(project, releaseApi(project, it)) }
            if (requestedRelease != "repo" && !latestOnly) release(project, releaseApi(project, "repo"))
            release(
                project,
                when (project.host) {
                    Host.GITHUB -> "${project.api}/releases/latest"
                    Host.GITLAB -> "${project.api}/releases/permalink/latest"
                },
                isLatest = true
            )

            val branch = metadata?.getString("default_branch")?.takeIf { it.isNotBlank() }
            if (branch != null) {
                when (project.host) {
                    Host.GITHUB -> {
                        // The Contents API supplies the raw URL, including the real branch/ref.
                        // Its own API URL is never the base for relative package references.
                        val file = json("${project.api}/contents/index.json?ref=${segment(branch)}")
                        file?.getString("download_url")?.let(::catalog)
                    }

                    Host.GITLAB -> catalog("https://gitlab.com/${project.webPath}/-/raw/${segment(branch)}/index.json")
                }
            }

            array("${project.api}/releases?per_page=$RECENT_RELEASES")?.take(RECENT_RELEASES)
                ?.filterIsInstance<JsonObject>()?.forEach { release(project, it) }
        }

        private fun releaseApi(project: Project, tag: String): String = when (project.host) {
            Host.GITHUB -> "${project.api}/releases/tags/${segment(tag)}"
            Host.GITLAB -> "${project.api}/releases/${segment(tag)}"
        }

        private fun release(project: Project, url: String, isLatest: Boolean = false) {
            json(url)?.let { release(project, it, isLatest) }
        }

        private fun release(project: Project, release: JsonObject, isLatest: Boolean = false) {
            if (release.getBoolean("draft", false) || release.getBoolean("upcoming_release", false)) return
            val assets = when (project.host) {
                Host.GITHUB -> release.getArray("assets")
                Host.GITLAB -> release.getObject("assets").getArray("links")
            }
            assets.filterIsInstance<JsonObject>().forEach { asset ->
                val url = asset.getString(if (project.host == Host.GITHUB) "browser_download_url" else "url")
                    ?: return@forEach
                val safe = attempt { https(url) } ?: return@forEach
                val aliases = assetAliases(project, release, asset, safe, isLatest)
                if (aliases.any { it in packageAliases }) packageAliases += aliases
                if (asset.getString("name") == "index.json" || URI(safe).path.substringAfterLast('/') == "index.json") {
                    // GitLab's permanent asset link may point elsewhere: use the actual linked
                    // file location so a relative .ppp remains beside the catalog's own file.
                    catalog(safe)
                }
            }
        }

        private fun releaseCatalog(source: Source): String {
            val project = source.project ?: return source.url
            if (project.host != Host.GITLAB ||
                !URI(source.url).path.startsWith("/${project.path}/-/releases/")
            ) {
                return source.url
            }
            val api = if (source.latest) {
                "${project.api}/releases/permalink/latest"
            } else {
                releaseApi(project, source.release ?: return source.url)
            }
            val release = json(api) ?: return source.url
            for (asset in release.getObject("assets").getArray("links").filterIsInstance<JsonObject>()) {
                val safe = asset.getString("url")?.let { attempt { https(it) } } ?: continue
                if (source.url in assetAliases(project, release, asset, safe, source.latest)) return safe
            }
            return source.url
        }

        private fun assetAliases(project: Project, release: JsonObject, asset: JsonObject, safe: String, isLatest: Boolean): Set<String> {
            val aliases = mutableSetOf(safe)
            if (project.host == Host.GITLAB) {
                asset.getString("direct_asset_url")?.let { attempt { https(it) } }?.let(aliases::add)
            }
            // Only metadata from the latest endpoint may create a latest-release alias.
            val tag = release.getString("tag_name")
            if (isLatest && tag != null) {
                val versioned = when (project.host) {
                    Host.GITHUB -> "https://github.com/${project.webPath}/releases/download/${segment(tag)}/"
                    Host.GITLAB -> "https://gitlab.com/${project.webPath}/-/releases/${segment(tag)}/downloads/"
                }
                val latest = when (project.host) {
                    Host.GITHUB -> "https://github.com/${project.webPath}/releases/latest/download/"
                    Host.GITLAB -> "https://gitlab.com/${project.webPath}/-/releases/permalink/latest/downloads/"
                }
                aliases.toList().filter { it.startsWith(versioned) }.forEach {
                    aliases += latest + it.removePrefix(versioned)
                }
            }
            return aliases
        }

        private fun resolveApiFile(url: String): String {
            val uri = URI(url)
            val parts = uri.rawPath.trim('/').split('/')
            if (uri.host.equals("api.github.com", true) && parts.size >= 5 &&
                parts[0] == "repos" && (parts[3] == "contents" || parts[3] == "releases" && parts[4] == "assets")
            ) {
                val file = json(url) ?: throw PluginException("Cannot read the public GitHub file", failure)
                return https(
                    file.getString("download_url") ?: file.getString("browser_download_url")
                        ?: throw PluginException("GitHub URL does not identify a downloadable file")
                )
            }
            if (uri.host.equals("gitlab.com", true) && parts.size == 8 &&
                parts.take(3) == listOf("api", "v4", "projects") &&
                parts[4] == "repository" && parts[5] == "files" && parts[7] == "raw"
            ) {
                val project = json("https://gitlab.com/api/v4/projects/${parts[3]}")
                    ?: throw PluginException("Cannot read the public GitLab project", failure)
                val web = https(project.getString("web_url") ?: throw PluginException("GitLab project has no public URL"))
                val ref = query(uri, "ref").takeUnless { it.isNullOrBlank() || it == "HEAD" }
                    ?: project.getString("default_branch") ?: throw PluginException("GitLab project has no default branch")
                return https("${web.trimEnd('/')}/-/raw/${segment(ref)}/${encodePath(decode(parts[6]))}")
            }
            return url
        }

        private fun catalog(url: String) {
            if (catalogs.size < MAX_CATALOGS) attempt { catalogs += https(url) }
        }

        private fun read(url: String): String? {
            val safe = https(url)
            if (safe in fetched) return fetched[safe]
            if (fetched.size >= MAX_FETCHES) return null
            // Record before invoking the transport, including failed attempts, to bound requests.
            fetched[safe] = null
            return attempt { fetchText(safe) }?.also { fetched[safe] = it }
        }

        private fun json(url: String): JsonObject? = attempt { read(url)?.let { JsonParser.`object`().from(it) } }
        private fun array(url: String): JsonArray? = attempt { read(url)?.let { JsonParser.array().from(it) } }

        private fun <T> attempt(block: () -> T): T? = try {
            block()
        } catch (e: Exception) {
            if (e is CancellationException || e is InterruptedException || e is InterruptedIOException) throw e
            if (failure == null) failure = e
            null
        }
    }

    private fun source(url: String): Source {
        val uri = URI(url)
        val parts = uri.rawPath.trim('/').split('/')
        val host = uri.host.lowercase()
        val isPackage = uri.path.endsWith(".ppp", true)
        if (host == "github.com" || host == "www.github.com" || host == "raw.githubusercontent.com") {
            if (parts.size < 2) return Source(url)
            val project = Project(Host.GITHUB, "${decode(parts[0])}/${decode(parts[1]).removeSuffix(".git")}")
            val tail = parts.drop(2)
            if (host == "raw.githubusercontent.com") return Source(url, project, packageUrl = isPackage)
            if (tail.firstOrNull() in listOf("blob", "raw")) {
                val raw = "https://raw.githubusercontent.com/${project.webPath}/${tail.drop(1).joinToString("/")}"
                return Source(https(raw), project, packageUrl = isPackage)
            }
            if (tail.firstOrNull() == "releases") {
                if (tail.getOrNull(1) == "download" && tail.size >= 4) {
                    return Source(url, project, decode(tail[2]), packageUrl = isPackage)
                }
                if (tail.getOrNull(1) == "latest" && tail.getOrNull(2) == "download") {
                    return Source(url, project, latest = true, packageUrl = isPackage)
                }
                val tag = if (tail.getOrNull(1) == "tag") tail.drop(2).joinToString("/").let(::decode) else null
                return Source(url, project, tag, tail.getOrNull(1) == "latest", catalog = false)
            }
            if (tail.isEmpty()) return Source(url, project, catalog = false)
        }
        if (host == "gitlab.com") {
            val split = parts.indexOf("-")
            if (split >= 2) {
                val project = Project(Host.GITLAB, parts.take(split).joinToString("/", transform = ::decode))
                val tail = parts.drop(split + 1)
                if (tail.firstOrNull() in listOf("blob", "raw")) {
                    return Source(https("https://gitlab.com/${project.webPath}/-/raw/${tail.drop(1).joinToString("/")}"), project, packageUrl = isPackage)
                }
                if (tail.firstOrNull() == "releases") {
                    val latest = tail.getOrNull(1) == "permalink" && tail.getOrNull(2) == "latest"
                    val tag = if (latest) null else tail.getOrNull(1)?.let(::decode)
                    val asset = tail.drop(if (latest) 3 else 2).firstOrNull() == "downloads"
                    return Source(url, project, tag, latest, catalog = asset, packageUrl = isPackage)
                }
            } else if (split == -1 && parts.size >= 2 && parts.first() != "api") {
                return Source(url, Project(Host.GITLAB, parts.joinToString("/", transform = ::decode).removeSuffix(".git")), catalog = false)
            }
        }
        return Source(url, packageUrl = isPackage)
    }

    private fun https(url: String): String {
        val uri = URI(PluginInstallPolicy.requireHttps(url)).normalize()
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host.lowercase()}$port${uri.rawPath.orEmpty().ifEmpty { "/" }}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
    }

    private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    private fun encodePath(value: String): String = value.split('/').joinToString("/", transform = ::segment)
    private fun query(uri: URI, key: String): String? = uri.rawQuery?.split('&')?.firstNotNullOfOrNull {
        val pair = it.split('=', limit = 2)
        if (pair.size == 2 && URLDecoder.decode(pair[0], "UTF-8") == key) URLDecoder.decode(pair[1], "UTF-8") else null
    }
}
