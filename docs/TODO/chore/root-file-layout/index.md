---
title: Root file layout
status: completed
upstream_target: dev
---
# 根目录文件归组
## 目标
把普通文档和开发模板从仓库根目录归入所属目录，同时保留 Git、Gradle、npm/pnpm 与 Agent 必须从根目录发现的文件。
## 范围
- `Repo_Arch_Basic.md` -> `docs/Repo_Arch_Basic.md`
- `local.properties.example` -> `docs/doc-src/dev-core/local.properties.example`
- 修正文档中的终端子模块路径与模板复制命令
## 步骤
1. [x] 盘点根文件并按工具链约定分类
2. [x] 移动普通文档和本地配置模板
3. [x] 更新链接、开发指南和 TODO 引用
4. [x] 核对根目录保留项、Markdown 链接与差异空白
## 验收
- 根目录保留许可证、双语 README、Agent 约定及 Git/Gradle/npm/pnpm 必需入口
- `local.properties` 仍由开发者或工作流创建在根目录，模板位于开发文档目录
- 不运行本地构建或测试
