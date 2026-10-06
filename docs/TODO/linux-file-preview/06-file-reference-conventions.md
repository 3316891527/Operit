# 模型生成可点击文件引用的约定

## 原行为与改动目的

聊天文件解析器已支持文件 URI、来源参数与文本行号，系统提示此前主要说明文件工具参数，没有指导模型生成可点击的文件引用。模型引用源码时可能只输出纯路径，或漏掉来源环境，用户因此需要自行构造链接。

新增 core/config/links/WorkspaceFileLinkGuidelines，提供中英文文件引用约定。SystemPromptConfig 在 XML、Tool Call API、CLI 与关闭工具的分支全部处理完成后统一追加说明，未绑定工作区或使用自定义系统模板时也包含该输出约定。该说明属于消息输出格式，不改变文件工具的参数或执行权限。

## 写法

标准 Markdown 链接由 `[显示名称]` 与 `(目标)` 连写组成；file:///绝对路径 是通用文件 URI；#L12 是 GitHub 常用行号约定，不是 Markdown 规范自带的行号功能，Operit 文件入口已经支持它。

模型使用用户提供或工具确认的真实路径、来源和行号生成普通正文中的链接。目标为 file URI，通过 environment 查询参数保留 android、linux 或 repo:仓库名称；路径段和查询参数值分别 URL 编码，路径分隔符保留。文本行号放在最后，按一基行号处理。网页仍引用原 HTTP/HTTPS 地址。

```markdown
[报告](file:///sdcard/Download/report.md?environment=android)
[源码第12行](file:///home/user/project/main.kt?environment=linux#L12)
[仓库源码第12行](file:///src/main.kt?environment=repo%3Ademo#L12)
[特殊字符文件](file:///sdcard/Download/notes%20%281%29%23%3F%25.md?environment=android)
```

以上是格式示例，实际输出使用已确认存在的文件。路径中的空格、括号、#、?、% 对应 %20、%28/%29、%23、%3F、%25，避免它们成为 Markdown 或 URI 语法的一部分。

## 验收与验证

既有工作区清单按用户本轮最终确认全部勾选，本节记录新增输出约定的源码验收，模型生成与点击实测未执行。

| 编号 | 操作 | 预期 | 源码检查结果 |
| --- | --- | --- | --- |
| W-LREF01 | 检查系统提示共用拼接入口的位置 | 各工具模式处理完成后追加；未绑定工作区及自定义模板仍包含约定 | ✅ 通过 |
| W-LREF02 | 检查中英文约定与示例 | 保留真实来源、路径编码与行号；普通网页使用原地址 | ✅ 通过 |
| W-LREF03 | 检查新增 JVM 用例接入 | 中英文示例直接交给真实链接解析器，另覆盖特殊文件名及仓库名称编码 | ✅ 通过；用例执行交由 Android Tests |

WorkspaceFileLinkGuidelinesTest 新增三个 JVM 用例，验证中英文实际提示示例及带特殊字符文件名、仓库名称的解析。Android Build 执行 assembleDebug；Android Tests 执行 :app:testDebugUnitTest，本地未执行 Android 构建和应用测试。工作流派发和运行通过分别记录。

[DONE] 模型文件引用约定及系统提示接线已实现，源码验收与运行记录分开保存。
