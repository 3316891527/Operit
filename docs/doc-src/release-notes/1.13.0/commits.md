# 1.13.0 完整提交覆盖

[返回更新日志](index.md) · [来源索引](sources.md) · [补丁时间线](patches.md)

本页逐条保留固定范围的 488 个唯一提交，按首次在所列补丁源码快照中可达的阶段分组，并在组内采用拓扑顺序。组别用于覆盖核对，不表示该功能一定首次在该版本公开；同一功能的多条修订已在正文归并。

有来源链接表示历史或整合归属，不表示 PR 标题穷尽该提交行为。标为“直接提交”的记录表示未在所用 PR 覆盖集合中归属，不等于证明整个开发过程从未创建 PR。单项 PR 未合并而经压缩或集成带入的情况另见来源索引。

唯一提交数量：488；合并提交：130；非合并提交：358。

<details><summary>v1.12.2+2 源码截止 · 276 条提交</summary>

- 001 [14c8afdc3bfb](https://github.com/3316891527/Operit/commit/14c8afdc3bfb3bb22caa824d164bb49b57d7bff1) · 2026-09-15 · 功能／修复／重构 · feat(draw): modernize SpaceXAI and prompt inputs · [上游 #1233](https://github.com/AAswordman/Operit/pull/1233)
- 002 [ef1bf4b6c307](https://github.com/3316891527/Operit/commit/ef1bf4b6c307f9d64e16912fc8ac256d14d57f15) · 2026-09-20 · 功能／修复／重构 · feat(draw): add image-to-image to bundled image packages · [上游 #1233](https://github.com/AAswordman/Operit/pull/1233)
- 003 [29d678120994](https://github.com/3316891527/Operit/commit/29d678120994ab535ddb80bda171a4fa77b4f1bc) · 2026-09-20 · 功能／修复／重构 · fix(draw): upload OpenAI URL references as multipart · [上游 #1233](https://github.com/AAswordman/Operit/pull/1233)
- 004 [08b34ad93162](https://github.com/3316891527/Operit/commit/08b34ad93162711af58f153865018f089ea0ab84) · 2026-09-28 · 检查／构建 · 修复 DeepSeek/XAI 单元测试工作流失败 (#1282) · [上游 #1282](https://github.com/AAswordman/Operit/pull/1282)
- 005 [a7676fa356dd](https://github.com/3316891527/Operit/commit/a7676fa356dd7a0562080b165733307bf51710cb) · 2026-10-01 · 功能／修复／重构 · feat(provider): add Antigravity OAuth login and fingerprint spoofing (#1257) · [上游 #1257](https://github.com/AAswordman/Operit/pull/1257)
- 006 [351d356248e6](https://github.com/3316891527/Operit/commit/351d356248e646dfef387aa14d0a7daf0dac6eed) · 2026-10-05 · 功能／修复／重构 · fix(waifu): preserve markdown list streams for issue 1280 · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 007 [770821f05aac](https://github.com/3316891527/Operit/commit/770821f05aac2b455e88ac9ddd9868a1e1d759cc) · 2026-10-05 · 功能／修复／重构 · fix(waifu): render registered plugin XML tags · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 008 [e4697098b0b2](https://github.com/3316891527/Operit/commit/e4697098b0b21376c710e985abcb8773ca8b71b9) · 2026-10-05 · 功能／修复／重构 · fix(waifu): keep registered emotion tags · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 009 [8e2ea297c799](https://github.com/3316891527/Operit/commit/8e2ea297c7991baba5e2f95590bd0faba1d5ce65) · 2026-10-05 · 功能／修复／重构 · fix(waifu): separate display cleanup from speech output · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 010 [a11eed11beef](https://github.com/3316891527/Operit/commit/a11eed11beef9d6cad6b1a6de37438773895e325) · 2026-10-14 · 功能／修复／重构 · fix(waifu): 转义 Android ICU 正则中的字面右花括号 · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 011 [f1bbcd80b927](https://github.com/3316891527/Operit/commit/f1bbcd80b927bdbda50d07483322a805b1bfe432) · 2026-10-06 · 功能／修复／重构 · feat(docs): 完善 ToolPkg 开发者文档并更新 SandboxPackage_DEV · [上游 #1330](https://github.com/AAswordman/Operit/pull/1330)
- 012 [aa779dac7057](https://github.com/3316891527/Operit/commit/aa779dac7057f3427d2605047138f939b3d40ed2) · 2026-10-13 · 功能／修复／重构 · feat(chat): 折叠消息生成详情并统一统计展示 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 013 [211ac40e729f](https://github.com/3316891527/Operit/commit/211ac40e729fa738933809ae6f65721ac3244c67) · 2026-10-13 · 功能／修复／重构 · feat(ui): 精简侧边栏并重组设置与工具入口 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 014 [01bb5b92b999](https://github.com/3316891527/Operit/commit/01bb5b92b999ba7d3e2d606eebd4e603a805f3ca) · 2026-10-13 · 功能／修复／重构 · feat(chat): 将嵌套对话目录与角色入口整合到侧边栏 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 015 [c793a3ae27b3](https://github.com/3316891527/Operit/commit/c793a3ae27b3ed87f5d7cd5890e839aa96ad205b) · 2026-10-13 · 功能／修复／重构 · feat(chat): 合并工作区与终端并增加统一主页 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 016 [36323033894e](https://github.com/3316891527/Operit/commit/36323033894eb7d05ef9de32f483069c30f48fd3) · 2026-10-13 · 功能／修复／重构 · fix(ui): 修正系统栏图标与透明工具栏文字色的反向判断 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 017 [6c6447a79a6f](https://github.com/3316891527/Operit/commit/6c6447a79a6faa4254dcb87be6d491a10a9b9350) · 2026-10-13 · 功能／修复／重构 · feat(ui): 重构透明工具栏并统一默认主题与侧栏玻璃 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 018 [d96f070aa6d6](https://github.com/3316891527/Operit/commit/d96f070aa6d64267ccfd3028558e8066ec570b25) · 2026-10-13 · 功能／修复／重构 · feat(character-card): 重构角色编辑分页与绑定资料入口 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 019 [7fe98fc52e86](https://github.com/3316891527/Operit/commit/7fe98fc52e862674266bb4e0bcc98f963758e0aa) · 2026-10-13 · 功能／修复／重构 · feat(memory): 统一向量模型绑定与记忆设置分页 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 020 [080951c85398](https://github.com/3316891527/Operit/commit/080951c85398ae58495516000c52987153b7f2aa) · 2026-10-13 · 功能／修复／重构 · feat(chat): 为 Agent 输入栏增加供应商标识 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 021 [ce64910f4210](https://github.com/3316891527/Operit/commit/ce64910f4210492b0b478a263efc85bb389a3801) · 2026-10-13 · 功能／修复／重构 · feat(packages): 重构包管理与插件获取配置界面 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 022 [8f317107dc07](https://github.com/3316891527/Operit/commit/8f317107dc073ba64c6309f236920f30bf192caa) · 2026-10-13 · 功能／修复／重构 · feat(workflow): 重构工作流界面并支持复制与 JSON 导入导出 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 023 [01d006e678e6](https://github.com/3316891527/Operit/commit/01d006e678e6261e6810f7129c2c1ca4a3e0ed12) · 2026-10-13 · 功能／修复／重构 · feat(settings): 将全局用户名纳入主题统一保存 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 024 [1faffb73454a](https://github.com/3316891527/Operit/commit/1faffb73454ade8f7c12b2ab82c2288d4e5d47c2) · 2026-10-13 · 文档／语言 · chore(i18n): 补齐本轮界面文案与本地化条目 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 025 [72df213a22e4](https://github.com/3316891527/Operit/commit/72df213a22e419a380d60fbdb1b7176707b0264e) · 2026-10-13 · 功能／修复／重构 · feat(chat): 将上下文统计移入输入设置并增加累计用量 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 026 [67eef68e1788](https://github.com/3316891527/Operit/commit/67eef68e17884dfe4e1c16d3fa40aa2fc515f5d7) · 2026-10-13 · 功能／修复／重构 · fix(ui): 修复审查指出的升级提示、转场与导航问题 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 027 [ba114e73890e](https://github.com/3316891527/Operit/commit/ba114e73890e2948b71481c39e798c37a1ee8711) · 2026-10-13 · 功能／修复／重构 · fix(chat): 修正上下文入口标题的主题文字色 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 028 [075c64355a8f](https://github.com/3316891527/Operit/commit/075c64355a8f77bd01234789596659a37d945e92) · 2026-10-15 · 功能／修复／重构 · feat(ui): 同步实机反馈修复与玻璃外观配置 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 029 [66d0c77dfb72](https://github.com/3316891527/Operit/commit/66d0c77dfb7202327d1938251c094ee68dab4bba) · 2026-10-15 · 功能／修复／重构 · fix(ui): 让设置分组与默认插件图标跟随定制主色 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 030 [90c03f82cb34](https://github.com/3316891527/Operit/commit/90c03f82cb34bd48dd227b6f73151d7ce6eb5389) · 2026-10-15 · 功能／修复／重构 · fix(ui): 补齐外观翻译并恢复聊天玻璃效果 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 031 [2aa3cb3202c2](https://github.com/3316891527/Operit/commit/2aa3cb3202c24fc34c6d49a6af9828cf1813dc90) · 2026-10-15 · 合并／集成 · chore(integration): 合入聊天修复并保留 Waifu 渲染与共存构建 · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 032 [287fa9c669ab](https://github.com/3316891527/Operit/commit/287fa9c669abf6087ea650af96548fe7a9f06c5e) · 2026-10-15 · 功能／修复／重构 · fix(i18n): 清理已删除设置的孤立日语翻译 · [上游 #1372](https://github.com/AAswordman/Operit/pull/1372)
- 033 [39a0abaaf234](https://github.com/3316891527/Operit/commit/39a0abaaf23446e5122f7951b5350f4f40b9d52d) · 2026-10-15 · 合并／集成 · chore(integration): 同步孤立日语翻译清理 · [上游 #1338](https://github.com/AAswordman/Operit/pull/1338)
- 034 [07c91aa3837c](https://github.com/3316891527/Operit/commit/07c91aa3837c04924fdead4c633a22757b0cc859) · 2026-10-15 · 文档／语言 · docs: 记录独立发行集成基点与52项PR来源 · 直接提交
- 035 [757cceecf94c](https://github.com/3316891527/Operit/commit/757cceecf94cbab4ff1248956253b4a79931a5d8) · 2026-10-14 · 功能／修复／重构 · feat(backup): manage snapshot theme resources and logs · [上游 #1104](https://github.com/AAswordman/Operit/pull/1104)
- 036 [2679206a020a](https://github.com/3316891527/Operit/commit/2679206a020a6a0e9b252442ff361f7a7a5695b9) · 2026-10-14 · 功能／修复／重构 · feat(backup): restore snapshot media references on import · [上游 #1104](https://github.com/AAswordman/Operit/pull/1104)
- 037 [dd8365848fed](https://github.com/3316891527/Operit/commit/dd8365848fed2601d5cbe14d3e3ed424fe32ebdd) · 2026-10-14 · 功能／修复／重构 · fix(backup): restore ZIP reader import and clean build warnings · [上游 #1104](https://github.com/AAswordman/Operit/pull/1104)
- 038 [67f5bfa02167](https://github.com/3316891527/Operit/commit/67f5bfa02167d61e53220db75c8d54cff85fe0e1) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1104 并协调功能冲突 · 直接提交
- 039 [9efa89d9b66d](https://github.com/3316891527/Operit/commit/9efa89d9b66db2d72400a1c85ed01474ac055049) · 2026-09-05 · 功能／修复／重构 · feat(tokenstats): add monthly/yearly activity views and re-anchor ranges to today · [上游 #1110](https://github.com/AAswordman/Operit/pull/1110)
- 040 [7b9d594278a2](https://github.com/3316891527/Operit/commit/7b9d594278a2337b971cab2c555ff886cfe10870) · 2026-09-05 · 功能／修复／重构 · feat(tokenstats): granular bar views per mode and standalone activity calendar · [上游 #1110](https://github.com/AAswordman/Operit/pull/1110)
- 041 [f941a73ff263](https://github.com/3316891527/Operit/commit/f941a73ff26345598a8f52e58beaa56657f01ea2) · 2026-09-05 · 功能／修复／重构 · fix(tokenstats): widen dense month labels and align monthly window with weekly · [上游 #1110](https://github.com/AAswordman/Operit/pull/1110)
- 042 [6af59142e86a](https://github.com/3316891527/Operit/commit/6af59142e86ad716a52b3b8b84bb9f56940f305c) · 2026-09-05 · 功能／修复／重构 · fix(tokenstats): align yearly window with weekly and monthly rolling semantics · [上游 #1110](https://github.com/AAswordman/Operit/pull/1110)
- 043 [6db9aa252336](https://github.com/3316891527/Operit/commit/6db9aa2523365f818bdcb6d50255504da11946b4) · 2026-09-05 · 功能／修复／重构 · fix(tokenstats): widen sparse weekly and monthly bars · [上游 #1110](https://github.com/AAswordman/Operit/pull/1110)
- 044 [5f8b5897e026](https://github.com/3316891527/Operit/commit/5f8b5897e026f43d2afca00f6bcaf5b927977bcd) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1110 · 直接提交
- 045 [a3b8ad42ee34](https://github.com/3316891527/Operit/commit/a3b8ad42ee34bf541a081a69f686603bc2a42273) · 2026-09-05 · 功能／修复／重构 · fix(shell): reject only dangerous command words, not argument substrings · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 046 [20e6a62d5490](https://github.com/3316891527/Operit/commit/20e6a62d5490e764671d10816275fb550747b389) · 2026-09-05 · 功能／修复／重构 · fix(tests): repair dev-broken provider tests so unit test workflow can compile · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 047 [62d9306316f6](https://github.com/3316891527/Operit/commit/62d9306316f62cf01f1ae35027aef59addb780a1) · 2026-09-05 · 功能／修复／重构 · fix(shell): treat rm force/recursive flags case-insensitively · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 048 [3451303620bf](https://github.com/3316891527/Operit/commit/3451303620bf851d7102167ce499e18adc9a2618) · 2026-09-25 · 功能／修复／重构 · fix(shell): close continuation and wrapper bypasses · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 049 [ffb813539ed1](https://github.com/3316891527/Operit/commit/ffb813539ed1bdad9ea04879d6d6985284cf1b4f) · 2026-09-25 · 功能／修复／重构 · fix(shell): check adb find and xargs execution · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 050 [ea8a1df64186](https://github.com/3316891527/Operit/commit/ea8a1df641864ea8aa7938bf97bf9a465172ab9d) · 2026-09-15 · 检查／构建 · ci: skip obsolete Android SDK tools package · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 051 [f430903ad3bf](https://github.com/3316891527/Operit/commit/f430903ad3bf435634c7c27a702b5f42c4561229) · 2026-09-25 · 功能／修复／重构 · fix(shell): read adb shell token by index · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 052 [3c5c76e83b6c](https://github.com/3316891527/Operit/commit/3c5c76e83b6c0790196a9789abd73ef649417601) · 2026-09-26 · 功能／修复／重构 · fix(shell): cover adb exec-out and find approvals · [上游 #1111](https://github.com/AAswordman/Operit/pull/1111)
- 053 [c89476348f94](https://github.com/3316891527/Operit/commit/c89476348f94f058023d4a039608ae91f98aeca5) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1111 · 直接提交
- 054 [dd508f1301da](https://github.com/3316891527/Operit/commit/dd508f1301dada5e09cd2d7c03ef8ee06dc0d794) · 2026-09-05 · 功能／修复／重构 · feat(deepseek): upload images via Files API for file_id references · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 055 [d01554fc3ea0](https://github.com/3316891527/Operit/commit/d01554fc3ea01ce8aac8f97d935ea36ebb22e2da) · 2026-09-05 · 功能／修复／重构 · feat(deepseek): share Files API uploader across chat completions &amp; responses · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 056 [f3c916df1530](https://github.com/3316891527/Operit/commit/f3c916df15301c7f8a9358430583c62c1ea36fd6) · 2026-09-05 · 功能／修复／重构 · feat(deepseek): support list/retrieve/delete Files API endpoints with content-addressed dedup · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 057 [fe7714cf2667](https://github.com/3316891527/Operit/commit/fe7714cf266735ab8b8c40579a58974b0fc8ec1f) · 2026-09-05 · 功能／修复／重构 · fix(deepseek): mark applyAuthAndCustomHeaders as suspend (getApiKey is suspend) · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 058 [86773d0ea45f](https://github.com/3316891527/Operit/commit/86773d0ea45f23e12c43521254fac0ee2b25b7ac) · 2026-09-05 · 功能／修复／重构 · fix(ui): move deepseek files hint under image toggle; prompt when attaching with no active chat · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 059 [c5d7665f940c](https://github.com/3316891527/Operit/commit/c5d7665f940cda152248ac6fdd6e04ac82f3191f) · 2026-09-05 · 功能／修复／重构 · fix(chat): show no-active-chat prompt for all attachment entry points · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 060 [62b787398764](https://github.com/3316891527/Operit/commit/62b78739876408fab595af35a55674aa1fce1421) · 2026-09-05 · 功能／修复／重构 · fix(chat): use native toast for no-active-chat prompt, matching send path · [上游 #1113](https://github.com/AAswordman/Operit/pull/1113)
- 061 [3516aa1dfae3](https://github.com/3316891527/Operit/commit/3516aa1dfae3dc6969ba6ade14e5dd4d609f16c5) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1113 · 直接提交
- 062 [aa8fb9ae6744](https://github.com/3316891527/Operit/commit/aa8fb9ae67446617fa4ce3ab05bfaddae73aa005) · 2026-09-06 · 功能／修复／重构 · feat(settings): use toasts with detailed reason for model list fetching · [上游 #1121](https://github.com/AAswordman/Operit/pull/1121)
- 063 [488f7252c909](https://github.com/3316891527/Operit/commit/488f7252c9093994639f3653bec8fe177a084931) · 2026-09-06 · 功能／修复／重构 · fix(settings): optimize model list fetching timeout, add cancellation on config switch and precise DNS error feedback · [上游 #1121](https://github.com/AAswordman/Operit/pull/1121)
- 064 [56b9423dce1b](https://github.com/3316891527/Operit/commit/56b9423dce1bf5b359f14d8e1bf77c844bcccf5e) · 2026-09-25 · 功能／修复／重构 · fix(settings): restore model list cancellation imports · [上游 #1121](https://github.com/AAswordman/Operit/pull/1121)
- 065 [02588055b7bf](https://github.com/3316891527/Operit/commit/02588055b7bf2c463f4b30a4e276c5dc989dc062) · 2026-10-01 · 合并／集成 · Merge dev into model-list-error-feedback · [上游 #1121](https://github.com/AAswordman/Operit/pull/1121)
- 066 [dfbb98ee9446](https://github.com/3316891527/Operit/commit/dfbb98ee9446ee4234dd2c05ae425d1b390e12b0) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1121 并协调功能冲突 · 直接提交
- 067 [f0bffa78e3f2](https://github.com/3316891527/Operit/commit/f0bffa78e3f280cc3c3b170ab4d8897acc025c48) · 2026-09-06 · 功能／修复／重构 · fix(tools): preserve string params in MCP and optional types in JS, optimize input IPC(#1125) · [上游 #1126](https://github.com/AAswordman/Operit/pull/1126)
- 068 [fd2cced602a2](https://github.com/3316891527/Operit/commit/fd2cced602a2c87986072f9b7c4cf00b49a97cb0) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1126 · 直接提交
- 069 [76a6e2a19745](https://github.com/3316891527/Operit/commit/76a6e2a197454c7752f6538d0f225f8d12bc6475) · 2026-09-07 · 功能／修复／重构 · feat(logcat): add export filters for debug, system, error context, and sensitive content · [上游 #1130](https://github.com/AAswordman/Operit/pull/1130)
- 070 [7a42aed7fb0a](https://github.com/3316891527/Operit/commit/7a42aed7fb0a6c6129d665a9c0af89342c884691) · 2026-09-07 · 功能／修复／重构 · fix(logcat): align MediaStore export string resource name · [上游 #1130](https://github.com/AAswordman/Operit/pull/1130)
- 071 [59129ab26310](https://github.com/3316891527/Operit/commit/59129ab263100262e0bf890141a1a6199b6ab0d2) · 2026-09-07 · 功能／修复／重构 · fix(logcat): keep LogExportFilterTest string literals valid · [上游 #1130](https://github.com/AAswordman/Operit/pull/1130)
- 072 [a8c42a190dfd](https://github.com/3316891527/Operit/commit/a8c42a190dfd509f88c5f724252bc8fdee6471af) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1130 · 直接提交
- 073 [85c21e6f1cb9](https://github.com/3316891527/Operit/commit/85c21e6f1cb99a38fd3b6639715a7933b1d687eb) · 2026-09-07 · 功能／修复／重构 · fix(extended_chat): report real chat errors instead of fake timeouts · [上游 #1134](https://github.com/AAswordman/Operit/pull/1134)
- 074 [5ff8c9ea1e28](https://github.com/3316891527/Operit/commit/5ff8c9ea1e28f2a875911d7c9a7e9accb459307e) · 2026-09-07 · 功能／修复／重构 · fix(extended_chat): recover chat lookup by id when findChat misses · [上游 #1134](https://github.com/AAswordman/Operit/pull/1134)
- 075 [915573f81b6e](https://github.com/3316891527/Operit/commit/915573f81b6eec1815414bc18f04129e1df20624) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1134 · 直接提交
- 076 [4285f1af4533](https://github.com/3316891527/Operit/commit/4285f1af45339ffcf67e2ae5c56294a551a05648) · 2026-09-08 · 功能／修复／重构 · fix(qqbot): persist listener state before service stop and allow IPC timeout · [上游 #1142](https://github.com/AAswordman/Operit/pull/1142)
- 077 [a3eb31a3de5e](https://github.com/3316891527/Operit/commit/a3eb31a3de5e1d22d4f9e6ef797084581dbbd61d) · 2026-09-25 · 功能／修复／重构 · fix(qqbot): validate service identity before stopping pid · [上游 #1142](https://github.com/AAswordman/Operit/pull/1142)
- 078 [c89acfb73245](https://github.com/3316891527/Operit/commit/c89acfb73245fcba2218f690cc7eb8f8b53f644d) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1142 · 直接提交
- 079 [51c84e5b5975](https://github.com/3316891527/Operit/commit/51c84e5b5975133a91848c2f396c7f877b4f4505) · 2026-09-08 · 功能／修复／重构 · feat(summary): allow thinking depth on summary function models · [上游 #1152](https://github.com/AAswordman/Operit/pull/1152)
- 080 [58a8aa22360c](https://github.com/3316891527/Operit/commit/58a8aa22360ccb21cc5e43b87e7f17381d46c4c3) · 2026-09-08 · 功能／修复／重构 · fix(summary): compile thinking request visibility and Gemini test · [上游 #1152](https://github.com/AAswordman/Operit/pull/1152)
- 081 [fd44b72fe35c](https://github.com/3316891527/Operit/commit/fd44b72fe35cb496dac535ecc9272c95b34babbc) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1152 · 直接提交
- 082 [a8040fcaec57](https://github.com/3316891527/Operit/commit/a8040fcaec57e6555a87af312ab9a40206cf5993) · 2026-09-09 · 功能／修复／重构 · feat(settings): apply current character theme to all character cards · [上游 #1156](https://github.com/AAswordman/Operit/pull/1156)
- 083 [1ce6db44594b](https://github.com/3316891527/Operit/commit/1ce6db44594b2f20ec93e2e481e3d574b3bb2f44) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1156 · 直接提交
- 084 [628f1b53fb27](https://github.com/3316891527/Operit/commit/628f1b53fb27ffa64b5025af8b9a909c08636a16) · 2026-09-09 · 功能／修复／重构 · fix(chat): move long-history work off main thread · [上游 #1157](https://github.com/AAswordman/Operit/pull/1157)
- 085 [d0fdd163cbd6](https://github.com/3316891527/Operit/commit/d0fdd163cbd6cf7b5fa5f25c7c1a425f99e6ecf2) · 2026-09-26 · 功能／修复／重构 · fix(chat): serialize window refresh and handle async send errors · [上游 #1157](https://github.com/AAswordman/Operit/pull/1157)
- 086 [a99f215e8eee](https://github.com/3316891527/Operit/commit/a99f215e8eeee0e5e96d83628936be515e670713) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1157 · 直接提交
- 087 [67e992a3e516](https://github.com/3316891527/Operit/commit/67e992a3e51660dfbbeaf31ba0b3e3bdfb070249) · 2026-09-11 · 功能／修复／重构 · fix(ui): allow markdown tables to expand naturally and scroll horizontally · [上游 #1168](https://github.com/AAswordman/Operit/pull/1168)
- 088 [6c45b2433a8a](https://github.com/3316891527/Operit/commit/6c45b2433a8ae8520626ea10ebddb7e45817e64a) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1168 · 直接提交
- 089 [faf330100000](https://github.com/3316891527/Operit/commit/faf330100000174c76da70eb8189c2ea6aafa53d) · 2026-09-13 · 功能／修复／重构 · fix(shell): prevent Shizuku process pipe deadlocks · [上游 #1178](https://github.com/AAswordman/Operit/pull/1178)
- 090 [d47cb7c9d79d](https://github.com/3316891527/Operit/commit/d47cb7c9d79dfb8567be3124e27d19115c3e2b07) · 2026-09-13 · 功能／修复／重构 · fix(shell): use coroutine context for reader cancellation · [上游 #1178](https://github.com/AAswordman/Operit/pull/1178)
- 091 [8e19f64b7531](https://github.com/3316891527/Operit/commit/8e19f64b7531a5fbbdf6b60ef4df5fffb88144ab) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1178 · 直接提交
- 092 [d6ff5d5bed30](https://github.com/3316891527/Operit/commit/d6ff5d5bed307f22cf1fae1ded28e39c78da5aff) · 2026-09-15 · 功能／修复／重构 · feat: organize and reorder model configurations · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 093 [23f45fda9145](https://github.com/3316891527/Operit/commit/23f45fda91450618b0004f41f2d809cb2fe4dd1a) · 2026-09-15 · 功能／修复／重构 · fix: stabilize grouped model configuration behavior · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 094 [15d482620189](https://github.com/3316891527/Operit/commit/15d48262018929d2f408ef33f4df7f2e94918b47) · 2026-09-15 · 功能／修复／重构 · feat: localize and filter model selectors by group · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 095 [6527548b7d84](https://github.com/3316891527/Operit/commit/6527548b7d842f156c6ca0dc5665dffb78df8bc2) · 2026-09-25 · 功能／修复／重构 · fix(config): preserve groups across config import · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 096 [6219e7daabc1](https://github.com/3316891527/Operit/commit/6219e7daabc1c9e17f74f555b1525eeb13037c0e) · 2026-09-15 · 检查／构建 · ci: skip obsolete Android SDK tools package · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 097 [ef38858014d9](https://github.com/3316891527/Operit/commit/ef38858014d917438437c102f1521cad12f70bb4) · 2026-09-25 · 功能／修复／重构 · fix(config): confirm group deletion and normalize moves · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 098 [0234da6f1dc3](https://github.com/3316891527/Operit/commit/0234da6f1dc30344e6bf498fc0fd4e9d6c08e3ba) · 2026-10-01 · 合并／集成 · Merge #1189 model config groups and ordering · [上游 #1189](https://github.com/AAswordman/Operit/pull/1189)
- 099 [78d63198e9c5](https://github.com/3316891527/Operit/commit/78d63198e9c5c9d015e2f220ede5e182f8269d6b) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1189 并协调功能冲突 · 直接提交
- 100 [91e83aa0b53d](https://github.com/3316891527/Operit/commit/91e83aa0b53db34c86b9adc7e131d3bad0be958a) · 2026-09-15 · 功能／修复／重构 · feat: add data and storage management · [上游 #1193](https://github.com/AAswordman/Operit/pull/1193)
- 101 [e3b9a6f8c690](https://github.com/3316891527/Operit/commit/e3b9a6f8c6902c544aabd9d1fff505580cb7e3c0) · 2026-09-15 · 功能／修复／重构 · feat: reshape Operit storage overview · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 102 [fe979335088f](https://github.com/3316891527/Operit/commit/fe979335088f6f07076412b021ccc6ac18279cbd) · 2026-09-16 · 功能／修复／重构 · feat: add Linux and local model storage pages · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 103 [6724fa99ea9b](https://github.com/3316891527/Operit/commit/6724fa99ea9bbad4ecdaec474c0cc81fef57efc1) · 2026-09-16 · 功能／修复／重构 · feat: manage storage categories in-page and move cookie cleanup · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 104 [b15eaa52fb2e](https://github.com/3316891527/Operit/commit/b15eaa52fb2eda87c3cd1c6aaac38830bf3a1f7b) · 2026-09-16 · 功能／修复／重构 · fix: stop exposing internal Linux storage cleanup types · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 105 [7a0c2eb738bb](https://github.com/3316891527/Operit/commit/7a0c2eb738bb33b83cbd71add63728ca505435b0) · 2026-09-16 · 功能／修复／重构 · feat: restyle storage pages with logos, avatars, and delete progress · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 106 [2b028c815a77](https://github.com/3316891527/Operit/commit/2b028c815a77b461ba619999a9f22824c6d646bd) · 2026-09-16 · 功能／修复／重构 · feat: expand memory and extension storage groups for deletion · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 107 [aa75446acbde](https://github.com/3316891527/Operit/commit/aa75446acbde86b19f74b3ecd355fb318a696424) · 2026-09-16 · 功能／修复／重构 · feat: polish storage empty states, avatars, and action bar · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 108 [bc12842bd2bf](https://github.com/3316891527/Operit/commit/bc12842bd2bf45f0fc4866fad5a5b0cfc0202c38) · 2026-09-16 · 功能／修复／重构 · fix: tighten storage delete guards and usage accounting · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 109 [04435e63e16b](https://github.com/3316891527/Operit/commit/04435e63e16b1ad091dd7d449d53898af6230300) · 2026-09-25 · 功能／修复／重构 · fix(storage): exclude terminal runtime from package deletion · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 110 [418b92d1494b](https://github.com/3316891527/Operit/commit/418b92d1494b0ec70d62df0b56ae6f653b46c570) · 2026-09-25 · 功能／修复／重构 · fix(storage): guard deletion links and media warnings · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 111 [8453aa575b0d](https://github.com/3316891527/Operit/commit/8453aa575b0d4edd2c9525f3de4a43c00e7a25b5) · 2026-09-25 · 功能／修复／重构 · fix(storage): keep media warning check in ui module · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 112 [be2fc7c8a59a](https://github.com/3316891527/Operit/commit/be2fc7c8a59af9ff2e86bcc9bb2cc52d98702b48) · 2026-10-01 · 合并／集成 · merge: resolve string resource conflicts for PR #1205 · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 113 [d4c8a9368b1a](https://github.com/3316891527/Operit/commit/d4c8a9368b1af0dd05dc0651236630587d35cc7b) · 2026-10-01 · 检查／构建 · test: assert local model acquire succeeds · [上游 #1205](https://github.com/AAswordman/Operit/pull/1205)
- 114 [ff6f57b61ac8](https://github.com/3316891527/Operit/commit/ff6f57b61ac8bff6197c284e8f7d55c86e623f57) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1205 并协调功能冲突 · 直接提交
- 115 [ffbed6870fb7](https://github.com/3316891527/Operit/commit/ffbed6870fb7a71d100bd26ff01c262a16bdc67b) · 2026-09-17 · 功能／修复／重构 · feat: fail over API key pool on quota errors with sticky scored routing · [上游 #1211](https://github.com/AAswordman/Operit/pull/1211)
- 116 [8de92874cd7b](https://github.com/3316891527/Operit/commit/8de92874cd7bb284d964c2932b33475343818680) · 2026-09-25 · 功能／修复／重构 · fix(keypool): preserve single-key retry budget · [上游 #1211](https://github.com/AAswordman/Operit/pull/1211)
- 117 [e1fa084bc5fc](https://github.com/3316891527/Operit/commit/e1fa084bc5fc323e461e354cdaf2ec52b5a635d2) · 2026-10-01 · 合并／集成 · Merge #1211 API key pool failover · [上游 #1211](https://github.com/AAswordman/Operit/pull/1211)
- 118 [3d936738c48b](https://github.com/3316891527/Operit/commit/3d936738c48b8e0db5b64cedb576db4e990788a2) · 2026-10-02 · 功能／修复／重构 · fix: address API key pool review feedback · [上游 #1211](https://github.com/AAswordman/Operit/pull/1211)
- 119 [7154afdc445e](https://github.com/3316891527/Operit/commit/7154afdc445edf0d7bbd3ef98bceff5b7bec5e32) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1211 并协调功能冲突 · 直接提交
- 120 [b4295499836a](https://github.com/3316891527/Operit/commit/b4295499836a8b050b12c0929cc29512325f359a) · 2026-09-17 · 功能／修复／重构 · fix(repo): keep nested SAF paths when writing workspace files · [上游 #1218](https://github.com/AAswordman/Operit/pull/1218)
- 121 [5ff31674399c](https://github.com/3316891527/Operit/commit/5ff31674399cc3328860b1d43d1b1679988919a6) · 2026-09-25 · 功能／修复／重构 · fix(files): copy repository directories recursively · [上游 #1218](https://github.com/AAswordman/Operit/pull/1218)
- 122 [6b42c451c83d](https://github.com/3316891527/Operit/commit/6b42c451c83d8442b6fc16de58ce32fd2f2676e3) · 2026-09-25 · 功能／修复／重构 · fix(repo): reject recursive copy into own directory · [上游 #1218](https://github.com/AAswordman/Operit/pull/1218)
- 123 [92dd0fa40bba](https://github.com/3316891527/Operit/commit/92dd0fa40bba278a1f8a12c592080d6d8107d27e) · 2026-09-25 · 功能／修复／重构 · fix(repo): clean failed copies and preserve cancellation · [上游 #1218](https://github.com/AAswordman/Operit/pull/1218)
- 124 [006a775b5774](https://github.com/3316891527/Operit/commit/006a775b5774f8f827c65ac4b396383784b7a300) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1218 · 直接提交
- 125 [51fe8f1f1270](https://github.com/3316891527/Operit/commit/51fe8f1f12703f5d648d1469a01e560478a4db76) · 2026-09-19 · 功能／修复／重构 · fix(chat): keep thinking turns when retrying empty visible replies · [上游 #1231](https://github.com/AAswordman/Operit/pull/1231)
- 126 [0c5ee5a0eb35](https://github.com/3316891527/Operit/commit/0c5ee5a0eb351b366a79dad0848614f8e05c378d) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1231 · 直接提交
- 127 [ab0efc7b1aba](https://github.com/3316891527/Operit/commit/ab0efc7b1abac5b7f5acf47346a21fc6419cc696) · 2026-09-20 · 功能／修复／重构 · fix(chat): attach screen content as image instead of OCR text · [上游 #1235](https://github.com/AAswordman/Operit/pull/1235)
- 128 [8438b136bf66](https://github.com/3316891527/Operit/commit/8438b136bf6634a4483de579f239749ce13ddaea) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1235 · 直接提交
- 129 [62efd6849aa8](https://github.com/3316891527/Operit/commit/62efd6849aa80fb717c6e631c96a721fdf759420) · 2026-09-20 · 功能／修复／重构 · feat(tokenstats): delete leftover stats for removed model configs · [上游 #1236](https://github.com/AAswordman/Operit/pull/1236)
- 130 [9d5a80f65f22](https://github.com/3316891527/Operit/commit/9d5a80f65f22e0f6c19e22fce6e06da335cd7484) · 2026-10-01 · 功能／修复／重构 · fix(tokenstats): preserve usage when deleting model configs · [上游 #1236](https://github.com/AAswordman/Operit/pull/1236)
- 131 [df9aed0f8fca](https://github.com/3316891527/Operit/commit/df9aed0f8fcaca652f7d1bc95478b42a68e37343) · 2026-10-01 · 功能／修复／重构 · fix(tokenstats): remove stale screen parameters · [上游 #1236](https://github.com/AAswordman/Operit/pull/1236)
- 132 [d61e5f432b15](https://github.com/3316891527/Operit/commit/d61e5f432b15de809965abf32e667a8d1fd9beb9) · 2026-10-01 · 合并／集成 · merge: resolve string resource conflicts for PR #1236 · [上游 #1236](https://github.com/AAswordman/Operit/pull/1236)
- 133 [0a39a0b101ae](https://github.com/3316891527/Operit/commit/0a39a0b101aee41df3aff927c4110a3ee8c1dcc0) · 2026-10-01 · 检查／构建 · test: provide application context in model config test · [上游 #1236](https://github.com/AAswordman/Operit/pull/1236)
- 134 [dc522c71f31d](https://github.com/3316891527/Operit/commit/dc522c71f31d46c582e1bfa5c6dd3531518cd947) · 2026-10-03 · 功能／修复／重构 · fix: preserve usage and zero deleted config cost · [上游 #1236](https://github.com/AAswordman/Operit/pull/1236)
- 135 [cd7332c3fa28](https://github.com/3316891527/Operit/commit/cd7332c3fa28e7908ee2432f2974299967c32207) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1236 · 直接提交
- 136 [a752eecefac7](https://github.com/3316891527/Operit/commit/a752eecefac71ad4d3f15427ead50937c065110e) · 2026-09-21 · 功能／修复／重构 · feat(tools): expose global permission mode in editor · [上游 #1247](https://github.com/AAswordman/Operit/pull/1247)
- 137 [c48017dbc32a](https://github.com/3316891527/Operit/commit/c48017dbc32a94651e996eb2a60fae1f14792b82) · 2026-09-21 · 功能／修复／重构 · fix(tools): sync editor permission tools into examples · [上游 #1247](https://github.com/AAswordman/Operit/pull/1247)
- 138 [41a2af8c0d52](https://github.com/3316891527/Operit/commit/41a2af8c0d52841f281e0dedb475660fcdbd6615) · 2026-09-25 · 功能／修复／重构 · fix(permissions): remove model-controlled tool bypass · [上游 #1247](https://github.com/AAswordman/Operit/pull/1247)
- 139 [9b35c0985fbe](https://github.com/3316891527/Operit/commit/9b35c0985fbe073de1829f8f39ba73a5731fac84) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1247 · 直接提交
- 140 [1f1c4be3585b](https://github.com/3316891527/Operit/commit/1f1c4be3585bd76e6053862b08b9c23d17048416) · 2026-09-22 · 功能／修复／重构 · fix(storage): keep dev package TypeScript out of the gallery · [上游 #1254](https://github.com/AAswordman/Operit/pull/1254)
- 141 [5c9b141e0184](https://github.com/3316891527/Operit/commit/5c9b141e01842cc4636e721063cc6bd02128cb00) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1254 · 直接提交
- 142 [a80f9ab99367](https://github.com/3316891527/Operit/commit/a80f9ab99367a8b8edb41a62ad6204f9fbbc198f) · 2026-09-22 · 功能／修复／重构 · fix(codex): keep prompt cache affinity stable · [上游 #1255](https://github.com/AAswordman/Operit/pull/1255)
- 143 [f565da671a8f](https://github.com/3316891527/Operit/commit/f565da671a8f8dcd20fe3e6c310991ee34d4d9e7) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1255 · 直接提交
- 144 [bd04cc967812](https://github.com/3316891527/Operit/commit/bd04cc9678129529a994c22da898f0cbf719ae3c) · 2026-09-22 · 功能／修复／重构 · fix(summary): drop retried stream prefixes · [上游 #1256](https://github.com/AAswordman/Operit/pull/1256)
- 145 [04e2914c34f2](https://github.com/3316891527/Operit/commit/04e2914c34f27bd4918a1802bc6681a18bc38460) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1256 · 直接提交
- 146 [89d85c75131f](https://github.com/3316891527/Operit/commit/89d85c75131f20bfddd86e1ca6b6d64e2a92f655) · 2026-09-23 · 功能／修复／重构 · fix(chat): draw the thinking guide without parent constraints · [上游 #1260](https://github.com/AAswordman/Operit/pull/1260)
- 147 [9c24cb972b6b](https://github.com/3316891527/Operit/commit/9c24cb972b6b4c2de10218862c7cafc1ec1da1ad) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1260 · 直接提交
- 148 [c896c6905639](https://github.com/3316891527/Operit/commit/c896c69056391e4c76fc2571d749cf4b2b7f98fe) · 2026-09-23 · 功能／修复／重构 · feat(summary): add a switch to review the previous summary · [上游 #1261](https://github.com/AAswordman/Operit/pull/1261)
- 149 [86f771e08216](https://github.com/3316891527/Operit/commit/86f771e08216fcacc6290a4a2127f94c61c12a81) · 2026-10-01 · 合并／集成 · Merge dev into summary-previous-review · [上游 #1261](https://github.com/AAswordman/Operit/pull/1261)
- 150 [db3cacec8b50](https://github.com/3316891527/Operit/commit/db3cacec8b50585efa775bc69a9dd8f23e97f945) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1261 并协调功能冲突 · 直接提交
- 151 [6395315765ee](https://github.com/3316891527/Operit/commit/6395315765ee088e5d787ce030d557528fe85900) · 2026-09-23 · 功能／修复／重构 · feat(config): raise default context lengths for new configs · [上游 #1262](https://github.com/AAswordman/Operit/pull/1262)
- 152 [d9350eca466b](https://github.com/3316891527/Operit/commit/d9350eca466b6d615b8be5fb9ca99c1880162935) · 2026-10-01 · 合并／集成 · Merge dev into context-length-defaults · [上游 #1262](https://github.com/AAswordman/Operit/pull/1262)
- 153 [73f2e09acb6d](https://github.com/3316891527/Operit/commit/73f2e09acb6dc6de9acfcaa1ccca32a60d4482bc) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1262 · 直接提交
- 154 [55d052c8368d](https://github.com/3316891527/Operit/commit/55d052c8368d12cf489f0a2ecd144ce7a767cfaf) · 2026-09-24 · 功能／修复／重构 · fix(tokenstats): add a delayed confirmation for clearing usage · [上游 #1267](https://github.com/AAswordman/Operit/pull/1267)
- 155 [a70ac4d03001](https://github.com/3316891527/Operit/commit/a70ac4d0300126e60511866b8f703091d0840c99) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1267 并协调功能冲突 · 直接提交
- 156 [fead591dfff9](https://github.com/3316891527/Operit/commit/fead591dfff972d41ca08569b7a9bb52a32f2a41) · 2026-09-24 · 功能／修复／重构 · fix(provider): remove DeepSeek built-in web search · [上游 #1269](https://github.com/AAswordman/Operit/pull/1269)
- 157 [c41ca2a3f98e](https://github.com/3316891527/Operit/commit/c41ca2a3f98efae196e02cb343276726229c0e15) · 2026-10-01 · 合并／集成 · Merge dev into remove-deepseek-web-search · [上游 #1269](https://github.com/AAswordman/Operit/pull/1269)
- 158 [3debc369d99c](https://github.com/3316891527/Operit/commit/3debc369d99cb8b11b27128095cda5831b8b587c) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1269 · 直接提交
- 159 [5ed96bffbdf5](https://github.com/3316891527/Operit/commit/5ed96bffbdf54164e79a58cecca2f26b681f0051) · 2026-09-24 · 功能／修复／重构 · fix(chat): explain thinking replies stopped by max tokens · [上游 #1270](https://github.com/AAswordman/Operit/pull/1270)
- 160 [294889b22c13](https://github.com/3316891527/Operit/commit/294889b22c13b41e15ff1e8ca306e61960e53a00) · 2026-10-01 · 合并／集成 · Merge dev into max-tokens-thinking-warning · [上游 #1270](https://github.com/AAswordman/Operit/pull/1270)
- 161 [f5f5e1b07293](https://github.com/3316891527/Operit/commit/f5f5e1b07293b58de6117eb50227faada1c98915) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1270 并协调功能冲突 · 直接提交
- 162 [03de658a4ac1](https://github.com/3316891527/Operit/commit/03de658a4ac12dbc80dcca8e5b0be96e6bda2fdb) · 2026-09-25 · 功能／修复／重构 · feat(chat): 结构化持久化聊天记录并统一思考与工具渲染 · [上游 #1278](https://github.com/AAswordman/Operit/pull/1278)
- 163 [63d9c2329c5c](https://github.com/3316891527/Operit/commit/63d9c2329c5c41290314bc3d426521be1ecf2adb) · 2026-10-06 · 文档／语言 · docs(chat): 补全 PR1278 固定回归清单与设备结果 · [上游 #1278](https://github.com/AAswordman/Operit/pull/1278)
- 164 [ff6d83b4cd0a](https://github.com/3316891527/Operit/commit/ff6d83b4cd0a5054e18e2de40ebedbf3580df933) · 2026-10-08 · 功能／修复／重构 · fix(chat): 按结构化片段过滤复制正文中的工具内容 · [上游 #1278](https://github.com/AAswordman/Operit/pull/1278)
- 165 [5220759746d6](https://github.com/3316891527/Operit/commit/5220759746d6909bcd5d8f5349cbee8828e16350) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1278 并协调功能冲突 · 直接提交
- 166 [9e32da6f8539](https://github.com/3316891527/Operit/commit/9e32da6f8539a9f8274c9b0590fd49b1c4e63cc9) · 2026-09-26 · 功能／修复／重构 · feat(tokenstats): add advanced pricing rules · [上游 #1284](https://github.com/AAswordman/Operit/pull/1284)
- 167 [56056677b9c9](https://github.com/3316891527/Operit/commit/56056677b9c93ee4f1bfc803b97b3413b933d742) · 2026-09-27 · 功能／修复／重构 · fix(tokenstats): fix peak period removal lambda · [上游 #1284](https://github.com/AAswordman/Operit/pull/1284)
- 168 [b55fa1b54ce1](https://github.com/3316891527/Operit/commit/b55fa1b54ce1c323e3b81a92f9adfc8265657367) · 2026-09-27 · 功能／修复／重构 · feat(tokenstats): add configurable advanced pricing rules · [上游 #1284](https://github.com/AAswordman/Operit/pull/1284)
- 169 [29bc3cc146e8](https://github.com/3316891527/Operit/commit/29bc3cc146e8e891e5829177177a1205897c1f68) · 2026-09-27 · 功能／修复／重构 · fix(tokenstats): keep aggregate input token calculation · [上游 #1284](https://github.com/AAswordman/Operit/pull/1284)
- 170 [ab2992e888eb](https://github.com/3316891527/Operit/commit/ab2992e888eb0b9cda3fb1a2b2830ae386944f23) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1284 并协调功能冲突 · 直接提交
- 171 [4067212991a5](https://github.com/3316891527/Operit/commit/4067212991a549a6f265bafc16d2e24e3c081883) · 2026-09-27 · 功能／修复／重构 · fix(deepseek): preserve both reasoning field types · [上游 #1290](https://github.com/AAswordman/Operit/pull/1290)
- 172 [475bc3a74c1e](https://github.com/3316891527/Operit/commit/475bc3a74c1e828d179f1b94b2d25afeb3fd21ef) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1290 · 直接提交
- 173 [94b57556eb63](https://github.com/3316891527/Operit/commit/94b57556eb638f8244918a5980f608139779f7d7) · 2026-09-28 · 功能／修复／重构 · fix(files): map Linux grep paths for native ripgrep · [上游 #1296](https://github.com/AAswordman/Operit/pull/1296)
- 174 [cd78ee895937](https://github.com/3316891527/Operit/commit/cd78ee895937618b6fe74a3dcace15b74acdde61) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1296 · 直接提交
- 175 [c848f2c4ffdd](https://github.com/3316891527/Operit/commit/c848f2c4ffdd4d51bd3e5972fa2cc4490bcd1b99) · 2026-09-28 · 功能／修复／重构 · fix(attachment): bypass unstable picker URI grants · [上游 #1298](https://github.com/AAswordman/Operit/pull/1298)
- 176 [61bd534c88a8](https://github.com/3316891527/Operit/commit/61bd534c88a8f4b668ca98b0a7c2b3d561493c52) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1298 · 直接提交
- 177 [f188fbdddcef](https://github.com/3316891527/Operit/commit/f188fbdddcef50cd40f531e0780eb956ff3ed2e7) · 2026-09-28 · 功能／修复／重构 · 重做聊天思考质量滑块 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 178 [bb12827ebde8](https://github.com/3316891527/Operit/commit/bb12827ebde8b889699a360af30e273cf2a1beb9) · 2026-09-29 · 功能／修复／重构 · 对齐 Operit2 思考滑块交互 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 179 [1a6f89f0d1f1](https://github.com/3316891527/Operit/commit/1a6f89f0d1f178fdfd883c0c119c3c2e1cec0188) · 2026-09-29 · 功能／修复／重构 · 修复思考滑块构建错误并清理旧配置 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 180 [83ed8ab939de](https://github.com/3316891527/Operit/commit/83ed8ab939ded3af5c7c9649b47dba4ba3d05fbd) · 2026-09-29 · 功能／修复／重构 · 修复思考滑块无障碍角色编译错误 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 181 [219f91aed7e0](https://github.com/3316891527/Operit/commit/219f91aed7e0f4d0a23b01894ea52eabb60b104b) · 2026-09-29 · 功能／修复／重构 · 优化思考滑块档位提示布局 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 182 [dc2f036bc0c9](https://github.com/3316891527/Operit/commit/dc2f036bc0c9ad4f1ad322ded5e60fa50d739c54) · 2026-09-29 · 功能／修复／重构 · 恢复思考滑块渐变和拖动高光 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 183 [ee0362969eb5](https://github.com/3316891527/Operit/commit/ee0362969eb543f045e16f64654bd7d9b7e880c7) · 2026-09-29 · 功能／修复／重构 · 增加思考粒子流动动画和主题颜色设置 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 184 [a662fc8886da](https://github.com/3316891527/Operit/commit/a662fc8886dab135e38f8858d10ae144ee405173) · 2026-09-29 · 功能／修复／重构 · 修复思考滑块最高档回弹并增加密集粒子 · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 185 [b8d9383eeb8c](https://github.com/3316891527/Operit/commit/b8d9383eeb8cd53c667b1873765c667806cb0478) · 2026-10-01 · 合并／集成 · Merge dev into feat/thinking-slider-control · [上游 #1299](https://github.com/AAswordman/Operit/pull/1299)
- 186 [8e6d84853055](https://github.com/3316891527/Operit/commit/8e6d8485305528555802eed7f618aa120ab41e8b) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1299 并协调功能冲突 · 直接提交
- 187 [e001799b1361](https://github.com/3316891527/Operit/commit/e001799b1361912f5c588eb98d30a6811d517e06) · 2026-09-29 · 功能／修复／重构 · fix(code-runner): bound hidden execution and recycle shells · [上游 #1308](https://github.com/AAswordman/Operit/pull/1308)
- 188 [f308ecd7825e](https://github.com/3316891527/Operit/commit/f308ecd7825e21171ef1265f19f373b4125e5bcf) · 2026-09-29 · 功能／修复／重构 · fix(code-runner): track hidden shell cleanup · [上游 #1308](https://github.com/AAswordman/Operit/pull/1308)
- 189 [fe3bf652d836](https://github.com/3316891527/Operit/commit/fe3bf652d8365a1c0387bae6ddef884aab74d3ff) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1308 · 直接提交
- 190 [56e01b03d00c](https://github.com/3316891527/Operit/commit/56e01b03d00cf43c0d582b41f2cc59d5ece18155) · 2026-09-29 · 功能／修复／重构 · feat: 为 operit_editor 增加性能指标查询 · [上游 #1311](https://github.com/AAswordman/Operit/pull/1311)
- 191 [cfd559675c1f](https://github.com/3316891527/Operit/commit/cfd559675c1fcfa5a10d782c514f2a88984be5d1) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1311 · 直接提交
- 192 [5e9e6e571390](https://github.com/3316891527/Operit/commit/5e9e6e571390ddc24541c59b9c1c2975ad841b2a) · 2026-09-30 · 功能／修复／重构 · feat(permissions): route tools by granted capabilities · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 193 [8a06c79c520b](https://github.com/3316891527/Operit/commit/8a06c79c520bfee86e903a08e434e8ab39965359) · 2026-09-30 · 功能／修复／重构 · fix(permissions): cache capability checks per tool session · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 194 [a640d12b74d9](https://github.com/3316891527/Operit/commit/a640d12b74d986b92bdf522d8382b9be0c436690) · 2026-09-30 · 功能／修复／重构 · fix(permissions): distribute level tabs across width · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 195 [49a68dc406c8](https://github.com/3316891527/Operit/commit/49a68dc406c8ed54a1797096ac8c0241cb4447a0) · 2026-09-30 · 功能／修复／重构 · fix(permissions): cache capability checks per tool session · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 196 [b7231b95e29b](https://github.com/3316891527/Operit/commit/b7231b95e29b34493390342f298d1ba4aa35e48d) · 2026-09-30 · 合并／集成 · merge: preserve remote permission routing history · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 197 [6e02bd19d926](https://github.com/3316891527/Operit/commit/6e02bd19d926c023af4f9066db2bcaf06670b3ec) · 2026-09-30 · 功能／修复／重构 · feat(permissions): add system settings shortcuts to all levels · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 198 [4eccdd2d6f58](https://github.com/3316891527/Operit/commit/4eccdd2d6f581e37507d8927ee28e73f1c2c340b) · 2026-09-30 · 功能／修复／重构 · fix(permissions): avoid permission row overload ambiguity · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 199 [512f02c07de0](https://github.com/3316891527/Operit/commit/512f02c07de0eefccd6ca4c78705b19fa7184cdf) · 2026-10-01 · 合并／集成 · merge: resolve string resource conflicts for PR #1313 · [上游 #1313](https://github.com/AAswordman/Operit/pull/1313)
- 200 [9ae3d52f3d59](https://github.com/3316891527/Operit/commit/9ae3d52f3d59336b609d96b9aa0f8853e43da240) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1313 并协调功能冲突 · 直接提交
- 201 [2411554cda43](https://github.com/3316891527/Operit/commit/2411554cda431e6d65e04bc07047058779671edb) · 2026-09-30 · 功能／修复／重构 · feat: restore chat export and streaming import · [上游 #1314](https://github.com/AAswordman/Operit/pull/1314)
- 202 [20a6d822ddb1](https://github.com/3316891527/Operit/commit/20a6d822ddb161b2da18a5f107d51b026a6cd445) · 2026-09-30 · 功能／修复／重构 · fix: correct CSV export model conversions · [上游 #1314](https://github.com/AAswordman/Operit/pull/1314)
- 203 [d8844ce89a48](https://github.com/3316891527/Operit/commit/d8844ce89a482e230f92d3f1593e89592fc40808) · 2026-09-30 · 功能／修复／重构 · fix(chat-export): resolve CSV ordering and resource scope · [上游 #1314](https://github.com/AAswordman/Operit/pull/1314)
- 204 [5f8cef5c857d](https://github.com/3316891527/Operit/commit/5f8cef5c857d5285ecbcb5c654723725cbeee09a) · 2026-10-01 · 合并／集成 · merge: resolve string resource conflicts for PR #1314 · [上游 #1314](https://github.com/AAswordman/Operit/pull/1314)
- 205 [566deb0c16b1](https://github.com/3316891527/Operit/commit/566deb0c16b1f003baefc85ef91c1ea368d6b652) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1314 并协调功能冲突 · 直接提交
- 206 [a29314371a74](https://github.com/3316891527/Operit/commit/a29314371a74b0bc9c54950d1e46d46c59660b0a) · 2026-10-01 · 功能／修复／重构 · feat(display): configure orientation reload behavior · [上游 #1321](https://github.com/AAswordman/Operit/pull/1321)
- 207 [3eb5bafae31e](https://github.com/3316891527/Operit/commit/3eb5bafae31ef914dd25a7e0852206c9514be9c2) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1321 · 直接提交
- 208 [3df6c1ca061c](https://github.com/3316891527/Operit/commit/3df6c1ca061ca74d8d60e8a9e04fd3b90215d33f) · 2026-10-02 · 功能／修复／重构 · fix: localize chat processing status messages · [上游 #1326](https://github.com/AAswordman/Operit/pull/1326)
- 209 [0a03e289d06f](https://github.com/3316891527/Operit/commit/0a03e289d06f4973659ad4bfa7099d51c3e084ec) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1326 · 直接提交
- 210 [59defadfd81a](https://github.com/3316891527/Operit/commit/59defadfd81a97849152a8fb26170bce5b3ffaa1) · 2026-10-04 · 功能／修复／重构 · fix(markdown): constrain code fences to line boundaries · [上游 #1334](https://github.com/AAswordman/Operit/pull/1334)
- 211 [ad7bd19f2b76](https://github.com/3316891527/Operit/commit/ad7bd19f2b76ec6ebf8949ad8a7d81bac2661029) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1334 并协调功能冲突 · 直接提交
- 212 [fd85521e8963](https://github.com/3316891527/Operit/commit/fd85521e89631d51dae39c14b8a1fb9f1ad30869) · 2026-10-05 · 功能／修复／重构 · fix(pc-agent): secure configuration management for issue 1322 · [上游 #1340](https://github.com/AAswordman/Operit/pull/1340)
- 213 [f30c2f9d4611](https://github.com/3316891527/Operit/commit/f30c2f9d461154967cf6c2b162e720234c9e2832) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1340 · 直接提交
- 214 [6801b64355cd](https://github.com/3316891527/Operit/commit/6801b64355cd751462265d36ebff70f20a74a8d9) · 2026-10-05 · 功能／修复／重构 · feat(workspace): 重构工作区文件浏览入口 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 215 [b6fbe8d5a5cb](https://github.com/3316891527/Operit/commit/b6fbe8d5a5cb29e3ce62a3a16586c09a93d83f15) · 2026-10-05 · 功能／修复／重构 · refactor(toolbox): 移除独立文件管理器入口 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 216 [2a3164b07d20](https://github.com/3316891527/Operit/commit/2a3164b07d203a58d5734a07df63b9ffdff79000) · 2026-10-05 · 功能／修复／重构 · 扩展工作区编辑器注释规则 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 217 [035f31998919](https://github.com/3316891527/Operit/commit/035f319989191323e22344f18a79dcaf55028ea3) · 2026-10-06 · 功能／修复／重构 · fix(workspace): preserve browser path after closing file · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 218 [6b3baefb81ec](https://github.com/3316891527/Operit/commit/6b3baefb81ecc98bedbfe73899a9c00994692fbe) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 创建默认直接进入类型选择 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 219 [f33c623fa71e](https://github.com/3316891527/Operit/commit/f33c623fa71e0aeb2d7e42c647b13bf77b1ed3b1) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 修复打开文件后顶部栏透明 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 220 [e24a30d9ae77](https://github.com/3316891527/Operit/commit/e24a30d9ae773828827d2de1d2cb752771496a51) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 适配工具栏主题并接通聊天文件链接 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 221 [a6ae432c965b](https://github.com/3316891527/Operit/commit/a6ae432c965bf0b2343ef90d460bdb303c855cb8) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 落实文件浏览与编辑审阅意见 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 222 [6f239395bd0f](https://github.com/3316891527/Operit/commit/6f239395bd0febe5c27853b1c1e88a382be605f1) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 补齐文件链接复审与大文本读取限制 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 223 [49e8f0431be1](https://github.com/3316891527/Operit/commit/49e8f0431be13dcb662a6341207c3a361011d888) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 修正设置标题主题并记录实机回归结果 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 224 [91b1d52fc79b](https://github.com/3316891527/Operit/commit/91b1d52fc79b1d78aa5844c1800b69971de13406) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 补充模型文件引用约定并更新回归签收 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 225 [f96fc2da194d](https://github.com/3316891527/Operit/commit/f96fc2da194d48c36c95aa0436d28aeff8fafb99) · 2026-10-06 · 功能／修复／重构 · fix(workspace): 修复 Markdown 预览链接跳转与异常处理 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 226 [122925d487bc](https://github.com/3316891527/Operit/commit/122925d487bcae86f10b359de9efca350f87c15f) · 2026-10-08 · 功能／修复／重构 · fix(prompt): 移除默认文件引用提示词注入 · [上游 #1344](https://github.com/AAswordman/Operit/pull/1344)
- 227 [c616593a9ed3](https://github.com/3316891527/Operit/commit/c616593a9ed3e234557b64c91db6fb6b532e7b03) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1344 并协调功能冲突 · 直接提交
- 228 [39fb3dbc7508](https://github.com/3316891527/Operit/commit/39fb3dbc750857c1ba957d167a7e665a8237c257) · 2026-10-07 · 功能／修复／重构 · feat(settings): 自动清理替换后的图片与字体资源 · [上游 #1356](https://github.com/AAswordman/Operit/pull/1356)
- 229 [8a3b5c855d42](https://github.com/3316891527/Operit/commit/8a3b5c855d42e02649cd0a67e032593e7ac11224) · 2026-10-15 · 合并／集成 · merge: 按发行清单集成 PR #1356 · 直接提交
- 230 [b759dab1f6b4](https://github.com/3316891527/Operit/commit/b759dab1f6b421b6681882094d49958e2e9308a9) · 2026-10-08 · 功能／修复／重构 · fix(chat): preserve cumulative token statistics across restores · [上游 #1360](https://github.com/AAswordman/Operit/pull/1360)
- 231 [03a5ecc62ece](https://github.com/3316891527/Operit/commit/03a5ecc62ece45fa5ee9316140a0b62d8189b616) · 2026-10-09 · 功能／修复／重构 · fix(chat): keep message loading and runtime sync after statistics failures · [上游 #1360](https://github.com/AAswordman/Operit/pull/1360)
- 232 [01e24f943032](https://github.com/3316891527/Operit/commit/01e24f943032f5d9dd0ec706c6ea8557a77efe50) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1360 并协调功能冲突 · 直接提交
- 233 [cf632dbb2a5d](https://github.com/3316891527/Operit/commit/cf632dbb2a5d992762dd670a214d37fbfc65b559) · 2026-10-10 · 功能／修复／重构 · fix(memory): narrow semantic candidates and expose query scores · [上游 #1361](https://github.com/AAswordman/Operit/pull/1361)
- 234 [ea7ce28f8828](https://github.com/3316891527/Operit/commit/ea7ce28f8828dd17828cf2c3f2bb738dc62d61f3) · 2026-10-10 · 检查／构建 · test(memory): type empty semantic candidate sequence explicitly · [上游 #1361](https://github.com/AAswordman/Operit/pull/1361)
- 235 [15369feca98b](https://github.com/3316891527/Operit/commit/15369feca98b59f73cf7f7fe9cf41ada02c5a8d3) · 2026-10-10 · 功能／修复／重构 · fix(memory): 同步工具阈值并优化范围检索 · [上游 #1361](https://github.com/AAswordman/Operit/pull/1361)
- 236 [e29aaa660fd6](https://github.com/3316891527/Operit/commit/e29aaa660fd6c57ddd18a2a1b9c4c8713f55fca9) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1361 并协调功能冲突 · 直接提交
- 237 [8d040e60e2e7](https://github.com/3316891527/Operit/commit/8d040e60e2e70bf23f7ce5cd451ea0c27e403c30) · 2026-10-10 · 功能／修复／重构 · fix(chat): 修复长回复末尾卡片点击并统一聊天滚动 · [上游 #1362](https://github.com/AAswordman/Operit/pull/1362)
- 238 [af08bbd2dd74](https://github.com/3316891527/Operit/commit/af08bbd2dd74d6f3a9f3da5cdf35e224aa1e1fb5) · 2026-10-10 · 功能／修复／重构 · perf(chat): 缓存消息拆块并清理遗留代码 · [上游 #1362](https://github.com/AAswordman/Operit/pull/1362)
- 239 [eb90bfdb634b](https://github.com/3316891527/Operit/commit/eb90bfdb634b285b25f36e7b91a958d47ebd2c58) · 2026-10-10 · 功能／修复／重构 · fix(chat): 按可见内容分块以修复空白气泡和首尾断层 · [上游 #1362](https://github.com/AAswordman/Operit/pull/1362)
- 240 [d699e69fc452](https://github.com/3316891527/Operit/commit/d699e69fc4524f6268c70db6623db710b3a0641e) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1362 并协调功能冲突 · 直接提交
- 241 [00237dd4d47d](https://github.com/3316891527/Operit/commit/00237dd4d47de4467365062319fc72468a14eb3a) · 2026-10-10 · 功能／修复／重构 · fix(chat): 降低长历史发送前的重复读取和请求日志开销 · [上游 #1365](https://github.com/AAswordman/Operit/pull/1365)
- 242 [4740da86fe5d](https://github.com/3316891527/Operit/commit/4740da86fe5d0c4a5aa581d69ee3ac21cca441a4) · 2026-10-10 · 检查／构建 · test(chat): 修复请求体回归断言的 Android JSON API 兼容性 · [上游 #1365](https://github.com/AAswordman/Operit/pull/1365)
- 243 [2388d61020e4](https://github.com/3316891527/Operit/commit/2388d61020e4ca71cae1962b571b23a20c28d1bc) · 2026-10-15 · 合并／集成 · merge: 集成 PR #1365 并协调功能冲突 · 直接提交
- 244 [76b226790376](https://github.com/3316891527/Operit/commit/76b22679037647c5b3eeb1d3365015ab601ff021) · 2026-10-15 · 功能／修复／重构 · fix(integration): 协调联合迁移与备份兼容并完成发行整合 · 直接提交
- 245 [c4920663426d](https://github.com/3316891527/Operit/commit/c4920663426dbeaa0bd28a67c85792c934d6bcdf) · 2026-10-15 · 功能／修复／重构 · fix(integration): 修复主题与流式渲染编译错误 · 直接提交
- 246 [5c6a52d636b2](https://github.com/3316891527/Operit/commit/5c6a52d636b2f772b6f9919d8a793ce4543fb790) · 2026-10-15 · 检查／构建 · ci: 发布打包结束后仅保留APK下载产物 · 直接提交
- 247 [e8b3b4002f8b](https://github.com/3316891527/Operit/commit/e8b3b4002f8b395f6fed9fada4b5b29c2f54c1bd) · 2026-10-15 · 功能／修复／重构 · fix(integration): 补齐原生插件声明并适配快照测试 · 直接提交
- 248 [bdff0056606a](https://github.com/3316891527/Operit/commit/bdff0056606a74761b1777926380d8e0dd64ee49) · 2026-10-15 · 检查／构建 · ci: 发行构建仅上传APK并接入固定签名密钥 · 直接提交
- 249 [eed0b0bb6886](https://github.com/3316891527/Operit/commit/eed0b0bb688658db60ab6b9c215d68686fdf9c0b) · 2026-10-15 · 功能／修复／重构 · feat(distribution): 固定发行签名并升级APK安装版本 · 直接提交
- 250 [9f252d12c61e](https://github.com/3316891527/Operit/commit/9f252d12c61e5064de9858fdc2dab4a533805fbc) · 2026-10-15 · 检查／构建 · test(integration): 修复JVM测试环境与过期预期 · 直接提交
- 251 [fa1a0325ac60](https://github.com/3316891527/Operit/commit/fa1a0325ac6036247c2a2b0bf307d3d186bba8ea) · 2026-10-15 · 发行整理 · fix: 修复发行版本格式并更新设置入口与终端核心 · 直接提交
- 252 [9ad3694fa3cf](https://github.com/3316891527/Operit/commit/9ad3694fa3cf7f9996c60e1c6b11e1ee28746e70) · 2026-10-15 · 检查／构建 · ci: 接入独立发行Release与增量包发布 · 直接提交
- 253 [292443425fea](https://github.com/3316891527/Operit/commit/292443425feaa62b5d558c0645c0d13f14ee2a2c) · 2026-10-15 · 功能／修复／重构 · feat: 接入独立发行更新源与增量发布 · 直接提交
- 254 [2a7331e1df16](https://github.com/3316891527/Operit/commit/2a7331e1df167e5c4ab8148ace45a90ab2e9a50d) · 2026-10-15 · 检查／构建 · ci: 校验外置资源未打包并报告 APK 大小 · 直接提交
- 255 [6dc1dbfae6e9](https://github.com/3316891527/Operit/commit/6dc1dbfae6e96c2d3f48a4b29f916180125a02e3) · 2026-10-15 · 功能／修复／重构 · feat: 外置离线资源并支持备份导入 · 直接提交
- 256 [45b644999367](https://github.com/3316891527/Operit/commit/45b644999367a4a62dc80d79fb037d10be1e9001) · 2026-10-15 · 检查／构建 · ci: 增加独立的云端离线资源发布作业 · 直接提交
- 257 [7dc8bf3d9cd3](https://github.com/3316891527/Operit/commit/7dc8bf3d9cd386cca20ffcea8a0485c54ab0f272) · 2026-10-15 · 检查／构建 · ci: 在云端校验并发布固定离线资源 · 直接提交
- 258 [38261aae4270](https://github.com/3316891527/Operit/commit/38261aae4270d356369e8813dec4176b374a0758) · 2026-10-15 · 功能／修复／重构 · perf(chat): 减少历史加载与分块渲染重复处理 · 直接提交
- 259 [c8f4ca9e7309](https://github.com/3316891527/Operit/commit/c8f4ca9e7309e7c403d970ba17b972bf2bf6d16f) · 2026-10-15 · 功能／修复／重构 · feat(ui): 整理工作台终端与离线资源入口 · 直接提交
- 260 [ec5705882e91](https://github.com/3316891527/Operit/commit/ec5705882e91455156e31954f2095dd99d77271e) · 2026-10-15 · 功能／修复／重构 · fix(htmlpackager): 修复导出完成后打开文件的工具引用 · 直接提交
- 261 [e949eb5eff3a](https://github.com/3316891527/Operit/commit/e949eb5eff3a7b9e651a3108b97563e4c79ff771) · 2026-10-15 · 功能／修复／重构 · fix(plugins): 修复页面加载生命周期并保存聊天草稿 · 直接提交
- 262 [8542070e1878](https://github.com/3316891527/Operit/commit/8542070e1878419533dc7eb7c8dbdf079c4229bd) · 2026-10-15 · 功能／修复／重构 · feat: 完善个人版设置并接入原生消息翻译 · 直接提交
- 263 [6803712c4437](https://github.com/3316891527/Operit/commit/6803712c4437436a4118ea2dc187f28a2a9de84e) · 2026-10-15 · 功能／修复／重构 · fix(settings): 补齐模型列表透明背景的 Color 导入 · 直接提交
- 264 [2c9212ae3bfb](https://github.com/3316891527/Operit/commit/2c9212ae3bfb6122fdaa342375ba214929f5c744) · 2026-10-15 · 功能／修复／重构 · feat: 接入原生动态界面并精简图标包 · 直接提交
- 265 [978fa79bc08b](https://github.com/3316891527/Operit/commit/978fa79bc08bab659335f30773d2a444a03e4dde) · 2026-10-15 · 功能／修复／重构 · fix: 修正工作台终端入口与默认包配置并适配 A2UI Compose · 直接提交
- 266 [ce727276f66e](https://github.com/3316891527/Operit/commit/ce727276f66e222c342d2459c46bce3b1ba4ad3d) · 2026-10-15 · 检查／构建 · test: 同步代码围栏的闭合状态断言 · 直接提交
- 267 [b2d7e30741ad](https://github.com/3316891527/Operit/commit/b2d7e30741ad40e59c252d8678aa949c929401b9) · 2026-10-15 · 功能／修复／重构 · feat(a2ui): 原生化生成工具并拆分外部可选引导 · 直接提交
- 268 [049202d23614](https://github.com/3316891527/Operit/commit/049202d23614bfe87dc8812190f053dea0a281b1) · 2026-10-16 · 检查／构建 · test(a2ui): 隔离 live 版本单测与 Android 落盘依赖 · 直接提交
- 269 [0dfbea95717c](https://github.com/3316891527/Operit/commit/0dfbea95717c61ae0c187088744e30a94c6e1251) · 2026-10-16 · 功能／修复／重构 · refactor(a2ui): 统一组件命名与私有状态目录 · 直接提交
- 270 [d8a7d2a6b04a](https://github.com/3316891527/Operit/commit/d8a7d2a6b04a9fb2343abc003dd4ee2a2fcce4c5) · 2026-10-16 · 功能／修复／重构 · fix(a2ui): 修正 live 输入校验与错误分类 · 直接提交
- 271 [66d9152da7f7](https://github.com/3316891527/Operit/commit/66d9152da7f7c111b0450d798b36d152e45b09fb) · 2026-10-16 · 功能／修复／重构 · fix(network): 统一传输错误 JSON 码 · 直接提交
- 272 [8c40a9d90174](https://github.com/3316891527/Operit/commit/8c40a9d901745b762c8008b517f7a6bf76ff0697) · 2026-10-16 · 功能／修复／重构 · fix(network): 补齐传输错误依赖导入 · 直接提交
- 273 [f43694ead879](https://github.com/3316891527/Operit/commit/f43694ead87982d7aed627bf6230870cdd4c286f) · 2026-10-16 · 检查／构建 · test(network): 补齐传输错误测试依赖导入 · 直接提交
- 274 [387ee3e8eb73](https://github.com/3316891527/Operit/commit/387ee3e8eb73a13ed827ef38a3283c6a14fd418c) · 2026-10-16 · 发行整理 · release(distribution): 发行版本设为 1.12.2+2 / versionCode 58 · 直接提交
- 275 [732cd8bf44b1](https://github.com/3316891527/Operit/commit/732cd8bf44b1257d0ce80043f49cb5d84ae00100) · 2026-10-16 · 功能／修复／重构 · fix(distribution): 排除终端传递的完整图标库并补齐图标子集 · 直接提交
- 276 [166a8f9e6d38](https://github.com/3316891527/Operit/commit/166a8f9e6d38f906410928d84a741e2b34062a22) · 2026-10-16 · 功能／修复／重构 · fix(ci): 图标引用检查改用 git ls-files，不再依赖 rg · 直接提交

</details>

<details><summary>v1.12.2+3 源码截止 · 14 条提交</summary>

- 277 [94a5313f739c](https://github.com/3316891527/Operit/commit/94a5313f739c652edd631e48c3a70e2a89877bad) · 2026-10-16 · 发行整理 · fix(distribution): 修复终端配置和资源删除，准备 1.12.2+3 调试版 · 直接提交
- 278 [eec347207321](https://github.com/3316891527/Operit/commit/eec3472073216ce34e83fa1d2082939802200c42) · 2026-10-16 · 功能／修复／重构 · fix(distribution): 恢复中断的终端删除并同步未安装状态 · 直接提交
- 279 [6808131a88bf](https://github.com/3316891527/Operit/commit/6808131a88bf2759fc6d614a6de9aa44c8220b7d) · 2026-10-16 · 功能／修复／重构 · 修复 Ubuntu 资源删除竞态 · 直接提交
- 280 [e12804ea13a2](https://github.com/3316891527/Operit/commit/e12804ea13a2b01e72999c02392bc343b29e44ce) · 2026-10-16 · 功能／修复／重构 · fix(resources): 清理无读取权限的终端目录并保留删除结果 · 直接提交
- 281 [df8fc803583e](https://github.com/3316891527/Operit/commit/df8fc803583eb8349475ea16f8e7b0e42f3348e6) · 2026-10-16 · 功能／修复／重构 · fix(resources): 修复零权限目录的权限恢复 · 直接提交
- 282 [95c7768b5f09](https://github.com/3316891527/Operit/commit/95c7768b5f09491b2908d2911e5eccf501facce4) · 2026-10-16 · 检查／构建 · test(resources): 按真实目录权限修正清理回归 · 直接提交
- 283 [285c628f6dab](https://github.com/3316891527/Operit/commit/285c628f6dab448de4fd3cc95bf80b1fb9490a10) · 2026-10-16 · 功能／修复／重构 · feat(theme): 支持气泡和输入框背景百分比透明度 · 直接提交
- 284 [0eb5674a381f](https://github.com/3316891527/Operit/commit/0eb5674a381fe0a0a1152fcb561dbc0da5fd15b0) · 2026-10-16 · 功能／修复／重构 · fix(market): 默认关闭插件市场精选筛选 · 直接提交
- 286 [ed386cc8bf87](https://github.com/3316891527/Operit/commit/ed386cc8bf8751c23ee6f009784c1852f58010cd) · 2026-10-16 · 功能／修复／重构 · fix(settings): 清理重复提醒并修正协议显示 · 直接提交
- 287 [73ed15702523](https://github.com/3316891527/Operit/commit/73ed157025234bea5e3632122d456a5129add8bd) · 2026-10-16 · 功能／修复／重构 · feat(onboarding): 重构单页首次使用流程 · 直接提交
- 288 [5fa6de67925f](https://github.com/3316891527/Operit/commit/5fa6de67925f4b726be186838d53bb4a252c1070) · 2026-10-17 · 功能／修复／重构 · feat(onboarding): 接入完整用户协议并重设计欢迎页 · 直接提交
- 289 [9e18fca73b2f](https://github.com/3316891527/Operit/commit/9e18fca73b2f284e2550c5f96a26e857e974cc5d) · 2026-10-17 · 功能／修复／重构 · fix(onboarding): 协议确认不再记录版本与日期 · 直接提交
- 290 [ee0c00701148](https://github.com/3316891527/Operit/commit/ee0c0070114858845416be2c3399737079820a96) · 2026-10-17 · 功能／修复／重构 · feat(onboarding): 重做启动页并整理协议正文 · 直接提交
- 291 [9d89cc3479b3](https://github.com/3316891527/Operit/commit/9d89cc3479b32eca376d35d1f07566b06dd25bc0) · 2026-10-17 · 功能／修复／重构 · fix(onboarding): 修正首次启动状态检查 · 直接提交

</details>

<details><summary>v1.12.2+4 源码截止 · 5 条提交</summary>

- 292 [721688b22b0a](https://github.com/3316891527/Operit/commit/721688b22b0a383a8864fae802d3f0ae1e2df425) · 2026-10-17 · 功能／修复／重构 · fix: 分块回填超长消息正文 · 直接提交
- 293 [0dbd1c8afd54](https://github.com/3316891527/Operit/commit/0dbd1c8afd546c0d9dff5b413bd3c2f03bb26176) · 2026-10-17 · 功能／修复／重构 · fix: 修复终端主题和独立更新下载 · 直接提交
- 294 [c86235ea9bf7](https://github.com/3316891527/Operit/commit/c86235ea9bf783303f25c0a88c9438665d51e770) · 2026-10-17 · 功能／修复／重构 · fix(skills): 从个人仓库获取新版开发技能安装脚本 · 直接提交
- 295 [832c8abd316e](https://github.com/3316891527/Operit/commit/832c8abd316ec34c33e1ebcf7613434d0ddc5222) · 2026-10-17 · 功能／修复／重构 · fix(skills): 从独立文档仓库下载开发参考文档 · 直接提交
- 296 [983109145fd4](https://github.com/3316891527/Operit/commit/983109145fd422c116e57979a32c4a112b6e192f) · 2026-10-17 · 功能／修复／重构 · fix(distribution): 发布 1.12.2+4 并补全技能示例 · 直接提交

</details>

<details><summary>v1.12.2+5 源码截止 · 10 条提交</summary>

- 297 [eacd0c084281](https://github.com/3316891527/Operit/commit/eacd0c084281ebe16004892ef1211788c1dba7cb) · 2026-10-17 · 功能／修复／重构 · chore: 从 assets 移除内置签名密钥库 · 直接提交
- 298 [9a89e4585dde](https://github.com/3316891527/Operit/commit/9a89e4585ddec55d369c82b60173d2d2755606cc) · 2026-10-17 · 功能／修复／重构 · fix: 修复终端键盘遮挡、补丁选择和模型提示 · 直接提交
- 299 [0cffc3b55d8e](https://github.com/3316891527/Operit/commit/0cffc3b55d8ed4e0b4a10adf651ef77cc560981d) · 2026-10-17 · 功能／修复／重构 · fix: 统一页面提示并修正终端键盘视口 · 直接提交
- 300 [3e79e54c2b2a](https://github.com/3316891527/Operit/commit/3e79e54c2b2a552163bbe75df0143c0673332ed6) · 2026-10-17 · 功能／修复／重构 · fix: 修复思考首字超时并完善对话和终端入口 · 直接提交
- 301 [9d053ef08171](https://github.com/3316891527/Operit/commit/9d053ef08171192f76280444e6c7e292b0b21f74) · 2026-10-17 · 功能／修复／重构 · fix: 区分脚本执行日志与最终结果 · 直接提交
- 302 [b659b1009544](https://github.com/3316891527/Operit/commit/b659b1009544a3324456ce517dc7907e3701dadc) · 2026-10-17 · 功能／修复／重构 · fix: 修复 Toast 替换导致的 Kotlin 编译错误 · 直接提交
- 303 [46e5ebcc7a12](https://github.com/3316891527/Operit/commit/46e5ebcc7a12f21edc36090dca663ab6796fe5ba) · 2026-10-18 · 功能／修复／重构 · fix: 修正终端键盘布局并更新通知品牌 · 直接提交
- 304 [ff0b0f73a038](https://github.com/3316891527/Operit/commit/ff0b0f73a038f591be905e8e549bfc436168b3cc) · 2026-10-18 · 功能／修复／重构 · fix: 修复终端键盘抬升并用 Beta 开关控制补丁更新 · 直接提交
- 305 [7e539475da23](https://github.com/3316891527/Operit/commit/7e539475da231322218f9e78374ffccfef944ccd) · 2026-10-18 · 功能／修复／重构 · fix: 将内置工具执行超时按失败返回 · 直接提交
- 306 [a745b398d576](https://github.com/3316891527/Operit/commit/a745b398d5766ee3c7dc6b7429b56f360bf7bd21) · 2026-10-18 · 文档／语言 · docs: 补录 TODO 功能验收结果 · 直接提交

</details>

<details><summary>v1.12.2+6 源码截止 · 22 条提交</summary>

- 285 [c2baad1eb473](https://github.com/3316891527/Operit/commit/c2baad1eb473f07bfbe8f68199226b3335edfd75) · 2026-10-17 · 功能／修复／重构 · chore: 从 assets 移除内置签名密钥库 · 直接提交
- 307 [19247194c8e8](https://github.com/3316891527/Operit/commit/19247194c8e8fddbbe2901123ff5d64932279809) · 2026-10-18 · 合并／集成 · release: 合入 1.12.2+5 修复与验证补录 (#5) · 直接提交
- 308 [0caccba48452](https://github.com/3316891527/Operit/commit/0caccba48452a1a3690f43c2f4a630f24fa2ec8f) · 2026-10-19 · 功能／修复／重构 · feat: 增加聊天归档并整理工作区与资料入口 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 309 [bb600a57b225](https://github.com/3316891527/Operit/commit/bb600a57b22509b47abf2a20bd31ba65b11e3520) · 2026-10-19 · 功能／修复／重构 · fix: 补齐工作区状态订阅的 Compose 导入 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 310 [722a08c35e03](https://github.com/3316891527/Operit/commit/722a08c35e03e2ee33538670d1ab80a0e22a5886) · 2026-10-19 · 功能／修复／重构 · fix(chat): 修正当前会话删除检查与锁定误报 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 311 [d6aee2465414](https://github.com/3316891527/Operit/commit/d6aee24654142d65e794026ae2a350faf992ea63) · 2026-10-19 · 合并／集成 · merge: 汇总聊天删除状态修复 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 312 [fdb9d4334ee6](https://github.com/3316891527/Operit/commit/fdb9d4334ee61237843fb2975911d5ee5f3eccd1) · 2026-10-19 · 功能／修复／重构 · fix: 修复请求错误静默结束并区分重试策略 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 313 [89108f540bd0](https://github.com/3316891527/Operit/commit/89108f540bd0e815611b14b61008a89b0857129f) · 2026-10-19 · 合并／集成 · merge: 汇总请求错误反馈与重试修复 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 314 [a9a33815e8a7](https://github.com/3316891527/Operit/commit/a9a33815e8a77aec80893ae1a08fa53d1c33304d) · 2026-10-19 · 功能／修复／重构 · feat: 增加聊天图片全屏预览与应用内相册选择器 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 315 [bc35908915b6](https://github.com/3316891527/Operit/commit/bc35908915b673ff2746ac455afb74786f6c5037) · 2026-10-19 · 合并／集成 · merge: 汇总图片选择与预览供统一测试 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 316 [7f9d45ce0aca](https://github.com/3316891527/Operit/commit/7f9d45ce0aca0437bc23a6724b72ca49867d4c94) · 2026-10-19 · 功能／修复／重构 · fix(profile): 恢复小图标并直达完整资料编辑页 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 317 [79fdb2e7a02c](https://github.com/3316891527/Operit/commit/79fdb2e7a02c972e0b34eb06bd0f3983307811b5) · 2026-10-19 · 合并／集成 · merge: 汇总角色资料入口修正供统一测试 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 318 [7af422088b2c](https://github.com/3316891527/Operit/commit/7af422088b2cd888afd6c17aaba3faf66a1702f6) · 2026-10-19 · 功能／修复／重构 · feat(logging): 增加每日恢复开启的日志写入开关 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 319 [634542f22dff](https://github.com/3316891527/Operit/commit/634542f22dffc7411412219f78c2b0b3688ee836) · 2026-10-19 · 合并／集成 · merge: 汇总日志写入开关供统一测试 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 320 [64c280b9382e](https://github.com/3316891527/Operit/commit/64c280b9382e8991b85e659f6fea447a8f9af68b) · 2026-10-19 · 功能／修复／重构 · feat(chat): 将语音通话迁到附件菜单并禁用空白发送 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 321 [ed7059de547b](https://github.com/3316891527/Operit/commit/ed7059de547b225c0e043cfd4a01ce51eec18262) · 2026-10-19 · 合并／集成 · merge: 汇总语音通话入口调整供统一测试 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 322 [ca96a5ee6df4](https://github.com/3316891527/Operit/commit/ca96a5ee6df4bfddf86ddc473f8e444f15764150) · 2026-10-19 · 功能／修复／重构 · feat(chat): 接入无用户消息的手动继续生成 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 323 [1576d70ae580](https://github.com/3316891527/Operit/commit/1576d70ae5807e34d3e47a90a3929d2eede6880a) · 2026-10-19 · 合并／集成 · merge: 汇总继续生成并更新完整统一测试清单 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 324 [42051a70ff05](https://github.com/3316891527/Operit/commit/42051a70ff05784d0f45a348342296726989ec20) · 2026-10-19 · 发行整理 · chore(release): 记录统一验收通过并准备 1.12.2+6 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 325 [f193c48282a6](https://github.com/3316891527/Operit/commit/f193c48282a625e64e453fdd202380aaff2dfeb4) · 2026-10-20 · 合并／集成 · feat: 合入已验收聊天体验并准备 1.12.2+6 · [本仓库 #7](https://github.com/3316891527/Operit/pull/7)
- 401 [e7ceaed8d0f6](https://github.com/3316891527/Operit/commit/e7ceaed8d0f6ea865f795bc825bcf58f7172adda) · 2026-10-18 · 合并／集成 · release: 同步 1.12.2+5 到 main (#6) · 直接提交
- 402 [99d2edeafb89](https://github.com/3316891527/Operit/commit/99d2edeafb89cab1743b94be1b21c7f205fb65c2) · 2026-10-20 · 合并／集成 · release: 同步 1.12.2+6 到 main · 直接提交

</details>

<details><summary>v1.12.2+7 源码截止 · 6 条提交</summary>

- 326 [ebe507e4d503](https://github.com/3316891527/Operit/commit/ebe507e4d5030fd1ad6523033f6be354d5c12841) · 2026-10-20 · 功能／修复／重构 · feat: 精简对话列表并统一短动画与 OKToast · [本仓库 #16](https://github.com/3316891527/Operit/pull/16)
- 327 [7a55b067b692](https://github.com/3316891527/Operit/commit/7a55b067b6926069a5dfa76858207f9b4d83390b) · 2026-10-20 · 功能／修复／重构 · fix: 调整 OKToast 比例布局并处理缺失模型配置与请求失败 · [本仓库 #16](https://github.com/3316891527/Operit/pull/16)
- 328 [e925c599baae](https://github.com/3316891527/Operit/commit/e925c599baae5b249e2ee6d1d24f265bec35db92) · 2026-10-20 · 功能／修复／重构 · fix: 配置与 HTTP 请求错误统一使用上方提示卡片 · [本仓库 #16](https://github.com/3316891527/Operit/pull/16)
- 329 [e6b770264ac9](https://github.com/3316891527/Operit/commit/e6b770264ac94eaf7a97990b9e29c71bbe4c7373) · 2026-10-20 · 发行整理 · release: 准备 1.12.2+7 并分离发行说明与增量元数据 · [本仓库 #16](https://github.com/3316891527/Operit/pull/16)
- 330 [2162097a5e66](https://github.com/3316891527/Operit/commit/2162097a5e66933408aa388b7682244932afe99c) · 2026-10-20 · 合并／集成 · feat: 对话列表、四态提示与 1.12.2+7 发行准备 (#16) · [本仓库 #16](https://github.com/3316891527/Operit/pull/16)
- 403 [9300be6442fe](https://github.com/3316891527/Operit/commit/9300be6442fe66353bd857a8ca226aa2f3c19d1f) · 2026-10-20 · 合并／集成 · release: 同步 1.12.2+7 到 main (#17) · 直接提交

</details>

<details><summary>v1.12.2+8 源码截止 · 3 条提交</summary>

- 331 [49db947cf3a5](https://github.com/3316891527/Operit/commit/49db947cf3a521867fe4c4b19966acd7b58bd8d9) · 2026-10-21 · 功能／修复／重构 · feat: 完善对话管理、模型工作区与主题交互 (#18) · [本仓库 #18](https://github.com/3316891527/Operit/pull/18)
- 332 [462e05202f03](https://github.com/3316891527/Operit/commit/462e05202f038781036df93693ff2dbd3206fa1e) · 2026-10-21 · 发行整理 · release: 准备 1.12.2+8 版本与发布说明 (#19) · 直接提交
- 404 [4820c82b528a](https://github.com/3316891527/Operit/commit/4820c82b528a6dc304ebfb44541466878ee5cb16) · 2026-10-21 · 合并／集成 · release: 同步 dev 并准备 1.12.2+8 (#20) · 直接提交

</details>

<details><summary>v1.12.2+9 源码截止 · 6 条提交</summary>

- 333 [632e16486a3f](https://github.com/3316891527/Operit/commit/632e16486a3f07b10a84ee79003d92a44c5d085e) · 2026-10-21 · 功能／修复／重构 · 完善模型配置、工作流与主题显示设置并支持对话批量移动 · [本仓库 #21](https://github.com/3316891527/Operit/pull/21)
- 334 [0c6a1d515404](https://github.com/3316891527/Operit/commit/0c6a1d515404979404c35b22108f8e590e0d4dd1) · 2026-10-22 · 功能／修复／重构 · 修复重试与插件大结果分配并分类整理 TODO · [本仓库 #21](https://github.com/3316891527/Operit/pull/21)
- 335 [20b021785106](https://github.com/3316891527/Operit/commit/20b0217851067985fa3a19b0a59b393606c26ef5) · 2026-10-22 · 功能／修复／重构 · 移除内置表情与恢复默认并处理空分组 · [本仓库 #21](https://github.com/3316891527/Operit/pull/21)
- 336 [6a02595b3fd0](https://github.com/3316891527/Operit/commit/6a02595b3fd06265458a5da8ae564e4d1c24bcde) · 2026-10-23 · 合并／集成 · 完善配置、主题与工作流并修复插件内存分配和内置表情清理 (#21) · [本仓库 #21](https://github.com/3316891527/Operit/pull/21)
- 337 [aac8a2eca557](https://github.com/3316891527/Operit/commit/aac8a2eca5578b434d633a9eec3e220558e1010a) · 2026-10-23 · 发行整理 · release: 准备 1.12.2+9 版本与发行说明 (#22) · 直接提交
- 405 [8eacc9f4788a](https://github.com/3316891527/Operit/commit/8eacc9f4788ad84ad034bbb7c26d16e2bc6682c2) · 2026-10-23 · 合并／集成 · release: 同步 dev 并准备 1.12.2+9 (#23) · 直接提交

</details>

<details><summary>v1.12.2+10 源码截止 · 2 条提交</summary>

- 338 [083b1d1bce13](https://github.com/3316891527/Operit/commit/083b1d1bce137c74b55003f1f6f96f8e6a496a01) · 2026-10-23 · 功能／修复／重构 · feat(settings): 整合数据备份、主题与角色配置 (#24) · [本仓库 #24](https://github.com/3316891527/Operit/pull/24)
- 406 [56bb1493113d](https://github.com/3316891527/Operit/commit/56bb1493113dce5f70bb7c5bccbfc936378c0784) · 2026-10-23 · 合并／集成 · release: 同步 dev 并准备 1.12.2+10 (#26) · 直接提交

</details>

<details><summary>v1.12.2+11 源码截止 · 35 条提交</summary>

- 339 [319d1b6e5f31](https://github.com/3316891527/Operit/commit/319d1b6e5f319c37d672b745b67a210dc79dea9b) · 2026-10-23 · 功能／修复／重构 · fix(tools): 补充包工具 JSON 参数错误诊断 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 340 [c9760950066b](https://github.com/3316891527/Operit/commit/c9760950066b68663f07f6183d60f988981a75c1) · 2026-10-23 · 合并／集成 · merge: 合入 fix/package-proxy-json-diagnostics 供统一联调 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 341 [7f2bbfefc3a1](https://github.com/3316891527/Operit/commit/7f2bbfefc3a129f34b3e07a382bf13e701d095f8) · 2026-10-23 · 功能／修复／重构 · fix(a2ui): 统一编译说明和围栏并由宿主投递卡片 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 342 [9702e7de47ae](https://github.com/3316891527/Operit/commit/9702e7de47aeaf62d5ca72d038339f63f1bc010d) · 2026-10-23 · 合并／集成 · merge: 合入 fix/a2ui-generation-delivery 供统一联调 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 343 [4796bce5f9ef](https://github.com/3316891527/Operit/commit/4796bce5f9ef1c262000b48662d4fa7260eae915) · 2026-10-23 · 功能／修复／重构 · fix(llm): 保留交错工具调用与最终快照的参数 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 344 [e1696ce11774](https://github.com/3316891527/Operit/commit/e1696ce117745584d6bb0071d6e84b59b129fb39) · 2026-10-23 · 检查／构建 · test(llm): 隔离工具参数流回归的 Android 日志 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 345 [db2d5db952de](https://github.com/3316891527/Operit/commit/db2d5db952de94efeb9372cc6551ece4213e44da) · 2026-10-23 · 合并／集成 · merge: 合入 fix/interleaved-tool-call-arguments 供统一联调 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 346 [25ab878642b3](https://github.com/3316891527/Operit/commit/25ab878642b3e6d06a4940fdc2eae7e0b4f8a79f) · 2026-10-23 · 功能／修复／重构 · fix(ui): 移除崩溃报告换行开关的灰色涟漪 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 347 [866482594af3](https://github.com/3316891527/Operit/commit/866482594af3319ca1ae2a7dbc24d063d7713e19) · 2026-10-23 · 合并／集成 · merge: 合入 fix/crash-report-wrap-feedback 供统一联调 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 348 [b917b3924069](https://github.com/3316891527/Operit/commit/b917b3924069e894fcff1a7e066ffa0b55cda45e) · 2026-10-23 · 文档／语言 · docs(ui): 增加离线资源与本地模型交互设计稿 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 349 [aba36dd2c619](https://github.com/3316891527/Operit/commit/aba36dd2c6194f01eaa64c7be1e4da16568bc20d) · 2026-10-23 · 合并／集成 · merge: 合入 feat/offline-resource-hub 供统一联调 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 350 [ec75dfb48be1](https://github.com/3316891527/Operit/commit/ec75dfb48be157ca496b8383266bd06372ef7b20) · 2026-10-23 · 文档／语言 · docs(ui): 增加模型与对话设置交互设计稿 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 351 [51641db8c2f2](https://github.com/3316891527/Operit/commit/51641db8c2f2753641770665915db26e996a1156) · 2026-10-23 · 文档／语言 · docs(ui): 基于原第二版修订四页签与功能分配 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 352 [4e0252774770](https://github.com/3316891527/Operit/commit/4e025277477078da6766d61bbd072161cb26aa7a) · 2026-10-23 · 功能／修复／重构 · feat(settings): 将完整上下文总结接入四标签模型工作区 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 353 [816a23ab64f0](https://github.com/3316891527/Operit/commit/816a23ab64f0c1b2da0a83a17969b211f2b7faac) · 2026-10-23 · 合并／集成 · merge: 合入 feat/model-settings-hub 供统一联调 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 354 [b561522caaf9](https://github.com/3316891527/Operit/commit/b561522caaf94e22d79292abb7ab2b6d6237899b) · 2026-10-23 · 文档／语言 · docs: 记录六个分支的统一联调范围与验证入口 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 355 [2230a7c95d3e](https://github.com/3316891527/Operit/commit/2230a7c95d3e64fda9546c248ad3a38dfc3b2d33) · 2026-10-23 · 功能／修复／重构 · fix(terminal): 将所有非零退出码统一判为失败 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 356 [44d4b9280b35](https://github.com/3316891527/Operit/commit/44d4b9280b35253dfc1d8a5cef37262fdc3c3f3c) · 2026-10-23 · 功能／修复／重构 · feat(chat): 支持独立语音条录制、试听和 STT 文字请求 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 357 [d81c402f5f13](https://github.com/3316891527/Operit/commit/d81c402f5f13d9adbe9a8a28efac05f4575695f0) · 2026-10-24 · 功能／修复／重构 · fix(chat): 修正语音发送提前返回的任务类型 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 358 [ca726e6af977](https://github.com/3316891527/Operit/commit/ca726e6af977564dc4cd91d4b434f70c1e6c7472) · 2026-10-24 · 功能／修复／重构 · fix(chat): 收紧语音气泡并重设计录音面板与波形反馈 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 359 [ccafa6f2e435](https://github.com/3316891527/Operit/commit/ccafa6f2e4357a987a77b6eb17e5f3d67bb2d911) · 2026-10-24 · 功能／修复／重构 · feat(chat): 接入语音音频理解顺序并修正命令框对齐 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 360 [a3bfde68f3ef](https://github.com/3316891527/Operit/commit/a3bfde68f3eff27e7882acb1c01257bec172650d) · 2026-10-24 · 文档／语言 · docs(memory): 新增记忆空间入口与图谱 HTML 交互稿 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 361 [f749f61aa399](https://github.com/3316891527/Operit/commit/f749f61aa399689612a847185ff2c7157e155197) · 2026-10-24 · 文档／语言 · docs(memory): 按反馈完善第二版空间入口与图谱交互稿 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 362 [916345f0620b](https://github.com/3316891527/Operit/commit/916345f0620b412836c673935ad49884c154a5cd) · 2026-10-24 · 功能／修复／重构 · feat(memory): 落地记忆空间入口与图谱工作台 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 363 [8a70022e1c4a](https://github.com/3316891527/Operit/commit/8a70022e1c4a9ac8e5269110a05c4a598826954e) · 2026-10-24 · 功能／修复／重构 · fix(memory): 补齐记忆工作台 Material 图标子集 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 364 [a5a1d744370c](https://github.com/3316891527/Operit/commit/a5a1d744370c041b1d81e051e33891a13bd95c01) · 2026-10-24 · 功能／修复／重构 · fix(keypool): 缩短冷却并完善恢复与状态提示 · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 365 [fe8ff0e9292d](https://github.com/3316891527/Operit/commit/fe8ff0e9292d26486b20e1a96c680d1cac4ae202) · 2026-10-24 · 合并／集成 · Merge pull request #33 from 3316891527/fix/key-pool-cooldown-recovery · [本仓库 #27](https://github.com/3316891527/Operit/pull/27)
- 366 [ecc46741d66a](https://github.com/3316891527/Operit/commit/ecc46741d66a7e447d354780b829afef89d49f09) · 2026-10-24 · 功能／修复／重构 · fix(memory): 改善选择弹窗与默认空间删除状态 · [本仓库 #34](https://github.com/3316891527/Operit/pull/34)
- 367 [6cf6331cdc1f](https://github.com/3316891527/Operit/commit/6cf6331cdc1f542ef57cfc28b6b2dcb8200b7cac) · 2026-10-24 · 文档／语言 · chore(i18n): 补齐多语言缺项并记录日语剩余范围 · [本仓库 #34](https://github.com/3316891527/Operit/pull/34)
- 368 [861009bde173](https://github.com/3316891527/Operit/commit/861009bde17378d3fc0184e2679c808215f0c301) · 2026-10-24 · 文档／语言 · chore(i18n): 完成日语缺项补译与八语种资源核对 · [本仓库 #34](https://github.com/3316891527/Operit/pull/34)
- 369 [00014395f7ac](https://github.com/3316891527/Operit/commit/00014395f7ac2497b7934103997236a468cecdf7) · 2026-10-24 · 合并／集成 · Merge pull request #34: 修正记忆选择器并补齐八语种翻译 · [本仓库 #34](https://github.com/3316891527/Operit/pull/34)
- 370 [0d8bc9455dc4](https://github.com/3316891527/Operit/commit/0d8bc9455dc451dec52de28ac23d3f1c160cc2d1) · 2026-10-24 · 功能／修复／重构 · refactor(resources): 清理工具箱与旧用户偏好86个废弃字符串 · [本仓库 #35](https://github.com/3316891527/Operit/pull/35)
- 371 [8eef0fbc802d](https://github.com/3316891527/Operit/commit/8eef0fbc802d6a0eacc4ba1d5b78b26b9a6603d1) · 2026-10-24 · 检查／构建 · ci(release): 准备1.12.2+11独立发行 · [本仓库 #35](https://github.com/3316891527/Operit/pull/35)
- 372 [0c0293314e29](https://github.com/3316891527/Operit/commit/0c0293314e295a8e44f4c7c35075750e8a1ed8a8) · 2026-10-24 · 合并／集成 · Merge pull request #35: 清理废弃资源并准备1.12.2+11 · [本仓库 #35](https://github.com/3316891527/Operit/pull/35)
- 407 [cfa62ff1031b](https://github.com/3316891527/Operit/commit/cfa62ff1031b76fed7dc123ff1027e8761200d34) · 2026-10-24 · 合并／集成 · Merge pull request #36: 发布同步1.12.2+11 · 直接提交

</details>

<details><summary>v1.12.2+12 源码截止 · 2 条提交</summary>

- 373 [5d234fb6e39c](https://github.com/3316891527/Operit/commit/5d234fb6e39c090623858eea1afa90c73d5f497e) · 2026-10-25 · 功能／修复／重构 · fix(ui): 修复界面媒体交互并准备1.12.2+12 (#37) · [本仓库 #37](https://github.com/3316891527/Operit/pull/37)
- 408 [4f88f59780e2](https://github.com/3316891527/Operit/commit/4f88f59780e2054d336e1bedf67672929219b5d2) · 2026-10-25 · 合并／集成 · Merge pull request #38: 同步dev并准备1.12.2+12 · 直接提交

</details>

<details><summary>v1.12.2+13 源码截止 · 10 条提交</summary>

- 374 [e203baddd621](https://github.com/3316891527/Operit/commit/e203baddd62145cbeac5806b5c04fd6127e83fd5) · 2026-10-25 · 功能／修复／重构 · fix(chat): 拆分发送协程并完善取消收尾 (#39) · [本仓库 #39](https://github.com/3316891527/Operit/pull/39)
- 375 [e0f8f17105be](https://github.com/3316891527/Operit/commit/e0f8f17105beed8aa869e5738aeef802b50cd117) · 2026-10-25 · 功能／修复／重构 · fix(update): 按实际字节范围筛选镜像并校验补丁分段 (#40) · [本仓库 #40](https://github.com/3316891527/Operit/pull/40)
- 376 [4d6578070e13](https://github.com/3316891527/Operit/commit/4d6578070e1344d920edaea804c7b1e75bacfee5) · 2026-10-25 · 功能／修复／重构 · fix(voice): 将音频理解限定为原话转写 (#41) · [本仓库 #41](https://github.com/3316891527/Operit/pull/41)
- 377 [96b44e58e05f](https://github.com/3316891527/Operit/commit/96b44e58e05f7539f253cfa77d16787cb7a31f66) · 2026-10-25 · 功能／修复／重构 · fix(chat): 缩小图片过期角标并将过期语音移出气泡 (#42) · [本仓库 #42](https://github.com/3316891527/Operit/pull/42)
- 378 [3a232219da63](https://github.com/3316891527/Operit/commit/3a232219da63454491ef79394613378cbac23465) · 2026-10-25 · 功能／修复／重构 · feat: 语音原话与声音观察分离，累计用量按九类展示 (#43) · [本仓库 #43](https://github.com/3316891527/Operit/pull/43)
- 379 [76336d49b1c8](https://github.com/3316891527/Operit/commit/76336d49b1c8766e7cada4a34b5e88ac2014ddcf) · 2026-10-25 · 功能／修复／重构 · fix: 保留终端失败输出并去除重复错误 (#44) · [本仓库 #44](https://github.com/3316891527/Operit/pull/44)
- 380 [4385d4aaef12](https://github.com/3316891527/Operit/commit/4385d4aaef129390169ec2c36236a3bddf7b0b0e) · 2026-10-25 · 功能／修复／重构 · fix(chat): 对齐过期媒体标签并保留音频消息头像 (#45) · [本仓库 #45](https://github.com/3316891527/Operit/pull/45)
- 381 [fc74dbf78a93](https://github.com/3316891527/Operit/commit/fc74dbf78a939f1c77a858d2abefe2c79e4b44d5) · 2026-10-25 · 功能／修复／重构 · fix(mcp): 改为手动启动并修复作者空值崩溃 (#46) · [本仓库 #46](https://github.com/3316891527/Operit/pull/46)
- 382 [f91ad450373b](https://github.com/3316891527/Operit/commit/f91ad450373b58e7d6bd2ef7891677fda26b93a6) · 2026-10-25 · 检查／构建 · ci(release): 准备 1.12.2+13 正式发行 (#47) · 直接提交
- 409 [2bd3c8e41acb](https://github.com/3316891527/Operit/commit/2bd3c8e41acb4115134296679b5191b55598b481) · 2026-10-25 · 合并／集成 · Merge pull request #48: 同步 dev 并准备 1.12.2+13 · 直接提交

</details>

<details><summary>v1.12.2+14 源码截止 · 7 条提交</summary>

- 383 [3de3d7567600](https://github.com/3316891527/Operit/commit/3de3d7567600036221892016358098719a0899d8) · 2026-10-26 · 功能／修复／重构 · fix(storage): 修复数据管理角色卡与聊天列表头像显示 (#56) · [本仓库 #56](https://github.com/3316891527/Operit/pull/56)
- 384 [a9e0e449957b](https://github.com/3316891527/Operit/commit/a9e0e449957bbb6e36c1a179b882843df331fe80) · 2026-10-25 · 文档／语言 · docs(speech): 添加语音服务配置交互设计稿 · [本仓库 #51](https://github.com/3316891527/Operit/pull/51)
- 385 [a0dac38ae323](https://github.com/3316891527/Operit/commit/a0dac38ae3236b374ac236b2d57f582f8ac74304) · 2026-10-25 · 功能／修复／重构 · feat(settings): 落地语音服务原生工作区 · [本仓库 #51](https://github.com/3316891527/Operit/pull/51)
- 386 [f80db2da93fd](https://github.com/3316891527/Operit/commit/f80db2da93fd51b816ac05f6efff482926760768) · 2026-10-26 · 合并／集成 · feat(settings): 合入原生语音服务工作区 (#53) · [本仓库 #51](https://github.com/3316891527/Operit/pull/51)
- 387 [541a1a144c6f](https://github.com/3316891527/Operit/commit/541a1a144c6fcaf807c15e6ccce228250e4d5295) · 2026-10-26 · 功能／修复／重构 · feat(resources): 离线资源按需分发与模型存储管理 (#54) · [本仓库 #54](https://github.com/3316891527/Operit/pull/54)
- 410 [9572920d76e8](https://github.com/3316891527/Operit/commit/9572920d76e8130817ab0a7914ed59a1957a1e5a) · 2026-10-26 · 合并／集成 · release: 同步 dev 并准备 1.12.2+14 · 直接提交
- 411 [42bbdb65e861](https://github.com/3316891527/Operit/commit/42bbdb65e861fbc6160ac570a0bc33c268c17fcc) · 2026-10-26 · 合并／集成 · release: 同步 dev 并准备 1.12.2+14 (#57) · 直接提交

</details>

<details><summary>v1.12.2+15 源码截止 · 9 条提交</summary>

- 388 [0a1860feb714](https://github.com/3316891527/Operit/commit/0a1860feb7146da45a5b8a38491e3b63f27f7bfe) · 2026-10-26 · 功能／修复／重构 · fix(packages): 保留完整名称并固定四标签等宽布局 (#50) · [本仓库 #50](https://github.com/3316891527/Operit/pull/50)
- 389 [2c48f62f8f56](https://github.com/3316891527/Operit/commit/2c48f62f8f563617d82a628e90471917b7484294) · 2026-10-26 · 功能／修复／重构 · fix(toolpkg): 避免助手语音条挂载时抢焦点 (#52) · [本仓库 #52](https://github.com/3316891527/Operit/pull/52)
- 390 [cbd04afdb66d](https://github.com/3316891527/Operit/commit/cbd04afdb66d62e3123230382659c9740ab82110) · 2026-10-26 · 功能／修复／重构 · fix(chat): 在流式解析前隐藏协议元数据 (#49) · [本仓库 #49](https://github.com/3316891527/Operit/pull/49)
- 391 [558632b41901](https://github.com/3316891527/Operit/commit/558632b41901d0c04c3e0a048f20dcd753c5b309) · 2026-10-26 · 功能／修复／重构 · fix(chat): 阻止重试后工具卡片累积并释放已完成子流 (#55) · [本仓库 #55](https://github.com/3316891527/Operit/pull/55)
- 392 [93503038d45b](https://github.com/3316891527/Operit/commit/93503038d45ba89bb61c32cbb65b9ef801b9a038) · 2026-10-26 · 功能／修复／重构 · feat(distribution): 发布1.12.2+15并分离补丁与资源仓库 (#58) · [本仓库 #58](https://github.com/3316891527/Operit/pull/58)
- 412 [58614403296c](https://github.com/3316891527/Operit/commit/58614403296ca53190b44b83c2f12090414b82a7) · 2026-10-26 · 合并／集成 · ci(release): 同步主分支历史以发布1.12.2+15 · 直接提交
- 413 [17e22f65f9fa](https://github.com/3316891527/Operit/commit/17e22f65f9fafbebe53db8dbadf493f5ec2b70fd) · 2026-10-26 · 合并／集成 · chore(release): 同步 1.12.2+15 并完成发行分仓 (#59) · 直接提交
- 414 [d1232b73a68d](https://github.com/3316891527/Operit/commit/d1232b73a68d5d1ae6c9a88b2769a650b5dbdbd0) · 2026-10-26 · 发行整理 · fix(release): 通过 Release ID 核对并公开迁移草稿 · 直接提交
- 415 [21a31bd14ad9](https://github.com/3316891527/Operit/commit/21a31bd14ad9532e81b99d9218669a23896ce2ee) · 2026-10-26 · 合并／集成 · chore(release): 同步 +15 迁移草稿修复 (#61) · 直接提交

</details>

<details><summary>v1.12.2+16 源码截止 · 14 条提交</summary>

- 393 [6383d701ff9d](https://github.com/3316891527/Operit/commit/6383d701ff9ddcbad1697dadcc72c90457e79422) · 2026-10-26 · 发行整理 · fix(release): 通过 Release ID 核对并公开迁移草稿 (#60) · [本仓库 #60](https://github.com/3316891527/Operit/pull/60)
- 394 [a9db43b8bd71](https://github.com/3316891527/Operit/commit/a9db43b8bd716cda649e1327a8e2729d9cb4e9b2) · 2026-10-26 · 文档／语言 · chore(docs): 删除未引用的 Legacy 图片 · 直接提交
- 395 [6a4b9bfb399c](https://github.com/3316891527/Operit/commit/6a4b9bfb399c670321518089592ae3e02e38897e) · 2026-10-27 · 功能／修复／重构 · fix(chat): 按故障位置细分请求错误并保留后台提示 (#66) · [本仓库 #66](https://github.com/3316891527/Operit/pull/66)
- 396 [3dd12589cae8](https://github.com/3316891527/Operit/commit/3dd12589cae8374379179f4eafb5a77c7daabe4f) · 2026-10-27 · 功能／修复／重构 · fix(packages): 按当前应用真实来源删除扩展并核实结果 (#62) · [本仓库 #62](https://github.com/3316891527/Operit/pull/62)
- 397 [74c8010cf44e](https://github.com/3316891527/Operit/commit/74c8010cf44e4294a146b3f443425809d10f532c) · 2026-10-27 · 功能／修复／重构 · fix(ui): 美化终端选项菜单并保持原导航 (#63) · [本仓库 #63](https://github.com/3316891527/Operit/pull/63)
- 398 [b1d7bc628cfb](https://github.com/3316891527/Operit/commit/b1d7bc628cfbe685f318ca31d1cfea8a3aeadf4b) · 2026-10-27 · 功能／修复／重构 · fix(workflow): 展示失败节点名称、执行动作和具体原因 (#64) · [本仓库 #64](https://github.com/3316891527/Operit/pull/64)
- 399 [7c72bf2a3d5f](https://github.com/3316891527/Operit/commit/7c72bf2a3d5f327139e9f4db976b16b5ebb81815) · 2026-10-27 · 功能／修复／重构 · feat(market): 显示真实下载进度、速度和剩余时间 (#65) · [本仓库 #65](https://github.com/3316891527/Operit/pull/65)
- 400 [3ff9a2a75755](https://github.com/3316891527/Operit/commit/3ff9a2a757554b9cd8e30c745656422016aaf1df) · 2026-10-27 · 功能／修复／重构 · refactor(layout): 整理职责目录、删除废弃界面并修复验收问题 (#67) · [本仓库 #67](https://github.com/3316891527/Operit/pull/67)
- 416 [852ec1e48347](https://github.com/3316891527/Operit/commit/852ec1e48347cdff1fe072a6aab2bad20e544a62) · 2026-10-26 · 文档／语言 · chore(docs): 删除未引用的 Legacy 图片 · 直接提交
- 417 [f4b28b216c53](https://github.com/3316891527/Operit/commit/f4b28b216c53553abf92587a3e621189d5733a07) · 2026-10-27 · 功能／修复／重构 · fix(chat): 按故障位置细分请求错误并保留后台提示 (#66) · 直接提交
- 418 [df0ce4b7d8dd](https://github.com/3316891527/Operit/commit/df0ce4b7d8dd2ef9fcbf681702bf4bf67c90409f) · 2026-10-27 · 合并／集成 · ci(release): 同步主分支历史并保留开发分支完整实现 · 直接提交
- 419 [d836594cc81c](https://github.com/3316891527/Operit/commit/d836594cc81cc4b2cf3bf66c5aad09a97ae45a4d) · 2026-10-27 · 检查／构建 · ci(release): 准备 1.12.2+16 与递增版本号 72 · 直接提交
- 420 [90981cefc2e1](https://github.com/3316891527/Operit/commit/90981cefc2e1e09fad482d256545b63d52dbeec7) · 2026-10-27 · 合并／集成 · Merge pull request #68 from 3316891527/ci/release-1.12.2-plus16 · 直接提交
- 427 [dd0ea02a900f](https://github.com/3316891527/Operit/commit/dd0ea02a900f831a7b94ab3834539db8ae857bb2) · 2026-10-27 · 合并／集成 · Merge pull request #69 from 3316891527/dev · 直接提交

</details>

<details><summary>v1.12.2+17 源码截止 · 14 条提交</summary>

- 421 [886580d4f1f9](https://github.com/3316891527/Operit/commit/886580d4f1f94879954097f7338de0df1e087d7f) · 2026-10-27 · 检查／构建 · ci: 将 PR 检查目标同步为 dev · [本仓库 #70](https://github.com/3316891527/Operit/pull/70)
- 422 [937a6e30b4a7](https://github.com/3316891527/Operit/commit/937a6e30b4a7539866698d8250f2e9e65efd5c8e) · 2026-10-27 · 检查／构建 · ci: 保留完整构建流程并停止创建源码仓库标签 · [本仓库 #70](https://github.com/3316891527/Operit/pull/70)
- 423 [42fc9d9f3362](https://github.com/3316891527/Operit/commit/42fc9d9f3362e9d473e30eec80240c7b4d3596a3) · 2026-10-27 · 文档／语言 · docs(ci): 记录工作流完整结构与发布范围核对 · [本仓库 #70](https://github.com/3316891527/Operit/pull/70)
- 424 [368c4efa7d1e](https://github.com/3316891527/Operit/commit/368c4efa7d1efbc580e1f0addd4b9121a613716e) · 2026-10-27 · 合并／集成 · Merge pull request #70 from 3316891527/ci/fix-workflow-publishing · [本仓库 #70](https://github.com/3316891527/Operit/pull/70)
- 425 [b60e94e33f1b](https://github.com/3316891527/Operit/commit/b60e94e33f1bf084866c630a998924aedf56fa6e) · 2026-10-27 · 功能／修复／重构 · fix(tools): 正确返回包激活失败与不存在错误 (#72) · [本仓库 #72](https://github.com/3316891527/Operit/pull/72)
- 426 [5d0c465657b6](https://github.com/3316891527/Operit/commit/5d0c465657b634782c27646f150784a2142cfcd8) · 2026-10-27 · 功能／修复／重构 · fix(resources): 升级保留已下载扩展及安装状态 (#73) · [本仓库 #73](https://github.com/3316891527/Operit/pull/73)
- 428 [998a9b00fa00](https://github.com/3316891527/Operit/commit/998a9b00fa0053e719b84c0bf4f5d431f83f5483) · 2026-10-27 · 合并／集成 · Merge pull request #71 from 3316891527/dev · 直接提交
- 429 [6f7552a846f0](https://github.com/3316891527/Operit/commit/6f7552a846f0171c867b26981c8b4f9e5b7c60e2) · 2026-10-27 · 合并／集成 · ci(release): 同步主分支历史以准备 +17 · 直接提交
- 430 [62758a0df179](https://github.com/3316891527/Operit/commit/62758a0df1795046573392a36fbc0a5b67335529) · 2026-10-27 · 检查／构建 · ci(release): 准备 1.12.2+17 紧急修复发行 · 直接提交
- 431 [f6bc900a053c](https://github.com/3316891527/Operit/commit/f6bc900a053cb1e6ca3e42c99b8b23d2b17a3bb6) · 2026-10-27 · 合并／集成 · Merge pull request #74 from 3316891527/ci/release-1.12.2-plus17 · 直接提交
- 432 [901471c562ce](https://github.com/3316891527/Operit/commit/901471c562ced9aea321526a49fb01e13f5955df) · 2026-10-27 · 功能／修复／重构 · fix: 合入 +17 统一验收修复与同步扩展资源 (#83) · [本仓库 #83](https://github.com/3316891527/Operit/pull/83)
- 433 [780c5849a315](https://github.com/3316891527/Operit/commit/780c5849a315d1c5ffc6e5a266e83a9827bf50a0) · 2026-10-27 · 功能／修复／重构 · fix(tools): 相同工具失败说明只输出一次 (#84) · [本仓库 #84](https://github.com/3316891527/Operit/pull/84)
- 441 [4179c6ac9810](https://github.com/3316891527/Operit/commit/4179c6ac9810b3b0ae32a40689a4787a893c0e64) · 2026-10-27 · 合并／集成 · Merge pull request #75 from 3316891527/dev · 直接提交
- 442 [2c5c43f16c27](https://github.com/3316891527/Operit/commit/2c5c43f16c27d574c7457bc2fb08e962e0eb1f12) · 2026-10-27 · 合并／集成 · ci(release): 同步 1.12.2+17 最终修复到 main (#85) · 直接提交

</details>

<details><summary>v1.12.2+18 源码截止 · 10 条提交</summary>

- 434 [92a321bdfd7a](https://github.com/3316891527/Operit/commit/92a321bdfd7a74d46b763b2c87dd8af586757151) · 2026-10-29 · 功能／修复／重构 · fix(speech): 修复离线语音崩溃与设置卡顿并完善试听配置 (#86) · [本仓库 #86](https://github.com/3316891527/Operit/pull/86)
- 435 [ea6359c86cd6](https://github.com/3316891527/Operit/commit/ea6359c86cd6fc35bb0a50ba91d5c1e15b37cae9) · 2026-10-28 · 功能／修复／重构 · feat(patch18): 集成工作区、消息、语音、工具计时、终端与资源管理改进 · [本仓库 #87](https://github.com/3316891527/Operit/pull/87)
- 436 [79001e79cb01](https://github.com/3316891527/Operit/commit/79001e79cb01bcd3a10688226212a6d5a5db0ab9) · 2026-10-29 · 功能／修复／重构 · fix(patch18): 修正删除警告、亚秒计时与图片读取失败状态 · [本仓库 #87](https://github.com/3316891527/Operit/pull/87)
- 437 [08a6c89d358e](https://github.com/3316891527/Operit/commit/08a6c89d358ed433e5693fbf77ebd6ac90a77bb1) · 2026-10-29 · 功能／修复／重构 · fix(patch18): 补齐资源导入并统一生成结构化失败结果 · [本仓库 #87](https://github.com/3316891527/Operit/pull/87)
- 438 [5b709b88d979](https://github.com/3316891527/Operit/commit/5b709b88d979a786cfc425c0507d930783f6538e) · 2026-10-29 · 功能／修复／重构 · fix(patch18): 修正流式完成收尾并记录连接中止进度 · [本仓库 #87](https://github.com/3316891527/Operit/pull/87)
- 439 [1962d3afbbba](https://github.com/3316891527/Operit/commit/1962d3afbbba332bbfcf42be6410d663d88c5b48) · 2026-10-29 · 合并／集成 · Merge pull request #87: 集成补丁 18 功能并保留离线语音文案 · [本仓库 #87](https://github.com/3316891527/Operit/pull/87)
- 440 [2f27a844a731](https://github.com/3316891527/Operit/commit/2f27a844a731aef6a1361ac2f0c6219e462835e0) · 2026-10-29 · 发行整理 · chore(release): 准备 1.12.2+18 并固定补丁仓库发布范围 · 直接提交
- 443 [21d01fe38e26](https://github.com/3316891527/Operit/commit/21d01fe38e266f359291085de86b335515a643ba) · 2026-10-29 · 合并／集成 · chore(release): 保留上一版 main 发行历史并同步补丁 18 · 直接提交
- 444 [8df3befaf6dd](https://github.com/3316891527/Operit/commit/8df3befaf6ddf00282034c2153213f2dfdce12e2) · 2026-10-29 · 合并／集成 · chore(release): 合并 1.12.2+18 版本准备 (#88) · 直接提交
- 483 [01cb48ce4768](https://github.com/3316891527/Operit/commit/01cb48ce476835ceacbaf5d23c9d4b2244f4270d) · 2026-10-29 · 合并／集成 · ci(release): 同步 1.12.2+18 到 main (#89) · 直接提交

</details>

<details><summary>v1.12.2+19 源码截止 · 43 条提交</summary>

- 445 [ffb78d3017df](https://github.com/3316891527/Operit/commit/ffb78d3017df07329b8de37af45a4f17e6800d6d) · 2026-10-29 · 检查／构建 · ci: 将构建工作流改名为 build 并更新入口 (#90) · [本仓库 #90](https://github.com/3316891527/Operit/pull/90)
- 446 [718707fd19ab](https://github.com/3316891527/Operit/commit/718707fd19ab7c27e38c8807055c80809d25e6dc) · 2026-10-29 · 功能／修复／重构 · refactor(tools): 收口三档权限实现与 Shizuku 后端命名 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 447 [f33b310ea579](https://github.com/3316891527/Operit/commit/f33b310ea5791946a7f6d07cbfdd6daa22bedd6d) · 2026-10-29 · 功能／修复／重构 · refactor(tools): 按逐文件清单整理内置工具与插件包目录 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 448 [cf31c7c74c25](https://github.com/3316891527/Operit/commit/cf31c7c74c255cd1d314d95ef7d7b9b43a23cfaa) · 2026-10-29 · 功能／修复／重构 · fix(tools): 补齐解析器字符串插值常量导入 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 449 [528a8070cb29](https://github.com/3316891527/Operit/commit/528a8070cb29774133234c353949943b79e640a5) · 2026-10-29 · 功能／修复／重构 · fix(tests): 修复工具卡片测试 XML 字符串引号 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 450 [1cc41dd2c907](https://github.com/3316891527/Operit/commit/1cc41dd2c90709dfd2299981be96ba44660ac0c2) · 2026-10-30 · 合并／集成 · merge: 合入工具目录与三档权限整理 #92 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 451 [a9dd83175295](https://github.com/3316891527/Operit/commit/a9dd831752956dd68fb2edb1395683e47f8a08ff) · 2026-10-29 · 功能／修复／重构 · fix(workspace): 流式复制文件并显示粘贴进度 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 452 [9b9bb75d93b9](https://github.com/3316891527/Operit/commit/9b9bb75d93b96b72204067fa576db30e5279d960) · 2026-10-30 · 功能／修复／重构 · fix(workspace): 补齐单项和批量删除进度窗口 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 453 [de2282dc919d](https://github.com/3316891527/Operit/commit/de2282dc919d4ba7fa17998cee90182034101bb7) · 2026-10-30 · 合并／集成 · merge: 合入工作区文件操作优化 #93 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 454 [d4e90bd1c02e](https://github.com/3316891527/Operit/commit/d4e90bd1c02e703240fbc15b27f71c5c1188a734) · 2026-10-30 · 功能／修复／重构 · refactor(chat): 按职责整理会话和工作区目录 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 455 [3a823f14c298](https://github.com/3316891527/Operit/commit/3a823f14c2984d050f1a7bfef96107c0cad4cfe2) · 2026-10-30 · 合并／集成 · merge: 合入聊天和工作区目录整理 #94 · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 456 [6517f5159330](https://github.com/3316891527/Operit/commit/6517f5159330d1a6c352f1eebb360f169354b363) · 2026-10-31 · 合并／集成 · merge: 集成工具权限、文件操作与聊天目录整理 (#95) · [本仓库 #92](https://github.com/3316891527/Operit/pull/92)
- 457 [029426a5acb7](https://github.com/3316891527/Operit/commit/029426a5acb7da5f6953b77927d8b82fc2ff36cb) · 2026-10-30 · 功能／修复／重构 · fix(test): 修正工具卡流边界用例的 XML 字符串 · [本仓库 #94](https://github.com/3316891527/Operit/pull/94)
- 458 [4bbea330b19d](https://github.com/3316891527/Operit/commit/4bbea330b19df411a93d4704d8e8fe12c8a167b6) · 2026-10-31 · 合并／集成 · refactor(chat): 按职责整理聊天和工作区目录 (#94) · [本仓库 #94](https://github.com/3316891527/Operit/pull/94)
- 459 [de1bc854fed1](https://github.com/3316891527/Operit/commit/de1bc854fed181ebdd1d9ee6091c6adcf7cc405b) · 2026-10-30 · 功能／修复／重构 · fix(ui): 将主题外观标签名称改为界面 · [本仓库 #96](https://github.com/3316891527/Operit/pull/96)
- 460 [f42e8bc06e7e](https://github.com/3316891527/Operit/commit/f42e8bc06e7e9d93946dfedada977f2dbffffd9a) · 2026-10-31 · 合并／集成 · fix(ui): 主题和外观标签使用“界面”名称 (#96) · [本仓库 #96](https://github.com/3316891527/Operit/pull/96)
- 461 [35396470127a](https://github.com/3316891527/Operit/commit/35396470127a4ee7d192e5c1089dc5ea2f862af1) · 2026-10-30 · 功能／修复／重构 · feat(terminal): 内置 Codex apply_patch 并支持现有 Ubuntu 环境自动安装 · [本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 462 [1de577c8ed57](https://github.com/3316891527/Operit/commit/1de577c8ed573bbffa118b317fd44f1e04b7deea) · 2026-10-30 · 功能／修复／重构 · fix(ci): 补齐 apply_patch ARM64 交叉链接开发依赖 · [本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 463 [432c36a61f7e](https://github.com/3316891527/Operit/commit/432c36a61f7eb18fd4723f4dc11671a5c166e69c) · 2026-10-30 · 功能／修复／重构 · feat(terminal): 扩展 apply_patch 参数、内存检查与 JSON 输出 · [本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 464 [4510d853e516](https://github.com/3316891527/Operit/commit/4510d853e516232f688f5af04b29718bb5e3706b) · 2026-10-30 · 功能／修复／重构 · feat(terminal): 增加 apply_patch 禁止覆盖、只读预览与静默规则 · [本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 465 [ba2a64e3e5da](https://github.com/3316891527/Operit/commit/ba2a64e3e5da43e9845f3d7a33dd2a87092d33c7) · 2026-10-30 · 功能／修复／重构 · fix(terminal): 保留 apply_patch 原生路径中的制表符与换行字节 · [本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 466 [05811804cdee](https://github.com/3316891527/Operit/commit/05811804cdeec36a686c5b1ebd65977f5b938426) · 2026-10-31 · 合并／集成 · feat(terminal): 内置 apply_patch 并支持覆盖保护与差异预览 (#97) · [本仓库 #97](https://github.com/3316891527/Operit/pull/97)
- 467 [28f97ea99039](https://github.com/3316891527/Operit/commit/28f97ea99039a1d32d13e20d9e3ef7910694ab5e) · 2026-10-30 · 功能／修复／重构 · fix(voice): 停止重发已拒绝音频并隔离临时错误通知 · [本仓库 #98](https://github.com/3316891527/Operit/pull/98)
- 468 [c56058e4e0dd](https://github.com/3316891527/Operit/commit/c56058e4e0dd6df4e6af5fd934cc04ec8fa253f0) · 2026-10-30 · 功能／修复／重构 · fix(voice): 按硅基流动文档适配 audio_url 音频输入 · [本仓库 #98](https://github.com/3316891527/Operit/pull/98)
- 469 [3e37884024c2](https://github.com/3316891527/Operit/commit/3e37884024c244a565893fbc017c214ac33b5a61) · 2026-10-31 · 合并／集成 · fix(voice): 修正硅基流动音频格式及识别重试通知 (#98) · [本仓库 #98](https://github.com/3316891527/Operit/pull/98)
- 470 [65cf774d8677](https://github.com/3316891527/Operit/commit/65cf774d8677d5348db72d2f31e8c3bf8cc4be55) · 2026-10-31 · 功能／修复／重构 · fix(chat): 简化附件成功提示并支持点击关闭 · [本仓库 #102](https://github.com/3316891527/Operit/pull/102)
- 471 [f562fc8efb8f](https://github.com/3316891527/Operit/commit/f562fc8efb8f215722b95cab118475fcebfa1b96) · 2026-11-01 · 合并／集成 · Merge pull request #102 from 3316891527/fix/attachment-success-toast · [本仓库 #102](https://github.com/3316891527/Operit/pull/102)
- 472 [86aacf8b6123](https://github.com/3316891527/Operit/commit/86aacf8b61236552b087ef71216f27195ee9fb51) · 2026-10-31 · 功能／修复／重构 · fix(chat): 按实际密钥模式生成请求失败提示 · [本仓库 #100](https://github.com/3316891527/Operit/pull/100)
- 473 [75a57a6d04f2](https://github.com/3316891527/Operit/commit/75a57a6d04f26e453eac408dc7d8c9c6dd368ba6) · 2026-10-31 · 检查／构建 · test(chat): 按异常类型校验密钥重试的原始 HTTP 错误 · [本仓库 #100](https://github.com/3316891527/Operit/pull/100)
- 474 [5bf033a35da8](https://github.com/3316891527/Operit/commit/5bf033a35da8511a098bf233392c3037a518bb5b) · 2026-11-01 · 合并／集成 · Merge pull request #100 from 3316891527/fix/key-pool-error-scope · [本仓库 #100](https://github.com/3316891527/Operit/pull/100)
- 475 [8ec87cd22b84](https://github.com/3316891527/Operit/commit/8ec87cd22b84699bd6eda09f65489970a8518603) · 2026-10-31 · 功能／修复／重构 · fix(chat): 实时显示总结重试进度并统一错误提示 · [本仓库 #101](https://github.com/3316891527/Operit/pull/101)
- 476 [53833ba27bb6](https://github.com/3316891527/Operit/commit/53833ba27bb6caea1ac5e7ecae943e33fbce4b81) · 2026-11-01 · 合并／集成 · Merge pull request #101 from 3316891527/fix/summary-retry-notices · [本仓库 #101](https://github.com/3316891527/Operit/pull/101)
- 477 [8f693b22e1b6](https://github.com/3316891527/Operit/commit/8f693b22e1b69729d2a7ac51aaa4470842d063e4) · 2026-10-31 · 功能／修复／重构 · feat(theme): add role-scoped JavaScript appearance API · [本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 478 [e9b4a0635132](https://github.com/3316891527/Operit/commit/e9b4a063513233a00339e84a040c079897608885) · 2026-10-31 · 功能／修复／重构 · feat(theme): 落地原生主题工作室与目录资源工程 · [本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 479 [2531e4953c46](https://github.com/3316891527/Operit/commit/2531e4953c46c5a5d136b796831818d89b4e3cce) · 2026-10-31 · 功能／修复／重构 · fix(theme): 完善外观消费、原生预览与源码编辑 · [本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 480 [a8cb4c99bec1](https://github.com/3316891527/Operit/commit/a8cb4c99bec128e400ae2feca05b08b1f162d6cb) · 2026-11-01 · 功能／修复／重构 · fix(theme): 修正气泡预览的偏好字段读取 · [本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 481 [208ddb515466](https://github.com/3316891527/Operit/commit/208ddb51546632e7032182098c7e31b56074cf1c) · 2026-11-01 · 功能／修复／重构 · fix(theme): 显示工作室背景并补充验收与方向评估 · [本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 482 [85f02f7a4c9f](https://github.com/3316891527/Operit/commit/85f02f7a4c9f630b0bcdfb128bde1d8e62e9b293) · 2026-11-01 · 合并／集成 · Merge pull request #99 from 3316891527/feat/oktheme-script-api · [本仓库 #99](https://github.com/3316891527/Operit/pull/99)
- 484 [cb30c94469e1](https://github.com/3316891527/Operit/commit/cb30c94469e1740b1be609905c12bd639555d168) · 2026-10-29 · 合并／集成 · ci: 同步 build 工作流改名到 main (#91) · 直接提交
- 485 [412d14f85365](https://github.com/3316891527/Operit/commit/412d14f85365a90127c26fe476bea35e4301da1c) · 2026-11-01 · 合并／集成 · chore(release): 接入现有主分支历史准备 +19 · 直接提交
- 486 [c47c24a2bcda](https://github.com/3316891527/Operit/commit/c47c24a2bcda42d761d0c6ce0cdddf7034b97a8f) · 2026-11-01 · 发行整理 · chore(release): 准备 1.12.2+19 发行与增量更新 · 直接提交
- 487 [e829418743de](https://github.com/3316891527/Operit/commit/e829418743de17a23e5cc9efe1caf8ef19735f0f) · 2026-11-01 · 合并／集成 · chore(release): 准备 1.12.2+19 发行 (#103) · 直接提交
- 488 [890e532070f1](https://github.com/3316891527/Operit/commit/890e532070f14eee8b21084bbedbe5c9ab6cb7e1) · 2026-11-01 · 合并／集成 · chore(release): 同步 1.12.2+19 到 main (#104) · 直接提交

</details>

<details><summary>v1.12.2+20 源码截止 · 13 条提交</summary>

- 489 [75703e5bdcd9](https://github.com/3316891527/Operit/commit/75703e5bdcd91322ff236b44db38d32e3f6ee057) · 2026-11-01 · 版本 +20 · fix(theme): 扩大工作室图片与工程资源预算
- 490 [e9cf4f826183](https://github.com/3316891527/Operit/commit/e9cf4f826183772099afc27650d6c8b0101d4e2d) · 2026-11-01 · 版本 +20 · fix(mcp): 已配置服务静默加载并移除加载浮层
- 491 [5e2a1547d3ed](https://github.com/3316891527/Operit/commit/5e2a1547d3ed21119438fa4e6a1593ff58720f80) · 2026-11-01 · 版本 +20 · fix(chat): 修复复制消息面板底色错位与内容裁切
- 492 [536d9e67fea6](https://github.com/3316891527/Operit/commit/536d9e67fea6a582b5a127a5070f6663fce8d3d9) · 2026-11-02 · 版本 +20 · feat(theme): 支持聊天弹起菜单独立形状与位置偏移
- 493 [4224c46c3717](https://github.com/3316891527/Operit/commit/4224c46c3717e6e73394cf490d46dc0629f8ca8d) · 2026-11-02 · 版本 +20 · fix(theme): 互斥菜单上下偏移并补全聊天预览参照
- 494 [d87daaea5d1a](https://github.com/3316891527/Operit/commit/d87daaea5d1af5360fff6ebfc78e9ce0d4901604) · 2026-11-02 · 版本 +20 · fix(theme): 放大小预览并支持滚动定位
- 495 [6df9213d3382](https://github.com/3316891527/Operit/commit/6df9213d338238a30ea6073b4a3eb884b72b3b84) · 2026-11-02 · 版本 +20 · fix(theme): 扩大工作室图片与工程资源预算 (#106)
- 496 [2ba2b7a738b1](https://github.com/3316891527/Operit/commit/2ba2b7a738b11945ca93d63300a74fb22c426399) · 2026-11-02 · 版本 +20 · fix(mcp): 已配置服务静默加载并移除加载浮层 (#107)
- 497 [f838fc666758](https://github.com/3316891527/Operit/commit/f838fc66675829d50c9070421f5ca554c5739469) · 2026-11-02 · 版本 +20 · fix(chat): 修复复制消息面板底色错位与内容裁切 (#108)
- 498 [e0230a247732](https://github.com/3316891527/Operit/commit/e0230a247732b5fe9b5607eafeffeb2ddd11d804) · 2026-11-02 · 版本 +20 · feat(theme): 支持聊天菜单外观、偏移与完整场景预览 (#109)
- 499 [1338163c8e47](https://github.com/3316891527/Operit/commit/1338163c8e470a1e5bcea9775f88aea2b1cd781e) · 2026-11-02 · 版本 +20 · chore(release): 接入现有 main 历史准备 +20
- 500 [33b8e6be24fa](https://github.com/3316891527/Operit/commit/33b8e6be24fa7ca93aeff0e3f00faa0876a944e7) · 2026-11-02 · 版本 +20 · chore(release): 准备 1.12.2+20 发行与增量更新
- 501 [84e6d8940dd9](https://github.com/3316891527/Operit/commit/84e6d8940dd983b69422fa43403e7dfd0009d549) · 2026-11-02 · 版本 +20 · chore(release): 同步 1.12.2+20 到 main (#111)

</details>

<details><summary>v1.12.2+21 源码截止 · 35 条提交</summary>

- 502 [ed184e54d113](https://github.com/3316891527/Operit/commit/ed184e54d1132c263ec30ede54aa94f38ab0b0f7) · 2026-11-02 · 版本 +21 · chore(release): 准备 1.12.2+20 发行 (#110)
- 503 [580b0daf0410](https://github.com/3316891527/Operit/commit/580b0daf0410d7530a94551e30c8a351e166fb46) · 2026-11-02 · 版本 +21 · refactor(model): 按业务领域拆分模型并保留持久化身份
- 504 [559633f959da](https://github.com/3316891527/Operit/commit/559633f959da872e82e22eaa6235c9cf4604c700) · 2026-11-02 · 版本 +21 · refactor(model): 合入模型领域拆分 (#112)
- 505 [12ec78c96a96](https://github.com/3316891527/Operit/commit/12ec78c96a967d950d2c8f55572ea58d7055e783) · 2026-11-02 · 版本 +21 · fix(chat): 过滤历史摘要中的模型协议负载
- 506 [6ff74671b0e7](https://github.com/3316891527/Operit/commit/6ff74671b0e7206529ad45d21a84936c886dadaf) · 2026-11-02 · 版本 +21 · feat(terminal): 接入托管任务并保留现有执行接口
- 507 [1caebe21d378](https://github.com/3316891527/Operit/commit/1caebe21d37809a43bb72d129e2f42510b14fdc0) · 2026-11-02 · 版本 +21 · fix(ci): 按固定资源快照校验开发构建
- 508 [0fc56cbb0f7c](https://github.com/3316891527/Operit/commit/0fc56cbb0f7c46c026d58721c6b6bbfc8a1e05b5) · 2026-11-03 · 版本 +21 · fix(terminal): 保留取消后恢复的会话并报告真实存活状态
- 509 [dbece7c52da2](https://github.com/3316891527/Operit/commit/dbece7c52da216644ac1bdd423fd7735be799a7b) · 2026-11-03 · 版本 +21 · Merge pull request #114 from 3316891527/feat/terminal-managed-tasks
- 510 [e312abd42f32](https://github.com/3316891527/Operit/commit/e312abd42f323dd2aba203181be9d0cfac7de95e) · 2026-11-03 · 版本 +21 · Merge pull request #113 from 3316891527/fix/summary-protocol-meta
- 511 [5c9ad041ae10](https://github.com/3316891527/Operit/commit/5c9ad041ae10c318d5001a84c6b709d5ca33143c) · 2026-11-03 · 版本 +21 · fix(notification): 点击通知打开来源会话
- 512 [9f991f480c52](https://github.com/3316891527/Operit/commit/9f991f480c5227c8224ea6d00ea2ec7cb82c9d5c) · 2026-11-03 · 版本 +21 · fix(gemini): 过滤空 Part 并拒绝空请求
- 513 [cd078fc0a4e6](https://github.com/3316891527/Operit/commit/cd078fc0a4e65a4161f0c1842b7b17f94ab74363) · 2026-11-03 · 版本 +21 · Merge pull request #115 from 3316891527/fix/notification-open-chat
- 514 [ce31b43f0397](https://github.com/3316891527/Operit/commit/ce31b43f0397dc14805a8f7c4edfdbadebc07acb) · 2026-11-03 · 版本 +21 · Merge pull request #116 from 3316891527/fix/gemini-empty-contents
- 515 [5b6933a0fa38](https://github.com/3316891527/Operit/commit/5b6933a0fa38ba376134b35524af1a811b450f6c) · 2026-11-03 · 版本 +21 · fix(test): 修正 Gemini 回归用例的工具类导入
- 516 [dc77a603e082](https://github.com/3316891527/Operit/commit/dc77a603e082d4627058096cce4d9a09da489bdc) · 2026-11-03 · 版本 +21 · Merge pull request #117 from 3316891527/fix/gemini-test-imports
- 517 [e5e51de0da24](https://github.com/3316891527/Operit/commit/e5e51de0da24975c7deafe5a5f2941d95f42e2c6) · 2026-11-03 · 版本 +21 · fix(ui): 补齐 MCP 启动反馈并仅用斜杠触发菜单
- 518 [e77503eb17d9](https://github.com/3316891527/Operit/commit/e77503eb17d90d9a2fe680dd2069c2654e60947d) · 2026-11-03 · 版本 +21 · Merge pull request #118 from 3316891527/fix/mcp-start-feedback-slash-menu
- 519 [6795ca9dd54e](https://github.com/3316891527/Operit/commit/6795ca9dd54e4e3f5b350417f1e5e224368e6e16) · 2026-11-03 · 版本 +21 · fix(a2ui): 隔离 HTML 标签页承载器的复用与销毁
- 520 [93f36a639ee9](https://github.com/3316891527/Operit/commit/93f36a639ee90920e510e3754ad10ac43261343b) · 2026-11-03 · 版本 +21 · feat(a2ui): 支持当前轮次等待表单提交并返回结构化数据
- 521 [ac86467c446d](https://github.com/3316891527/Operit/commit/ac86467c446da4971f7594fe0b5fb9cec55fb472) · 2026-11-03 · 版本 +21 · fix(a2ui): 统一快照原子读写并补齐 live 等待联用校验
- 522 [a71bf9bee0e7](https://github.com/3316891527/Operit/commit/a71bf9bee0e7b42087943691f9be166ffc52d9be) · 2026-11-03 · 版本 +21 · fix(theme): 记住工作室预览模式与编辑位置
- 523 [c7182072c69e](https://github.com/3316891527/Operit/commit/c7182072c69ee7dfc5044439a3f78659628dd9c4) · 2026-11-03 · 版本 +21 · fix(chat): 修复分块气泡重复切角与九宫格首尾
- 524 [0e7deeac2069](https://github.com/3316891527/Operit/commit/0e7deeac2069a6db1366eb3c16738c325fb9d067) · 2026-11-03 · 版本 +21 · Merge pull request #119 from 3316891527/fix/a2ui-tab-composition
- 525 [75337b8af9f0](https://github.com/3316891527/Operit/commit/75337b8af9f01810d59f9d0a97b01d38194d9c12) · 2026-11-03 · 版本 +21 · Merge pull request #120 from 3316891527/feat/a2ui-await-submission
- 526 [07344a366c4e](https://github.com/3316891527/Operit/commit/07344a366c4e5dfc37a89fe4a3dcda9fd6055ff3) · 2026-11-03 · 版本 +21 · Merge pull request #121 from 3316891527/fix/theme-studio-memory
- 527 [539fb8376673](https://github.com/3316891527/Operit/commit/539fb8376673e5f06cfbe0f656c2e8ef78f1f004) · 2026-11-03 · 版本 +21 · Merge pull request #122 from 3316891527/fix/message-bubble-surface
- 528 [8907ae28d665](https://github.com/3316891527/Operit/commit/8907ae28d6658370bcc30837abdadcb40ffe6347) · 2026-11-03 · 版本 +21 · Merge remote-tracking branch 'origin/main' into ci/release-1.12.2-plus21
- 529 [4dd1f23368a1](https://github.com/3316891527/Operit/commit/4dd1f23368a11dd71bf2bd68425d6d9c1e75476c) · 2026-11-03 · 版本 +21 · chore(release): 准备 1.12.2+21 发行
- 530 [416155f08f60](https://github.com/3316891527/Operit/commit/416155f08f601fe8310b90bff45e71d23164ea9e) · 2026-11-03 · 版本 +21 · Merge pull request #123 from 3316891527/ci/release-1.12.2-plus21
- 531 [6369cba47089](https://github.com/3316891527/Operit/commit/6369cba4708945ab5d999984605bb13d63b8fdff) · 2026-11-03 · 版本 +21 · Merge pull request #124 from 3316891527/dev
- 532 [2d21ab4baf95](https://github.com/3316891527/Operit/commit/2d21ab4baf955a17dbf5a30e5a4a4e3432c04304) · 2026-11-03 · 版本 +21 · fix(a2ui): 移除外部示例引导脚本并更新使用说明
- 533 [5dc391ce9df9](https://github.com/3316891527/Operit/commit/5dc391ce9df9057ab5a3e6605e4a596625ff2038) · 2026-11-03 · 版本 +21 · fix(ci): 签名发行前准备并发布当前源码离线资源
- 534 [68b56a55ea66](https://github.com/3316891527/Operit/commit/68b56a55ea664231f093d81975c907ffd5208ae8) · 2026-11-03 · 版本 +21 · Merge pull request #125 from 3316891527/fix/remove-a2ui-guide
- 535 [38d7a1aa806e](https://github.com/3316891527/Operit/commit/38d7a1aa806e21eb2f8939fa56158603e2e53c1c) · 2026-11-03 · 版本 +21 · Merge pull request #126 from 3316891527/fix/release-offline-resource-preparation
- 536 [c01ac4fd936d](https://github.com/3316891527/Operit/commit/c01ac4fd936d7ce2e4f77913688b4c654fc5915f) · 2026-11-03 · 版本 +21 · Merge pull request #127 from 3316891527/dev

</details>

<details><summary>v1.12.2+22 源码截止 · 49 条提交</summary>

- 537 [f9cda4296182](https://github.com/3316891527/Operit/commit/f9cda4296182df809bd6df29a17374ba73914e98) · 2026-11-03 · 版本 +22 · fix(permissions): 权限页工具说明跟随应用语言
- 538 [59b146a1cc4e](https://github.com/3316891527/Operit/commit/59b146a1cc4eb852394878686690bc44fc536abc) · 2026-11-03 · 版本 +22 · fix(permissions): 权限列表实时反映已安装启用的工具
- 539 [0285ae32a01b](https://github.com/3316891527/Operit/commit/0285ae32a01b3adb2a7785d61f9d2a764627af5e) · 2026-11-04 · 版本 +22 · feat(http): 终端管理服务并支持浏览器自动登录
- 540 [ff54ab5e6b92](https://github.com/3316891527/Operit/commit/ff54ab5e6b92f1ba7e896334f919ba85817b2dbd) · 2026-11-04 · 版本 +22 · refactor(terminal): 拆分公共命令构建与资源校验
- 541 [b7e7922db621](https://github.com/3316891527/Operit/commit/b7e7922db621152e9a9aebc77c1511f8ef420dba) · 2026-11-04 · 版本 +22 · fix(chat): 避免发送触发总结时阻塞主线程
- 542 [018f6867653d](https://github.com/3316891527/Operit/commit/018f6867653db65d2226d09df0409ba0f7ad80dd) · 2026-11-04 · 版本 +22 · fix(memory): 同步上游文档块编辑器高度限制
- 543 [a4c7cef7e883](https://github.com/3316891527/Operit/commit/a4c7cef7e8836f0aa3b5258cf5140fbb40837b90) · 2026-11-04 · 版本 +22 · fix(chat): 同步 NUL 消息读取修复并适配结构化存储
- 544 [3f3ec85ccaea](https://github.com/3316891527/Operit/commit/3f3ec85ccaea5a7281d9de1d31db42732131bb36) · 2026-11-04 · 版本 +22 · fix(chat): 同步流式重试修订与 Waifu 撤回
- 545 [9de59d2444de](https://github.com/3316891527/Operit/commit/9de59d2444dea4848abf049731e57a4bfce0a770) · 2026-11-04 · 版本 +22 · fix(mcp): 同步 stdio 工作目录与启动失败诊断
- 546 [74fd5c394190](https://github.com/3316891527/Operit/commit/74fd5c394190d1b2ca4f9a3a05db25383a930968) · 2026-11-04 · 版本 +22 · fix(browser): 同步点击目标与导航结果校验
- 547 [501bc9767f88](https://github.com/3316891527/Operit/commit/501bc9767f88ff3d5ffe49a9afc594a5a490bab7) · 2026-11-04 · 版本 +22 · test(chat): 对齐协程异常传播与内部 XML 标签断言
- 548 [30fb82c13912](https://github.com/3316891527/Operit/commit/30fb82c13912c9d962b63699747c1eb952c432af) · 2026-11-04 · 版本 +22 · test(chat): 修正文档标签断言的字符串引号
- 549 [8c3635fc2e5c](https://github.com/3316891527/Operit/commit/8c3635fc2e5cc85fdf4670a38b51861d76af22fb) · 2026-11-04 · 版本 +22 · fix(theme): 补齐自定义主题多语言并统一消息名称
- 550 [2e6050d27caa](https://github.com/3316891527/Operit/commit/2e6050d27caa382371dc3a11e82105741718fab4) · 2026-11-04 · 版本 +22 · fix(theme): 修正主题属性入口与描边可见效果
- 551 [54c5554a0f90](https://github.com/3316891527/Operit/commit/54c5554a0f90eb18c4da6c70d3996992d1050e19) · 2026-11-04 · 版本 +22 · feat(theme): 拆分 11 个动作按钮的独立图标入口
- 552 [4334e021a616](https://github.com/3316891527/Operit/commit/4334e021a6169546511f8a306605814e58656e50) · 2026-11-04 · 版本 +22 · feat(theme): 为自定义主题字体增加独立文字描边
- 553 [c5f50a04a8fe](https://github.com/3316891527/Operit/commit/c5f50a04a8fe076a068a633940d657dc90fca09e) · 2026-11-04 · 版本 +22 · merge: 对齐主题属性与文字颜色入口修复
- 554 [203176c07feb](https://github.com/3316891527/Operit/commit/203176c07feba2be81d901a0cf85cdd925a03604) · 2026-11-04 · 版本 +22 · fix(theme): 明确文字描边组件的 Compose UI 目标
- 555 [d23e1b8a5e65](https://github.com/3316891527/Operit/commit/d23e1b8a5e653333df70ac5b07dfc944c0488ddb) · 2026-11-04 · 版本 +22 · fix(summary): 空摘要重新生成一次并阻止无效结果入库
- 556 [3d92ee2bcc0c](https://github.com/3316891527/Operit/commit/3d92ee2bcc0cdaf596d5e2901d55f44dff3f3e29) · 2026-11-04 · 版本 +22 · fix(code-runner): 隔离脚本运行并限制清理等待
- 557 [15566eff98a6](https://github.com/3316891527/Operit/commit/15566eff98a6160f5b86adc88abcfc6523b213df) · 2026-11-04 · 版本 +22 · 合入 PR #128：fix(permissions): 权限页工具说明跟随应用语言，用于联合测试
- 558 [81b096f3e3b4](https://github.com/3316891527/Operit/commit/81b096f3e3b408650d37722559717cff156e188c) · 2026-11-04 · 版本 +22 · 合入 PR #129：fix(permissions): 权限列表实时反映已安装启用的工具，用于联合测试
- 559 [752caac55736](https://github.com/3316891527/Operit/commit/752caac55736fe6855baf8e440fab313b335d9bf) · 2026-11-04 · 版本 +22 · 合入 PR #130：feat(http): 终端管理服务并支持浏览器自动登录，用于联合测试
- 560 [49f27d6c8916](https://github.com/3316891527/Operit/commit/49f27d6c89162cf6ec16373363626dc464873111) · 2026-11-04 · 版本 +22 · 合入 PR #131：fix(chat): 避免发送触发总结时阻塞主线程，用于联合测试
- 561 [bd6027c21b98](https://github.com/3316891527/Operit/commit/bd6027c21b98403b2176ea140509a341f611f3d5) · 2026-11-04 · 版本 +22 · 合入 PR #132：fix(memory): 同步上游文档块编辑器高度限制，用于联合测试
- 562 [e39d9ec28d38](https://github.com/3316891527/Operit/commit/e39d9ec28d38c7fa318d57ce6c9134e345220cd7) · 2026-11-04 · 版本 +22 · 合入 PR #133：fix(chat): 同步 NUL 消息读取修复并适配结构化存储，用于联合测试
- 563 [5b09d1d56732](https://github.com/3316891527/Operit/commit/5b09d1d567321f2c5ac201f11236d22d36290345) · 2026-11-04 · 版本 +22 · 合入 PR #134：fix(mcp): 同步 stdio 工作目录与启动失败诊断，用于联合测试
- 564 [b3dad2db75ce](https://github.com/3316891527/Operit/commit/b3dad2db75cead7189bfe4331e956bfa98448619) · 2026-11-04 · 版本 +22 · 合入 PR #135：fix(browser): 同步点击目标与导航结果校验，用于联合测试
- 565 [50f932495818](https://github.com/3316891527/Operit/commit/50f932495818a192b05fb132588cd8ef95cb6315) · 2026-11-04 · 版本 +22 · 合入 PR #136：fix(chat): 同步流式重试修订与 Waifu 撤回，用于联合测试
- 566 [bda249a8899c](https://github.com/3316891527/Operit/commit/bda249a8899c9d7ad4258a10326cc8bca1502bf7) · 2026-11-04 · 版本 +22 · 合入 PR #137：fix(theme): 补齐自定义主题多语言并统一消息名称，用于联合测试
- 567 [59140c1027a6](https://github.com/3316891527/Operit/commit/59140c1027a69484d8a96da6bd27b53a42d3af28) · 2026-11-04 · 版本 +22 · 合入 PR #138：fix(theme): 修正主题属性入口与描边可见效果，用于联合测试
- 568 [220698fde528](https://github.com/3316891527/Operit/commit/220698fde528082ec5f72754d66f23a80bb46ada) · 2026-11-04 · 版本 +22 · 合入 PR #139：feat(theme): 拆分 11 个动作按钮的独立图标入口，用于联合测试
- 569 [edf24dd1cf53](https://github.com/3316891527/Operit/commit/edf24dd1cf538ee8dd8c3665e7792ab22603db9c) · 2026-11-04 · 版本 +22 · 合入 PR #140：feat(theme): 为自定义主题字体增加独立文字描边，用于联合测试
- 570 [6cfef36bbce3](https://github.com/3316891527/Operit/commit/6cfef36bbce3969b14b7cbdbf2dfe479ca438464) · 2026-11-04 · 版本 +22 · 合入 PR #141：fix(summary): 空摘要重新生成一次并阻止无效结果入库，用于联合测试
- 571 [2de57bff2e14](https://github.com/3316891527/Operit/commit/2de57bff2e1433850f74521e1b2eb7dc55e385be) · 2026-11-04 · 版本 +22 · 合入 PR #142：fix(code-runner): 隔离脚本运行并限制清理等待，用于联合测试
- 572 [5dd74a86d561](https://github.com/3316891527/Operit/commit/5dd74a86d561357264d53eec09e27465e1cc9465) · 2026-11-04 · 版本 +22 · fix(theme): 显示描边小数值并提供细线编辑预设
- 573 [a8c95ceb4253](https://github.com/3316891527/Operit/commit/a8c95ceb425325bd483664bdc2a405c3f87a695c) · 2026-11-04 · 版本 +22 · feat(theme): 为动作按钮接入独立外观图层和交互状态
- 574 [1fa7e510f853](https://github.com/3316891527/Operit/commit/1fa7e510f8537ba2186e2a80ae11d5e5efbf73d9) · 2026-11-04 · 版本 +22 · merge: 同步text主题外观修正到联合测试分支
- 575 [6f32afc9b132](https://github.com/3316891527/Operit/commit/6f32afc9b13294c636e0a9690845b3eba0d62e05) · 2026-11-04 · 版本 +22 · merge: 同步独立按钮外观并保留文字描边与主题属性修复
- 576 [f1acdd02ec9b](https://github.com/3316891527/Operit/commit/f1acdd02ec9b9ab6b88239791890975e76e42701) · 2026-11-04 · 版本 +22 · fix(theme): 移除文字描边宽度下方说明
- 577 [5c4923ec2f7d](https://github.com/3316891527/Operit/commit/5c4923ec2f7d44785abf3d902e04da208704964a) · 2026-11-04 · 版本 +22 · fix(theme): 保留动作图标独立尺寸
- 578 [adc0995d1739](https://github.com/3316891527/Operit/commit/adc0995d17397854696dc4a1e273c4469212faed) · 2026-11-04 · 版本 +22 · merge: 同步文字描边说明精简到联合测试分支
- 579 [02f3ab34a18b](https://github.com/3316891527/Operit/commit/02f3ab34a18ba04c02a0d9a8b80d33d63890016e) · 2026-11-04 · 版本 +22 · merge: 同步动作按钮图标尺寸修复到联合测试分支
- 580 [0415730e2884](https://github.com/3316891527/Operit/commit/0415730e28849bc94452a59ada7331464e961ce9) · 2026-11-04 · 版本 +22 · chore(release): 准备 1.12.2+22 并在发布后清理已合入分支
- 581 [a0e4ee5722ba](https://github.com/3316891527/Operit/commit/a0e4ee5722bae69108ca8ce033f286fa4d6baa20) · 2026-11-04 · 版本 +22 · Merge pull request #143: 联合测试与 1.12.2+22 发行准备
- 582 [a456610e9ad8](https://github.com/3316891527/Operit/commit/a456610e9ad80ccbf3e20604e68161f4b19c88fd) · 2026-11-04 · 版本 +22 · Merge pull request #144: 发布同步 1.12.2+22
- 583 [472d1680611a](https://github.com/3316891527/Operit/commit/472d1680611a40d1f73639a34a909ab70f87c83c) · 2026-11-04 · 版本 +22 · fix(theme): 修复文字描边输入与正文覆盖一致性
- 584 [e9bb154a6313](https://github.com/3316891527/Operit/commit/e9bb154a631300ec6fd7d9a073b50d58792aca38) · 2026-11-04 · 版本 +22 · Merge pull request #145: 文字描边输入与显示一致性修复
- 585 [aa0237a016c5](https://github.com/3316891527/Operit/commit/aa0237a016c55d399b5feb1edff8c0c491e3b250) · 2026-11-04 · 版本 +22 · Merge pull request #146: 描边修复同步与 1.12.2+22 重发

</details>

<details><summary>v1.12.2+23 源码截止 · 50 条提交</summary>

- 586 [d2758d21d7aa](https://github.com/3316891527/Operit/commit/d2758d21d7aaaba395544d72da996c2d66cfaafb) · 2026-11-05 · 版本 +23 · fix(ai): 按持久化配置刷新模型服务缓存
- 587 [a368f2773767](https://github.com/3316891527/Operit/commit/a368f2773767a767b67cfe47e8b3de49df8cf365) · 2026-11-05 · 版本 +23 · fix(bluetooth): 同步 BLE 写属性与完整负载传输
- 588 [e22e8706ac57](https://github.com/3316891527/Operit/commit/e22e8706ac57c0af3b2a44b373fe392b513d5f4e) · 2026-11-05 · 版本 +23 · ci: 同步 Shell 与容器定义的 LF 属性
- 589 [abf9aa8f9128](https://github.com/3316891527/Operit/commit/abf9aa8f91282d0ad7fabfb97d43976f874cbc7f) · 2026-11-05 · 版本 +23 · fix(settings): 保留提示词选区与编辑视口
- 590 [e677a2a63241](https://github.com/3316891527/Operit/commit/e677a2a632414aead5de89062daf386618043533) · 2026-11-05 · 版本 +23 · fix(update): 统一下载来源排序与文件校验
- 591 [ca126f99c4db](https://github.com/3316891527/Operit/commit/ca126f99c4dbaada730e34c4ca4996e339b3be82) · 2026-11-05 · 版本 +23 · test(ai): 补齐缓存回归的 applicationContext
- 592 [6cce30bb46db](https://github.com/3316891527/Operit/commit/6cce30bb46dbe97ba4780b4e2937bdc2935d5202) · 2026-11-05 · 版本 +23 · feat(ui): add guarded local execution to PhoneAgent
- 593 [67de0cb5450a](https://github.com/3316891527/Operit/commit/67de0cb5450addea0d7c1a43ccd83f357dc05b64) · 2026-11-05 · 版本 +23 · fix(resources): prevent extension reimports and show descriptions
- 594 [0e66c813da4f](https://github.com/3316891527/Operit/commit/0e66c813da4f2585ecf27c79612e5fc90fc3328a) · 2026-11-06 · 版本 +23 · fix(ui): prevent PhoneAgent Android parsing crashes and stalled plans
- 595 [cba45d358fa9](https://github.com/3316891527/Operit/commit/cba45d358fa9b998d9b9949725853a3cfa2437ee) · 2026-11-06 · 版本 +23 · feat(ui): show PhoneAgent tap locations and persistent execution phases
- 596 [9c5be2719ade](https://github.com/3316891527/Operit/commit/9c5be2719adeb78144191883d196384fb6b21111) · 2026-11-06 · 版本 +23 · fix(ui): keep PhoneAgent input service alive during planning
- 597 [d6cf7b94bfa4](https://github.com/3316891527/Operit/commit/d6cf7b94bfa45f7fd10bf3db171776da2407f1d8) · 2026-11-06 · 版本 +23 · perf(phone-agent): compact node handoff and confirm launch completion
- 598 [a7cd8ed5520c](https://github.com/3316891527/Operit/commit/a7cd8ed5520c76ad637378ba95a921a1d55fe8f5) · 2026-11-06 · 版本 +23 · fix(phone-agent): invalidate node dispatch after user takeover
- 599 [061bedbbb37c](https://github.com/3316891527/Operit/commit/061bedbbb37c53949e3276fe6cf37d4ea5260aa3) · 2026-11-06 · 版本 +23 · fix(phone-agent): preserve launch state and acknowledge tap delivery
- 600 [ad642967091b](https://github.com/3316891527/Operit/commit/ad642967091b01ed1a2d4041955541a227306992) · 2026-11-06 · 版本 +23 · fix(theme): include custom appearance fields in theme transfers
- 601 [421f5b208f26](https://github.com/3316891527/Operit/commit/421f5b208f2623c4e437533719885233709630d7) · 2026-11-06 · 版本 +23 · fix: sync verified TTS cleanup and A2A protocol fixes
- 602 [8643aa96b734](https://github.com/3316891527/Operit/commit/8643aa96b734dd43fddfc5dc8a43900cc311e224) · 2026-11-06 · 版本 +23 · fix(speech): project local message sections before TTS cleaning
- 603 [b56bea3a6418](https://github.com/3316891527/Operit/commit/b56bea3a6418e02574d302167787f89d4dd8a378) · 2026-11-06 · 版本 +23 · fix: correct grep line numbers and guard SSE writer close
- 604 [88cf4f27d555](https://github.com/3316891527/Operit/commit/88cf4f27d5551326c96ac3dd517039aba3316b54) · 2026-11-06 · 版本 +23 · fix(a2ui): support computed table cells and structured events
- 605 [baff7720bde8](https://github.com/3316891527/Operit/commit/baff7720bde8f34621c3a7573590796b91ae2179) · 2026-11-06 · 版本 +23 · feat(extensions): 隔离脚本列表并路由化扩展详情
- 606 [f53afd73118a](https://github.com/3316891527/Operit/commit/f53afd73118a3926fb5f755272caeeacddbd6d81) · 2026-11-06 · 版本 +23 · fix(ui): preserve launcher task semantics when opening apps
- 607 [988f9c6df7c2](https://github.com/3316891527/Operit/commit/988f9c6df7c2f36521750785cbb718520fb6dd49) · 2026-11-06 · 版本 +23 · build(shower): package app launch changes from current server sources
- 608 [6b4f94d9d187](https://github.com/3316891527/Operit/commit/6b4f94d9d18769e20f1a6ac7f468e8ff40bc215a) · 2026-11-07 · 版本 +23 · feat(extensions): rebuild complete extension detail pages
- 609 [a4c4b4fe0c44](https://github.com/3316891527/Operit/commit/a4c4b4fe0c448a50c4010a7a15e594ce1263eba8) · 2026-11-07 · 版本 +23 · fix(extensions): import DpRect height in detail regression test
- 610 [f8434861ca0f](https://github.com/3316891527/Operit/commit/f8434861ca0f6a9eccb506aee7a98d0f04272988) · 2026-11-07 · 版本 +23 · fix(extensions): enforce disabled state in details and script execution
- 611 [a7547e203725](https://github.com/3316891527/Operit/commit/a7547e2037255153556b99e229a70e474248f414) · 2026-11-07 · 版本 +23 · fix(extensions): use a single disabled-package error name
- 612 [070b71a213a3](https://github.com/3316891527/Operit/commit/070b71a213a331dd13e90da4c3a7dffec6a1a0ee) · 2026-11-07 · 版本 +23 · fix(extensions): use the requested disabled-package message
- 613 [bae1ed5b0617](https://github.com/3316891527/Operit/commit/bae1ed5b0617335bb7bb2781695fc1a8ce82fa75) · 2026-11-07 · 版本 +23 · fix(market): sync ToolPkg API compatibility from upstream #1474
- 614 [a576507ed3cd](https://github.com/3316891527/Operit/commit/a576507ed3cde27e51b887e5423fa4125421f21e) · 2026-11-07 · 版本 +23 · fix(terminal): integrate protocol replies for interactive prompts
- 615 [a4e4f9b3bcdf](https://github.com/3316891527/Operit/commit/a4e4f9b3bcdf2f86a95b146cf137f34f0c14f01a) · 2026-11-07 · 版本 +23 · fix(vision): preserve DeepSeek Responses images and expose native image reads
- 616 [5062f6dec54c](https://github.com/3316891527/Operit/commit/5062f6dec54c5ad5c99281f61a8cb5a709ca4614) · 2026-11-07 · 版本 +23 · feat(settings): simplify API endpoint hints and refresh model defaults
- 617 [6ce3cbba78b2](https://github.com/3316891527/Operit/commit/6ce3cbba78b21c96392e0ed9c731d498d634df8f) · 2026-11-07 · 版本 +23 · feat(draw): sync GPT Image 2.6 GIF parameters from upstream #1476
- 618 [0a4507bb026c](https://github.com/3316891527/Operit/commit/0a4507bb026ced48e96c2f8ab32f7a961f98649a) · 2026-11-07 · 版本 +23 · fix(gemini): keep configured service for model discovery
- 619 [35e26aac5cd8](https://github.com/3316891527/Operit/commit/35e26aac5cd86f127f1cc0736b38588b26785342) · 2026-11-07 · 版本 +23 · perf(phone-agent): align actions and measure observation costs
- 620 [b69aa717fc43](https://github.com/3316891527/Operit/commit/b69aa717fc4340bc862a7344184474f2d0cfc518) · 2026-11-07 · 版本 +23 · feat(extensions): unify updates and move built-ins to market
- 621 [2f775380e0e1](https://github.com/3316891527/Operit/commit/2f775380e0e16ed43cb1e795b057d8265525ab5b) · 2026-11-08 · 版本 +23 · fix(ui): refine extension updates and batch theme resource imports
- 622 [aa03c52e3f72](https://github.com/3316891527/Operit/commit/aa03c52e3f7280acf4494228fd307fd8cdb2ac1b) · 2026-11-08 · 版本 +23 · fix(extensions): restore built-in resource management and update feedback
- 623 [730fe33a39b5](https://github.com/3316891527/Operit/commit/730fe33a39b5a0cdc51c1a87e93dc15c5f406b5a) · 2026-11-08 · 版本 +23 · fix(ui): integrate task-preserving launch and real-time swipe dispatch
- 624 [bd9ddba7a67a](https://github.com/3316891527/Operit/commit/bd9ddba7a67a7512bef0a89ecd81401c6933acb7) · 2026-11-08 · 版本 +23 · fix(extensions): clarify ZIP import and finish deletion navigation
- 625 [080fead02ab7](https://github.com/3316891527/Operit/commit/080fead02ab709cf7a78ba77c68b02b100ab5fc5) · 2026-11-08 · 版本 +23 · refactor(ui): simplify phone assistant progress and gesture feedback
- 626 [4f298ee870c1](https://github.com/3316891527/Operit/commit/4f298ee870c130137bbc7056f2fdeb817645704e) · 2026-11-08 · 版本 +23 · fix(worldbook): protect stored data and serialize package operations
- 627 [3902eb01fd0a](https://github.com/3316891527/Operit/commit/3902eb01fd0ad79f04aab6d8f97f340166f9e85e) · 2026-11-08 · 版本 +23 · fix(worldbook): synchronize distribution and validate fault fixtures
- 628 [662d23bc30e6](https://github.com/3316891527/Operit/commit/662d23bc30e6a80e7e10dad2df501c32571409f9) · 2026-11-08 · 版本 +23 · docs(worldbook): record passing storage regression and validation limits
- 629 [779ad3235811](https://github.com/3316891527/Operit/commit/779ad3235811d44b71a56a8a5e1c30d14c76c023) · 2026-11-08 · 版本 +23 · fix(chat): parse streamed display incrementally and restore rollback state off main
- 630 [f9e1ffbd73f5](https://github.com/3316891527/Operit/commit/f9e1ffbd73f50b24d0c14857138a6afef3a52211) · 2026-11-08 · 版本 +23 · test(chat): restore tool payload filter checkpoints in rollback regression
- 631 [c5c62fd7cbc1](https://github.com/3316891527/Operit/commit/c5c62fd7cbc13e0fe34661b1480720690fbf91b8) · 2026-11-08 · 版本 +23 · test: integrate open PRs #147-#161 excluding #105
- 632 [76a63af71341](https://github.com/3316891527/Operit/commit/76a63af7134121260912247839ff348180704e7d) · 2026-11-08 · 版本 +23 · chore(release): preserve main history before 1.12.2+23
- 633 [f13ace037da1](https://github.com/3316891527/Operit/commit/f13ace037da138a4be882eace78d5946d4fc8d95) · 2026-11-08 · 版本 +23 · release: prepare 1.12.2+23 and retire verified temporary PR checks
- 634 [60ca5cfbbab0](https://github.com/3316891527/Operit/commit/60ca5cfbbab074b3b3a15c52464de180f591b797) · 2026-11-08 · 版本 +23 · Merge release preparation into dev
- 635 [02349712262d](https://github.com/3316891527/Operit/commit/02349712262da53a878f40a77702cc381b582580) · 2026-11-08 · 版本 +23 · Sync dev to main for 1.12.2+23

</details>

<details><summary>v1.12.2+24 源码截止 · 85 条提交</summary>

- 636 [c0205be59ca4](https://github.com/3316891527/Operit/commit/c0205be59ca42b23b273395df7dbc989b4f971cf) · 2026-09-08 · 版本 +24 · fix(skills): key GitHub zip cache by commit SHA and invalidate on delete
- 637 [4356d0b84d13](https://github.com/3316891527/Operit/commit/4356d0b84d1377f59a9115c42d36f45521f3d44e) · 2026-09-08 · 版本 +24 · fix(ffmpeg): serialize native FFmpegKit sessions
- 638 [71b292ca05b6](https://github.com/3316891527/Operit/commit/71b292ca05b6cc8d3b381be3f9d57dac60e8efec) · 2026-09-08 · 版本 +24 · fix(ffmpeg): use MediaInformationSession and valid scale escape
- 639 [02ea76a7ecd8](https://github.com/3316891527/Operit/commit/02ea76a7ecd837c8cc93de7522979891769b379d) · 2026-09-10 · 版本 +24 · fix(provider): sort Claude tool results by tool use order to preserve prompt cache
- 640 [95332c70b1d3](https://github.com/3316891527/Operit/commit/95332c70b1d3d85d3581852bf7332e8cda3b59eb) · 2026-09-10 · 版本 +24 · test(provider): fix @Test annotation in ClaudeProviderHistoryTest
- 641 [f21f0ae25107](https://github.com/3316891527/Operit/commit/f21f0ae25107c6752a192e6195f387f2ec4c4dfa) · 2026-09-17 · 版本 +24 · fix(http): honor timeout on built-in http_request
- 642 [c9597e411a1e](https://github.com/3316891527/Operit/commit/c9597e411a1efee74ef3af71e6ebf9b4ca79ba3e) · 2026-09-25 · 版本 +24 · fix(http): enforce overall request timeout semantics
- 643 [8e2379f034cd](https://github.com/3316891527/Operit/commit/8e2379f034cd9624b72de97928fd601548cf31b2) · 2026-11-08 · 版本 +24 · fix(chat): bound inline code context to prevent protocol leaks and stalled output
- 644 [f674c32f0275](https://github.com/3316891527/Operit/commit/f674c32f02751f9fe31c3c8921f5612d3a180da1) · 2026-11-08 · 版本 +24 · ci(terminal): 自动同步应用采用的终端核心版本
- 645 [b734ee72dfde](https://github.com/3316891527/Operit/commit/b734ee72dfde713f48edbd0e9e8ef1d5d33d966f) · 2026-11-08 · 版本 +24 · ci(terminal): 自动同步应用采用的终端核心版本
- 646 [8bc97a723b95](https://github.com/3316891527/Operit/commit/8bc97a723b95246682d07421cdb72a2c98235a4d) · 2026-11-08 · 版本 +24 · Merge pull request #167: 自动同步应用采用的终端核心版本
- 647 [62e4a63e5d16](https://github.com/3316891527/Operit/commit/62e4a63e5d16fb618657f93bdbb0ac4c873bcc65) · 2026-11-08 · 版本 +24 · Merge pull request #168: 同步 main 的终端核心自动化工作流
- 648 [9916834d1a46](https://github.com/3316891527/Operit/commit/9916834d1a46a5a621c0f204a28d3ffb19c000c1) · 2026-11-08 · 版本 +24 · docs(terminal): 记录自动同步部署和实际运行结果
- 649 [50069cbac65f](https://github.com/3316891527/Operit/commit/50069cbac65f4e4360c6b271854abe527124b84f) · 2026-11-08 · 版本 +24 · Merge pull request #169: 回填终端自动同步部署结果
- 650 [dfa164dc2038](https://github.com/3316891527/Operit/commit/dfa164dc2038aefd3775314ddb966726e1be659b) · 2026-11-08 · 版本 +24 · Merge current dev into fix/skill-repo-zip-cache-stale
- 651 [1869563a4f10](https://github.com/3316891527/Operit/commit/1869563a4f10d097040964fdc1130c49c4e02659) · 2026-11-08 · 版本 +24 · Merge current dev into fix/ffmpeg-serialize-native-sessions
- 652 [cc9968966812](https://github.com/3316891527/Operit/commit/cc9968966812e7256ec0ac2e85bff2716318760e) · 2026-11-08 · 版本 +24 · Merge current dev into fix/claude-prompt-cache-tool-result-order
- 653 [03d14ecfe863](https://github.com/3316891527/Operit/commit/03d14ecfe8630836fc82eabfffa39b3478633c9c) · 2026-11-08 · 版本 +24 · Merge current dev into fix/issue-1202-http-request-timeout
- 654 [e054b23f3d0a](https://github.com/3316891527/Operit/commit/e054b23f3d0a0359f52f8050b8fa7eb3aaee1457) · 2026-11-08 · 版本 +24 · fix(http): preserve timeout forwarding in package source
- 655 [29f6728034d6](https://github.com/3316891527/Operit/commit/29f6728034d64e3b1242418e562223749db882ae) · 2026-11-08 · 版本 +24 · Merge pull request #170: 修复 skill 仓库压缩包缓存失效
- 656 [da5c92d37fb4](https://github.com/3316891527/Operit/commit/da5c92d37fb43ac9434c0cc15cf78f002d10b680) · 2026-11-08 · 版本 +24 · fix(claude): keep serializer private and isolate history test logging
- 657 [7f8363341ff3](https://github.com/3316891527/Operit/commit/7f8363341ff36792d2b0e938e094f3ec32f75efd) · 2026-11-08 · 版本 +24 · Merge pull request #171: 串行化 FFmpegKit 原生会话
- 658 [2f5570f6a9db](https://github.com/3316891527/Operit/commit/2f5570f6a9dbf502a1a22b518a8609f44c3c3d3b) · 2026-11-08 · 版本 +24 · Merge pull request #173: 修复 issue1202 HTTP 整体超时
- 659 [a5c9cdeeb041](https://github.com/3316891527/Operit/commit/a5c9cdeeb04100dfec5389bdbf691787794cd5e1) · 2026-11-08 · 版本 +24 · Merge latest dev before validating Claude cache repair
- 660 [1d44a096bde2](https://github.com/3316891527/Operit/commit/1d44a096bde2eba5dd258b5ea758d72975cb7716) · 2026-11-08 · 版本 +24 · ci: validate PR merge candidate against its base history
- 661 [51e8efaa331d](https://github.com/3316891527/Operit/commit/51e8efaa331d783dc44daf9fe7ffee2bc52a93e6) · 2026-11-08 · 版本 +24 · Merge pull request #174: 修复 PR Check 候选基准校验
- 662 [8b56a0ca9a2a](https://github.com/3316891527/Operit/commit/8b56a0ca9a2ae56eb5a4811a3729b5849f9c56d0) · 2026-11-08 · 版本 +24 · Merge validated PR Check repair before Claude integration
- 663 [798841d88a15](https://github.com/3316891527/Operit/commit/798841d88a15b6de60e5b91a7c4ab0d3c860a5a9) · 2026-11-08 · 版本 +24 · Merge pull request #172: 稳定 Claude 工具结果顺序与提示词缓存
- 664 [174f4db2f423](https://github.com/3316891527/Operit/commit/174f4db2f423d64b69fe8d359f4ba14438b38ce4) · 2026-11-08 · 版本 +24 · fix(chat): stop appending thinking levels to message model names
- 665 [cc8ef86caf4b](https://github.com/3316891527/Operit/commit/cc8ef86caf4b5d75dbd8d64c39c0631de91faec0) · 2026-11-08 · 版本 +24 · Merge PR #175: 移除消息模型名附加的思考档位后缀
- 666 [a26c052d0b6a](https://github.com/3316891527/Operit/commit/a26c052d0b6a6df9b64a028b7fb1fe296e74c0ad) · 2026-11-09 · 版本 +24 · feat(theme): add gradients, page backgrounds and text targets
- 667 [74002c36359f](https://github.com/3316891527/Operit/commit/74002c36359f9506995ec0bea65478c5819a16ba) · 2026-11-09 · 版本 +24 · fix(theme): wire page preview focus and scene selection
- 668 [c944cdd8bd1b](https://github.com/3316891527/Operit/commit/c944cdd8bd1b81dd92a53b00f3680f966fe10bc8) · 2026-11-09 · 版本 +24 · Merge PR #176: 扩展主题渐变、页面背景与独立文字排版
- 669 [d25ac2932511](https://github.com/3316891527/Operit/commit/d25ac2932511a080bc48e97b03b457a453b51056) · 2026-11-09 · 版本 +24 · revert: undo PRs #170 through #176 pending maintainer review
- 670 [70a5323ecfbc](https://github.com/3316891527/Operit/commit/70a5323ecfbcbb3eaa3f780ec89625f7d8d27579) · 2026-11-09 · 版本 +24 · ci: require complete Android validation for restored PRs
- 671 [56c2300725a4](https://github.com/3316891527/Operit/commit/56c2300725a41f799ec5b8edb1de64b3d0105db5) · 2026-11-09 · 版本 +24 · fix(skill): restore ZIP cache fix for review
- 672 [4d7b99e82649](https://github.com/3316891527/Operit/commit/4d7b99e82649aa5ae434096784103313a9765360) · 2026-11-09 · 版本 +24 · fix(ffmpeg): restore native session serialization for review
- 673 [c0375d6de81c](https://github.com/3316891527/Operit/commit/c0375d6de81c7e3b811effa89fbfcaf5fcb03f7f) · 2026-11-09 · 版本 +24 · fix(claude): restore tool result ordering for review
- 674 [a1db67cb030c](https://github.com/3316891527/Operit/commit/a1db67cb030c9282208bd3310a5fcdcd627c99da) · 2026-11-09 · 版本 +24 · fix(http): restore overall request timeout for review
- 675 [eb8f4885a3f3](https://github.com/3316891527/Operit/commit/eb8f4885a3f3b986eebb193dad3fe97556bf9c9d) · 2026-11-09 · 版本 +24 · ci: restore candidate base validation fix for review
- 676 [c0a0e3991a34](https://github.com/3316891527/Operit/commit/c0a0e3991a340dae040b25b8dc5803493270ccc4) · 2026-11-09 · 版本 +24 · fix(chat): restore model name suffix removal for review
- 677 [97e8e027564b](https://github.com/3316891527/Operit/commit/97e8e027564b56a046463620a095756d8d4195ff) · 2026-11-09 · 版本 +24 · feat(theme): restore foundation extensions for review
- 678 [c389c49e472f](https://github.com/3316891527/Operit/commit/c389c49e472f39aaabe9c854eec2c26913ff6fa6) · 2026-11-09 · 版本 +24 · fix(worldbook): register storage IPC when the main runtime loads
- 679 [0c2856e309c7](https://github.com/3316891527/Operit/commit/0c2856e309c7496c815d05e0b1d4fe3d5816755a) · 2026-11-09 · 版本 +24 · feat(theme): 收拢背景编辑并加入原生交互效果
- 680 [025a80d1fc2e](https://github.com/3316891527/Operit/commit/025a80d1fc2e59c5b9b4cf2dd39c2e2ca18ec633) · 2026-11-09 · 版本 +24 · fix(theme): 补齐页面预览引用的 Material 图标
- 681 [e82e26c2dacd](https://github.com/3316891527/Operit/commit/e82e26c2dacd0ce5e352f2b640d1839e6dd2889c) · 2026-11-09 · 版本 +24 · fix(theme): 修正背景绘制判断函数的导入
- 682 [1f72a906a8ee](https://github.com/3316891527/Operit/commit/1f72a906a8eea9acfe11ffefa0948e579ee53a18) · 2026-11-09 · 版本 +24 · feat(theme): 整理属性分组并复用真实页面预览
- 683 [6b5709d226d7](https://github.com/3316891527/Operit/commit/6b5709d226d71bc9fee2d1c6053ffa5f6ef2b1af) · 2026-11-09 · 版本 +24 · fix(waifu): 保留思考展示并仅在末条显示请求统计
- 684 [a57c971e60b2](https://github.com/3316891527/Operit/commit/a57c971e60b2582123152a1131b3ed8229065c72) · 2026-11-09 · 版本 +24 · fix(theme): 完善 JPG 媒体导入与定量资源限制提示
- 685 [1861c708536e](https://github.com/3316891527/Operit/commit/1861c708536eff67776e83657ed36eb667432d37) · 2026-11-09 · 版本 +24 · fix(theme): 精简超限红条并补充本地化错误提示
- 686 [52445499d095](https://github.com/3316891527/Operit/commit/52445499d0956d59d9ac895738d70fae7693cf96) · 2026-11-09 · 版本 +24 · fix(theme): 移除重复的资源错误 Toast
- 687 [d136c363309d](https://github.com/3316891527/Operit/commit/d136c363309d4a30ed76aac0afb9c2599131a36f) · 2026-11-09 · 版本 +24 · fix(stats): hide deleted configurations without removing usage
- 688 [c61678fdc384](https://github.com/3316891527/Operit/commit/c61678fdc384d80bf76df54a631550831a654e4e) · 2026-11-09 · 版本 +24 · fix: retain navigation across activity recreation
- 689 [af2e4deaf9c6](https://github.com/3316891527/Operit/commit/af2e4deaf9c6117200dbb1fd25ccd690e926bf33) · 2026-11-09 · 版本 +24 · feat: collapse completed response activity
- 690 [799cf450548a](https://github.com/3316891527/Operit/commit/799cf450548ae2fc5ed1cb2555ccbfc84962f632) · 2026-11-09 · 版本 +24 · feat: queue messages and steer the active response
- 691 [1edc03516a45](https://github.com/3316891527/Operit/commit/1edc03516a45008984a6eb4e2020f1d3f8878f06) · 2026-11-10 · 版本 +24 · fix: use existing toast and chat draft APIs
- 692 [fb7bf270aac0](https://github.com/3316891527/Operit/commit/fb7bf270aac04039f26ccb8ee52abcce6bdac1c6) · 2026-11-10 · 版本 +24 · test: isolate Android system logging in sampling tests
- 693 [4444d0478b19](https://github.com/3316891527/Operit/commit/4444d0478b1918da8cc0aab9c0f3e3f369fc73c6) · 2026-11-10 · 版本 +24 · docs: plan integration of thirteen pending PRs
- 694 [e5620dab9893](https://github.com/3316891527/Operit/commit/e5620dab989313251e46b31b2eaababe9f6c0e5f) · 2026-11-10 · 版本 +24 · merge: integrate PR #178 for device testing
- 695 [7725229d8088](https://github.com/3316891527/Operit/commit/7725229d8088b9489bea39f4fb3f91979485e8ed) · 2026-11-10 · 版本 +24 · merge: integrate PR #179 for device testing
- 696 [2709b6557be5](https://github.com/3316891527/Operit/commit/2709b6557be5ff980b8a2c1c4708c555d4854404) · 2026-11-10 · 版本 +24 · merge: integrate PR #180 for device testing
- 697 [ef163abf4671](https://github.com/3316891527/Operit/commit/ef163abf4671d5956c44afccc81114bfd33adba0) · 2026-11-10 · 版本 +24 · merge: integrate PR #181 for device testing
- 698 [7aeaf8d04f4d](https://github.com/3316891527/Operit/commit/7aeaf8d04f4dcea25a36effb00b108a721a708e8) · 2026-11-10 · 版本 +24 · merge: integrate PR #182 for device testing
- 699 [e3740d46b233](https://github.com/3316891527/Operit/commit/e3740d46b233653894ec5047e58df48b45feb51f) · 2026-11-10 · 版本 +24 · merge: integrate PR #183 for device testing
- 700 [65bdb7857aa6](https://github.com/3316891527/Operit/commit/65bdb7857aa6c47284c9673e879f0dc920a19d4e) · 2026-11-10 · 版本 +24 · merge: integrate PR #184 for device testing
- 701 [5b26415a8eec](https://github.com/3316891527/Operit/commit/5b26415a8eec82b0add9904fb200ee25c923547f) · 2026-11-10 · 版本 +24 · merge: integrate PR #185 for device testing
- 702 [6189da9201fd](https://github.com/3316891527/Operit/commit/6189da9201fd4712208b44e304919cf049f89d12) · 2026-11-10 · 版本 +24 · merge: integrate PR #186 for device testing
- 703 [a9a88f7cf00c](https://github.com/3316891527/Operit/commit/a9a88f7cf00c6596ba0155b86c29ecac2a8b915c) · 2026-11-10 · 版本 +24 · merge: integrate PR #187 for device testing
- 704 [17b6618cf0c1](https://github.com/3316891527/Operit/commit/17b6618cf0c1b548cf492b05cf61dc1a3a4ca0bf) · 2026-11-10 · 版本 +24 · merge: integrate PR #188 for device testing
- 705 [dc49eabcf58c](https://github.com/3316891527/Operit/commit/dc49eabcf58c7b6e158cda8cfd913909f474d08a) · 2026-11-10 · 版本 +24 · merge: integrate PR #189 for device testing
- 706 [6bf67262ff36](https://github.com/3316891527/Operit/commit/6bf67262ff368ce2668622358759d8f8f124e554) · 2026-11-10 · 版本 +24 · merge: integrate PR #190 for device testing
- 707 [72ecce9a609c](https://github.com/3316891527/Operit/commit/72ecce9a609c5210b72f3aa725832c498cdfd33c) · 2026-11-10 · 版本 +24 · docs: record integrated sources and device acceptance checklist
- 708 [ba1d2075e562](https://github.com/3316891527/Operit/commit/ba1d2075e562744c58b7fc947013a64751bd8ec5) · 2026-11-10 · 版本 +24 · fix: apply theme tint to active-turn guidance text
- 709 [ef634a87c758](https://github.com/3316891527/Operit/commit/ef634a87c758889638c239905e9843a62f395a25) · 2026-11-10 · 版本 +24 · merge: update PR #190 guidance theme colors
- 710 [2dbc0154813d](https://github.com/3316891527/Operit/commit/2dbc0154813d947e2932a1c325d9dc3ecedcac04) · 2026-11-10 · 版本 +24 · docs: update integrated guidance theme fix source
- 711 [954b067f7ce2](https://github.com/3316891527/Operit/commit/954b067f7ce2a8847995550765dc98ff45f0b7b5) · 2026-11-10 · 版本 +24 · merge: integrate PRs #178–#190 into dev
- 712 [c3d0588e2a91](https://github.com/3316891527/Operit/commit/c3d0588e2a9161623922e6d9af59778612ea1d10) · 2026-11-10 · 版本 +24 · fix(extensions): show update progress and completion feedback
- 713 [8f275aa0620d](https://github.com/3316891527/Operit/commit/8f275aa0620d2015df4177746209385bb39b8c6f) · 2026-11-10 · 版本 +24 · chore(release): prepare 1.12.2+24 version and notes
- 714 [6f16f0c6c5b9](https://github.com/3316891527/Operit/commit/6f16f0c6c5b9b0379301d90c377e20d5df1b09f8) · 2026-11-10 · 版本 +24 · merge: preserve main history before 1.12.2+24
- 715 [1f1a38813439](https://github.com/3316891527/Operit/commit/1f1a38813439abaf09efab519f30bdf70fd3f226) · 2026-11-10 · 版本 +24 · merge: plugin update feedback and 1.12.2+24 preparation
- 716 [c9b55f2d04a5](https://github.com/3316891527/Operit/commit/c9b55f2d04a51fe4b90122ccf371d54c497541d4) · 2026-11-10 · 版本 +24 · merge: sync dev to main for 1.12.2+24
- 717 [2add39541086](https://github.com/3316891527/Operit/commit/2add3954108604eec4d3e488ee1a23192844beed) · 2026-11-10 · 版本 +24 · merge: update PR #166 with current dev and preserve documentation entries
- 718 [aafc4d6acc30](https://github.com/3316891527/Operit/commit/aafc4d6acc3035dfe48c8680d45ef4e2f9ee355a) · 2026-11-10 · 版本 +24 · docs(release): include PR #166 in the 1.12.2+24 restart
- 719 [9532d2664f92](https://github.com/3316891527/Operit/commit/9532d2664f921d1f138813a22c69f26c3de457b9) · 2026-11-10 · 版本 +24 · merge: include PR #166 protocol streaming fix in dev
- 720 [275ed3e43c04](https://github.com/3316891527/Operit/commit/275ed3e43c04d0e05caf67660276ffa5edaff504) · 2026-11-10 · 版本 +24 · merge: sync PR #166 to main for the 1.12.2+24 restart

</details>
