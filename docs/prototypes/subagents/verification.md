# 原型验证记录

本轮在 Ubuntu ARM64 容器、Node.js `24.22.2` 下完成脚本语法、CSS 解析与 DOM 交互检查。DOM 模拟器使用 jsdom `31.0.1`，CSS 解析器使用 css-tree `3.2.1`。

## 可复现的检查

页面本身无依赖。仅运行开发检查时，在仓库根目录准备临时依赖：

```sh
prototype_check_dir=$(mktemp -d)
npm install --prefix "$prototype_check_dir" --no-package-lock --no-audit --no-fund jsdom@31.0.1 css-tree@3.2.1
NODE_PATH="$prototype_check_dir/node_modules" node docs/prototypes/subagents/verify.cjs
```

[检查脚本](verify.cjs) 直接加载交付的 [HTML 页面](index.html)，通过 DOM 点击、表单和输入事件操作页面，不读取内部应用状态。

实际检查共 23 项，通过 23 项：

- 初始化、DOM ID 唯一性、CSS 解析、内嵌资源及网络策略声明
- 分派前的补充、两项运行与排队、演示计时推进、草稿保留
- 补充入队与下一轮读取、特殊字符按文本显示
- 单项停止、名额释放、停止后的新执行与排队
- 单项失败、应用中断、保留未读补充与沿用历史恢复
- 报告送回和汇总的区别、后续执行保留旧报告快照
- Markdown 内容生成，包括旧执行、新执行与演示标记
- 管理页搜索、筛选、归档、恢复与系统后台分组
- 角色编辑、模型配置快照、新增任务和重置边界
- 手机宽度条件下的面板、导航与 inert 属性状态
- 组合输入期间保留输入节点、提交后更新搜索结果
- 整段 DOM 交互没有脚本异常

## 验证边界

jsdom 不渲染页面。检查脚本为计时、媒体查询、dialog、滚动和下载接口提供测试桩，验证页面逻辑和生成内容；它不证明原生弹窗、焦点约束、像素布局、真实触摸或浏览器下载已经验收。

本轮安装了 Playwright `1.65.1` 与 Chromium Headless Shell `159.0.8154.6`，尝试执行手机和宽屏点击验证。浏览器在创建页面前关闭，直接启动时收到 `SIGTRAP`；容器还输出 GPU 子进程及系统服务相关错误。尚未定位到可确认的底层原因，也未获得有效页面截图。

因此，手机和宽屏的真实布局、软键盘与安全区、焦点返回、拖动关闭、真实下载，以及 Safari 和 Android WebView 兼容性仍待浏览器验收。没有把启动失败报告成通过，没有将 DOM 模拟结果称作设备测试。

## 仓库检查

提交后使用仓库现有的 hygiene、localization 和 Markdown 链接检查比对本分支与基线。最终结果随 PR 交付记录提供。构建和 Android Tests 按既有协作约定派发，启动确认不代表最终通过。
