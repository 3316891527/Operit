package com.ai.assistance.operit.ui.features.conversation.workspace.changes

import android.content.Context
import com.ai.assistance.operit.terminal.provider.filesystem.PRootMountMapping
import java.io.File

internal data class TerminalObservationRoot(val file: File, val location: ChangeLocation)

/** 从实际 PRoot 挂载表构造观察树；同一物理目录的多个 Linux 别名只观察一次。 */
internal fun localTerminalObservationRoots(context: Context): List<TerminalObservationRoot> {
    val ubuntu = File(context.filesDir, "usr/var/lib/proot-distro/installed-rootfs/ubuntu")
    val mounts = PRootMountMapping.buildRuntimeBindMounts(context.filesDir.absolutePath,
        context.applicationInfo.dataDir, context.packageName,
        context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE).getBoolean("chroot_enabled", false))
    val virtual = setOf("/dev", "/proc", "/sys")
    val candidates = listOf(TerminalObservationRoot(ubuntu, ChangeLocation("/", "linux", "linux:local"))) +
        mounts.filter { mount -> virtual.none { changePathContains(it, mount.sourcePath) } }.map { mount ->
            TerminalObservationRoot(File(mount.sourcePath), ChangeLocation(changePath(mount.targetPath), "linux", "linux:local"))
        }
    return candidates.filter { it.file.isDirectory && it.file.canRead() }
        .distinctBy { it.file.canonicalPath }
        .map { it.copy(file = it.file.canonicalFile) }
}
