import type { ThemeScript } from "./oktheme";

// 与 minimal.ts 相同，编译为 CommonJS 后导入生成的主题脚本。
const theme: ThemeScript = {
    apiVersion: 1,
    render(context) {
        return {
            ...context.appearance,
            apiVersion: 1,
            surfaces: {
                ...context.appearance.surfaces,
                button: {
                    ...context.appearance.surfaces?.button,
                    fillColor: context.colors.primary,
                    contentColor: context.colors.onPrimary,
                    transition: { durationMillis: 180 },
                    ripple: { enabled: true, color: context.colors.onPrimary },
                    states: { pressed: { opacity: 0.82 }, disabled: { opacity: 0.38 } },
                },
            },
        };
    },
};
export = theme;
