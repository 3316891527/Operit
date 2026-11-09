# 渐变、页面背景与独立文字样式

此页说明 `.oktheme` 原生外观扩展。JS 与主题工作室共用数据字段；`apiVersion: 1` 的已有字段保持原义。没有使用扩展的主题照常读取，使用新增字段的主题需要支持该扩展的应用版本。

## 渐变

`Surface.fillGradient` 与 `fillColor` 互斥。矩形、椭圆和线条绘图层的 `gradient` 与 `color` 互斥；图像和九宫格不接受渐变填充。颜色继续使用 `#RRGGBB` 或 Android `#AARRGGBB`。

```js
{
  type: "linear",
  stops: [
    { offset: 0, color: "#FF6750A4" },
    { offset: 0.5, color: "#FFD3B5FF" },
    { offset: 1, color: "#FFD9F6ED" }
  ],
  start: { x: 0, y: 0 },
  end: { x: 1, y: 1 }
}
```

`type` 为 `linear` 或 `radial`，默认线性。2–16 个停点必须严格递增，起点为 0，终点为 1。坐标归一化到当前填充或图层边界，X 向右、Y 向下，不随文字布局方向改变。线性起终点不得重合；径向使用 `center`，默认 `0.5/0.5`，`radius` 为短边的 `0.01..2` 倍，默认 0.5。所有坐标均为 `0..1`。原生绘制支持应用的最低 Android 版本，不要求 Shader 或逐帧 JS。

渐变继承部件及图层现有透明度和裁剪。状态的纯色填充覆盖基础渐变；未声明状态填充时保留渐变。工作室在外观和图层中编辑相同字段，提供颜色停点、水平/垂直/对角方向以及完整的坐标参数。

## 页面独立背景

已有 `surfaces.background` 在工作室中显示为全局背景，存储标识没有改名。每个主导航路由都有自己的 `page.*` 背景目标，例如：

```js
{
  apiVersion: 1,
  surfaces: {
    "page.packages": {
      layers: [{ type: "image", asset: "packagesWallpaper", fit: "crop", opacity: 0.8 }]
    },
    "page.settings": {
      fillGradient: {
        stops: [{ offset: 0, color: "#FFEDE7FF" }, { offset: 1, color: "#FFFFFFFF" }]
      }
    }
  }
}
```

页面复用背景填充、图片、SVG、铺放、透明度及绘图层，并分别保存浅深模式。页面不能添加内容留白。没有绘制声明的页面跟随聊天主界面 `page.aiChat`；聊天也没有独立背景时，使用共享的全局背景。工作室把全局背景和全部 66 个页面背景收在同一入口内，单页恢复跟随只清除当前模式的独立外观；“全部页面跟随聊天主界面”同时清除其他页面在公共层及所有模式下的独立背景，保留聊天与全局背景，并可一次撤销。

页面背景位于内容画布，独立声明时内容 Scaffold 透明，卡片和业务控件保留自身材质。页面顶栏、工作区、终端及 WebView 等拥有独立绘制内容，背景设置不会替换其中的文档或原生画面。弹出窗口不构成额外主路由，它们继续使用既有部件样式。

稳定页面标识如下，路由中的会话 ID、路径、包名和其他动态参数不传入主题选择器：

- `page.aiChat`
- `page.memoryBase`
- `page.memorySpaceDetail`
- `page.packages`
- `page.packageDetail`
- `page.market`
- `page.marketCategory`
- `page.marketNotifications`
- `page.builtInMarketDetail`
- `page.marketEntryDetail`
- `page.marketAuthor`
- `page.marketManage`
- `page.artifactPublish`
- `page.artifactContinuePublish`
- `page.artifactEdit`
- `page.repoPublish`
- `page.repoEdit`
- `page.repoPublishVersion`
- `page.shizukuCommands`
- `page.settings`
- `page.gitHubAccount`
- `page.help`
- `page.about`
- `page.agreement`
- `page.updateHistory`
- `page.workflow`
- `page.workflowDetail`
- `page.tokenConfig`
- `page.userPreferencesSettings`
- `page.toolPermission`
- `page.modelConfig`
- `page.speechServicesSettings`
- `page.mnnModelDownload`
- `page.personaCardGeneration`
- `page.waifuModeSettings`
- `page.customEmojiManagement`
- `page.tagMarket`
- `page.modelPromptsSettings`
- `page.functionalConfig`
- `page.themeSettings`
- `page.globalDisplaySettings`
- `page.dataStorageManagement`
- `page.localModelStorage`
- `page.linuxEnvironmentStorage`
- `page.workspaceMediaStorage`
- `page.chatHistoryStorage`
- `page.memoryLibraryStorage`
- `page.backupExportStorage`
- `page.configurationResourceStorage`
- `page.chatHistorySettings`
- `page.chatBackupSettings`
- `page.languageSettings`
- `page.tokenUsageStatistics`
- `page.performanceMonitor`
- `page.contextSummarySettings`
- `page.toolPkgComposeDsl`
- `page.toolPkgPluginConfig`
- `page.terminal`
- `page.terminalSetup`
- `page.terminalAutoConfig`
- `page.uIDebugger`
- `page.logcat`
- `page.markdownDemo`
- `page.speechToText`
- `page.defaultAssistantGuide`
- `page.htmlPackager`

