package com.ai.assistance.operit.core.tools.permissions

import android.content.Context
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.repository.UIHierarchyManager

/** 每次操作重新查询授权，不缓存会随服务断开或授权撤销而变化的能力。 */
object PermissionCapabilityResolver {
    fun shellSnapshot(
        context: Context,
        mode: AndroidPermissionLevel = androidPermissionPreferences.getPreferredPermissionLevel()
            ?: AndroidPermissionLevel.STANDARD
    ): PermissionCapabilities {
        val shizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
        val shizukuGranted = shizukuRunning && ShizukuAuthorizer.hasShizukuPermission()
        val rootAvailable = if (mode == AndroidPermissionLevel.ROOT) {
            val executor = ShellExecutorFactory.getBackendExecutor(context, PermissionBackend.ROOT)
            executor.hasPermission().granted
        } else false
        return PermissionCapabilities(mode, shizukuRunning, shizukuGranted, rootAvailable = rootAvailable)
    }

    suspend fun uiSnapshot(
        context: Context,
        mode: AndroidPermissionLevel = androidPermissionPreferences.getPreferredPermissionLevel()
            ?: AndroidPermissionLevel.STANDARD
    ): PermissionCapabilities = shellSnapshot(context, mode).copy(
        accessibilityAvailable = mode != AndroidPermissionLevel.STANDARD &&
            UIHierarchyManager.isAccessibilityServiceEnabled(context)
    )
}
