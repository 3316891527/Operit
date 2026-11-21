# 来源与合并记录

[返回计划](index.md)

按固定提交集成；只有在目标提交成为集成分支祖先后才记作已合入。

- [#222](https://github.com/3316891527/Operit/pull/222) fix(workspace): observe file changes, simplify navigation and stream imports，提交 4c45381649ab961b3aae05d6675d432bb41a0bde，通过 #224 纳入
- [#223](https://github.com/3316891527/Operit/pull/223) fix(browser): prevent recursion when opening floating browser，提交 7acc5472e17813bca420b9d88e51a2677114be3d
- [#224](https://github.com/3316891527/Operit/pull/224) fix(terminal): capture changes beyond workspace binding on #222，提交 89e6801bbf0ab6752ce7f2606f8949a0bacad7f7
- [#225](https://github.com/3316891527/Operit/pull/225) feat(theme): customize chat input placeholder，提交 edfcd77644e6f540063cc0e68757000bea89c7ca
- [#226](https://github.com/3316891527/Operit/pull/226) fix(chat): stabilize tool group expansion and remaining counts，提交 cfdf982b8dfe0875d6d65c77737f9c5aadd43743
- [#227](https://github.com/3316891527/Operit/pull/227) fix(web): expose visit timeouts and detect access blocks，提交 52090b1cf4add37913e2232dc5ab15f1bbe76b27
- [#228](https://github.com/3316891527/Operit/pull/228) fix(shell): isolate root tool commands and enforce deadlines，提交 d63e72b2f0d9b4184152b7f0960ac34c8280aa25
- [#229](https://github.com/3316891527/Operit/pull/229) fix(chat): stream JSON imports one message at a time，提交 7ea432f305934bf480a1f8eea596602941899654
- [#230](https://github.com/3316891527/Operit/pull/230) feat(markdown): extend syntax and draw Mermaid with native Canvas，提交 f668864aadaa3c375f84dbef3045c60a9fdd6dcc
- [#231](https://github.com/3316891527/Operit/pull/231) feat(tools): route platform editor through host commands，提交 2d16801f0b12f4d01af54d56936e845f1dca4000
- [#232](https://github.com/3316891527/Operit/pull/232) feat(ui): refine chat theme, voice recording and editor scrolling，提交 104aa428ee7bc95db85656dc834e1ed765d8345e
- [#233](https://github.com/3316891527/Operit/pull/233) fix(runtime): sync upstream fixes and align glass preview，提交 eb30e8d5a0576159ca0318553774bc221860af1b
- [#234](https://github.com/3316891527/Operit/pull/234) docs(ui): add interactive subagent prototype and design research，提交 13ee91266b43b653145e72642f32c68dc9f33cc7
- [#235](https://github.com/3316891527/Operit/pull/235) feat: add native subagent collaboration with persistent task trees，提交 30fae4614990d2cf0c66f0a932d8aa2b90008817

## 交叉改动

- 工作区变化观察、终端命令隔离、编辑器宿主命令与子代理执行归属
- 逐消息流式导入、完整子会话图和 Room 30 数据／迁移
- 主题脚本占位文字、聊天主题和录音操作
- Markdown／Mermaid、工具组状态与任务窗口
- 子代理原型文档与原生实现的来源记录

冲突逐处核对各来源的行为后解决，不能整文件选择一侧覆盖另一侧的功能。

全部 14 个来源 HEAD 已经通过 git merge-base --is-ancestor 检查；#222 由 #224 带入，共生成 13 个合并提交。#105 的 HEAD 不在集成历史中。

## 合并时的接线修正

- Shell 的取消作业与工具来源信息同时保留，脚本入口支持超时并沿用终端变化采集。
- 两套导入共用 OperitChatStreamReader 逐 token 读取；OperitChatArchiveReader 只处理类型转换与图归档，保留每批最多 32 条／256 Ki 字符预算、每会话事务和提交后 A2UI 清理。
- 图导入接入统一暂存入口，只有带子树的备份物化根消息，并校验 32000000 字符边界；普通大聊天继续逐条处理。补充实际流式读取、根消息和子树一起保留的交叉回归。
- 工具真实 callerChatId 保留子会话身份，workspaceChatId 统一映射根聊天。文件工具、工作区观察和终端来源使用同一归属，避免多层子代理的修改隐藏在中间子会话。
- CI 保留全部脚本回归、instrumentation 源码编译和可选同次签名 APK 对，多语言资源与 TODO 索引两侧都保留。

[DONE]
