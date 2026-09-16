package com.ai.assistance.operit.data.storage

import android.content.Context
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PackageSkillKind {
    PLUGIN,
    SKILL,
}

data class PackageSkillEntry(
    val id: String,
    val kind: PackageSkillKind,
    val name: String,
    val path: File?,
    val bytes: Long,
    val fileCount: Long,
    val subtitle: String,
    val installed: Boolean,
)

data class PackageSkillSnapshot(
    val plugins: List<PackageSkillEntry>,
    val skills: List<PackageSkillEntry>,
    val totalBytes: Long,
    val scannedAtMillis: Long,
)

class PackageSkillInventory(context: Context) {
    private val appContext = context.applicationContext
    private val mcpRepository = MCPRepository(appContext)
    private val skillManager = SkillManager.getInstance(appContext)
    private val storageRepository = DataStorageRepository(appContext)

    suspend fun load(): PackageSkillSnapshot = withContext(Dispatchers.IO) {
        mcpRepository.refreshInstalledPlugins()
        val plugins = mcpRepository.mcpServers.value.map { metadata ->
            val path = mcpRepository.getInstalledPluginPath(metadata.id)
            val file = path?.takeUnless { it.startsWith("virtual://") }?.let(::File)
            val stats = file?.computeStorageStats() ?: PathStorageStats()
            PackageSkillEntry(
                id = "plugin:${metadata.id}",
                kind = PackageSkillKind.PLUGIN,
                name = metadata.name.ifBlank { metadata.id },
                path = file,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = metadata.author.ifBlank { metadata.type },
                installed = metadata.isInstalled || stats.exists,
            )
        }.filter { it.installed || it.bytes > 0L }

        val skills = skillManager.getAvailableSkills().values.map { skill ->
            val stats = skill.directory.computeStorageStats()
            PackageSkillEntry(
                id = "skill:${skill.name}",
                kind = PackageSkillKind.SKILL,
                name = skill.name,
                path = skill.directory,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = skill.description,
                installed = true,
            )
        }

        val extraPluginDirs = listOf(
            OperitPaths.pluginsDir(),
            File(OperitPaths.operitRootDir(), "bridge"),
            File(OperitPaths.operitRootDir(), "dev_package"),
        ).filter { dir ->
            dir.isDirectory && plugins.none { entry ->
                entry.path?.canonicalOrAbsolute() == dir.canonicalOrAbsolute()
            }
        }.map { directory ->
            val stats = directory.computeStorageStats()
            PackageSkillEntry(
                id = "plugin-dir:${directory.canonicalOrAbsolute()}",
                kind = PackageSkillKind.PLUGIN,
                name = directory.name,
                path = directory,
                bytes = stats.bytes,
                fileCount = stats.fileCount,
                subtitle = directory.absolutePath,
                installed = stats.exists,
            )
        }

        PackageSkillSnapshot(
            plugins = (plugins + extraPluginDirs).sortedByDescending { it.bytes },
            skills = skills.sortedByDescending { it.bytes },
            totalBytes = plugins.sumOf { it.bytes } + extraPluginDirs.sumOf { it.bytes } + skills.sumOf { it.bytes },
            scannedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun delete(entries: List<PackageSkillEntry>, onProgress: (String, Int, Int, Long) -> Unit): StorageDeleteBatchResult {
        var released = 0L
        var deleted = 0
        var failed = 0
        entries.forEachIndexed { index, entry ->
            onProgress(entry.name, index, entries.size, released)
            val ok = runCatching {
                when (entry.kind) {
                    PackageSkillKind.PLUGIN -> {
                        if (entry.id.startsWith("plugin:")) {
                            mcpRepository.uninstallMCPServer(entry.id.removePrefix("plugin:"))
                        } else {
                            entry.path?.deleteRecursively() == true || entry.path?.exists() != true
                        }
                    }
                    PackageSkillKind.SKILL -> skillManager.deleteSkill(entry.name)
                }
            }.getOrDefault(false)
            if (ok) {
                deleted++
                released += entry.bytes
            } else {
                failed++
            }
        }
        storageRepository.invalidateCache()
        return StorageDeleteBatchResult(deleted, failed, released)
    }
}
