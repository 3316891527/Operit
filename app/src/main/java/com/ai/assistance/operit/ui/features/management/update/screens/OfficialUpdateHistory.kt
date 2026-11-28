package com.ai.assistance.operit.ui.features.management.update.screens

import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.updates.distribution.DistributionReleaseResolver
import java.time.Instant
import java.time.ZoneOffset

internal object OfficialUpdateHistory {
    private val stableTag = Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")

    fun merge(
        upstream: List<GitHubRelease>,
        kong: List<GitHubRelease>,
        versionTitle: (String) -> String
    ): List<UpdateInfo> {
        // 旧版同名发行沿用原有 Operit 记录；从 2.0.0 起只认 Operit Kong 的独立发行。
        val upstreamEntries = upstream.mapNotNull { release ->
            val match = stableTag.matchEntire(release.tag_name) ?: return@mapNotNull null
            if (match.groupValues[1] !in setOf("0", "1")) return@mapNotNull null
            publishedRelease(release, release.html_url)
        }
        val kongEntries = kong.mapNotNull { release ->
            val match = stableTag.matchEntire(release.tag_name) ?: return@mapNotNull null
            if (match.groupValues[1] in setOf("0", "1")) return@mapNotNull null
            val apk = release.assets.singleOrNull { it.name == DistributionReleaseResolver.APK_NAME }
                ?: return@mapNotNull null
            publishedRelease(release, apk.browser_download_url)
        }
        return (upstreamEntries + kongEntries)
            .distinctBy { it.release.tag_name.removePrefix("v") }
            .sortedByDescending { it.publishedAt }
            .mapIndexed { index, entry ->
                val release = entry.release
                UpdateInfo(
                    version = release.tag_name,
                    date = entry.publishedAt.atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                    title = release.name?.takeIf { it.isNotBlank() } ?: versionTitle(release.tag_name),
                    description = release.body.orEmpty(),
                    highlights = emptyList(),
                    allChanges = emptyList(),
                    isLatest = index == 0,
                    downloadUrl = entry.downloadUrl,
                    releaseUrl = release.html_url
                )
            }
    }

    private fun publishedRelease(release: GitHubRelease, downloadUrl: String): PublishedRelease? {
        val publishedAt = release.published_at ?: return null
        if (release.draft || release.prerelease || downloadUrl.isBlank()) return null
        return PublishedRelease(release, Instant.parse(publishedAt), downloadUrl)
    }

    private data class PublishedRelease(
        val release: GitHubRelease,
        val publishedAt: Instant,
        val downloadUrl: String
    )
}