## 独立文字样式

在全局文字排版中选择文字角色或具体位置；JS 在 `textStyles` 中写入相同声明。支持 `family`、`color`、`weight`、`scale`、`lineHeight`、`letterSpacing` 和 `stroke`。`weight` 为 `1..1000`，省略时保留基础字重。

```js
{
  apiVersion: 1,
  textStyles: {
    "bodyMedium": { scale: 1.05 },
    "chat.modelName": {
      family: "monospace", weight: 600, color: "#FF493181",
      stroke: { color: "#FFFFFFFF", width: 0.1 }
    }
  }
}
```

基础全局与区域排版先按既有规则消费。共用文字封装将业务提供的字号、颜色等作为基础，再应用匹配的文字角色和具体语义目标。缩放按层叠加；颜色、字体、字重由更具体目标覆盖。默认数值保留现有排版语义，特别是 `letterSpacing: 0` 沿用基础字距。局部 `stroke` 省略时继承，`width: 0` 关闭该目标的描边。

角色匹配当前 Material `TextStyle`，覆盖使用共用文字封装的对应文字；原生输入控件与第三方绘制文字保留它们自己的排版。具体目标不根据显示文案匹配，不会因为型号或语言变化失效。

15 个文字角色：

- `displayLarge`
- `displayMedium`
- `displaySmall`
- `headlineLarge`
- `headlineMedium`
- `headlineSmall`
- `titleLarge`
- `titleMedium`
- `titleSmall`
- `bodyLarge`
- `bodyMedium`
- `bodySmall`
- `labelLarge`
- `labelMedium`
- `labelSmall`

9 个语义目标及当前接入位置：

- `chat.modelName`：Classic 与 Agent 的模型名称，包括 Agent 简略输入标签和模型选择器
- `chat.title`：共用聊天顶栏标题
- `chat.tokenCount`：输入设置中上下文用量的百分比
- `navigation.title`：主导航标准顶栏标题
- `package.name`、`package.description`：包列表项和共用扩展详情头
- `package.version`：市场详情的版本历史行
- `settings.title`、`settings.description`：设置首页卡片标题、列表标题和说明

`modes.light/dark.textStyles` 按目标覆盖公共声明，单个目标使用完整局部对象，未声明目标保留公共配置。字体素材的引用、删除检查和 `.oktheme` 归档与现有排版共用；新增独立样式无需重新注册字体。

可直接导入的 JS 示例见 [foundation.js](../../../examples/theme-script/foundation.js)，类型定义见 [oktheme.d.ts](../../../examples/theme-script/oktheme.d.ts)。自定义脚本继续显式合并需要保留的 `context.appearance`，可视化不会反向改写任意 JS 逻辑。

## 原生交互效果

按钮、图标按钮、独立动作按钮和导航项可以声明 `transition: { durationMillis: 180 }` 与 `ripple: { enabled: true, color: "#80FFFFFF" }`。过渡时长为 0–1000 的整数毫秒，0 为立即切换。它们都是可选字段；未使用扩展的旧主题不会增加字段。

过渡在原生端插值透明度、边框宽度，以及明确设置了基础色的填色、正文色和边框色。图层素材、形状、渐变和原生继承色仍按当前状态解析。一个部件共用一份原生动画结果，不逐帧运行 JS；初次加载和主题声明改变时立即使用新配置，动画时长由系统设置控制。

涟漪沿用 Material3 的点击反馈，`enabled: false` 显式关闭；未指定颜色时使用原生默认颜色。没有新增点击层或业务事件。工作室在部件的“状态”页中编辑开关、时长滑条和涟漪颜色，所有设置分别保存浅深模式，并随工程草稿与主题归档往返。完整示例见 `examples/theme-script/interaction.ts`。

## 工作室属性与真实预览

属性面板按外观、布局与位置、文字排版、图标及高级效果分组。消息分别使用消息外观、消息布局和消息文字标题。高级效果默认折叠，原生渐变位于部件面板末尾；图层渐变和图像模糊也位于对应图层的普通属性之后。字重与文字描边宽度继续使用滑条和数值输入，不提供枚举预设。

全部 66 个页面背景复用应用的 AppContent 和真实 Screen.Content，包含真实顶栏、页面背景和业务控件。52 个页面直接打开；13 个依赖路由参数的页面需从实际列表、市场或包详情中选择对象；普通插件页面从已安装插件的真实路由中选择。未选择对象时显示选择入口，未安装插件时提示安装条件。对象和路由参数仅保留在当前工作室会话，不进入主题工程或导出。

普通预览拦截触摸，选择窗口则允许在实际页面中导航。预览使用独立路由、保存状态注册表、页面 ViewModelStore 和插件执行标识，生命周期跟随窗口，关闭后释放。聊天读取当前会话，但不消费共享输入、会话钩子或待发队列。预览不会更改主窗口输入模式。

实际页面会保留数据加载和插件初始化，可能读取全局数据库、业务服务或网络。独立页面宿主不构成业务数据或插件脚本的沙箱；需要已有对象、登录或管理权限的页面沿用原条件。终端、WebView 等独立画布仍由实际页面控制。
