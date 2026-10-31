/** Android 原生主题外观协议；apiVersion 仅用于机器校验。 */
export type SurfaceId = "background" | "userMessage" | "assistantMessage" | "composer" |
    "userAvatar" | "assistantAvatar" | "appBar" | "navigation" | "navigationItem" |
    "button" | "iconButton" | "input" | "settingsCard" | "dialog" | "sheet" | "menu" |
    "listItem" | "status" | "toolCard";
export type IconId = "composer.settings" | "composer.attachment" | "composer.microphone" |
    "composer.send" | "composer.stop" | "composer.fullscreen" | "composer.queue" |
    "composer.expand" | "appBar.back" | "appBar.menu" | "appBar.history" |
    "appBar.floating" | "navigation.item";
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
export interface Typography {
    family?: "default" | "sans-serif" | "serif" | "monospace" | "cursive";
    scale?: number;
    lineHeight?: number;
    letterSpacing?: number;
}
export interface Shape { type?: "rounded" | "cut" | "circle"; radius?: number; }
export interface IconAsset { asset: string; size?: number; opacity?: number; useContentTint?: boolean; }
/** 坐标和宽高为所在部件的归一化比例。 */
export interface Bounds { x?: number; y?: number; width?: number; height?: number; }
/** 内容留白使用 dp，九宫格切片使用原图像素。 */
export interface Insets { start?: number; top?: number; end?: number; bottom?: number; }
interface BaseLayer {
    id?: string;
    bounds?: Bounds;
    opacity?: number;
    placement?: "background" | "foreground";
    state?: "always" | "pressed" | "focused" | "selected" | "disabled" | "error";
}
export interface PaintLayer extends BaseLayer {
    type: "rect" | "ellipse" | "line";
    color?: Color;
    strokeColor?: Color;
    strokeWidth?: number;
    radius?: number;
}
export interface ImageLayer extends BaseLayer {
    type: "image";
    asset: string;
    fit?: "fill" | "fit" | "crop";
}
export interface NineSliceLayer extends BaseLayer {
    type: "nineSlice";
    asset: string;
    slices: Insets;
}
export type Layer = PaintLayer | ImageLayer | NineSliceLayer;
export interface Surface {
    padding?: Insets;
    layers?: readonly Layer[];
    shape?: Shape;
    fillColor?: Color;
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
export interface Appearance {
    apiVersion: 1;
    surfaces?: Partial<Record<SurfaceId, Surface>>;
    icons?: Partial<Record<IconId, IconAsset>>;
    palette?: Palette;
    typography?: Typography;
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
export interface ScriptManifest {
    apiVersion: 1;
    entry: string;
    assets?: Record<string, string>;
    appearance?: Appearance;
}
