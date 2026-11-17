---
title: 加载器拆分、素材颜色与缺失标识
status: pending_validation
---
# 加载器拆分、素材颜色与缺失标识

[返回计划](index.md)

## 实施范围

- [x] 将字节、流和 assets 的通用图片解码与 Compose Painter 移到 `ImageAssetLoader.kt`
- [x] 将提供商素材查找、Painter 与 ProviderLogo 组件集中到 `ProviderLogo.kt`
- [x] 通用调用方使用图片资源入口，远程图标保留下载与缓存策略并复用新解码器
- [x] 单色素材跟随主题前景色，彩色素材保留原色，所有提供商页面移除外部重复染色
- [x] OTHER 增加中性连接节点 SVG，补充固定上游的彩色品牌 SVG 与许可来源
- [x] 为实际 AndroidSVG 解码、所有内置素材和深浅主题颜色行为补充设备回归源码
- [x] 静态核对代码、素材和文档，准备原 PR 提交与远程 Build、Android Tests 的交付脚本

## 实现约定

`ImageAssetLoader` 只负责图片内容与正方形等比例居中输出，不引用提供商、MaterialTheme 或品牌目录。`rememberImageAssetPainter` 提供 assets 路径与图片字节两种入口。

`ProviderLogoLoader` 保留按提供商身份查找素材的逻辑，复用通用解码器。主题颜色由提供商 Painter 统一处理；彩色素材不接受单色滤镜。通用图片、插件、远程图标和 Linux 图标保持素材原色。

38 个内置提供商中，仅 OTHER 原先没有素材。OTHER 使用原创中性连接节点标识。彩色品牌素材取自 Lobe Icons 的固定提交，保留 MIT 许可、路径与 SHA-256 记录；已有 INFINIAI 和 IFLOW 的紫色 PNG 保留。

## 验证边界

本地执行源码语法、调用关系、SVG XML、素材覆盖、许可摘要、差异及仓库卫生检查。设备回归源码交由远程 Android Tests 验证，静态检查不代表运行通过。确认远程运行记录建立后停止查询工作流进度。

## 完成情况

通用实现迁入 `ImageAssetLoader.kt`，提供商查找、主题策略、Painter 和组件集中在 `ProviderLogo.kt`，原 `ProviderLogoLoader.kt` 删除。工具包、发布预览、存储管理与 Linux 使用重载的 `rememberImageAssetPainter`；远程图标只替换解码器调用，下载限制、缓存和网络行为不变。

提供商 Painter 按素材策略封装颜色，调用页面无需再传 `providerLogoColorFilter`。20 个提供商标识保留原色，其余单色标识使用 MaterialTheme 的 onSurface。16 个彩色 SVG 原样取自固定的 Lobe Icons 提交；已有四份彩色标识保留。Antigravity 彩色版本包含当前 AndroidSVG 不支持的 filter，本轮继续使用原单色素材。

OTHER 使用原创 SVG，所有 38 个内置提供商均有素材，保留 IFLOW 后共有 39 份图片。素材来源、路径、字节数、SHA-256 和 MIT 许可放在 `assets/model_logos`。来源清单仅声明本轮引入的 16 份第三方资源。

新增 `ImageAssetLoaderAndroidTest` 六项设备回归源码，覆盖带偏移 viewBox 的双色 SVG、PNG/JPEG/WebP 的 MIME 与扩展名入口、39 份素材实际解码、单色标识在浅深主题的颜色与透明度、所有彩色标识逐像素保色及历史身份、损坏或不支持的图片和非法尺寸。这些回归未在本地执行。

静态检查与新远程运行的链接由 PR 交付记录维护。设备视觉效果及实际解码结果等待远程验证，不将源码或静态解析视为通过结论。

[DONE]
