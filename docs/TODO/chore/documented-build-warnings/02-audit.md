# 原日志对账与验证

## 范围和结果

保存的工作流日志共有 552 条警告，涉及 209 个文件。本次为其中 204 条迁移源码，另 348 条保留。这里统计的是旧日志条目与代码处理的对应关系，不是重新编译后的警告数量。

Google / AndroidX 迁移共 193 条；Coil、OkHttp 和 kotlinx.coroutines 的官方迁移共 10 条；Kotlin 编译器明确要求的类型实参迁移共 1 条。

- AutoMirrored 图标：97 条
- OkHttp 请求体扩展：1 条
- 协程 resume 取消回调重载：7 条
- Kotlin 数据库绑定参数显式类型：1 条
- LifecycleOwner 导入：11 条
- HorizontalDivider：1 条
- Path.quadraticTo：2 条
- 固定与滚动标签页：23 条
- 标签页指示器作用域：6 条
- 异步 Clipboard：18 条
- 下拉锚点类型：22 条
- 描边按钮边框重载：3 条
- Coil 加载器注入：2 条
- Tooltip 定位重载：2 条
- 进度值 lambda：6 条
- Pointer.consume：1 条
- ColorScheme 完整构造：1 条

## 行为保持

标签页显式保留旧默认内容色、全宽和 24.dp 指示器；下拉锚点区分只读、可编辑和次级图标。异步剪贴板通过父组件作用域完成复制后再提示或关闭，粘贴保留首条直接文本语义。

Coil 正常屏幕仍使用 context.imageLoader 单例；截图只创建一个共享资源的软件加载器并关闭 crossfade，经显式参数传至图片、头像、背景和 XML 插件的 DSL Image。不改变全局加载配置。

ColorScheme 新角色保持旧构造的 Color.Unspecified，序列化格式不变。DSL Divider 公开节点保持原名，零厚度继续占一物理像素，生成器与模板、产物同步。模板现有类型导入也与产物中的真实 uidsl 包路径对齐。

终端子模块仅迁移设置返回图标，来源为 [OperitTerminalCore 配套 PR](https://github.com/3316891527/OperitTerminalCore/pull/4)，主应用固定到提交 13e7e9139dcf2c34ea9de357014bb7675f81f918。

AutoMirrored 导入同时需要同步仓内 Material 图标子集。使用 `tools/icons/sync_material_icons.py` 从固定 SHA-256 的 1.7.8 官方源码重新生成：静态扩展图标由 332 个变为 298 个，新增 14 个镜像版本，删除 48 个当前主应用与终端源码均不再引用的旧版本。core 图标目录与 2132 个动态 Filled 名称保持不变。

## 保留项

- 仅提示 Deprecated in Java、没有明确迁移步骤的 Android / Java / ExoPlayer 调用
- 未检查转换、可空类型风险、恒真恒假条件及冗余提示
- 注解目标未来变化，以及只建议标记 Deprecated、OptIn 或 suppress 的诊断

没有新增批量警告抑制，也没有为了清理日志而更改依赖版本。

## 签名依据

- [Compose BOM 2026.02.01 POM](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.02.01/compose-bom-2026.02.01.pom)
- [Material3 Android 1.4.0 官方源码](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-android/1.4.0/material3-android-1.4.0-sources.jar)
- [Compose UI Android 1.10.4 官方源码](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.10.4/ui-android-1.10.4-sources.jar)
- [Coil 2.5.0 局部加载器迁移说明](https://github.com/coil-kt/coil/blob/2.5.0/coil-compose-singleton/src/main/java/coil/compose/LocalImageLoader.kt)
- [OkHttp 4.12.0 请求体迁移](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/RequestBody.kt)
- [协程 1.10.2 取消回调说明](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/common/src/CancellableContinuation.kt)

## 已完成的静态核验

204 条逐项源码对账通过。127 个 Kotlin 文件进行 Tree-sitter 解析，修改前后均为 18 处基线解析诊断，没有新增；这不等同于 Kotlin 编译或类型检查。Python 生成器 AST、严格 UTF-8 和差异空白检查通过。

图标子集引用检查通过；全部 298 个保留的扩展源码与已校验的官方源码逐字节一致，动态图标数据与 core 清单均未变化。

本地没有编译、构建或运行测试；未为机械 API 替换增加与实现重复的测试。提交后运行仓库卫生、本地化和 Markdown 链接门禁；远端 assembleDebug、JVM 回归与仪器测试 APK 编译单独登记结果，不持续轮询。设备上的截图、复制粘贴和标签页过渡效果仍待验收。

[DONE] 源码迁移与静态核验完成，远端及设备结果另行记录。
