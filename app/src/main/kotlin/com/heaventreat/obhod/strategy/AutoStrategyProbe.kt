package com.heaventreat.obhod.strategy

import android.content.Context
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import org.json.JSONObject

internal object AutoStrategyProbe {
    data class Candidate(val id: String, val cost: Int, val arguments: List<String>)

    data class Config(
        val version: Int,
        val requests: Int,
        val timeoutMs: Int,
        val concurrency: Int,
        val minBytes: Long,
        val maxBytes: Long,
        val sites: List<String>,
        val candidates: List<Candidate>
    )

    fun loadConfig(context: Context): Config {
        val settings = context.assets.open("strategies/autotest.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(settings)
        val rawCandidates = context.assets.open("strategies/proxytest_strategies.list")
            .bufferedReader().use { it.readLines() }
            .map(String::trim)
            .filter(String::isNotEmpty)
        val candidates = rawCandidates.mapIndexed { index, raw ->
            val arguments = shellWords(raw.replace("{sni}", "google.com"))
            Candidate("byedpi-%02d".format(index + 1), index + 1, arguments)
        }
        require(candidates.isNotEmpty()) { "ByeByeDPI strategy list is empty" }

        val sites = mutableListOf<String>()
        root.optJSONArray("sites")?.let { list ->
            for (index in 0 until list.length()) {
                list.optString(index).takeIf(String::isNotBlank)?.let(sites::add)
            }
        }
        require(sites.isNotEmpty()) { "autotest.json has no test sites" }
        return Config(
            version = root.optInt("version", 1),
            requests = root.optInt("requests", 2),
            timeoutMs = root.optInt("timeout_ms", 3000),
            concurrency = root.optInt("concurrency", 6),
            minBytes = root.optLong("min_bytes", 8192),
            maxBytes = root.optLong("max_bytes", 262144),
            sites = sites,
            candidates = candidates
        )
    }

    private fun shellWords(source: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (char in source) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' && quote != '\'' -> escaped = true
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote == null && char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        words += current.toString()
                        current.setLength(0)
                    }
                }
                else -> current.append(char)
            }
        }
        if (escaped) current.append('\\')
        if (current.isNotEmpty()) words += current.toString()
        return words
    }

    fun checkSites(
        port: Int,
        config: Config,
        executor: ExecutorService,
        onProgress: (Float) -> Unit = {}
    ): Int {
        val completions = ExecutorCompletionService<Int>(executor)
        config.sites.forEach { site ->
            completions.submit { checkSite(port, site, config) }
        }
        var score = 0
        repeat(config.sites.size) { completed ->
            score += completions.take().get()
            onProgress((completed + 1f) / config.sites.size)
        }
        return score
    }

    private fun checkSite(port: Int, url: String, config: Config): Int {
        var successfulRequests = 0
        for (attempt in 0 until config.requests.coerceAtLeast(1)) {
            var connection: HttpURLConnection? = null
            var success = false
            try {
                connection = URL(url).openConnection(
                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
                ) as HttpURLConnection
                connection.connectTimeout = config.timeoutMs
                connection.readTimeout = config.timeoutMs
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                )

                val code = connection.responseCode
                val declared = connection.contentLengthLong
                val limit = if (declared in 1..config.maxBytes) declared else config.maxBytes
                var actual = 0L
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                if (stream != null) {
                    stream.use { input ->
                        val buffer = ByteArray(8192)
                        while (actual < limit) {
                            val want = minOf(buffer.size.toLong(), limit - actual).toInt()
                            val read = input.read(buffer, 0, want)
                            if (read < 0) break
                            actual += read
                        }
                    }
                }
                success = when {
                    code !in 200..399 -> false
                    declared > 0 -> actual >= minOf(declared, config.maxBytes)
                    code == 204 -> true
                    else -> actual >= config.minBytes
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
            if (success) successfulRequests++ else break
        }
        return successfulRequests
    }
}
