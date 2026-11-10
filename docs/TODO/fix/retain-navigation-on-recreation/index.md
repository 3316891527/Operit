---
fork: https://github.com/3316891527/Operit
branch: fix/retain-navigation-on-recreation
status: completed
upstream_pr: https://github.com/AAswordman/Operit/pull/1480
upstream_commit: 4f8daf33043d72090417e52c839accc2c53f7447
---

# Activity 重建保留页面与返回栈

将导航路由及待处理分享、快捷方式、插件和通知请求保留在 Activity ViewModel。配置重建不重新消费原始 Intent；确认允许或明确拒绝后才结束导航请求，协程取消继续保留请求。保留当前目录与主题接口，适配上游 #1480。

[实施与验收](01-implementation.md) · [返回分类索引](../README.md)
