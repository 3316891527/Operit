# 包路径迁移静态核对

这些检查器用于 refactor/package-layout 的两轮迁移审查，依赖 Python 3.10 以上与 Git，无第三方 Python 依赖。不编译应用、不执行应用单元或设备测试。

## 第二轮当前迁移

在仓库根目录执行：

```sh
python tools/package_layout/verify_layout_migration.py
```

浅克隆需取回固定基线：

```sh
git fetch origin 8566d30589199a01570eb7cb2901f533a9cf5074
```

映射位于 docs/TODO/chore/package-layout/ui-utilities-migration.json，比较工具箱清理后的基线与第二轮结果，核对 677 个范围文件、643 个主源码移动、全部源码集合、正文、字面量、未改文件摘要、显式/隐式引用、95 个测试移动、43 份固定入口与可执行目录旧完整名称。九份保留 package 声明的文件在映射中逐一登记。正文仅允许清单中的包、完整名称与路径变更，以及列明的三行行尾空格清理。

源码集合包括 Git 已跟踪及未忽略的新文件，因此迁移目标必须进入 Git 跟踪范围。应用编译与 JVM 测试由既有 Android Build 和 Android Tests 工作流承担。

## 第一轮历史检查

verify_api_migration.py 仅适用于第一轮实现结果，后续路径改动不再满足第一轮固定范围。在独立工作树复现：

```sh
git worktree add --detach ../operit-api-verification b419768cd9fa166b56031bcb38bf5fe957d69e98
cd ../operit-api-verification
python tools/package_layout/verify_api_migration.py
```

浅克隆还需取回第一轮基线 `342694313de3a129e320f326d861903c662930f5`。映射位于 docs/TODO/chore/package-layout/api-migration.json，覆盖 117 个原文件、74 个移动、五处类名调整及当时的全部源码、绑定和六份固定入口。

两个检查器都是固定迁移相对基线的证据。后续业务改动应按新任务审查，不将这些工具作为所有未来提交的永久门禁。第一轮和第二轮的验证记录分别位于 docs/TODO/chore/package-layout/verification.md 与 verification-round2.md。
