/**
 * `@baicodex/liquid` —— 液态玻璃。
 *
 * 只提供原语，不提供业务组件。你要一块玻璃就用 `GlassSurface`，要按钮自己拼。
 * 这样这个包能被任何项目拿去用，不绑死在聊天界面上。
 *
 * ## 三层效果
 *
 * | 层 | 谁做的 | 效果 |
 * |---|---|---|
 * | 窗口底板 | 宿主（DWM Mica / Acrylic） | 桌面壁纸透进来 |
 * | 控件折射 | 本包（SVG 位移滤镜） | 边缘像透镜一样弯折背景 |
 * | 表面质感 | 本包（高光 + 颗粒 + 描边） | 玻璃的"厚度" |
 *
 * 三层缺一层都会露馅：没有 DWM，窗口是个不透明的方块；没有折射，玻璃像磨砂塑料；
 * 没有高光，它像一张半透明的纸。
 *
 * ## 一行上手
 *
 * ```ts
 * import { install, GlassSurface } from "@baicodex/liquid";
 * install();
 * const g = new GlassSurface({ radius: 24, blur: 28, depth: 14 });
 * g.inner.textContent = "hello";
 * document.body.append(g.el);
 * ```
 */

export { GlassSurface, glass } from "./surface";
export type { GlassOptions } from "./surface";

export { Spring, criticalDamping } from "./spring";

export { caps, defaultBlur } from "./capability";
export type { Capabilities } from "./capability";

export { displacementMap, registerFilter, unregisterFilter, clearCache } from "./refract";
export type { DisplacementMap } from "./refract";

export { install, css, NOISE_URL } from "./style";
