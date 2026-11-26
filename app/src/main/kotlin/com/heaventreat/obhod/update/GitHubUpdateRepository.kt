package com.heaventreat.obhod.update

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
        val apk = assets?.let(::selectApkAsset)
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

    internal fun selectApkAsset(assets: JSONArray): JSONObject? =
        (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == "app-universal-release.apk" }

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
