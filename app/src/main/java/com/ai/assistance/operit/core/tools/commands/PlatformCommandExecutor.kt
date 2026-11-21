package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.tools.FunctionModelConfigResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.climode.HiddenToolCatalogEntry
import com.ai.assistance.operit.core.tools.prompt.ToolParameterSchema
import com.ai.assistance.operit.core.tools.protocol.AITool
import com.ai.assistance.operit.core.tools.protocol.ToolResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 依赖宿主提供的受控调用，不持有全局执行锁，嵌套包调用仍沿原调用上下文执行。 */
internal class PlatformCommandExecutor(
    private val invoke: (String, Map<String, String>) -> ToolResult,
    private val catalog: () -> List<HiddenToolCatalogEntry>,
    private val schema: (String) -> List<ToolParameterSchema>?,
    private val isVisible: (String) -> Boolean,
    private val skillDirectory: String,
) {
    companion object {
        const val TOOL_NAME = "execute_cli_command"
        val contextParameterNames = setOf(
            "__operit_package_caller_name", "__operit_package_chat_id", "__operit_package_caller_card_id",
        )
    }

    fun execute(tool: AITool): ToolResult {
        val request = try {
            require(tool.parameters.all { it.name == "args" || it.name in contextParameterNames }) {
                "Only args is accepted. Use [] for help; query is not supported."
            }
            val args = tool.parameters.filter { it.name == "args" }
            require(args.size == 1) { "Exactly one args string array is required. Use [] for help." }
            PlatformCommandRegistry.parse(PlatformCommandRegistry.parseArgsJson(args.single().value))
        } catch (error: IllegalArgumentException) {
            return failure(tool.name, requireNotNull(error.message))
        } catch (error: JSONException) {
            return failure(tool.name, "Invalid JSON arguments: ${error.message}")
        }
        val result = when (request) {
            is PlatformCommandRequest.Help -> help(tool.name, request)
            is PlatformCommandRequest.Catalog -> describeTools(tool.name, request)
            is PlatformCommandRequest.Invoke -> invokeValidated(request.toolName, request.parameters)
            is PlatformCommandRequest.ContextUpdate -> updateContext(tool.name, request)
            is PlatformCommandRequest.SkillGuide -> text(tool.name, skillGuide())
        }
        // 普通模式直接保留业务数据；JSON 模式仅序列化成功结果，失败状态与错误仍由原结果表达。
        return if (request.json && result.success && request !is PlatformCommandRequest.Help && request !is PlatformCommandRequest.Catalog) {
            result.copy(result = StringResultData(
                if (result.result is StringResultData) JSONObject.quote(result.result.value) else result.result.toJson()
            ))
        } else result
    }

    private fun invokeValidated(name: String, parameters: Map<String, String>): ToolResult {
        val declarations = schema(name)
        if (declarations != null) {
            val known = declarations.map { it.name }.toSet()
            val unknown = parameters.keys - known
            if (unknown.isNotEmpty()) return failure(name, "Unexpected parameters for $name: ${unknown.joinToString()}")
            val missing = declarations.filter { it.required && it.name !in parameters }.map { it.name }
            if (missing.isNotEmpty()) return failure(name, "Missing required parameters for $name: ${missing.joinToString()}")
        }
        return invoke(name, parameters)
    }

    private fun updateContext(toolName: String, request: PlatformCommandRequest.ContextUpdate): ToolResult {
        val bindingResult = invokeValidated("get_function_model_config", mapOf("function_type" to request.functionType))
        if (!bindingResult.success) return bindingResult
        val binding = bindingResult.result as? FunctionModelConfigResultData
            ?: return failure(toolName, "get_function_model_config returned an invalid binding result")
        if (binding.configId.isBlank()) return failure(toolName, "No model configuration is bound to ${request.functionType}")
        return invokeValidated("update_model_config", request.parameters + ("config_id" to binding.configId))
    }

    private fun visibleCommands(prefix: String): List<PlatformCommandSpec> = PlatformCommandRegistry.commands.filter { spec ->
        (prefix.isEmpty() || spec.path == prefix || spec.path.startsWith("$prefix ")) && when (spec.target) {
            "@context-update" -> isVisible("get_function_model_config") && isVisible("update_model_config")
            "@list", "@show", "@tool", "@package", "@skill-guide" -> true
            else -> isVisible(spec.target)
        }
    }

    private fun commandParameters(spec: PlatformCommandSpec): List<ToolParameterSchema> = when (spec.target) {
        "@context-update" -> schema("update_model_config").orEmpty().filter { it.name in PlatformCommandRegistry.contextFields }
        else -> schema(spec.target).orEmpty()
    }

    private fun help(toolName: String, request: PlatformCommandRequest.Help): ToolResult {
        val commands = visibleCommands(request.prefix)
        if (commands.isEmpty()) return failure(toolName, "No accessible commands: ${request.prefix}")
        if (request.json) {
            val rows = JSONArray()
            commands.forEach { spec ->
                rows.put(JSONObject().put("command", spec.path).put("usage", spec.usage)
                    .put("description", spec.description).put("target", spec.target)
                    .put("parameters", JSONArray(commandParameters(spec).map { parameter ->
                        JSONObject().put("name", parameter.name).put("type", parameter.type)
                            .put("required", parameter.required).put("description", parameter.description)
                    })))
            }
            return text(toolName, JSONObject().put("minimumSoftwareVersion", "1.12.2+27").put("commands", rows).toString())
        }
        return text(toolName, buildString {
            appendLine("Operit 平台命令 / Platform commands (1.12.2+27+)")
            appendLine("args=[]: help; [help, group, command]: detailed help; [--json, ...]: JSON output.")
            appendLine("Each argument is forwarded unchanged. params-json is one JSON object; this is not a shell command line.")
            commands.forEach { spec ->
                appendLine("${spec.usage} — ${spec.description}")
                if (request.prefix == spec.path) {
                    commandParameters(spec).forEach { parameter ->
                        appendLine("  ${parameter.name} [${parameter.type}, ${if (parameter.required) "required" else "optional"}]: ${parameter.description}")
                    }
                }
            }
            append("Read current state before changes; verify it afterward. New editor scripts require updating the software to 1.12.2+27 or later.")
        })
    }

    private fun describeTools(toolName: String, request: PlatformCommandRequest.Catalog): ToolResult {
        val entries = catalog().filter { entry ->
            when {
                request.name != null -> entry.targetToolName == request.name || entry.displayName == request.name
                request.query != null -> listOf(entry.targetToolName, entry.displayName, entry.description)
                    .any { it.contains(request.query, ignoreCase = true) }
                else -> true
            }
        }
        if (request.name != null && entries.isEmpty()) return failure(toolName, "Unknown or inaccessible tool: ${request.name}")
        if (request.json) {
            return text(toolName, JSONArray(entries.map { entry ->
                JSONObject().put("toolName", entry.targetToolName).put("displayName", entry.displayName)
                    .put("description", entry.description).put("parameters", JSONArray(entry.parameterHints))
                    .put("source", entry.sourceKind.name)
            }).toString())
        }
        return text(toolName, entries.joinToString("\n\n") { entry ->
            buildString {
                appendLine("${entry.displayName} -> ${entry.targetToolName}: ${entry.description}")
                entry.parameterHints.forEach { appendLine("  $it") }
                entry.suggestedParamsJson?.let { append("  params: $it") }
            }.trimEnd()
        }.ifEmpty { "No matching accessible tools." })
    }

    private fun skillGuide(): String = """
        Skill directory: $skillDirectory/<skill_name>/
        Required file: SKILL.md. Use frontmatter with name and description, followed by use cases, steps, constraints and expected outputs.
        可选 scripts/、templates/、examples/、assets/；正文使用相对路径引用。用 skill show <name> 读取实际 Skill。
        ---
        name: your_skill_name
        description: Describe what the Skill does
        ---
    """.trimIndent()

    private fun text(name: String, value: String) = ToolResult(name, true, StringResultData(value))
    private fun failure(name: String, error: String) = ToolResult(name, false, StringResultData(""), error)
}