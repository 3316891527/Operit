---
repo: https://github.com/3316891527/Operit
branch: refactor/package-layout
base: 6a4b9bfb399c670321518089592ae3e02e38897e
status: analysis
---

# 包职责与命名整理

按维护者要求先分析，从 API 包开始逐包审查。当前仅新增分析文档，应用源码、包名、类名和运行行为均未修改。未运行构建或测试，未创建 PR。

- [职责、命名与上游维护关系分析](analysis.md)
- [API 117 个源文件的候选归属表](api-migration-candidates.csv)：74 项候选迁移，43 项暂保留；均未实施。

## 范围约束

保留 api 顶层名和现有根包前缀。根源包、Gradle namespace、applicationId、持久化类型、Java 桥类名与 Android 外部组件标识分别决策，不统一替换。本轮不拆方法、不调整算法或业务执行流程。

## 下一步顺序

1. [DONE] API、语音、模型提供方、记忆提取与对话编排的职责分析，以及外围包的初步核对。
2. [DONE] 建立逐文件候选路径表和已确认的外部完整名称依赖记录。
3. [PENDING] 根据维护者反馈定稿首批语音目录迁移及少量消歧义类名。
4. [PENDING] 实施首批包路径移动，更新全部绑定引用并派发适用仓库验证。
5. [PENDING] 逐批整理模型访问公共支持、记忆与对话编排、util、UI/data 和 Android 集成边界。

全项目语义审计、真实源码迁移及运行验证均未完成；不要把本表的候选归属当作已实现结果。
