package com.ai.assistance.operit.core.tools.commands

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 命令目录同时用于解析和帮助，脚本只负责校验参数和转发。 */
internal data class PlatformCommandSpec(
    val path: String,
    val target: String,
    val description: String,
    val arguments: List<String> = emptyList(),
    val optionalArguments: List<String> = emptyList(),
    val jsonBody: Boolean = false,
    val bodyRequired: Boolean = false,
    val fixedParameters: Map<String, String> = emptyMap(),
) {
    val usage: String get() = buildString {
        append(path)
        arguments.forEach { append(" <$it>") }
        optionalArguments.forEach { append(" [$it]") }
        if (jsonBody) append(if (bodyRequired) " <params-json>" else " [params-json]")
    }
}

internal sealed interface PlatformCommandRequest {
    val json: Boolean
    data class Help(val prefix: String, override val json: Boolean) : PlatformCommandRequest
    data class Catalog(val name: String?, val query: String?, override val json: Boolean) : PlatformCommandRequest
    data class Invoke(val toolName: String, val parameters: Map<String, String>, override val json: Boolean) : PlatformCommandRequest
    data class ContextUpdate(val functionType: String, val parameters: Map<String, String>, override val json: Boolean) : PlatformCommandRequest
    data class SkillGuide(override val json: Boolean) : PlatformCommandRequest
}

internal object PlatformCommandRegistry {
    private fun command(
        path: String, target: String, description: String,
        arguments: List<String> = emptyList(), optional: List<String> = emptyList(),
        jsonBody: Boolean = false, bodyRequired: Boolean = false,
        fixed: Map<String, String> = emptyMap(),
    ) = PlatformCommandSpec(path, target, description, arguments, optional, jsonBody, bodyRequired, fixed)

    val contextFields = setOf(
        "context_length", "max_context_length", "enable_max_context_mode",
        "summary_token_threshold", "enable_summary", "enable_summary_by_message_count",
        "summary_message_count_threshold",
    )

    val commands: List<PlatformCommandSpec> = listOf(
        command("package list", "list_sandbox_packages", "列出沙盒包及启用状态 / List sandbox packages"),
        command("package enable", "set_sandbox_package_enabled", "启用包 / Enable package", listOf("package_name"), fixed = mapOf("enabled" to "true")),
        command("package disable", "set_sandbox_package_enabled", "停用包 / Disable package", listOf("package_name"), fixed = mapOf("enabled" to "false")),
        command("package use", "use_package", "加载包、Skill 或 MCP 的真实说明 / Load package, Skill or MCP", listOf("package_name")),
        command("package install", "debug_install_sandbox_package", "调试安装 .js、.toolpkg 或 ToolPkg 目录 / Install a development package", listOf("source_path"), jsonBody = true),
        command("package exec", "@package", "执行 package:tool / Execute a package tool", listOf("tool_name"), jsonBody = true),
        command("script run", "execute_sandbox_script_direct", "直接调试沙盒脚本 / Run sandbox script", jsonBody = true, bodyRequired = true),
        command("skill guide", "@skill-guide", "查看 Skill 制作规范和实际目录 / Show Skill authoring guide"),
        command("skill show", "use_package", "加载 Skill 的实际说明 / Load a Skill", listOf("package_name")),
        command("env get", "read_environment_variable", "读取环境变量 / Read environment variable", listOf("key")),
        command("env set", "write_environment_variable", "写入环境变量 / Write environment variable", listOf("key", "value")),
        command("env unset", "write_environment_variable", "清除环境变量 / Clear environment variable", listOf("key"), fixed = mapOf("value" to "")),
        command("permission show", "get_tool_permission_mode", "读取权限模式 / Read tool permission mode"),
        command("permission set", "set_tool_permission_mode", "设置权限模式 / Set tool permission mode", listOf("permission_level")),
        command("mcp restart", "restart_mcp_with_logs", "重启 MCP 并返回日志 / Restart MCP with logs", optional = listOf("timeout_ms")),
        command("mcp ping", "use_package", "加载并探测 MCP / Load and probe MCP", listOf("package_name")),
        command("speech show", "get_speech_services_config", "读取 TTS/STT 配置 / Read speech configuration"),
        command("speech set", "set_speech_services_config", "更新 TTS/STT 配置 / Update speech configuration", jsonBody = true, bodyRequired = true),
        command("speech test", "test_tts_playback", "测试语音播报 / Test TTS playback", listOf("text"), jsonBody = true),
        command("model list", "list_model_configs", "列出模型配置及绑定 / List model configurations"),
        command("model create", "create_model_config", "新增模型配置 / Create model configuration", jsonBody = true, bodyRequired = true),
        command("model update", "update_model_config", "更新模型配置 / Update model configuration", listOf("config_id"), jsonBody = true, bodyRequired = true),
        command("model delete", "delete_model_config", "删除模型配置 / Delete model configuration", listOf("config_id")),
        command("model test", "test_model_config_connection", "测试模型连接 / Test model connection", listOf("config_id"), listOf("model_index")),
        command("model function-list", "list_function_model_configs", "列出功能模型绑定 / List function model bindings"),
        command("model function-show", "get_function_model_config", "读取功能绑定和完整配置 / Read function binding and configuration", listOf("function_type")),
        command("model function-set", "set_function_model_config", "设置功能模型绑定 / Set function model binding", listOf("function_type", "config_id"), listOf("model_index")),
        command("context show", "get_function_model_config", "读取功能模型的上下文总结配置 / Read context and summary settings", listOf("function_type")),
        command("context set", "@context-update", "按功能绑定修改指定上下文总结字段 / Update selected context and summary fields", listOf("function_type"), jsonBody = true, bodyRequired = true),
        command("character list", "list_character_cards_settings", "列出角色卡 / List character cards"),
        command("character show", "get_character_card", "读取角色卡 / Read character card", listOf("character_card_id")),
        command("character create", "create_character_card", "创建角色卡 / Create character card", jsonBody = true, bodyRequired = true),
        command("character update", "update_character_card", "更新角色卡 / Update character card", listOf("character_card_id"), jsonBody = true, bodyRequired = true),
        command("character delete", "delete_character_card", "删除角色卡 / Delete character card", listOf("character_card_id")),
        command("character activate", "set_active_character_card", "激活角色卡 / Activate character card", listOf("character_card_id")),
        command("character clear", "clear_active_character_card", "清除激活角色卡 / Clear active character card"),
        command("character import", "import_character_card_from_tavern_json", "导入 Tavern JSON / Import Tavern JSON", listOf("tavern_json")),
        command("character export", "export_character_card_to_tavern_json", "导出 Tavern JSON / Export Tavern JSON", listOf("character_card_id")),
        command("performance show", "get_performance_metrics", "读取软件或插件性能 / Read performance metrics", optional = listOf("plugin_id")),
        command("tool list", "@list", "查询实际工具目录及参数 / Discover live tools", optional = listOf("query")),
        command("tool show", "@show", "查看工具的实际参数 / Show live tool parameters", listOf("tool_name")),
        command("tool exec", "@tool", "执行现有宿主或包工具 / Execute a host or package tool", listOf("tool_name"), jsonBody = true),
    )

