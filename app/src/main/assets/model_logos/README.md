# 提供商标识素材

每个提供商目录只保留一个 SVG 或 PNG。目录名是提供商身份，按既有身份归一化规则查找。内置 OTHER 使用本项目原创的中性连接节点 SVG，不代表第三方品牌。

本轮引入的 16 个彩色 SVG 来自 [Lobe Icons](https://github.com/lobehub/lobe-icons)，固定提交 `55746b0b082258211121c70bd26e6878fd3bd292`，原样保留上游内容。[来源清单](sources.lobe-icons.json)记录路径、URL、字节数与 SHA-256，[MIT 许可](LICENSE.lobe-icons)随包分发。此清单只声明本轮引入的素材，不追溯声明其他历史资源的许可。

品牌原有单色标识继续保持单色。`ProviderLogoLoader` 的彩色素材集合声明哪些标识保留原色，其余标识由主题前景色绘制。新增或替换素材时同步更新该策略，避免彩色图案被整图染色。现有 INFINIAI 和 IFLOW 的紫色 PNG 保留。Antigravity 上游彩色 SVG 含 AndroidSVG 1.4 不支持的 filter，本轮保留原单色素材。

通用 `ImageAssetLoader` 不读取本目录的品牌颜色策略，插件、远程资源和 Linux 素材均按其内容原色显示。
