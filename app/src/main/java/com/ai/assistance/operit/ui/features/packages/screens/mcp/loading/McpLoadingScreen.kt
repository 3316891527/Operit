package com.ai.assistance.operit.ui.features.packages.screens.mcp.loading

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** 表示模型上下文协议服务的手动加载状态。 */
enum class McpLoadingStatus {
    WAITING, // 等待加载
    LOADING, // 正在加载
    SUCCESS, // 加载成功
    FAILED // 加载失败
}

/** 单个模型上下文协议服务的加载信息。 */
data class McpLoadingItem(
        val id: String,
        val displayName: String,
        var status: McpLoadingStatus = McpLoadingStatus.WAITING,
        var message: String = "",
        var serviceName: String = ""
) {
    val shortName: String
        get() = id.split("/").lastOrNull() ?: id
}

/**
 * 模型上下文协议手动加载面板
 *
 * 用户手动启动模型上下文协议服务时显示进度，不参与应用开屏流程。
 */
@Composable
fun McpLoadingScreen(
        isVisible: Boolean,
        progress: Float,
        message: String,
        pluginsStarted: Int,
        pluginsTotal: Int,
        pluginsList: List<McpLoadingItem>,
        isExpanded: Boolean,
        onToggleExpansion: () -> Unit,
        onSkip: () -> Unit = {},
        onPluginClick: (McpLoadingItem) -> Unit = {},
        modifier: Modifier = Modifier
) {
    AnimatedVisibility(
            visible = isVisible,
            enter =
                    fadeIn(
                            initialAlpha = 0f,
                            animationSpec = androidx.compose.animation.core.tween(500)
                    ),
            exit =
                    fadeOut(
                            targetAlpha = 0f,
                            animationSpec = androidx.compose.animation.core.tween(800)
                    )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            AnimatedContent(targetState = isExpanded, modifier = Modifier.align(if(isExpanded) Alignment.BottomCenter else Alignment.TopEnd), label = "") { expanded ->
                if (expanded) {
                    ExpandedLoadingView(
                        progress,
                        message,
                        pluginsStarted,
                        pluginsTotal,
                        pluginsList,
                        onSkip,
                        onCollapse = onToggleExpansion,
                        onPluginClick = onPluginClick
                    )
                } else {
                    DraggableCollapsedIndicator(progress, pluginsStarted, pluginsTotal, onClick = onToggleExpansion)
                }
            }
        }
    }
}

@Composable
private fun DraggableCollapsedIndicator(
    progress: Float,
    pluginsStarted: Int,
    pluginsTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                }
            }
    ) {
        CollapsedLoadingIndicator(
            progress = progress,
            pluginsStarted = pluginsStarted,
            pluginsTotal = pluginsTotal,
            onClick = onClick
        )
    }
}

