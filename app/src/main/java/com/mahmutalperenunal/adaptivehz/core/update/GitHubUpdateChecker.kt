package com.mahmutalperenunal.adaptivehz.core.update

import com.mahmutalperenunal.adaptivehz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class StableRelease(
    val tagName: String,
    val name: String,
    val htmlUrl: String,
    val notes: String,
)

sealed interface UpdateCheckResult {
    data class Available(val release: StableRelease) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object InvalidResponse : UpdateCheckResult
    data class Failed(val cause: Throwable? = null) : UpdateCheckResult
}

object GitHubUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/mahmutaunal/Adaptive-Hz/releases/latest"

    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "AdaptiveHz/$currentVersion")
                }

                try {
                    if (connection.responseCode !in 200..299) {
                        return@runCatching UpdateCheckResult.Failed()
                    }

                    val json = connection.inputStream.bufferedReader().use { reader ->
                        JSONObject(reader.readText())
                    }
                    if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) {
                        return@runCatching UpdateCheckResult.InvalidResponse
                    }

                    val tag = json.optString("tag_name").trim()
                    val pageUrl = json.optString("html_url").trim()
                    val latestVersion = SemanticVersion.parse(tag)
                        ?: return@runCatching UpdateCheckResult.InvalidResponse
                    val installedVersion = SemanticVersion.parse(currentVersion)
                        ?: return@runCatching UpdateCheckResult.InvalidResponse
                    if (tag.isBlank() || pageUrl.isBlank()) {
                        return@runCatching UpdateCheckResult.InvalidResponse
                    }

                    if (latestVersion > installedVersion) {
                        UpdateCheckResult.Available(
                            StableRelease(
                                tagName = tag,
                                name = json.optString("name").ifBlank { tag },
                                htmlUrl = pageUrl,
                                notes = json.optString("body").trim(),
                            )
                        )
                    } else {
                        UpdateCheckResult.UpToDate
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { UpdateCheckResult.Failed(it) }
        }
}

internal data class SemanticVersion(
    val core: List<Int>,
    val preRelease: List<String>?,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        val width = maxOf(core.size, other.core.size)
        repeat(width) { index ->
            val comparison = core.getOrElse(index) { 0 }.compareTo(other.core.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }

        if (preRelease == null && other.preRelease != null) return 1
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease == null) return 0

        val otherPreRelease = requireNotNull(other.preRelease)
        val widthPreRelease = maxOf(preRelease.size, otherPreRelease.size)
        repeat(widthPreRelease) { index ->
            val left = preRelease.getOrNull(index) ?: return -1
            val right = otherPreRelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        fun parse(raw: String): SemanticVersion? {
            val normalized = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
            val parts = normalized.split('-', limit = 2)
            val core = parts.first().split('.').map { it.toIntOrNull() ?: return null }
            if (core.isEmpty()) return null
            val preRelease = parts.getOrNull(1)?.let { suffix ->
                val identifiers = suffix.split('.')
                if (identifiers.any(String::isBlank)) return null
                identifiers
            }
            return SemanticVersion(core, preRelease)
        }
    }
}
