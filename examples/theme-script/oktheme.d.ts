/** Android 原生主题外观协议；apiVersion 仅用于机器校验。 */
export type SurfaceId = "background" | "userMessage" | "assistantMessage" | "composer" |
    "userAvatar" | "assistantAvatar" | "appBar" | "navigation" | "navigationItem" |
    "button" | "iconButton" | "input" | "settingsCard" | "dialog" | "sheet" | "menu" |
    "listItem" | "status" | "toolCard" | "attachmentMenu" | "modelMenu" | "chatSettingsMenu" | ActionButtonId | PageId;
export type IconId = "composer.settings" | "composer.attachment" | "composer.microphone" |
    "composer.send" | "composer.stop" | "composer.fullscreen" | "composer.queue" |
    "composer.expand" | "appBar.back" | "appBar.menu" | "appBar.workspace" |
    "appBar.floating" | "navigation.item";
/** 动作按钮容器与图标素材分别声明，未声明容器时继承共用 iconButton。 */
export type ActionButtonId = Exclude<IconId, "navigation.item">;
/** 六位是 RGB，八位是 ARGB。 */
export type Color = `#${string}`;
export type ColorToken = "primary" | "onPrimary" | "primaryContainer" | "onPrimaryContainer" |
    "inversePrimary" | "secondary" | "onSecondary" | "secondaryContainer" | "onSecondaryContainer" |
    "tertiary" | "onTertiary" | "tertiaryContainer" | "onTertiaryContainer" |
    "background" | "onBackground" | "surface" | "onSurface" | "surfaceVariant" | "onSurfaceVariant" |
    "surfaceTint" | "inverseSurface" | "inverseOnSurface" | "error" | "onError" | "errorContainer" |
    "onErrorContainer" | "outline" | "outlineVariant" | "scrim" | "surfaceBright" | "surfaceDim" |
    "surfaceContainerLowest" | "surfaceContainerLow" | "surfaceContainer" |
    "surfaceContainerHigh" | "surfaceContainerHighest";
export interface Palette {
    light?: Partial<Record<ColorToken, Color>>;
    dark?: Partial<Record<ColorToken, Color>>;
}
/** 文字轮廓使用 dp；区域省略 stroke 时继承全局，width 为 0 时单独关闭。 */
export interface TextStroke { color?: Color; width?: number; }
export interface Typography {
    /** 1..1000; omitted weight retains the parent style. */
    weight?: number;
    family?: "default" | "sans-serif" | "serif" | "monospace" | "cursive" | `asset:${string}`;
    scale?: number;
    lineHeight?: number;
    letterSpacing?: number;
    /** 全局或区域文字颜色；null 表示沿用原有配色。 */
    color?: Color | null;
    stroke?: TextStroke | null;
}
export interface Shape { type?: "rounded" | "cut" | "circle"; radius?: number; }
export interface IconAsset { asset: string; size?: number; opacity?: number; useContentTint?: boolean; tintColor?: Color; }
/** 位置为 -1..1，宽高为 (0,1]；平移保持尺寸，超出部件边缘的部分裁剪。 */
export interface Bounds { x?: number; y?: number; width?: number; height?: number; }
/** 内容留白使用 dp，九宫格切片使用原图像素。 */
export interface Insets { start?: number; top?: number; end?: number; bottom?: number; }
interface BaseLayer {
    id?: string;
    bounds?: Bounds;
    opacity?: number;
    placement?: "background" | "foreground";
    sampling?: "smooth" | "nearest";
    alignment?: "center" | "top" | "bottom" | "start" | "end";
    /** 0..8 dp；非零值要求 Android 12 或更新系统。 */
    blur?: number;
    state?: "always" | "pressed" | "focused" | "selected" | "disabled" | "error";
}
export interface PaintLayer extends BaseLayer {
    type: "rect" | "ellipse" | "line";
    color?: Color;
    /** Mutually exclusive with color. */
    gradient?: Gradient;
    strokeColor?: Color;
    strokeWidth?: number;
    radius?: number;
}
export interface ImageLayer extends BaseLayer {
    type: "image";
    asset: string;
    fit?: "fill" | "fit" | "crop" | "tile";
}
export interface NineSliceLayer extends BaseLayer {
    type: "nineSlice";
    asset: string;
    slices: Insets;
}
export type Layer = PaintLayer | ImageLayer | NineSliceLayer;
/** 仅三个聊天弹起菜单使用；每项为 0..256 dp，向上与向下取差，不提供向右偏移。 */
export interface PopupOffset { up?: number; down?: number; left?: number; }
/** 原生按钮和导航项的状态过渡；0..1000 ms，零表示立即切换。 */
export interface StateTransition { durationMillis?: number; }
/** 原生按钮和导航项的点击反馈；未声明时继承应用默认设置。 */
export interface Ripple { enabled?: boolean; color?: Color; }
export interface Surface {
    transition?: StateTransition;
    ripple?: Ripple;
    /** 整个弹起菜单的物理位置偏移，窗口边缘限制与实际命中区域同步生效。 */
    popupOffset?: PopupOffset;
    padding?: Insets;
    layers?: readonly Layer[];
    opacity?: number;
    states?: Partial<Record<"pressed" | "focused" | "selected" | "disabled" | "error", SurfaceState>>;
    shape?: Shape;
    fillColor?: Color;
    /** Mutually exclusive with fillColor. */
    fillGradient?: Gradient;
    contentColor?: Color;
    strokeColor?: Color;
    strokeWidth?: number;
    elevation?: number;
    minHeight?: number;
    /** 仅用于头像，单位 dp。 */
    size?: number;
    /** 仅用于消息，为现有最大宽度的比例。 */
    messageWidth?: number;
    textStyle?: Typography;
}
/** 空状态字段继承该部件的默认值，透明度与默认透明度相乘。 */
export interface SurfaceState {
    fillColor?: Color;
    contentColor?: Color;
    strokeColor?: Color;
    strokeWidth?: number;
    opacity?: number;
}
/** 只覆盖声明的部件；单个部件以完整局部外观替换公共声明。 */
export interface AppearanceMode {
    surfaces?: Partial<Record<SurfaceId, Surface>>;
    icons?: Partial<Record<IconId, IconAsset>>;
    typography?: Typography;
    textStyles?: Partial<Record<TextTarget, Typography>>;
}
export interface Appearance {
    apiVersion: 1;
    surfaces?: Partial<Record<SurfaceId, Surface>>;
    icons?: Partial<Record<IconId, IconAsset>>;
    palette?: Palette;
    typography?: Typography;
    textStyles?: Partial<Record<TextTarget, Typography>>;
    modes?: Partial<Record<"light" | "dark", AppearanceMode>>;
}
export interface ThemeContext {
    readonly apiVersion: 1;
    readonly mode: "light" | "dark";
    readonly colors: Readonly<Record<ColorToken, Color>>;
    readonly appearance: Readonly<Appearance>;
    readonly settings: {
        readonly strings: Readonly<Record<string, string>>;
        readonly booleans: Readonly<Record<string, boolean>>;
        readonly ints: Readonly<Record<string, number>>;
        readonly floats: Readonly<Record<string, number>>;
    };
}
export interface ThemeScript {
    apiVersion: 1;
    render(context: ThemeContext): Appearance;
}
/** 制作信息与相对 assets/ 空目录随完整主题包保留。 */
export interface StudioMetadata {
    id?: string;
    name?: string;
    author?: string;
    version?: string;
    description?: string;
    license?: string;
    folders?: readonly string[];
}
export interface ScriptManifest {
    apiVersion: 1;
    entry: string;
    assets?: Record<string, string>;
    appearance?: Appearance;
    studio?: StudioMetadata;
}

