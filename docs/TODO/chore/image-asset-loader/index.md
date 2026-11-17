---
title: 通用图片资源加载与提供商图标分层
status: pending_validation
fork: https://github.com/3316891527/Operit
branch: feat/workspace-file-management
---
# 通用图片资源加载与提供商图标分层

现有 `ProviderLogoLoader.kt` 同时容纳通用 SVG、PNG、JPEG、WebP 解码与提供商专属查找、主题着色。工具包、市场和 Linux 图标已复用通用部分；提供商深色主题整图染色会覆盖彩色品牌素材，存储管理入口又没有相同处理。

本轮提取 `ImageAssetLoader`，提供商业务集中在同包 `ProviderLogo.kt`。通用 Painter 入口统一使用图片资源命名，提供商 Painter 内部负责素材颜色策略。给缺失的内置 OTHER 补中性 SVG，选择固定来源的品牌彩色素材。

[实施与验证](1-loader-and-provider-logos.md)记录接口、来源和检查范围。继续通过 [PR #216](https://github.com/3316891527/Operit/pull/216) 交付，关联[工作区交付计划](../../feat/workspace-file-management/index.md)。

主题脚本的 Picture 渲染链路不属于本轮作用域。动态工具包提供商仍使用各自身份，本轮不为没有品牌素材的动态标识伪造品牌图案。
