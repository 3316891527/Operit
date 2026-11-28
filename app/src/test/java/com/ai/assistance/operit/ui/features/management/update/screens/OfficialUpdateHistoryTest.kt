package com.ai.assistance.operit.ui.features.management.update.screens

import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.api.GitHubReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialUpdateHistoryTest {
    @Test
    fun kongReleaseKeepsItsOwnBodyDateAndLinksAlongsideOlderHistory() {
        val old1 = upstream("v1.12.2", "2026-09-18T21:00:00Z")
        val old2 = upstream("v1.12.1", "2026-08-18T21:00:00Z")
        val old3 = upstream("v1.12.0", "2026-07-01T21:00:00Z")
        val new = kong("v2.0.0").copy(body = "主要变化\n\n- 完善对话与工作区体验")
        val updates = OfficialUpdateHistory.merge(
            listOf(old3, old2, old1, upstream("v2.0.0", "2027-01-01T01:00:00Z")),
            listOf(kong("1.12.2"), new),
            ::versionTitle
        )

        assertEquals(listOf("v2.0.0", "v1.12.2", "v1.12.1", "v1.12.0"), updates.map { it.version })
        assertEquals(listOf("2026-11-30", "2026-09-18", "2026-08-18", "2026-07-01"), updates.map { it.date })
        assertEquals(listOf(true, false, false, false), updates.map { it.isLatest })
        val kongUpdate = updates.first()
        assertEquals(new.body, kongUpdate.description)
        assertEquals(new.html_url, kongUpdate.releaseUrl)
        assertEquals(new.assets.single().browser_download_url, kongUpdate.downloadUrl)
        assertEquals(old1.body, updates[1].description)
        assertEquals(old1.html_url, updates[1].downloadUrl)
    }

    @Test
    fun draftsPreviewsPatchesAndReleasesWithoutPublishedApkStayOutOfHistory() {
        val valid = kong("v2.0.0").copy(name = null)
        val updates = OfficialUpdateHistory.merge(
            listOf(upstream("v1.12.2+99", "2026-09-18T00:00:00Z")),
            listOf(
                kong("v2.0.0+1"),
                kong("v2.0.1").copy(draft = true),
                kong("v2.0.2").copy(prerelease = true),
                kong("v2.0.3").copy(published_at = null),
                kong("v2.0.4").copy(assets = emptyList()),
                kong("v2.0.5-beta"),
                kong("resources-v1"),
                kong("v02.0.0"),
                valid
            ),
            ::versionTitle
        )

        assertEquals("v2.0.0", updates.single().version)
        assertEquals("版本 v2.0.0", updates.single().title)
        assertTrue(updates.single().isLatest)
    }

    @Test
    fun oldHistoryRemainsWhenNoKongVersionIsPublic() {
        val old = upstream("v1.12.2", "2026-09-18T20:00:00Z")
        val pending = kong("v2.0.0").copy(draft = true, published_at = null)
        val updates = OfficialUpdateHistory.merge(listOf(old), listOf(pending), ::versionTitle)
        assertEquals(old.tag_name, updates.single().version)
    }

    @Test
    fun kongHistoryStartsAtTwoAndSortsByPublicationInstant() {
        val newest = kong("v2.0.0")
        val older = kong("v10.0.0").copy(published_at = "2026-11-29T23:59:59Z")
        val updates = OfficialUpdateHistory.merge(
            emptyList(),
            listOf(kong("v0.9.0"), kong("v1.13.0"), older, newest),
            ::versionTitle
        )

        assertEquals(listOf("v2.0.0", "v10.0.0"), updates.map { it.version })
        assertEquals(listOf("2026-11-30", "2026-11-29"), updates.map { it.date })
        assertEquals(listOf(true, false), updates.map { it.isLatest })
    }

    private fun versionTitle(tag: String) = "版本 $tag"

    private fun upstream(tag: String, publishedAt: String) = GitHubRelease(
        id = 1,
        tag_name = tag,
        name = "Operit $tag",
        body = "原发布说明 $tag",
        html_url = "https://github.com/AAswordman/Operit/releases/tag/$tag",
        published_at = publishedAt,
        created_at = "2026-06-01T00:00:00Z"
    )

    private fun kong(tag: String) = GitHubRelease(
        id = 2,
        tag_name = tag,
        name = "Operit Kong $tag",
        body = "Kong 更新",
        html_url = "https://github.com/3316891527/Operit-Kong-patch/releases/tag/$tag",
        published_at = "2026-11-30T01:00:00Z",
        created_at = "2026-06-01T00:00:00Z",
        assets = listOf(
            GitHubReleaseAsset(
                id = 3,
                name = "app-kong.apk",
                browser_download_url = "https://github.com/3316891527/Operit-Kong-patch/releases/download/$tag/app-kong.apk",
                size = 1L,
                download_count = 0,
                content_type = "application/vnd.android.package-archive"
            )
        )
    )
}
