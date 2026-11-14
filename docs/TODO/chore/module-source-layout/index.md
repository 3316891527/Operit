---
title: Support modules and Web Chat layout
status: completed
personal_repository: https://github.com/3316891527/Operit
upstream_target: dev
---
# 支持模块与 Web Chat 归组
## 目标
将独立 Gradle/native 支持模块统一放入 `modules/`，将 Web Chat 放入 `app/web-chat/`，在不改变 Gradle 模块名、资源内部路径和运行时接口的前提下，把非隐藏根目录从 14 个减少到 8 个。
## 范围
- `a2ui-compat/` -> `modules/a2ui-compat/`
- `llm/` -> `modules/llm/`
- `material-icons-subset/` -> `modules/material-icons-subset/`
- `quickjs/` -> `modules/quickjs/`
- `showerclient/` -> `modules/showerclient/`
- `terminal/` -> `modules/terminal/`
- `web-chat/` -> `app/web-chat/`
- 更新 Gradle settings、`.gitmodules`、Web Chat 同步脚本、Material Icons/终端工具、CI 分类和工作流触发路径、开发文档
## 不在本批范围
- `cmake/` 物理归组
- `app/build.gradle.kts` 拆分
- Gradle convention plugins 或其他构建逻辑重构
- 删除 `app/libs`、debug/kong 资源或改动子模块内容
## 步骤
1. [x] 移动六个支持模块和 Web Chat，保持 Gradle 模块 ID 与 terminal gitlink 不变
2. [x] 更新构建、子模块同步、CI 分类/触发规则和 Web Chat 资源同步路径
3. [x] 更新架构、贡献、编译和模块开发文档
4. [x] 完成 Markdown 链接、差异空白、旧物理路径和工作流触发路径静态检查
## 验收
- 根目录非隐藏目录从 14 个变为 8 个：`app`、`ci`、`cmake`、`docs`、`gradle`、`modules`、`plugins`、`tools`
- `settings.gradle.kts` 的 Gradle 模块名未改变，`modules/terminal` gitlink 与 `.gitmodules` 一致
- Web Chat 构建输出仍同步到 `app/src/main/assets/web-chat`
- 本地未执行 Gradle/Node 构建或测试，远程 PR 工作流负责行为验证

## 验证记录

- 索引树确认非隐藏根目录为 8 个；支持模块和 Web Chat 已提交并推送
- terminal gitlink 在迁移前后均为 `62dbf46a0c1af5255eb22f0c48ca215974ab1a10`
- 三个移动后 CMake 公共脚本引用均能解析到根 `cmake/operit_git_source.cmake`
- 终端同步按各分支固定提交的 `.gitmodules` 声明解析路径
- 全仓 Markdown 对比新增断链 0；Python/JSON 语法与差异空白检查通过
- [Debug Build](https://github.com/3316891527/Operit/actions/runs/43591712450) 在 `fd7337f8` 上已成功：Gradle assembleDebug、终端命令打包、外部资源打包校验和 Android 产物上传均通过；后续提交仅调整文档和本地配置模板
- 远程 PR Check 已确认 `853cdd29` 的 Fast checks 和 Android JVM tests 通过；最终检查状态见 PR checks
