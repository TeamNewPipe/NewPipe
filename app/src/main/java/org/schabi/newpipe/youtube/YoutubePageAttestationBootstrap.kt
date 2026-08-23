package org.schabi.newpipe.youtube

import com.grack.nanojson.JsonParser
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrProtocolException

internal enum class YoutubePoTokenBinding {
    CONTENT,
    SESSION,
    NONE,
}

internal data class YoutubePageAttestationBootstrap(
    val visitorData: String,
    val dataSyncId: String?,
    val clientName: String,
    val clientVersion: String,
    val binding: YoutubePoTokenBinding,
    val eventId: String,
    val challenge: SabrAttChallengeData,
)

internal data class SabrAttChallengeData(
    val program: String,
    val globalName: String,
    val interpreterJavascript: String?,
    val interpreterUrl: String?,
)

internal fun parseYoutubePageAttestationBootstrap(
    pageHtml: String,
): YoutubePageAttestationBootstrap {
    val configCalls = extractObjectCallArguments(pageHtml, YTCFG_CALLEE)
        .mapNotNull { call ->
            try {
                call to JsonParser.`object`().from(call.argument)
            } catch (_: Exception) {
                null
            }
        }
    val challengeCall = extractObjectCallArguments(pageHtml, INITIAL_ATTESTATION_CALLEE)
        .asSequence()
        .mapNotNull { call -> parseInitialAttestationChallenge(call.argument)?.let { call to it } }
        .firstOrNull()
        ?: throw SabrProtocolException("YouTube home has no initial attestation challenge")
    val configs = configCalls
        .filter { (call) -> call.start < challengeCall.first.start }
        .map { (_, config) -> config }
    val clientConfig = configs.lastOrNull { it.has("INNERTUBE_CONTEXT") }
        ?: throw SabrProtocolException("YouTube home has no client context")
    val eventId = configs.asSequence()
        .mapNotNull { it.getString("EVENT_ID")?.takeIf(String::isNotEmpty) }
        .lastOrNull()
        ?: throw SabrProtocolException("YouTube home has no EVENT_ID")
    val visitorData = (
        clientConfig.getString("EOM_VISITOR_DATA")?.takeIf(String::isNotEmpty)
            ?: clientConfig.getString("VISITOR_DATA")?.takeIf(String::isNotEmpty)
        )?.replace("%3D", "=", ignoreCase = true)
        ?: throw SabrProtocolException("YouTube home has no anonymous visitor data")
    val dataSyncId = configs.asSequence()
        .mapNotNull { it.getString("DATASYNC_ID")?.takeIf(String::isNotEmpty) }
        .lastOrNull()
    val client = clientConfig.getObject("INNERTUBE_CONTEXT")?.getObject("client")
        ?: throw SabrProtocolException("YouTube home has no Innertube client context")
    val clientName = client.getString("clientName")?.takeIf(String::isNotEmpty)
        ?: throw SabrProtocolException("YouTube home has no client name")
    if (clientName != "WEB") {
        throw SabrProtocolException("Unsupported YouTube home client: $clientName")
    }
    val clientVersion = client.getString("clientVersion")?.takeIf(String::isNotEmpty)
        ?: throw SabrProtocolException("YouTube home has no client version")
    val watchConfig = clientConfig.getObject("WEB_PLAYER_CONTEXT_CONFIGS")
        ?.getObject("WEB_PLAYER_CONTEXT_CONFIG_ID_KEVLAR_WATCH")
    val experimentFlags = watchConfig?.getString("serializedExperimentFlags")
        ?.let(::parseExperimentFlags)
        .orEmpty()
    val binding = when {
        experimentFlags["html5_generate_content_po_token"] == "true" -> {
            YoutubePoTokenBinding.CONTENT
        }
        experimentFlags["html5_generate_session_po_token"] == "true" -> {
            YoutubePoTokenBinding.SESSION
        }
        watchConfig == null -> YoutubePoTokenBinding.CONTENT
        else -> YoutubePoTokenBinding.NONE
    }
    return YoutubePageAttestationBootstrap(
        visitorData,
        dataSyncId,
        clientName,
        clientVersion,
        binding,
        eventId,
        challengeCall.second,
    )
}

