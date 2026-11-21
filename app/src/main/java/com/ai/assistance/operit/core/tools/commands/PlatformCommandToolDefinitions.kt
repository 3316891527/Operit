package com.ai.assistance.operit.core.tools.commands

import com.ai.assistance.operit.core.tools.prompt.SystemToolPromptCategory
import com.ai.assistance.operit.core.tools.prompt.ToolParameterSchema
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt

/** 新增内部接口的声明与命令参数帮助共用一份定义。 */
internal object PlatformCommandToolDefinitions {
    fun category(useEnglish: Boolean) = SystemToolPromptCategory(
        categoryName = if (useEnglish) "Platform commands" else "平台命令",
        tools = listOf(
            ToolPrompt(
                name = "execute_cli_command",
                description = if (useEnglish) "Execute structured platform arguments. [] returns live help. Requires Operit 1.12.2+27 or later."
                    else "执行结构化平台命令，[] 返回实际帮助。需要 Operit 1.12.2+27 或更新版本。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "args", type = "array", description = "CLI string argument array; values are forwarded unchanged", required = true),
                ),
            ),
            ToolPrompt(
                name = "debug_install_sandbox_package",
                description = if (useEnglish) "Install or replace a development JS package, ToolPkg archive or ToolPkg source directory. Returns the verified installation state."
                    else "安装或替换调试 JS 包、ToolPkg 归档或源码目录，返回实际安装校验结果。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source_path", type = "string", description = "Readable absolute Android path to .js, .toolpkg, or a directory containing a manifest", required = true),
                    ToolParameterSchema(name = "enable_after_install", type = "boolean", description = "Enable the installed package (default true)", required = false),
                    ToolParameterSchema(name = "activate_after_install", type = "boolean", description = "Activate a JS package (default true); ToolPkg containers use activate_subpackages", required = false),
                    ToolParameterSchema(name = "reset_subpackage_states", type = "boolean", description = "Reset ToolPkg subpackage switches to manifest defaults (default true)", required = false),
                    ToolParameterSchema(name = "activate_subpackages", type = "array", description = "ToolPkg subpackage ids to enable and activate; unknown ids fail before installation", required = false),
                ),
            ),
            ToolPrompt(
                name = "execute_sandbox_script_direct",
                description = if (useEnglish) "Run a sandbox script directly with parameters and an environment file."
                    else "直接运行沙盒脚本，可传入参数及环境文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source_path", type = "string", description = "Android script file path; provide exactly one source", required = false),
                    ToolParameterSchema(name = "source_code", type = "string", description = "Inline script source", required = false),
                    ToolParameterSchema(name = "params_json", type = "object", description = "Script argument object", required = false),
                    ToolParameterSchema(name = "env_file_path", type = "string", description = "Android environment file path", required = false),
                    ToolParameterSchema(name = "script_label", type = "string", description = "Execution label", required = false),
                    ToolParameterSchema(name = "wait_ms", type = "integer", description = "Execution timeout in milliseconds", required = false),
                ),
            ),
        ) + PlatformSettingsToolDefinitions.tools(useEnglish),
    )
}
