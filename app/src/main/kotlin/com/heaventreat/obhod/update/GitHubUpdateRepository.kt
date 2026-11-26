package com.heaventreat.obhod.update

import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal object GitHubUpdateRepository {
    private const val REPOSITORY = "heavenTreatment/Obhod"
    private const val TAGS_URL = "https://api.github.com/repos/$REPOSITORY/tags?per_page=100"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPOSITORY/releases/tags/"
    private const val RELEASE_PAGE_URL = "https://github.com/$REPOSITORY/releases/tag/"

    fun findUpdate(currentVersion: String): AppUpdate? {
        val installed = ReleaseVersion.parse(currentVersion)
            ?: throw IllegalArgumentException("Invalid installed version: $currentVersion")
        val tags = getJsonArray(TAGS_URL)
        var latest: Pair<String, ReleaseVersion>? = null
        for (index in 0 until tags.length()) {
            val tag = tags.getJSONObject(index).optString("name")
            val version = ReleaseVersion.parse(tag) ?: continue
            if (latest == null || version > latest.second) latest = tag to version
        }

        val (tag, version) = latest ?: return null
        if (version <= installed) return null

        val release = getJsonOrNull(RELEASES_URL + tag)
        val assets = release?.optJSONArray("assets")
        val apk = if (assets == null) null else {
            val apkAssets = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            val universal = apkAssets.firstOrNull {
                "universal" in it.optString("name").lowercase()
            }
            val generic = apkAssets.firstOrNull {
                !hasArchitectureSuffix(it.optString("name"))
            }
            universal ?: generic ?: apkAssets
                .mapNotNull { asset ->
                    Build.SUPPORTED_ABIS.indexOfFirst {
                        matchesAbi(asset.optString("name"), it)
                    }.takeIf { it >= 0 }?.let { it to asset }
                }
                .minByOrNull { it.first }
                ?.second
        }
        return AppUpdate(
            tag = tag,
            version = version,
            releasePageUrl = release?.optString("html_url")
                ?.takeIf(String::isNotBlank)
                ?: RELEASE_PAGE_URL + tag,
            apkName = apk?.optString("name")?.takeIf(String::isNotBlank),
            apkUrl = apk?.optString("browser_download_url")?.takeIf(String::isNotBlank)
        )
    }

    private fun hasArchitectureSuffix(name: String): Boolean {
        val normalized = name.lowercase()
        return listOf("arm64", "aarch64", "armeabi", "armv7", "arm32", "x86", "x64")
            .any(normalized::contains)
    }

    private fun matchesAbi(name: String, abi: String): Boolean {
        val normalized = name.lowercase()
        return when (abi) {
            "arm64-v8a" -> listOf("arm64-v8a", "arm64", "aarch64").any(normalized::contains)
            "armeabi-v7a" -> listOf("armeabi-v7a", "armeabi", "armv7", "arm32")
                .any(normalized::contains)
            "x86_64" -> listOf("x86_64", "x64").any(normalized::contains)
            "x86" -> "x86" in normalized
            else -> abi in normalized
        }
    }

    private fun getJsonArray(url: String): JSONArray =
        JSONArray(getBody(url) ?: throw IOException("GitHub repository not found"))

    private fun getJsonOrNull(url: String): JSONObject? =
        getBody(url)?.let(::JSONObject)

    private fun getBody(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Obhod-Android")
        try {
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub API returned HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

}
