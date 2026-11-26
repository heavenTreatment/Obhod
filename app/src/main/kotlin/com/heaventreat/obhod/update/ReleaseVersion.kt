package com.heaventreat.obhod.update

internal data class ReleaseVersion(private val parts: List<Int>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        val maxParts = maxOf(parts.size, other.parts.size)
        for (index in 0 until maxParts) {
            val comparison = parts.getOrElse(index) { 0 }.compareTo(
                other.parts.getOrElse(index) { 0 }
            )
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private val VERSION_PATTERN = Regex("^v?(\\d+(?:\\.\\d+)*)$")

        fun parse(value: String): ReleaseVersion? {
            val match = VERSION_PATTERN.matchEntire(value) ?: return null
            val parts = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            return ReleaseVersion(parts)
        }
    }
}

internal data class AppUpdate(
    val tag: String,
    val version: ReleaseVersion,
    val releasePageUrl: String,
    val apkName: String?,
    val apkUrl: String?
)

internal enum class UpdateDownloadStage {
    DOWNLOADING,
    INSTALLING,
    FAILED
}

internal data class UpdateDownloadProgress(
    val stage: UpdateDownloadStage,
    val percent: Int,
    val message: String? = null
)
