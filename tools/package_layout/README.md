# API 包迁移静态核对

该工具用于本次 refactor/package-layout 的审查，比较固定基线与当前工作树。它不会编译应用、执行单元测试或调用语音服务。依赖 Python 3.10 以上与 Git，无第三方 Python 依赖。

在仓库根目录执行：

```sh
python tools/package_layout/verify_api_migration.py
```

浅克隆需先取回基线：

```sh
git fetch origin 342694313de3a129e320f326d861903c662930f5
```

映射位于 docs/TODO/chore/package-layout/api-migration.json。检查器确认 117 个原文件、74 个目标移动、源码总集合、改动正文、字符串与字符字面量、未改文件摘要、导入新增冲突、六个固定入口及可执行目录的旧完整名称。仅允许 package/import 和清单内完整名称、路径、五个类名的变化。

检查结果是这次迁移相对固定基线的证据。后续业务改动不再满足该固定基线，届时应按新任务审查，不把此工具作为所有未来提交的永久门禁。Kotlin 编译与 JVM 测试由仓库现有工作流承担。
