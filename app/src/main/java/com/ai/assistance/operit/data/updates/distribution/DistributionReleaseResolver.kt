package com.ai.assistance.operit.data.updates.distribution

import com.ai.assistance.operit.data.api.GitHubRelease
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import org.json.JSONObject

/** 独立发行版的发布源与更新选择规则，不受上游内测开关影响。 */
internal object DistributionReleaseResolver {
    const val OWNER = "3316891527"
    const val REPOSITORY = "Operit"
    const val APK_NAME = "app-kong.apk"
    private val versionPattern = Regex("""^\d+\.\d+\.\d+(?:\+\d+)?$""")

    fun resolve(currentVersion: String, releases: List<GitHubRelease>): UpdateStatus {
        require(versionPattern.matches(currentVersion)) { "Invalid installed version: $currentVersion" }
        val latest = releases
            .filter { release ->
                !release.draft && !release.prerelease &&
                    versionPattern.matches(release.tag_name.removePrefix("v")) &&
                    release.assets.any { it.name == APK_NAME }
            }
            .maxWithOrNull { left, right ->
                UpdateManager.compareVersions(left.tag_name, right.tag_name)
            } ?: return UpdateStatus.UpToDate
        val version = latest.tag_name.removePrefix("v")
        if (UpdateManager.compareVersions(version, currentVersion) <= 0) {
            return UpdateStatus.UpToDate
        }

        val body = latest.body.orEmpty().trim()
        // 发布流程的 JSON 正文同时保存补丁链索引和展示文案；手动完整包发布可使用普通正文。
        val notes = if (body.startsWith("{")) JSONObject(body).getString("releaseNotes") else body
        val patch = latest.assets.firstOrNull {
            it.name.startsWith("apkrawpatch_") && it.name.endsWith(".zip")
        }
        val meta = latest.assets.firstOrNull {
            it.name.startsWith("patch_") && it.name.endsWith(".json")
        }
        // 仅同一基础版本使用已有补丁协议，跨基础版本明确提供完整 APK 更新。
        if (version.substringBefore('+') == currentVersion.substringBefore('+') && patch != null && meta != null) {
            return UpdateStatus.PatchAvailable(
                newVersion = version,
                updateUrl = latest.html_url,
                releaseNotes = notes,
                patchUrl = patch.browser_download_url,
                metaUrl = meta.browser_download_url
            )
        }
        val apk = latest.assets.single { it.name == APK_NAME }
        return UpdateStatus.Available(
            newVersion = version,
            updateUrl = latest.html_url,
            releaseNotes = notes,
            downloadUrl = apk.browser_download_url
        )
    }
}
