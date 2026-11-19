# Mermaid browser asset

本目录的 `mermaid.min.js` 是 npm `mermaid@11.17.2` 的完整浏览器构建，保持上游文件字节和内置依赖许可证注释不变。

- 上游项目：https://github.com/mermaid-js/mermaid
- 下载地址：https://registry.npmjs.org/mermaid/-/mermaid-11.17.2.tgz
- npm 归档 SHA-512：`V6K3C8EBdEsPFZXSKMJe6ppQOENxuHARr9GvHX4hh47lAbhMRD9qf4oEK7LoaRQxULMa80/qt5gHO73aCleBBg==`
- `mermaid.min.js` SHA-256：`581ed7d74bd9048d0e3a91363927d72ef22942d7722546b27f7cc29e35390eb8`
- 主包许可：MIT，原文见 [LICENSE](LICENSE)。浏览器文件末尾保留了上游内嵌依赖的许可证说明。

升级时下载固定版本的 npm 归档，核对 registry 的 `dist.integrity`，提取 `package/dist/mermaid.min.js` 与 `package/LICENSE`，同步更新本记录并执行图表兼容性验收。不重新压缩或改写第三方脚本。

[preview.html](preview.html) 和 [renderer.js](renderer.js) 是应用维护的本地页面与控制器。它们通过 Android `WebViewAssetLoader` 加载，只允许同目录的三个运行时文件，拒绝外部网络和导航。

该上游压缩文件包含模板和着色器中的行末空白，`.gitattributes` 仅对该文件关闭空白差异检查以保留已核对的上游字节。应用页面与控制器继续接受普通空白检查。
