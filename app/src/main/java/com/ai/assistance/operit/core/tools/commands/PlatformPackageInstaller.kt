package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.tools.SandboxPackageInstallResultData
import com.ai.assistance.operit.core.tools.packTool.parser.ToolPkgArchiveParser
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.File
import kotlinx.coroutines.CancellationException

/** 调试安装直接调用包服务，结束前核对外部来源，逐项保留启用和激活的实际失败。 */
internal class PlatformPackageInstaller(
    private val packages: PackageManager,
    private val cacheDirectory: File,
    private val invoke: (String, Map<String, String>) -> ToolResult,
    private val isExternalSourceAllowed: (String) -> Boolean,
) {
    fun execute(tool: AITool): ToolResult {
        var packageName = ""
        var targetPath = ""
        var installed = false
        val logs = mutableListOf<String>()
        val removedDuplicates = mutableListOf<String>()
        val activated = mutableListOf<String>()
        var temporaryArchive: File? = null
        var previouslyActiveSubpackages: List<String> = emptyList()
        val parameters = tool.parameters.filterNot { it.name in PlatformCommandExecutor.contextParameterNames }
        val values = parameters.associate { it.name to it.value }
        val sourcePath = values["source_path"].orEmpty()
        fun snapshot() = SandboxPackageInstallResultData(
            installed, packageName, sourcePath, targetPath,
            packageName.isNotEmpty() && packages.isPackageEnabled(packageName),
            activated.toList(), removedDuplicates.toList(), logs.toList(),
        )
        return try {
            val declarations = PlatformCommandToolDefinitions.category(true).tools
                .single { it.name == "debug_install_sandbox_package" }.parametersStructured.orEmpty()
            require(parameters.map { it.name }.distinct().size == parameters.size) { "Duplicate install parameters" }
            require(values.keys.all { key -> declarations.any { it.name == key } }) { "Unexpected install parameter" }
            val source = File(sourcePath)
            require(source.isAbsolute && source.exists() && source.canRead()) { "source_path must be a readable absolute Android path: $sourcePath" }
            val isScript = source.isFile && source.extension.equals("js", ignoreCase = true)
            val expectedScript = if (isScript) source.readText() else null
            val activateAfterInstall = boolean(values, "activate_after_install", isScript)
            val enableAfterInstall = boolean(values, "enable_after_install", true)
            val resetStates = boolean(values, "reset_subpackage_states", true)
            val requestedSubpackages = values["activate_subpackages"]?.let(PlatformCommandRegistry::parseArgsJson).orEmpty()
            require(requestedSubpackages.distinct().size == requestedSubpackages.size) { "Duplicate activate_subpackages ids" }
            val installFile: File
            val subpackages: Map<String, String>
            if (isScript) {
                require(requestedSubpackages.isEmpty()) { "activate_subpackages is only supported for ToolPkg" }
                packageName = packages.inspectDebugJsPackage(source).name
                installFile = source
                subpackages = emptyMap()
            } else {
                require(!activateAfterInstall) { "ToolPkg containers cannot be activated; use activate_subpackages" }
                installFile = when {
                    source.isDirectory -> PlatformPackageFiles.archiveDirectory(source, cacheDirectory).also { temporaryArchive = it }
                    source.isFile && source.name.lowercase() in setOf("manifest.json", "manifest.hjson") ->
                        PlatformPackageFiles.archiveDirectory(requireNotNull(source.parentFile), cacheDirectory).also { temporaryArchive = it }
                    source.isFile && source.extension.equals("toolpkg", ignoreCase = true) -> source
                    else -> throw IllegalArgumentException("Expected .js, .toolpkg, manifest.json/manifest.hjson, or a ToolPkg directory")
                }
                val preview = ToolPkgArchiveParser.readToolPkgManifestPreview { installFile.inputStream() }
                    ?: throw IllegalArgumentException("ToolPkg manifest is missing")
                packageName = preview.manifest.toolpkgId
                subpackages = preview.manifest.subpackages.associate { it.id to packages.normalizePackageName(it.id) }
                require(requestedSubpackages.all { it in subpackages }) { "Unknown activate_subpackages ids: ${(requestedSubpackages - subpackages.keys).joinToString()}" }
            }
            packageName = packages.normalizePackageName(packageName)
            require(isExternalSourceAllowed(packageName)) { "The current role card cannot install package: $packageName" }
            requestedSubpackages.forEach { id ->
                require(isExternalSourceAllowed(subpackages.getValue(id))) { "The current role card cannot activate subpackage: $id" }
            }
            val directory = packages.getExternalPackagesDirectory()
            val target = PlatformPackageFiles.destination(directory, packageName, if (isScript) "js" else "toolpkg")
            targetPath = target.absolutePath
            logs += "Installing $packageName from $sourcePath to $targetPath"
            PlatformPackageFiles.replace(installFile, target)
            if (isScript) {
                // 与包管理器使用同一个解析器识别同名脚本，避免用文件名推测包身份。
                packages.findDuplicateExternalJsPackageFiles(packageName, target).forEach { candidate ->
                    check(candidate.delete()) { "Failed to remove duplicate package: $candidate" }
                    removedDuplicates += candidate.absolutePath
                }
                packages.getAvailablePackages(forceRefresh = true)
                packages.unregisterPackageTools(packageName)
            } else {
                val result = packages.installDebugToolPkgResult(packageName, targetPath, resetStates, enableContainer = false, reactivatePackages = false)
                logs += result.message
                installed = result.installed
                previouslyActiveSubpackages = result.activationTargets
                check(result.success) { result.message }
            }
            val loaded = packages.getAvailablePackages()[packageName]
            check(loaded != null && !loaded.isBuiltIn) {
                "Installed package was not loaded from external storage: $packageName\n${packages.getPackageLoadErrors()}"
            }
            if (isScript) check(loaded.tools.all { it.script == expectedScript } && loaded.states.all { state -> state.tools.all { it.script == expectedScript } }) { "Loaded JS package does not match installed source" }
            installed = true
            fun step(name: String, params: Map<String, String>) {
                val result = invoke(name, params)
                logs += "$name: ${result.result}"
                check(result.success) { result.error ?: "Install step failed: $name" }
            }
            // 新命令的启用与重新激活全部经过目标工具的角色卡及权限检查。
            val shouldEnable = enableAfterInstall || activateAfterInstall || requestedSubpackages.isNotEmpty()
            step("set_sandbox_package_enabled", mapOf("package_name" to packageName, "enabled" to shouldEnable.toString()))
            if (activateAfterInstall) {
                step("use_package", mapOf("package_name" to packageName))
                activated += packageName
            }
            if (shouldEnable) previouslyActiveSubpackages.filterNot { it in subpackages.filterKeys { id -> id in requestedSubpackages }.values }.forEach { name ->
                step("use_package", mapOf("package_name" to name))
                activated += name
            }
            requestedSubpackages.forEach { id ->
                val name = subpackages.getValue(id)
                step("set_sandbox_package_enabled", mapOf("package_name" to name, "enabled" to "true"))
                step("use_package", mapOf("package_name" to name))
                activated += name
            }
            ToolResult(tool.name, true, snapshot())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.e("PlatformPackageInstaller", "调试包安装失败: $sourcePath", error)
            logs += "Installation failed: ${error.message}"
            ToolResult(tool.name, false, snapshot(), error.message)
        } finally {
            temporaryArchive?.let { file ->
                if (!file.delete() && file.exists()) AppLogger.w("PlatformPackageInstaller", "无法删除调试安装临时归档: $file")
            }
        }
    }

    private fun boolean(values: Map<String, String>, name: String, default: Boolean): Boolean =
        values[name]?.let { value ->
            require(value == "true" || value == "false") { "$name must be true or false" }
            value == "true"
        } ?: default
}