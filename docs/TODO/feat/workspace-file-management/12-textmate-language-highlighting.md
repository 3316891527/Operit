---
title: TextMate 多语言高亮接入
status: pending_validation
---

# TextMate 多语言高亮接入

[返回计划](index.md)

使用现成的 Sora TextMate 0.23.6 Java 引擎接入自有编辑器。保留经典绿色字符串、不可变行快照、异步调度和现有画布控件，语言规则使用可追溯的离线语法包。

## 实施清单

- [x] 核实发布 AAR、Java 17、最低 Android 版本及运行依赖
- [x] 固定语言包、逐项来源、许可证与校验值，建立统一目录和离线检查入口
- [x] 按行调用 TextMate，保存跨行规则栈并复用未变化行和子树
- [x] 作用域映射浅深主题，保留绿色字符串并处理 UTF-16、嵌入代码与插值
- [x] 文件扩展名、复合后缀、特殊文件名及别名共用生成目录，未知格式明确使用纯文本
- [x] 移除手写扫描器，语法加载或分词失败提供具体提示和完整日志
- [x] 准备真实语法、增量边界、Unicode 和异步调度回归源码
- [x] 完成静态校验及原 PR 的提交、说明与远程验证入口准备

## 语言与资源

导入 `tm-grammars` 1.32.22 的 242 个语言定义和 18 个注入定义，共 260 个语法文件。语法展开约 11.26 MiB，属于资源大小，不是 APK 增量测量。目录登记 844 个后缀、284 个特殊文件名，以及 Dockerfile 和环境变量文件的前缀规则。

覆盖 JavaScript、TypeScript、JSX、TSX、Kotlin、Java、Python、C、C++、C#、Go、Rust、Dart、Swift、Ruby、PHP、HTML、CSS、SCSS、Sass、Less、JSON、JSON5、JSONC、YAML、TOML、Shell、PowerShell、SQL、Markdown、Vue、Svelte、Dockerfile、Makefile 等格式。识别优先处理特殊文件名和更长的复合后缀，例如 `build.gradle.kts`、`view.blade.php` 与 `schema.tf.json`。注入规则不计为独立语言。

资源和来源维护在应用 assets 的 `workspace-textmate/catalog.json`，运行时直接读取生成的 Kotlin 目录，主线程不打开规则文件。文件关联参考 GitHub Linguist 9.1.0；重复后缀明确登记选择，`.conf`、`.inc`、`.cgi`、`.fcgi` 等无法安全确定的通用后缀作为纯文本。

同语言的历史作用域在 `scopeAliases` 中显式登记。当前规则包缺少的包外嵌入语法在 `optionalScopes` 中逐项声明，不能把语言定义数量解读为所有嵌入格式均已覆盖。新增未登记缺失作用域会报错，便于升级时发现遗漏资源。

## 引擎与增量路径

Sora 的 TextMate 引擎是现成 AAR，另外显式引入 editor 模块提供其日志辅助类，以及发布 POM 对应的 Joni、jcodings、SnakeYAML 与 JDT 注解。Gson 按发布依赖对齐至 2.13.1。使用纯 Java 正则实现，不引入新的 JNI 库，也不替换 Operit 的编辑器控件。

发布版的 `OnigString` 把补充字符按码点递增，却在其他位置使用 Java UTF-16 坐标。应用保留这一类的二进制接口并修正字节与 UTF-16 映射，覆盖 Emoji、多个补充字符、孤立代理项与字符串末端。动态终点另通过 `RegExpSource` 修正重复转义。构建脚本校验原始 AAR SHA-256，只移除这两个同名类及 `OnigString` 的两个嵌套类，再编译本地修正版；其余引擎仍使用原发布物。不能在输出颜色时再次转换坐标，否则会重复偏移。

Kotlin 原语法的块注释规则增加递归包含，支持嵌套块注释。该资源同时记录原始 SHA-256、当前 SHA-256 与修改说明。后续真实引擎失败涉及的 XML、Racket、Stata、Wikitext 和 Jinja 规则结构修正见[兼容修复与资源管理评估](14-textmate-grammar-compatibility.md)。包级 LICENSE、逐来源 NOTICE、引擎和 Linguist 许可证随资源保留，具体许可证以各来源为准。

每个编辑器的注册表、语法加载与分词独占同一后台线程。按行调用 `tokenizeLine`，缓存进入与退出的规则栈；当规则对象及进入状态相同时，未变化的行和整棵子树直接复用。状态变化持续传播到稳定位置，必要时到文件末尾。首次分析仍需处理全文。

请求仍合并为一个运行工作和一个最新请求，逐行及结果发布前检查有效性。引擎单行使用一秒的协作式分词预算，早停按错误报告，不发布不完整着色；该预算不能硬中断单次 Joni 正则搜索。发布核对请求序号、文档身份和版本，切换文件清空旧样式，绘制只消费相同版本的颜色区间。

作用域已扩展为动态样式，旧八类仅保留语义查询。字符串默认使用深色 `#6A8759` 和 Atom One Light 浅色 `#50A14F`；编辑器与 Diff 保留完整字体效果，并支持[轻量 JSON 自定义配置](../../../doc-src/dev-core/editor-highlighting/editor-highlighting.md)。内置嵌入作用域隔开外层样式，主题切换只更新绘制样式，不重置语法规则栈。

加载或分词错误由九种语言的提示展示语言和原因，完整异常写日志。旧扫描器已移除；保留的语言关键字目录仍供既有补全功能使用。

## 回归与交付

本轮准备 29 项 JVM 回归源码，其中包含对原有 6 项扫描器回归的迁移重写，并更新既有注释动作与快照缓存断言。覆盖完整目录加载、文件识别、作用域、16 种常用语言的绿色字符串及数值、其他语言分词边界、HTML 与 Markdown 嵌入 JavaScript、JSX/TSX、中文与 Emoji、捕获与动态结束规则、嵌套注释、增量状态、取消、纯文本及加载错误。

新增 4 项设备回归源码，覆盖快速输入只发布最新版本、语言和文档切换、释放后取消回调，以及显式未知语言的错误回调。上一轮 7 项失败的快照修复记录见[全文快照修正](13-snapshot-materialization.md)。

静态检查、实际执行和设备体验分别记录。本地没有执行编译、构建或测试，没有设备帧率、输入延迟或 APK 增量测量。最终静态计数和新提交的 Build、Android Tests、PR Check 链接由 [PR #216](https://github.com/3316891527/Operit/pull/216) 维护；确认运行记录建立后停止查询进度。

[DONE]