    /** 仅解析首位输出选项，正文中的 --json、空格、空串和换行保持原样。 */
    fun parse(rawArgs: List<String>): PlatformCommandRequest {
        val json = rawArgs.firstOrNull() == "--json"
        val args = if (json) rawArgs.drop(1) else rawArgs
        if (args.isEmpty() || args == listOf("--help")) return PlatformCommandRequest.Help("", json)
        if (args.first() == "help") {
            require(args.size <= 3) { "Usage: help [group [command]]" }
            return help(args.drop(1).joinToString(" "), json)
        }
        if (args.size == 1 || args.size == 2 && args[1] in setOf("help", "--help")) {
            return help(args[0], json)
        }
        val path = args.take(2).joinToString(" ")
        val spec = commands.singleOrNull { it.path == path }
            ?: throw IllegalArgumentException("Unknown command: $path. Use [] for help.")
        val values = args.drop(2)
        require(values.size >= spec.arguments.size) { "Usage: ${spec.usage}" }
        val parameters = LinkedHashMap(spec.fixedParameters)
        spec.arguments.forEachIndexed { index, name -> parameters[name] = values[index] }
        val invocationTarget = if (spec.target in setOf("@tool", "@package")) parameters.remove("tool_name") else null
        var cursor = spec.arguments.size
        spec.optionalArguments.forEach { name ->
            if (cursor < values.size) parameters[name] = values[cursor++]
        }
        if (spec.jsonBody) {
            require(!spec.bodyRequired || cursor < values.size) { "Usage: ${spec.usage}" }
            if (cursor < values.size) {
                val body = parseJsonObject(values[cursor++])
                body.keys().forEach { key ->
                    require(!parameters.containsKey(key)) { "Parameter already supplied by command: $key" }
                    require(!key.startsWith("__operit_")) { "System context parameters cannot be supplied: $key" }
                    parameters[key] = body.get(key).toString()
                }
            }
        }
        require(cursor == values.size) { "Usage: ${spec.usage}" }
        return when (spec.target) {
            "@list" -> PlatformCommandRequest.Catalog(null, parameters["query"], json)
            "@show" -> PlatformCommandRequest.Catalog(requireNotNull(parameters["tool_name"]), null, json)
            "@skill-guide" -> PlatformCommandRequest.SkillGuide(json)
            "@context-update" -> {
                val functionType = requireNotNull(parameters.remove("function_type"))
                require(parameters.isNotEmpty()) { "context set requires at least one context or summary field" }
                require(parameters.keys.all { it in contextFields }) { "Allowed fields: ${contextFields.joinToString()}" }
                PlatformCommandRequest.ContextUpdate(functionType, parameters, json)
            }
            "@tool", "@package" -> {
                val target = requireNotNull(invocationTarget)
                require(target.isNotBlank() && target == target.trim()) { "tool_name must be a non-empty exact tool name" }
                require(spec.target != "@package" || target.split(':').let { it.size == 2 && it.all(String::isNotBlank) }) {
                    "package exec requires packageName:toolName"
                }
                PlatformCommandRequest.Invoke(target, parameters, json)
            }
            else -> PlatformCommandRequest.Invoke(spec.target, parameters, json)
        }
    }

    private fun help(prefix: String, json: Boolean): PlatformCommandRequest.Help {
        require(prefix.isEmpty() || commands.any { it.path == prefix || it.path.startsWith("$prefix ") }) {
            "Unknown command: $prefix. Use [] for help."
        }
        return PlatformCommandRequest.Help(prefix, json)
    }

    fun parseJsonObject(raw: String): JSONObject {
        val tokener = JSONTokener(raw)
        val value = tokener.nextValue()
        require(value is JSONObject && tokener.nextClean() == '\u0000') { "params-json must contain exactly one JSON object" }
        return value
    }

    fun parseArgsJson(raw: String): List<String> {
        val tokener = JSONTokener(raw)
        val values = tokener.nextValue()
        require(values is JSONArray && tokener.nextClean() == '\u0000') { "args must contain exactly one JSON array" }
        return (0 until values.length()).map { index ->
            val value = values.get(index)
            require(value is String) { "args[$index] must be a string" }
            value
        }
    }
}