private fun parseInitialAttestationChallenge(argument: String): SabrAttChallengeData? {
    val responseProperty = INITIAL_ATTESTATION_RESPONSE.find(argument) ?: return null
    val quote = responseProperty.groupValues[1].single()
    val rawChallenge = try {
        decodeJavascriptString(argument, responseProperty.range.last + 1, quote)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return try {
        parseSabrAttChallengeData(rawChallenge)
    } catch (_: Exception) {
        null
    }
}

internal fun parseSabrAttChallengeData(rawAttestationData: String): SabrAttChallengeData {
    val challenge = JsonParser.`object`().from(rawAttestationData).getObject("bgChallenge")
        ?: throw SabrProtocolException("Attestation response has no BotGuard challenge")
    val interpreterJavascript = challenge.getObject("interpreterJavascript")
        ?.getString("privateDoNotAccessOrElseSafeScriptWrappedValue")
        ?.takeIf(String::isNotEmpty)
    val rawInterpreterUrl = challenge.getObject("interpreterUrl")
        ?.getString("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue")
        ?.takeIf(String::isNotEmpty)
    val interpreterUrl = rawInterpreterUrl?.let {
        if (it.startsWith("//")) "https:$it" else it
    }
    if (interpreterJavascript == null && interpreterUrl == null) {
        throw SabrProtocolException("Attestation challenge has no interpreter script or URL")
    }
    val program = challenge.getString("program")?.takeIf(String::isNotEmpty)
        ?: throw SabrProtocolException("Attestation challenge has no program")
    val globalName = challenge.getString("globalName")?.takeIf(String::isNotEmpty)
        ?: throw SabrProtocolException("Attestation challenge has no global name")
    return SabrAttChallengeData(program, globalName, interpreterJavascript, interpreterUrl)
}

private data class JavascriptObjectCall(
    val start: Int,
    val argument: String,
)

private fun extractObjectCallArguments(
    source: String,
    callee: String,
): List<JavascriptObjectCall> {
    val arguments = ArrayList<JavascriptObjectCall>()
    var searchFrom = 0
    while (true) {
        val callStart = source.indexOf(callee, searchFrom)
        if (callStart < 0) return arguments
        var openingParenthesis = callStart + callee.length
        while (openingParenthesis < source.length && source[openingParenthesis].isWhitespace()) {
            openingParenthesis++
        }
        if (openingParenthesis >= source.length || source[openingParenthesis] != '(') {
            searchFrom = callStart + callee.length
            continue
        }
        var objectStart = openingParenthesis + 1
        while (objectStart < source.length && source[objectStart].isWhitespace()) objectStart++
        if (objectStart >= source.length || source[objectStart] != '{') {
            searchFrom = openingParenthesis + 1
            continue
        }
        val objectEnd = findJavascriptObjectEnd(source, objectStart)
        if (objectEnd < 0) {
            searchFrom = objectStart + 1
            continue
        }
        arguments.add(JavascriptObjectCall(callStart, source.substring(objectStart, objectEnd + 1)))
        searchFrom = objectEnd + 1
    }
}

private fun findJavascriptObjectEnd(source: String, start: Int): Int {
    var depth = 0
    var quote = '\u0000'
    var escaped = false
    for (index in start until source.length) {
        val character = source[index]
        if (quote != '\u0000') {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == quote) {
                quote = '\u0000'
            }
            continue
        }
        when (character) {
            '\'', '"' -> quote = character
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return -1
}

private fun parseExperimentFlags(serializedFlags: String): Map<String, String> {
    return serializedFlags.split('&').associate { part ->
        val separator = part.indexOf('=')
        if (separator < 0) part to "true"
        else part.substring(0, separator) to part.substring(separator + 1)
    }
}

private fun decodeJavascriptString(source: String, start: Int, quote: Char): String {
    val result = StringBuilder()
    var index = start
    while (index < source.length) {
        val character = source[index++]
        if (character == quote) return result.toString()
        if (character != '\\') {
            result.append(character)
            continue
        }
        require(index < source.length) { "Incomplete JavaScript string escape" }
        when (val escaped = source[index++]) {
            'b' -> result.append('\b')
            'f' -> result.append('\u000C')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'v' -> result.append('\u000B')
            'x' -> {
                result.append(readJavascriptHex(source, index, 2).toChar())
                index += 2
            }
            'u' -> {
                result.append(readJavascriptHex(source, index, 4).toChar())
                index += 4
            }
            '\n' -> Unit
            '\r' -> if (index < source.length && source[index] == '\n') index++
            else -> result.append(escaped)
        }
    }
    throw IllegalArgumentException("Unterminated JavaScript string")
}

private fun readJavascriptHex(source: String, start: Int, length: Int): Int {
    require(start + length <= source.length) { "Incomplete hexadecimal escape" }
    var value = 0
    repeat(length) { offset ->
        val digit = source[start + offset].digitToIntOrNull(16)
            ?: throw IllegalArgumentException("Invalid hexadecimal escape")
        value = value * 16 + digit
    }
    return value
}

private const val YTCFG_CALLEE = "ytcfg.set"
private const val INITIAL_ATTESTATION_CALLEE = "window.ytAtN"
private val INITIAL_ATTESTATION_RESPONSE = Regex("['\"]R['\"]\\s*:\\s*(['\"])")
