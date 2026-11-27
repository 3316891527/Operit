/* METADATA
{
    "name": "operit_editor",
    "display_name": {"zh": "Operit 平台编辑器", "en": "Operit Platform Editor"},
    "description": {
        "zh": "通过宿主命令执行器修改 Operit 软件配置，通过 Help 获取帮助。需要 Operit 1.12.2+27 及以上版本。",
        "en": "Modify Operit software settings through the host command executor. Use Help for guidance. Requires Operit 1.12.2+27 or later."
    },
    "enabledByDefault": true,
    "category": "Chat",
    "tools": [{
        "name": "operit_editor",
        "display": {"label": {"zh": "管理 Operit 配置", "en": "Manage Operit settings"}, "icon": "edit"},
        "description": {
            "zh": "执行结构化平台命令，返回真实结果。args=[] 获取宿主实际帮助。需要 Operit 1.12.2+27 及以上版本。",
            "en": "Execute structured platform commands and return real results. args=[] requests live host help. Requires Operit 1.12.2+27 or later."
        },
        "parameters": [{
            "name": "args",
            "description": {"zh": "CLI 字符串参数数组，也接受其 JSON 字符串。空数组获取帮助，JSON 正文作为单个参数传入，保留空格、空串和换行。", "en": "CLI string argument array, or its JSON encoding. [] requests help. Pass JSON bodies as a single argument; empty strings, spaces and newlines are preserved."},
            "type": "array",
            "required": true
        }]
    }]
}*/

/// <reference path="./types/index.d.ts" />

type OperitEditorParams = { args: string[] | string };

/** 校验外部参数，不拆分或改写数组中的内容。 */
function commandArgs(value: unknown): string[] {
    let args = value;
    if (typeof args === "string") {
        try {
            args = JSON.parse(args);
        } catch (error) {
            NativeInterface.logError(`operit_editor: invalid args JSON: ${error}`);
            throw new Error('operit_editor: args 必须是字符串数组或其 JSON 字符串，例如 ["package","list"]。');
        }
    }
    if (!Array.isArray(args)) {
        throw new Error('operit_editor: 必须提供 args 字符串数组；用 [] 获取命令帮助，不接受自然语言 query。');
    }
    for (let index = 0; index < args.length; index++) {
        if (typeof args[index] !== "string") throw new Error(`operit_editor: args[${index}] 必须是字符串。`);
    }
    return args;
}

/** 直接返回宿主的结果，让执行失败沿工具运行时传播。 */
async function operit_editor(params: OperitEditorParams) {
    if (!params || typeof params !== "object" || Array.isArray(params)) {
        throw new Error('operit_editor: 必须提供参数对象，例如 {"args":["package","list"]}。');
    }
    if (Object.prototype.hasOwnProperty.call(params, "query")) {
        throw new Error('operit_editor: query 已移除；请使用 args 字符串数组，用 [] 获取命令帮助。');
    }
    const args = commandArgs(params.args);
    if (typeof Tools.SoftwareSettings.exec !== "function") {
        throw new Error('operit_editor: 请将 Operit 软件同步更新至 1.12.2+27 或更新版本；单独更新脚本无法添加宿主命令执行器。');
    }
    return await Tools.SoftwareSettings.exec(args);
}

exports.operit_editor = operit_editor;
exports.main = operit_editor;
