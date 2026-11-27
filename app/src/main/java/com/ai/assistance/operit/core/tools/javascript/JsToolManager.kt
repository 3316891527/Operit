package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import com.ai.assistance.operit.core.tools.ScriptExecutionTraceData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.runtime.PackageManager
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import org.json.JSONArray
import org.json.JSONObject

class JsToolManager private constructor(
    private val context: Context,
    private val packageManager: PackageManager
) {

    private class ToolParameterConversionException(
        message: String
    ) : IllegalArgumentException(message)

    companion object {
        private const val TAG = "JsToolManager"
        private const val MAX_CONCURRENT_ENGINES = 4

        @Volatile
        private var instance: JsToolManager? = null

        fun getInstance(context: Context, packageManager: PackageManager): JsToolManager {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: JsToolManager(context.applicationContext, packageManager).also {
                            instance = it
                        }
                }
        }
    }

    private val githubTerminalSessions = GithubTerminalSessionGate()
    private val engines = List(MAX_CONCURRENT_ENGINES) { JsEngine(context) }
    private val enginePool = Channel<JsEngine>(capacity = MAX_CONCURRENT_ENGINES).also { pool ->
        engines.forEach(pool::trySend)
    }

    private fun logTerminalWait(traceId: String?, phase: String, started: Long) {
        if (traceId == null) return
        // ToolPkg 日志按启动归档，强停后仍可关联等待阶段；不记录参数或命令内容。
        AppLogger.d("ToolPkg", "package=github function=terminal_exec invocation=$traceId phase=$phase elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
    }

    private suspend fun <T> withEngine(
        traceId: String? = null,
        block: suspend (JsEngine) -> T,
    ): T {
        val started = System.nanoTime()
        logTerminalWait(traceId, "pool.wait", started)
        val engine = try {
            enginePool.receive()
        } catch (error: Exception) {
            logTerminalWait(traceId, "pool.failed type=${error.javaClass.simpleName}", started)
            throw error
        }
        logTerminalWait(traceId, "pool.acquired", started)
        return try {
            block(engine)
        } finally {
            val released = enginePool.trySend(engine)
            logTerminalWait(traceId, "pool.released success=${released.isSuccess}", started)
        }
    }

    private fun <T> withEngineBlocking(block: (JsEngine) -> T): T {
        return runBlocking {
            withEngine { engine -> block(engine) }
        }
    }

    private fun parseDotCall(toolName: String): Pair<String, String>? {
        val separatorIndex = toolName.lastIndexOf('.')
        if (separatorIndex <= 0 || separatorIndex >= toolName.lastIndex) {
            return null
        }
        return toolName.substring(0, separatorIndex) to toolName.substring(separatorIndex + 1)
    }

    private fun parsePackageToolName(toolName: String): Pair<String, String>? {
        val separatorIndex = toolName.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex >= toolName.lastIndex) {
            return null
        }
        return toolName.substring(0, separatorIndex) to toolName.substring(separatorIndex + 1)
    }

    private fun buildRuntimeParams(
        packageName: String,
        params: Map<String, Any?>
    ): MutableMap<String, Any?> {
        val runtimeParams = params.toMutableMap()
        // 嵌套包调用继续沿用发起它的对话，避免跨引擎后丢失文件操作归属。
        com.ai.assistance.operit.core.chat.tools.ToolExecutionManager.currentToolRuntimeContext()?.callerChatId?.let {
            runtimeParams["__operit_package_chat_id"] = it
        }
        packageManager.getActivePackageStateId(packageName)?.let {
            runtimeParams["__operit_package_state"] = it
        }

        listOf(
            "__operit_package_caller_name",
            "__operit_package_chat_id",
            "__operit_package_caller_card_id"
        ).forEach { key ->
            val value = runtimeParams[key]?.toString()?.takeIf { it.isNotBlank() }
            if (value == null) {
                runtimeParams.remove(key)
            } else {
                runtimeParams[key] = value
            }
        }

        runtimeParams["__operit_package_name"] = packageName
        runtimeParams["__operit_toolpkg_runtime_kind"] = "sandbox"

        packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName)?.let { runtime ->
            runtimeParams["__operit_execution_context_key"] =
                "toolpkg_main:${runtime.containerPackageName}"
            runtimeParams["__operit_toolpkg_subpackage_id"] = runtime.subpackageId
            runtimeParams["containerPackageName"] = runtime.containerPackageName
            runtimeParams["toolPkgId"] = runtime.containerPackageName
            runtimeParams["__operit_ui_package_name"] = runtime.containerPackageName
            runtimeParams["__operit_script_screen"] = runtime.entryPath
        } ?: run {
            runtimeParams.remove("__operit_toolpkg_subpackage_id")
            runtimeParams.remove("containerPackageName")
            runtimeParams.remove("toolPkgId")
            runtimeParams.remove("__operit_ui_package_name")
            runtimeParams.remove("__operit_script_screen")
        }

        return runtimeParams
    }

    private suspend fun <T> withExecutionEngineForPackage(
        packageName: String,
        traceId: String? = null,
        terminalSessionName: String? = null,
        block: suspend (JsEngine) -> T,
    ): T {
        suspend fun execute(): T {
            val toolPkgRuntime = packageManager.resolveToolPkgSubpackageRuntimeInternal(packageName)
            if (toolPkgRuntime != null) {
                val contextKey = "toolpkg_main:${toolPkgRuntime.containerPackageName}"
                return block(
                    packageManager.getToolPkgExecutionEngine(
                        contextKey = contextKey,
                        containerPackageName = toolPkgRuntime.containerPackageName,
                    )
                )
            }
            return withEngine(traceId = traceId, block = block)
        }
        if (terminalSessionName == null) return execute()

        // 同名会话的获取、执行与关闭必须共用一次租用；等待时尚未占用 JS 引擎。
        val started = System.nanoTime()
        logTerminalWait(traceId, "session_gate.wait", started)
        return try {
            githubTerminalSessions.withSession(terminalSessionName) {
                logTerminalWait(traceId, "session_gate.acquired", started)
                execute()
            }
        } finally {
            logTerminalWait(traceId, "session_gate.finished", started)
        }
    }

    private fun <T> withExecutionEngineForPackageBlocking(
        packageName: String,
        terminalSessionName: String? = null,
        traceId: String? = null,
        block: (JsEngine) -> T,
    ): T {
        // 同步包调用新增会话等待时也继承取消，避免取消的等待者随后进入引擎。
        val callerJob = if (terminalSessionName != null) {
            com.ai.assistance.operit.core.chat.tools.ToolExecutionManager.currentToolRuntimeContext()?.cancellationJob
        } else null
        return runBlocking(callerJob ?: EmptyCoroutineContext) {
            withExecutionEngineForPackage(packageName, traceId, terminalSessionName) { engine -> block(engine) }
        }
    }

    private fun convertToolParameters(
        tool: AITool,
        packageName: String,
        functionName: String
    ): MutableMap<String, Any?> {
        val toolDefinition = packageManager.getPackageTools(packageName)
            ?.tools
            ?.find { it.name == functionName }
        val parameterDefinitions = toolDefinition?.parameters?.associateBy { it.name }.orEmpty()
        val missingRequiredParameters = toolDefinition
            ?.parameters
            ?.filter { definition ->
                definition.required && tool.parameters.none { it.name == definition.name }
            }
            ?.map { it.name }
            .orEmpty()

        if (missingRequiredParameters.isNotEmpty()) {
            throw ToolParameterConversionException(
                "Missing required parameters: ${missingRequiredParameters.joinToString(", ")}"
            )
        }

        val converted = linkedMapOf<String, Any?>()
        tool.parameters.forEach { parameter ->
            val definition = parameterDefinitions[parameter.name]
            val isRequired = definition?.required ?: true
            val type = definition?.type?.lowercase() ?: "string"
            val rawValue = parameter.value
            val isBlankOrNull = rawValue.isBlank() ||
                rawValue.equals("null", ignoreCase = true) ||
                rawValue.equals("undefined", ignoreCase = true)
            if (!isRequired && type != "string" && isBlankOrNull) {
                // 非必填的非字符串参数传入空值/空白时置为 null，避免数值/布尔/JSON解析异常；字符串类型原样保留
                converted[parameter.name] = null
            } else {
                converted[parameter.name] = convertToolParameterValue(
                    toolName = tool.name,
                    parameterName = parameter.name,
                    rawValue = rawValue,
                    type = type
                )
            }
        }

        return buildRuntimeParams(packageName, converted)
    }

    private fun convertToolParameterValue(
        toolName: String,
        parameterName: String,
        rawValue: String,
        type: String
    ): Any? {
        val normalizedValue = rawValue.trim()
        return when (type) {
            "number" -> parseNumberValue(normalizedValue)
                ?: throw invalidParameterType(toolName, parameterName, type, rawValue)
            "integer" -> normalizedValue.toLongOrNull()
                ?: throw invalidParameterType(toolName, parameterName, type, rawValue)
            "boolean" -> parseBooleanValue(normalizedValue)
                ?: throw invalidParameterType(toolName, parameterName, type, rawValue)
            "array" -> runCatching { jsonArrayToKotlin(JSONArray(rawValue)) }
                .getOrElse { throw invalidParameterType(toolName, parameterName, type, rawValue, it) }
            "object" -> runCatching { jsonObjectToKotlin(JSONObject(rawValue)) }
                .getOrElse { throw invalidParameterType(toolName, parameterName, type, rawValue, it) }
            else -> rawValue
        }
    }

    private fun parseNumberValue(value: String): Number? {
        if (value.isEmpty()) {
            return null
        }
        if (!value.contains('.') && !value.contains('e', ignoreCase = true)) {
            value.toLongOrNull()?.let { return it }
        }
        return value.toDoubleOrNull()
    }

    private fun parseBooleanValue(value: String): Boolean? {
        return when (value.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private fun invalidParameterType(
        toolName: String,
        parameterName: String,
        expectedType: String,
        rawValue: String,
        cause: Throwable? = null
    ): ToolParameterConversionException {
        val detail = cause?.message?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val preview = rawValue.replace("\n", "\\n").take(120)
        AppLogger.w(
            TAG,
            "Strict parameter conversion failed: tool=$toolName, param=$parameterName, type=$expectedType, value=$preview${if (rawValue.length > 120) "..." else ""}${detail}"
        )
        return ToolParameterConversionException(
            "Invalid parameter '$parameterName' for tool '$toolName': expected $expectedType"
        )
    }

    private fun jsonObjectToKotlin(jsonObject: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = jsonValueToKotlin(jsonObject.opt(key))
        }
        return result
    }

    private fun jsonArrayToKotlin(jsonArray: JSONArray): List<Any?> {
        val result = mutableListOf<Any?>()
        for (index in 0 until jsonArray.length()) {
            result.add(jsonValueToKotlin(jsonArray.opt(index)))
        }
        return result
    }

    private fun jsonValueToKotlin(value: Any?): Any? {
        return when (value) {
            null,
            JSONObject.NULL -> null
            is JSONObject -> jsonObjectToKotlin(value)
            is JSONArray -> jsonArrayToKotlin(value)
            else -> value
        }
    }

    private fun success(toolName: String, value: Any?): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = true,
            result = StringResultData(value?.toString() ?: "null")
        )
    }

    private fun failure(toolName: String, message: String): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(""),
            error = message
        )
    }

    private fun failure(toolName: String, failure: JsExecutionFailure): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = false,
            result = StringResultData(failure.dataText),
            error = failure.message
        )
    }

    private fun trace(
        toolName: String,
        kind: String,
        message: String,
        level: String? = null,
        callId: String? = null
    ): ToolResult {
        return ToolResult(
            toolName = toolName,
            success = true,
            result = ScriptExecutionTraceData(
                kind = kind,
                level = level,
                message = message,
                callId = callId
            )
        )
    }


    fun executeScript(toolName: String, params: Map<String, String>): String {
        val parsed = parseDotCall(toolName)
            ?: return "Invalid tool name format: $toolName. Expected format: packageName.functionName"
        val (packageName, functionName) = parsed
        packageManager.packageAvailabilityError(packageName)?.let { return it }
        val script = packageManager.getPackageScript(packageName)
            ?: return "Package not found: $packageName"
        val toolPkgApiVersion = packageManager.getToolPkgApiVersion(packageName)

        val runtimeParams = buildRuntimeParams(
            packageName = packageName,
            params = params.mapValues { it.value as Any? }
        )
        val terminalSessionName = githubTerminalSessionKey(packageName, functionName, runtimeParams["session_name"] as? String)
        val terminalTraceId = terminalSessionName?.let { UUID.randomUUID().toString() }
        return withExecutionEngineForPackageBlocking(packageName, terminalSessionName, terminalTraceId) { engine ->
            // Activation may change while this invocation waits for an execution engine.
            packageManager.packageAvailabilityError(packageName)?.let {
                return@withExecutionEngineForPackageBlocking it
            }
            packageManager.recordPluginUse(packageName)
            try {
                engine.executeScriptFunction(
                    script = script,
                    functionName = functionName,
                    params = runtimeParams,
                    toolPkgApiVersion = toolPkgApiVersion
                )?.toString()
                    ?: "null"
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "Script execution failed: package=$packageName, function=$functionName, error=${e.message}",
                    e
                )
                buildJsExecutionErrorPayload(e.message.orEmpty())
            }
        }
    }

    fun executeScript(script: String, tool: AITool): Flow<ToolResult> = channelFlow {
        val parsed = parsePackageToolName(tool.name)
        if (parsed == null) {
            send(failure(tool.name, "Invalid tool name format. Expected 'packageName:toolName'"))
            return@channelFlow
        }

        val (packageName, functionName) = parsed
        // Check at collection time, including retained dialogs and previously registered executors.
        packageManager.packageAvailabilityError(packageName)?.let {
            send(failure(tool.name, it))
            return@channelFlow
        }
        val toolPkgApiVersion = packageManager.getToolPkgApiVersion(packageName)
        val runtimeParams = try {
            convertToolParameters(tool, packageName, functionName)
        } catch (e: ToolParameterConversionException) {
            send(failure(tool.name, e.message ?: "Invalid tool parameters"))
            return@channelFlow
        }
        val terminalSessionName = githubTerminalSessionKey(packageName, functionName, runtimeParams["session_name"] as? String)
        val terminalTraceId = terminalSessionName?.let { UUID.randomUUID().toString() }
        val terminalStarted = System.nanoTime()
        logTerminalWait(terminalTraceId, "invocation.begin", terminalStarted)
        withExecutionEngineForPackage(packageName, traceId = terminalTraceId, terminalSessionName = terminalSessionName) { engine ->
            packageManager.packageAvailabilityError(packageName)?.let {
                send(failure(tool.name, it))
                return@withExecutionEngineForPackage
            }
            val traceListener =
                object : JsExecutionListener {
                    override fun onCallLog(callId: String, level: String, message: String) {
                        if (terminalTraceId != null && message.startsWith("[github-terminal] ")) {
                            AppLogger.d("ToolPkg", "package=github function=terminal_exec invocation=$terminalTraceId scriptCall=$callId $message")
                        }
                        trySend(
                            trace(
                                toolName = tool.name,
                                kind = "log",
                                message = message,
                                level = level,
                                callId = callId
                            )
                        )
                    }

                    override fun onIntermediateResult(callId: String, value: Any?) {
                        trySend(
                            trace(
                                toolName = tool.name,
                                kind = "intermediate",
                                message = value?.toString() ?: "null",
                                callId = callId
                            )
                        )
                    }
                }
            try {
                withTimeout(JsTimeoutConfig.SCRIPT_TIMEOUT_MS) {
                    packageManager.recordPluginUse(packageName)
                    logTerminalWait(terminalTraceId, "script.begin", terminalStarted)
                    val result = engine.executeScriptFunction(
                        script = script,
                        functionName = functionName,
                        params = runtimeParams,
                        toolPkgApiVersion = toolPkgApiVersion,
                        executionListener = traceListener
                    )

                    logTerminalWait(terminalTraceId, "script.returned", terminalStarted)
                    val normalizedFailure = extractJsExecutionFailure(result)
                    if (normalizedFailure != null) {
                        send(failure(tool.name, normalizedFailure))
                    } else {
                        send(success(tool.name, result))
                    }
                    logTerminalWait(terminalTraceId, "result.sent success=${normalizedFailure == null}", terminalStarted)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logTerminalWait(terminalTraceId, "script.timeout", terminalStarted)
                send(
                    failure(
                        tool.name,
                        "Script execution timed out after ${JsTimeoutConfig.SCRIPT_TIMEOUT_MS}ms"
                    )
                )
            } catch (e: Exception) {
                logTerminalWait(terminalTraceId, "script.failed type=${e.javaClass.simpleName}", terminalStarted)
                AppLogger.e(TAG, "Script execution failed: tool=${tool.name}, error=${e.message}", e)
                send(failure(tool.name, "Script execution failed: ${e.message}"))
            } finally {
                logTerminalWait(terminalTraceId, "script.finished", terminalStarted)
            }
        }
    }

    fun executeComposeDsl(
        script: String,
        packageName: String = "",
        uiModuleId: String = "",
        toolPkgId: String = "",
        state: Map<String, Any?> = emptyMap(),
        memo: Map<String, Any?> = emptyMap(),
        moduleSpec: Map<String, Any?> = emptyMap(),
        envOverrides: Map<String, String> = emptyMap()
    ): String {
        val updatingName = listOf(packageName, toolPkgId).firstOrNull {
            com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState.contains(it)
        }
        if (updatingName != null) return "package is unavailable: $updatingName"
        val runtimeOptions = mutableMapOf<String, Any?>()
        if (packageName.isNotBlank()) {
            runtimeOptions["packageName"] = packageName
        }
        if (uiModuleId.isNotBlank()) {
            runtimeOptions["uiModuleId"] = uiModuleId
        }
        if (toolPkgId.isNotBlank()) {
            runtimeOptions["toolPkgId"] = toolPkgId
            runtimeOptions["__operit_ui_package_name"] = toolPkgId
        }
        if (state.isNotEmpty()) {
            runtimeOptions["state"] = state
        }
        if (memo.isNotEmpty()) {
            runtimeOptions["memo"] = memo
        }
        if (moduleSpec.isNotEmpty()) {
            runtimeOptions["moduleSpec"] = moduleSpec
        }
        if (packageName.isNotBlank()) {
            packageManager.getActivePackageStateId(packageName)?.let {
                runtimeOptions["__operit_package_state"] = it
            }
        }

        return withEngineBlocking { engine ->
            val unavailable = listOf(packageName, toolPkgId).firstOrNull {
                com.ai.assistance.operit.core.tools.packTool.runtime.PackageUpdateState.contains(it)
            }
            if (unavailable != null) return@withEngineBlocking "package is unavailable: $unavailable"
            try {
                engine.executeComposeDslScript(
                    script = script,
                    runtimeOptions = runtimeOptions,
                    envOverrides = envOverrides
                )?.toString() ?: "null"
            } catch (e: Exception) {
                AppLogger.e(TAG, "Compose DSL execution failed: ${e.message}", e)
                buildJsExecutionErrorPayload(e.message.orEmpty())
            }
        }
    }

    fun destroy() {
        enginePool.close()
        engines.forEach(JsEngine::destroy)
    }
}
