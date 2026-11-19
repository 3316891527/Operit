# Mermaid 真实浏览器几何回归

该检查直接加载应用 `assets/markdown/mermaid` 中的本地页面、控制器和 Mermaid 脚本。验证内容包括图形是否完整落入视口、初始居中、普通预览和全屏尺寸、缩放与拖动后的复位，以及加载失败和源码变更。

使用 Node.js 22 及以上版本。在桌面 Linux 的 CI 环境中执行：

```sh
npm ci --prefix ci/mermaid-preview
npx --prefix ci/mermaid-preview playwright install --with-deps --only-shell chromium
npm test --prefix ci/mermaid-preview
```

Android Tests 工作流的 `Mermaid browser geometry` 作业独立执行这套回归，不需要 Android 模拟器。设备上的 WebView 字体、手势分发和 Compose 生命周期仍由 instrumentation 与真机验收覆盖。Playwright 所用 Chromium 和 Android WebView 属于不同发行环境，不能把浏览器回归通过等同于设备测试通过。

每项场景结束后会保存当前视口截图和实际几何数据到 `artifacts/mermaid-preview`，内容仅含本文件夹定义的合成图表，不包含用户文档。