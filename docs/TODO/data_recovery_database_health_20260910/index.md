---
Repository: https://github.com/AAswordman/Operit
Branch: feat/data-recovery-database-health
Status: completed
---

# 数据救援数据库健康检查

## 原有状况

已发布的数据救援界面提供原始快照导入导出和 SQL 执行器，但普通用户无法判断 Room 数据库是否完整、版本是否匹配，也无法区分能够确定处理的问题与必须人工救援的数据损坏。

PR #1006 尝试在应用启动期间统一恢复 Preferences、Room 和 ObjectBox，同时混入备份格式、业务配置修正和语音配置改造。该范围无法安全合并，现改为从当前 `dev` 提取 Room 检测与修复能力。

## 意图

- 保持现有数据救援入口和 SQL、原始快照接口兼容
- 在 SQL 执行器下方增加用户主动触发的 Room 健康检查
- 检测数据库文件、SQLite 完整性、版本和外键状态
- 仅对能够确定处理的问题提供修复操作
- 修改数据库前保全原始 DB、WAL、SHM 和 journal 文件
- 不接入应用启动流程，不修改 Preferences 或 ObjectBox 生命周期

## 作用域

- `DataRecoveryActivity` 和 `DataRecoveryViewModel`
- 新增 Room 健康检查与修复管理器
- 中文、英文和日文界面文案
- 与该功能直接相关的开发文档

## 非目标

- 启动时主动扫描或恢复数据库
- Raw Snapshot 格式升级
- Preferences、ObjectBox 或业务配置自动修正
- 自动替换无法验证的数据库内容

## 步骤

1. [Room 健康检查](./1_RoomHealthCheck.md)
2. [明确修复与原件保全](./2_RepairAndPreservation.md)
3. [兼容性与核对](./3_CompatibilityAndVerification.md)

[DONE]
