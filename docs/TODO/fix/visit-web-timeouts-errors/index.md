---
fork: https://github.com/3316891527/Operit
branch: fix/visit-web-timeouts-errors
---

# visit_web 调用时限、取消与失败状态

PR：[227](https://github.com/3316891527/Operit/pull/227)

对比 Operit Ry CATMIAOZHI/Operit 的 personal/main 提交 5f2b73557fbf787cc3d4f5270b6acd4367adaf66，其 StandardWebVisitTool 已把页面生命周期从 Compose 中拆出，加入固定加载预算、脚本提取时限、整次访问时限、主页面错误处理与协程取消。本次借鉴其生命周期、并发上限和只读界面，按本项目接口适配，保留来源和 LGPL v3 许可证说明。

原实现的加载计时会随重定向重置；提取错误、取消和无效 JSON 又会构造成功网页结果。注册时的阻塞调用也妨碍外层取消关闭窗口。

最终行为：

- 访问沿用调用方协程，最多同时创建 3 个 WebView，整次调用的期限包含排队
- 加载、等待和提取由独立会话管理；重定向不重新计时，完成、失败和取消共用终态与主线程资源清理
- 时限改为逐次工具参数，删除显示设置页的网页等待选项和偏好读写；详细参数与拦截方案见 [调用参数与拦截提前结束](02-call-options-and-blocking.md)
- 主页面网络、HTTP、证书、安全浏览拒绝及渲染退出立即失败；明确 Cloudflare 挑战、拒绝响应或访问策略拦截页在识别后提前结束
- 页面首次可见后开始检查，并在加载及内容等待期间每 500 毫秒检查一次当前主文档；正文提取前再检查，覆盖 HTTP 200 的拦截页，不发送额外预检请求
- 没有响应或明确证据时按本次期限结束，不推断用户是否配置代理或是否遭到网络封锁
- JSON 标准解码保留转义文字，空页面和错误包失败；仅成功结果写缓存，保留最终 URL、链接与图片编号以及长正文存文件约定

参数与重试：

- timeout_seconds 默认 30 秒，调用方可以延长，没有固定业务上限
- load_timeout_seconds 和 read_timeout_seconds 默认跟随整次期限，也可指定更短的阶段期限
- wait_seconds 默认 0 秒，用于页面完成后的懒加载等待，可超过旧设置页的 10 秒；必须小于整次期限
- 仅网络慢造成超时时适合延长期限重试；Cloudflare 验证和明确访问拒绝使用单独错误，增加超时不会解决验证
- 不传新参数的旧调用继续有效；旧网页等待偏好不再参与执行。成功结果结构保持兼容

验证记录：

- 实际编译四个与 Android 无关的生产文件和四个项目 JVM 测试类，44 项全部通过
- 独立验证环境为 Kotlin 2.0.21、coroutines/core/test 1.7.3、serialization 1.6.2、JUnit 4.13.2；项目使用 Kotlin 2.2.21、coroutines 1.10.2、serialization 1.9.0，完整 Android 类型检查和项目构建仍由 CI 执行
- 实际页面检查 JavaScript 的 8 项 Node 回归通过，覆盖挑战结构、拒绝页、普通 Cloudflare 脚本、Turnstile 控件及文章误判防护；已接入 PR 和 Android Tests 工作流
- 最终 Kotlin 语法、YAML、仓库规范、Markdown 链接和本地化检查随提交记录在 PR
- 真机 WebView、悬浮窗、证书、HTTP 200 挑战及停止对话后的清理仍待设备验收

状态：实现和独立回归已完成 [DONE]，CI 发起和设备验收分别记录。