@Composable
private fun CollapsedLoadingIndicator(
    progress: Float,
    pluginsStarted: Int,
    pluginsTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "$pluginsStarted/$pluginsTotal",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpandedLoadingView(
    progress: Float,
    message: String,
    pluginsStarted: Int,
    pluginsTotal: Int,
    pluginsList: List<McpLoadingItem>,
    onSkip: () -> Unit,
    onCollapse: () -> Unit,
    onPluginClick: (McpLoadingItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp), // Constrain height
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            // 折叠按钮
            IconButton(onClick = onCollapse, modifier = Modifier
                .align(Alignment.TopStart)
                .padding(0.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
            }
            // 跳过加载文本 - 放在右上角
            Text(
                    text = stringResource(id = R.string.plugin_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clickable {
                                    onSkip()
                                }
            )

            // 主要内容区域
            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
            ) {
                // 应用名称/Logo
                Text(
                        text = stringResource(id = R.string.plugin_app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 使用平滑过渡的进度条组件
                SmoothLinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        height = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        progressColor = MaterialTheme.colorScheme.primary,
                        intermediateSteps = 20, // 增加中间步骤数量，使过渡更加平滑
                        stepDuration = 50 // 减少每步时长，保持总体流畅感
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 简洁的状态消息
                Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 总模型上下文协议服务统计
                Text(
                        text =
                                stringResource(
                                        id = R.string.plugin_status,
                                        pluginsStarted,
                                        pluginsTotal
                                ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                )

                // 移除此处的跳过按钮
                Spacer(modifier = Modifier.height(16.dp))

                // 模型上下文协议服务列表
                if (pluginsList.isNotEmpty()) {
                    Text(
                            text = stringResource(id = R.string.plugin_loading_status_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 模型上下文协议服务加载状态列表
                    LazyColumn(
                            modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .heightIn(max = 200.dp) // reduce height for smaller card
                    ) {
                        items(pluginsList) { plugin ->
                            McpLoadingStatusItem(
                                    plugin = plugin,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = if (plugin.status == McpLoadingStatus.FAILED) {
                                        { onPluginClick(plugin) }
                                    } else {
                                        null
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部版权信息
                Text(
                        text = stringResource(id = R.string.about_copyright),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 单个模型上下文协议服务状态项 */
@Composable
fun McpLoadingStatusItem(
    plugin: McpLoadingItem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val animatedProgress by
            animateFloatAsState(
                    targetValue = if (plugin.status == McpLoadingStatus.LOADING) 1f else 0f,
                    label = "loading_progress"
            )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 4.dp)
            .let { base ->
                if (onClick != null) base.clickable(onClick = onClick) else base
            }
    ) {
        // 状态图标或加载指示器
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
            when (plugin.status) {
                McpLoadingStatus.WAITING -> {
                    Box(
                            modifier =
                                    Modifier.size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                McpLoadingStatus.LOADING -> {
                    CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                    )
                }
                McpLoadingStatus.SUCCESS -> {
                    Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription =
                                    stringResource(id = R.string.plugin_loading_success),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                    )
                }
                McpLoadingStatus.FAILED -> {
                    Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription =
                                    stringResource(id = R.string.plugin_loading_failed),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 模型上下文协议服务名称和状态
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = plugin.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )

            if (plugin.message.isNotEmpty()) {
                Text(
                        text = plugin.message,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                                when (plugin.status) {
                                    McpLoadingStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 跳过加载的回调函数接口
interface SkipLoadingCallback {
    fun onSkip()
}

/**
 * 模型上下文协议服务加载状态管理器
 *
 * 管理用户主动启动服务时的进度、状态与日志。
 */
class McpLoadingState {
    // 进度值 (0.0f - 1.0f)
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    // 当前状态消息
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    // 已启动的模型上下文协议服务数量
    private val _pluginsStarted = MutableStateFlow(0)
    val pluginsStarted: StateFlow<Int> = _pluginsStarted

    // 总模型上下文协议服务数量
    private val _pluginsTotal = MutableStateFlow(0)
    val pluginsTotal: StateFlow<Int> = _pluginsTotal

    // 是否显示加载屏幕
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    // 是否展开
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    // 模型上下文协议服务列表及其状态
    private val _plugins = MutableStateFlow<List<McpLoadingItem>>(emptyList())
    val plugins: StateFlow<List<McpLoadingItem>> = _plugins

    private val _pluginLogs = MutableStateFlow<Map<String, String>>(emptyMap())
    val pluginLogs: StateFlow<Map<String, String>> = _pluginLogs

    // 应用上下文，用于获取模型上下文协议相关服务
    private var appContext: Context? = null

    private val mcpInitInProgress = AtomicBoolean(false)
    private var mcpInitJob: Job? = null

    // 跳过加载事件回调
    private var onSkipCallback: (() -> Unit)? = null

    // 保护模型上下文协议服务状态与日志的并发更新，避免回调互相覆盖
    private val pluginStateLock = Any()

    // 设置应用上下文
    fun setAppContext(context: Context) {
        this.appContext = context
    }

    fun toggleExpansion() {
        _isExpanded.value = !_isExpanded.value
    }

    private fun forceExpanded() {
        _isExpanded.value = true
    }

    /** 更新进度信息 */
    fun updateProgress(progress: Float) {
        _progress.value = progress
    }

    /** 更新状态消息 */
    fun updateMessage(message: String) {
        _message.value = message
    }

    /** 更新模型上下文协议服务统计 */
    fun updatePluginStats(started: Int, total: Int) {
        synchronized(pluginStateLock) {
            _pluginsStarted.value = started
            _pluginsTotal.value = total
        }
    }

    /** 设置模型上下文协议服务列表 */
    fun setPlugins(pluginIds: List<String>) {
        val context = appContext
        val plugins =
                pluginIds.map { id ->
                    // 尝试从metadata获取模型上下文协议服务名称
                    var displayName = id.split("/").lastOrNull() ?: id

                    // 如果上下文可用，尝试从元数据获取名称
                    if (context != null) {
                        try {
                            val mcpLocalServer = MCPLocalServer.getInstance(context)
                            val pluginInfo = mcpLocalServer.getPluginMetadata(id)
                            if (pluginInfo != null) {
                                displayName = pluginInfo.name
                            }
                        } catch (e: Exception) {
                            // 获取元数据失败，使用默认名称
                        }
                    }

                    McpLoadingItem(id = id, displayName = displayName)
                }
        synchronized(pluginStateLock) {
            _plugins.value = plugins
            _pluginsTotal.value = plugins.size
            _pluginsStarted.value = plugins.count { it.status == McpLoadingStatus.SUCCESS }
        }
    }

    /** 更新模型上下文协议服务状态 */
    fun updateMcpLoadingStatus(pluginId: String, status: McpLoadingStatus, message: String = "") {
        synchronized(pluginStateLock) {
            val currentPlugins = _plugins.value.toMutableList()
            val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

            if (pluginIndex >= 0) {
                val plugin = currentPlugins[pluginIndex].copy(status = status, message = message)
                currentPlugins[pluginIndex] = plugin
                _plugins.value = currentPlugins
                _pluginsStarted.value = currentPlugins.count { it.status == McpLoadingStatus.SUCCESS }
            }
        }
    }

    private fun updatePluginMessage(pluginId: String, message: String) {
        synchronized(pluginStateLock) {
            val currentPlugins = _plugins.value.toMutableList()
            val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

            if (pluginIndex >= 0) {
                val plugin = currentPlugins[pluginIndex]
                currentPlugins[pluginIndex] = plugin.copy(message = message)
                _plugins.value = currentPlugins
            }
        }
    }

    private fun appendPluginLog(pluginId: String, message: String) {
        if (message.isBlank()) return

        synchronized(pluginStateLock) {
            val maxCharsPerPlugin = 2_000_000
            val existing = _pluginLogs.value[pluginId].orEmpty()
            val combined = if (existing.isBlank()) message else "$existing\n$message"
            val trimmed =
                if (combined.length > maxCharsPerPlugin) combined.takeLast(maxCharsPerPlugin) else combined

            _pluginLogs.value = _pluginLogs.value.toMutableMap().apply {
                put(pluginId, trimmed)
            }
        }
    }

    /** 更新模型上下文协议服务注册状态 */
    fun updatePluginRegistration(pluginId: String, serviceName: String, success: Boolean) {
        synchronized(pluginStateLock) {
            val currentPlugins = _plugins.value.toMutableList()
            val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

            if (pluginIndex >= 0) {
                val plugin = currentPlugins[pluginIndex]
                val message = if (success) {
                    appContext?.getString(R.string.plugin_registered) ?: "Registered"
                } else {
                    appContext?.getString(R.string.plugin_registration_failed) ?: "Registration Failed"
                }
                currentPlugins[pluginIndex] = plugin.copy(
                    serviceName = serviceName,
                    // 不要改变主状态，只更新消息
                    message = message
                )
                _plugins.value = currentPlugins
            }
        }
    }

    /** 开始加载指定模型上下文协议服务 */
    fun startLoadingPlugin(pluginId: String) {
        updateMcpLoadingStatus(
                pluginId,
                McpLoadingStatus.LOADING,
                appContext?.getString(R.string.plugin_loading) ?: "Loading..."
        )
    }

    /** 标记模型上下文协议服务加载成功 */
    fun setPluginSuccess(pluginId: String, message: String = "") {
        updateMcpLoadingStatus(
                pluginId,
                McpLoadingStatus.SUCCESS,
                message.ifEmpty { appContext?.getString(R.string.plugin_loading_success) ?: "Loading successful" }
        )
    }

    /** 标记模型上下文协议服务加载失败 */
    fun setPluginFailed(pluginId: String, message: String = "") {
        updateMcpLoadingStatus(
                pluginId,
                McpLoadingStatus.FAILED,
                message.ifEmpty { appContext?.getString(R.string.plugin_loading_failed) ?: "Loading failed" }
        )
    }

    // 设置跳过回调
    fun setOnSkipCallback(callback: () -> Unit) {
        onSkipCallback = callback
    }



    // 触发跳过操作
    fun skip() {
        hide()
        onSkipCallback?.invoke()
    }

    /** 显示加载屏幕 */
    fun show() {
        _isVisible.value = true
        // _isExpanded.value = true // 默认展开
    }

    /** 隐藏加载屏幕 */
    fun hide() {
        _isVisible.value = false
        // _isExpanded.value = false // 关闭时重置为折叠状态
    }

    /** 重置所有状态 */
    fun reset() {
        mcpInitJob?.cancel()
        mcpInitInProgress.set(false)
        _progress.value = 0f
        _message.value = ""
        synchronized(pluginStateLock) {
            _pluginsStarted.value = 0
            _pluginsTotal.value = 0
            _plugins.value = emptyList()
            _pluginLogs.value = emptyMap()
        }
        _isVisible.value = false
        _isExpanded.value = false
    }

    // 添加方法来初始化MCP服务器并启动模型上下文协议服务
    fun startConfiguredServers(context: Context, lifecycleScope: kotlinx.coroutines.CoroutineScope) {
        if (!mcpInitInProgress.compareAndSet(false, true)) {
            AppLogger.d("McpLoadingState", "startConfiguredServers already running, skipping")
            return
        }

        mcpInitJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 更新初始状态
                updateMessage(context.getString(R.string.plugin_initializing))
                updateProgress(0.05f)

                // 获取MCPLocalServer实例
                val mcpLocalServer = MCPLocalServer.getInstance(context)

                // 更新状态
                updateMessage(context.getString(R.string.plugin_starting_server))
                updateProgress(0.1f)

                // 服务器配置阶段
                updateMessage(context.getString(R.string.plugin_configuring_server))
                updateProgress(0.15f)

                // 服务器启动成功，更新状态
                updateMessage(context.getString(R.string.plugin_server_success))
                updateProgress(0.2f)

                // 服务器初始化中
                updateMessage(context.getString(R.string.plugin_server_initializing))
                updateProgress(0.25f)

                try {
                    // 获取MCPRepository实例
                    val mcpRepository = MCPRepository(context)

                    // 注意：工具注册现在在MCPStarter的验证阶段进行，确保模型上下文协议服务真正就绪后才注册工具

                    // 获取已安装的模型上下文协议服务列表 (这是一个Set<String>)
                    updateMessage(context.getString(R.string.plugin_loading_list))
                    updateProgress(0.28f)
                    AppLogger.d("McpLoadingState", "启动前刷新模型上下文协议配置与服务列表")
                    mcpRepository.refreshPluginList()
                    val installedPluginsSet = mcpRepository.installedPluginIds.first()

                    // 显式转换为List<String>
                    val installedPluginsList = installedPluginsSet.toList()

                    if (installedPluginsSet.isEmpty()) {
                        // 没有可启动的服务时结束本次手动加载。
                        AppLogger.d("McpLoadingState", "没有检测到已安装的服务，结束手动加载")
                        updateMessage(context.getString(R.string.plugin_no_plugins))
                        updateProgress(1.0f)

                        // 隐藏手动加载面板。
                        hide()
                        return@launch
                    }

                    // 筛选出将要启动的模型上下文协议服务（已启用且满足条件的模型上下文协议服务）
                    val pluginsToStart = installedPluginsList.filter { pluginId ->
                        val isEnabled = mcpLocalServer.isServerEnabled(pluginId)
                        if (!isEnabled) {
                            false
                        } else {
                            val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
                            when (pluginInfo?.type) {
                                "remote" -> true // 远程模型上下文协议服务只需启用
                                else -> true // 本地模型上下文协议服务现在会自动部署，所以也包含在内
                            }
                        }
                    }

                    // 设置模型上下文协议服务列表，只传入将要启动的模型上下文协议服务
                    updateMessage(
                            context.getString(R.string.plugin_preparing, pluginsToStart.size)
                    )
                    updateProgress(0.32f)
                    setPlugins(pluginsToStart)

                    // 有安装的模型上下文协议服务，使用MCPStarter启动
                    updateMessage(context.getString(R.string.plugin_checking_env))
                    updateProgress(0.35f)

                    val mcpStarter = MCPStarter(context)
                    val mcpLocalServer = MCPLocalServer.getInstance(context)

                    // 创建一个适配器匿名类实现模型上下文协议服务启动监听器
                    updateMessage(context.getString(R.string.plugin_starting_plugins))
                    updateProgress(0.38f)

                    val progressListener =
                            createPluginStartProgressListener(
                                    mcpLocalServer,
                                    lifecycleScope,
                                    context
                            )

                    // 启动所有模型上下文协议服务 - MCPStarter会处理各种检查逻辑
                    // 等待实际批次结束，生命周期取消时同步停止批次，避免重复启动争抢资源。
                    val startupJob = mcpStarter.startAllDeployedPlugins(progressListener)
                    try {
                        startupJob.join()
                    } catch (cancelled: CancellationException) {
                        startupJob.cancel()
                        throw cancelled
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    // 处理模型上下文协议服务加载过程中的异常
                    AppLogger.e("McpLoadingState", "加载模型上下文协议服务过程中出错", e)
                    updateMessage(e.message ?: context.getString(R.string.plugin_loading_failed))
                    updateProgress(1.0f)

                    forceExpanded()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                AppLogger.e("McpLoadingState", "启动模型上下文协议服务时出错", e)
                updateMessage(e.message ?: context.getString(R.string.plugin_other_error))
                updateProgress(1.0f)

                forceExpanded()
            } finally {
                mcpInitInProgress.set(false)
            }
        }
    }

    // 创建模型上下文协议服务启动进度监听器
    private fun createPluginStartProgressListener(
            mcpLocalServer: MCPLocalServer,
            lifecycleScope: kotlinx.coroutines.CoroutineScope,
            context: Context
    ): MCPStarter.PluginStartProgressListener {
        return object : MCPStarter.PluginStartProgressListener {
            override fun onPluginStarting(pluginId: String, index: Int, total: Int) {
                // 在这里检查模型上下文协议服务是否被启用
                val isEnabled = mcpLocalServer.isServerEnabled(pluginId) // 从配置读取

                // 更新总体状态
                val disabledSuffix =
                        if (!isEnabled) context.getString(R.string.plugin_disabled_suffix) else ""
                updateMessage(
                        context.getString(
                                R.string.plugin_starting_number,
                                index,
                                total,
                                disabledSuffix
                        )
                )
                updateProgress(0.4f + 0.1f * (index.toFloat() / total)) // 注册占10% (0.4 -> 0.5)

                // 更新特定模型上下文协议服务状态
                startLoadingPlugin(pluginId)
                appendPluginLog(pluginId, "START")
            }

            override fun onPluginRegistered(pluginId: String, serviceName: String, success: Boolean) {
                updatePluginRegistration(pluginId, serviceName, success)
                if (!success) {
                    val lastLogLine = _pluginLogs.value[pluginId]?.lineSequence()?.lastOrNull().orEmpty()
                    val message = lastLogLine.ifBlank { context.getString(R.string.plugin_registration_failed) }
                    setPluginFailed(pluginId, message)
                    forceExpanded()
                }
            }

            override fun onPluginStarted(
                    pluginId: String,
                    success: Boolean,
                    index: Int,
                    total: Int
            ) {
                // 记录模型上下文协议服务加载结果
                if (success) {
                    setPluginSuccess(pluginId)
                } else {
                    setPluginFailed(pluginId, context.getString(R.string.plugin_verification_failed))
                    forceExpanded()
                }

                // 更新总体进度
                updateProgress(0.5f + 0.5f * (index.toFloat() / total)) // 验证和处理占50% (0.5 -> 1.0)
            }

            override fun onPluginLog(pluginId: String, message: String) {
                appendPluginLog(pluginId, message)
                val brief = message.lineSequence().firstOrNull().orEmpty().take(160)
                updatePluginMessage(pluginId, brief)
            }

            override fun onAllPluginsStarted(
                    successCount: Int,
                    totalCount: Int,
                    status: MCPStarter.PluginInitStatus
            ) {
                // 根据初始化状态显示不同的消息
                when (status) {
                    MCPStarter.PluginInitStatus.NODEJS_MISSING -> {
                        updateMessage(context.getString(R.string.plugin_nodejs_missing))
                    }
                    MCPStarter.PluginInitStatus.BRIDGE_FAILED -> {
                        updateMessage(context.getString(R.string.plugin_bridge_failed))
                    }
                    MCPStarter.PluginInitStatus.OTHER_ERROR -> {
                        updateMessage(context.getString(R.string.plugin_other_error))
                    }
                    else -> {
                        // 所有模型上下文协议服务加载完成
                        val successRate =
                                if (totalCount > 0) {
                                    (successCount * 100) / totalCount
                                } else {
                                    0 // 当没有部署的模型上下文协议服务时，成功率为0
                                }

                        // 工具注册将在验证阶段自动进行，无需在此处触发

                        // 如果有模型上下文协议服务加载失败，则特别提示可以跳过
                        if (successCount < totalCount && totalCount > 0) {
                            updateMessage(
                                    context.getString(
                                            R.string.plugin_complete_with_failures,
                                            successRate
                                    )
                            )
                        } else if (totalCount > 0) {
                            updateMessage(
                                    context.getString(R.string.plugin_complete_success, successRate)
                            )
                        } else {
                            updateMessage(context.getString(R.string.plugin_no_plugins_to_start))
                        }
                    }
                }

                updateProgress(1.0f)

                val hasFailures = successCount < totalCount && totalCount > 0
                if (status != MCPStarter.PluginInitStatus.SUCCESS || hasFailures) {
                    forceExpanded()
                } else {
                    lifecycleScope.launch {
                        delay(100L)
                        if (isVisible.value) {
                            hide()
                        }
                    }
                }
            }

            override fun onAllPluginsVerified(
                    verificationResults: List<MCPStarter.VerificationResult>
            ) {
                // 不需要修改这部分
            }
        }
    }
}

/** 模型上下文协议服务加载屏幕的预览视图 */
@Composable
fun McpLoadingScreenWithState(loadingState: McpLoadingState, modifier: Modifier = Modifier) {
    val isVisible by loadingState.isVisible.collectAsState()
    val progress by loadingState.progress.collectAsState()
    val message by loadingState.message.collectAsState()
    val pluginsStarted by loadingState.pluginsStarted.collectAsState()
    val pluginsTotal by loadingState.pluginsTotal.collectAsState()
    val plugins by loadingState.plugins.collectAsState()
    val isExpanded by loadingState.isExpanded.collectAsState()
    val pluginLogs by loadingState.pluginLogs.collectAsState()

    var selectedLogPluginId by remember { mutableStateOf<String?>(null) }

    selectedLogPluginId?.let { pluginId ->
        val logText = pluginLogs[pluginId].orEmpty()
        AlertDialog(
            onDismissRequest = { selectedLogPluginId = null },
            title = { Text(text = pluginId) },
            text = {
                SelectionContainer {
                    Text(
                        text = if (logText.isBlank()) "(no logs)" else logText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLogPluginId = null }) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            }
        )
    }

    McpLoadingScreen(
            isVisible = isVisible,
            progress = progress,
            message = message,
            pluginsStarted = pluginsStarted,
            pluginsTotal = pluginsTotal,
            pluginsList = plugins,
            isExpanded = isExpanded,
            onToggleExpansion = { loadingState.toggleExpansion() },
            onSkip = { loadingState.skip() },
            onPluginClick = { plugin -> selectedLogPluginId = plugin.id },
            modifier = modifier
    )
}
