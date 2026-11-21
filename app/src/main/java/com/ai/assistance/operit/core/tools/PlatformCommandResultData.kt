package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable

/** 安装后续步骤失败时仍保留已落盘状态，避免把部分完成误报为全部成功。 */
@Serializable
data class SandboxPackageInstallResultData(
    val installed: Boolean,
    val packageName: String,
    val sourcePath: String,
    val targetPath: String,
    val enabled: Boolean,
    val activatedPackages: List<String>,
    val removedDuplicatePaths: List<String>,
    val logs: List<String>,
) : ToolResultData() {
    override fun toString(): String = "Package $packageName: installed=$installed, enabled=$enabled, source=$targetPath\n${logs.joinToString("\n")}"
}