/** Main route identities, independent of route instance parameters. */
export type PageId =
    "page.aiChat"
    | "page.memoryBase"
    | "page.memorySpaceDetail"
    | "page.packages"
    | "page.packageDetail"
    | "page.market"
    | "page.marketCategory"
    | "page.marketNotifications"
    | "page.builtInMarketDetail"
    | "page.marketEntryDetail"
    | "page.marketAuthor"
    | "page.marketManage"
    | "page.artifactPublish"
    | "page.artifactContinuePublish"
    | "page.artifactEdit"
    | "page.repoPublish"
    | "page.repoEdit"
    | "page.repoPublishVersion"
    | "page.shizukuCommands"
    | "page.settings"
    | "page.gitHubAccount"
    | "page.help"
    | "page.about"
    | "page.agreement"
    | "page.updateHistory"
    | "page.workflow"
    | "page.workflowDetail"
    | "page.tokenConfig"
    | "page.userPreferencesSettings"
    | "page.toolPermission"
    | "page.modelConfig"
    | "page.speechServicesSettings"
    | "page.mnnModelDownload"
    | "page.personaCardGeneration"
    | "page.waifuModeSettings"
    | "page.customEmojiManagement"
    | "page.tagMarket"
    | "page.modelPromptsSettings"
    | "page.functionalConfig"
    | "page.themeSettings"
    | "page.globalDisplaySettings"
    | "page.dataStorageManagement"
    | "page.localModelStorage"
    | "page.linuxEnvironmentStorage"
    | "page.workspaceMediaStorage"
    | "page.chatHistoryStorage"
    | "page.memoryLibraryStorage"
    | "page.backupExportStorage"
    | "page.configurationResourceStorage"
    | "page.chatHistorySettings"
    | "page.chatBackupSettings"
    | "page.languageSettings"
    | "page.tokenUsageStatistics"
    | "page.performanceMonitor"
    | "page.contextSummarySettings"
    | "page.toolPkgComposeDsl"
    | "page.toolPkgPluginConfig"
    | "page.terminal"
    | "page.terminalSetup"
    | "page.terminalAutoConfig"
    | "page.uIDebugger"
    | "page.logcat"
    | "page.markdownDemo"
    | "page.speechToText"
    | "page.defaultAssistantGuide"
    | "page.htmlPackager";

export type TextTarget =
    "displayLarge"
    | "displayMedium"
    | "displaySmall"
    | "headlineLarge"
    | "headlineMedium"
    | "headlineSmall"
    | "titleLarge"
    | "titleMedium"
    | "titleSmall"
    | "bodyLarge"
    | "bodyMedium"
    | "bodySmall"
    | "labelLarge"
    | "labelMedium"
    | "labelSmall"
    | "chat.modelName"
    | "chat.title"
    | "chat.tokenCount"
    | "navigation.title"
    | "package.name"
    | "package.description"
    | "package.version"
    | "settings.title"
    | "settings.description";

/** Normalized coordinates: x increases rightward, y downward, independent of layout direction. */
export interface GradientPoint { x: number; y: number; }
export interface GradientStop { offset: number; color: Color; }
/** 2..16 strictly increasing stops, spanning 0..1; radius uses the shortest side. */
export interface Gradient {
    type?: "linear" | "radial";
    stops: readonly GradientStop[];
    start?: GradientPoint;
    end?: GradientPoint;
    center?: GradientPoint;
    radius?: number;
}
