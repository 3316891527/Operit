---
owner: https://github.com/3316891527/Operit
branch: feat/subagent-interaction-prototype
status: completed
---

# 子代理交互 HTML 原型

用户希望先体验子代理的交互，再确定正式系统的范围。本次仅制作可离线打开的前端原型，使用演示数据，不接入模型、工具或 Android 运行服务。

## 参考与边界

以本地 OperitRy 的紧凑事件卡、详情到子会话导航、任务管理与代理切换作为交互基准；结合 OpenMinis 的移动端任务详情。页面延续 Operit 默认深色 #161616 和浅色 #F7F9FF。独立工作树基于 fork dev，不夹带未合并的聊天界面实现。

- 主聊天：任务简报、自动分工、紧凑进度、完成后由主代理汇总
- 子代理详情：任务、模型、过程、报告、补充指令、停止与继续
- 任务管理：委派任务和系统后台分开，状态筛选、搜索与归档
- 演示状态：排队、运行、完成、停止、失败、中断恢复；任何操作都不执行真实任务
- 设置：深浅主题、演示推进与重置；手机使用底部详情面板，宽屏使用侧栏

OperitRy 的 send_message 只排信箱、不启动空闲代理；followup_task 才启动后续执行。普通父聊天空闲时也不会像 Orbit talk 那样自动唤醒。本原型用“让主代理汇总”的明确入口展示结果收回，避免把这些语义混在一起。

## 文件与验证计划

入口为 [HTML 页面](../../../prototypes/subagents/index.html)，使用说明与参考代码位置见[原型说明](../../../prototypes/subagents/README.md)。页面自带样式、图标和脚本，无 CDN 或 API 请求。

脚本语法、CSS 解析与 23 项 DOM 交互检查通过。Chromium 在容器中创建页面前退出，真实布局、焦点、触摸与原生下载尚未验收，详见[验证记录](../../../prototypes/subagents/verification.md)。

[实施记录](01-prototype.md)和[源码研究](../../../prototypes/subagents/research.md)随页面交付。正式子代理后端、权限模型、数据库和原生 UI 不属于本次原型交付。

[DONE]
