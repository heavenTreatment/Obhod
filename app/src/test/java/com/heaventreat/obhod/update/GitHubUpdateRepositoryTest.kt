package com.heaventreat.obhod.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubUpdateRepositoryTest {
    @Test
    fun selectsOnlyTheUniversalReleaseApk() {
        val assets = JSONArray()
            .put(asset("app-release.apk"))
            .put(asset("app-arm64-v8a-release.apk"))
            .put(asset("app-universal-release.apk"))

        assertEquals(
            "https://example.com/app-universal-release.apk",
            GitHubUpdateRepository.selectApkAsset(assets)?.getString("browser_download_url")
        )
    }

    @Test
    fun returnsNoApkWhenTheExactAssetIsMissing() {
        val assets = JSONArray()
            .put(asset("app-universal.apk"))
            .put(asset("app-release.apk"))

        assertNull(GitHubUpdateRepository.selectApkAsset(assets))
    }

    private fun asset(name: String) = JSONObject()
        .put("name", name)
        .put("browser_download_url", "https://example.com/$name")
}
