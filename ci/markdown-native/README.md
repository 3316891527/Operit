# Markdown 核心检查

该目录验证生产 Kotlin 全文语法和 C++ 原生流解析器，不启动 Android 应用。

## 执行

准备 Gradle 8.13 的 lib 目录、JDK 和 g++ 后，在仓库根目录运行：

```sh
bash ci/markdown-native/run-core.sh /path/to/gradle-8.13/lib
bash ci/markdown-native/run-stream.sh
```

Kotlin 检查直接编译生产类型枚举与稳定片段边界分类函数，覆盖新增类型的块级、行内语义和 JNI 序号兼容性；同时覆盖任务前缀、表格对齐与竖线、链接标题、资源地址、引用式语法、Setext 标题、目录、脚注、嵌套容器和受保护范围。C++ 检查调用实际生产解析器，检查转义、公式、样式、链接、图片、列表、引用和代码保护在各个分片位置的结果。

输出只保存在临时目录并在退出时删除。Android Tests 的核心作业执行这两项检查和既有 Mermaid 核心检查。工作流使用 Bash 自带路径展开寻找编译器，不依赖 rg。

这些检查不证明 Compose 视觉效果、设备字体、媒体加载和手势已经验收。编辑器初始位置的设备回归位于 WorkspaceEditorInitialLineTest。新增语法范围与设备验收记录见 [Markdown 扩展检查与验收](../../docs/TODO/feat/markdown-mermaid-preview/06-extension-validation.md)。