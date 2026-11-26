---
repository: https://github.com/3316891527/Operit
branch: fix/quickjs-job-drain-and-cleanup
status: implementation-complete-awaiting-validation
---

# QuickJS 待处理任务续排与临时引擎清理

## 背景与目标

长 Promise 链在一次原生执行的 128 项预算耗尽后可能停住，等待下一个计时事件。调试接收器另有临时 JsEngine 创建后未销毁的问题，会保留计时源、运行线程和原生对象。

本次修复运行时的任务推进与临时执行资源归属。工具参数、脚本公开接口、聊天数据和登录配置均不变。当前设备已看到额外 QuickJS 线程高 CPU，但没有将具体线程与某个历史调试会话一一关联，不能据此宣称解决了所有 UI 卡顿来源。

## 实施范围

1. [待处理任务按预算续排](01-pending-jobs.md)：保留每批 128 项，在预算耗尽时向运行队列追加唯一后续批次，队列空时不再调度；关闭后不再访问原生运行时
2. [临时调试执行清理](02-execution-cleanup.md)：全部准备、执行及结果落盘出口统一进入 finally，销毁临时引擎并清理临时文件

## 验证与交付

新增 QuickJS 模块 JVM 回归，覆盖长队列、批次公平性、续排合并、空队列、关闭与调度异常。新增 Android 原生回归，直接检查长 await／then 链、计时回调、拒绝处理，以及关闭后线程退出；调试接收器另覆盖准备失败和结果写入失败等清理出口。

Android Tests 和 PR 的 JVM 任务加入 `:quickjs:testDebugUnitTest`，设备回归由现有仪器测试编译任务纳入。提交后对同一分支发起 `assembleDebug` 和 Android Tests，登记执行链接；设备测试 APK 编译不等于设备测试已经执行。

已完成实现和源码审查，新增 20 项回归源码：模块 JVM 8 项、真实 JNI 调度 7 项、接收器清理 5 项。调度回归还覆盖关闭调用方已中断、原生桥回调内部发起关闭的销毁时序。

本地静态检查通过：12 份变更文件严格 UTF-8、7 份 Kotlin／Gradle Kotlin 文件原始语法解析、2 份工作流 YAML 解析、文档相对链接和差异空白检查。静态检查不证明 Kotlin 类型正确或测试通过。本地未执行 Gradle 编译、构建和测试，12 项 Android 回归尚未在设备执行。

实现完成后推送工作分支并提交目标为 dev 的 PR；远端 Debug 构建、Android Tests 的运行链接与结果按实际状态登记。完成启动登记后停止进度查询，构建成功、仪器测试 APK 生成和设备用例执行分别记录。