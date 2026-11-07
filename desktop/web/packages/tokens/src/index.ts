/**
 * 设计令牌。
 *
 * 这一层是**唯一**允许写死颜色数值的地方。组件里出现 `#2f6bff` 就是漏了——
 * 换成 `var(--bai-accent)`。理由很实在：暗色模式、高对比度、品牌换色，全都只改
 * 这一处。
 *
 * 为什么不用 CSS 里的 `light-dark()`：WebView2 的版本跟着 Edge 走，用户可能停在
 * 一个不支持它的版本上。用 `[data-theme]` 属性切换，兼容性没有悬念。
 *
 * 命名规则：`--bai-<类别>-<语义>`，语义名不带颜色词。`--bai-danger` 而不是
 * `--bai-red`，因为红不一定是危险色（有些品牌用橙）。
 */

/** 一套完整的配色。 */
export interface Palette {
  /** 窗口兜底色。窗口透明透出桌面时看不到它，WebView2 没起来时才露出来。 */
  bg: string;
  /** 比 bg 略深/略浅一层，用于侧栏这种"永远存在"的面。 */
  bgSunken: string;
  /** 正文。 */
  fg: string;
  /** 次要文字：时间戳、说明。 */
  fgDim: string;
  /** 更弱的：占位符、禁用。 */
  fgMute: string;
  /** 分隔线。用 rgba 而不是实色，叠在任何背景上都对。 */
  line: string;
  /** 更明显的边框，用于可聚焦控件。 */
  lineStrong: string;
  /** 主色。 */
  accent: string;
  /** 主色上的文字。 */
  accentFg: string;
  /** 主色的淡底，用于选中态。 */
  accentSoft: string;
  danger: string;
  warn: string;
  ok: string;
  /** 玻璃体本身的染色。 */
  glass: string;
  /** 玻璃体的高光边（顶部那道亮线）。 */
  glassEdge: string;
  /** 玻璃体的暗边（底部）。 */
  glassShade: string;
  /** 阴影色。 */
  shadow: string;
  /** 代码块底色。 */
  codeBg: string;
}

export const light: Palette = {
  bg: "#edf0f6",
  bgSunken: "#e3e7ef",
  fg: "#14161c",
  fgDim: "#565d6e",
  fgMute: "#8b91a1",
  line: "rgba(12, 16, 26, 0.09)",
  lineStrong: "rgba(12, 16, 26, 0.18)",
  accent: "#2f6bff",
  accentFg: "#ffffff",
  accentSoft: "rgba(47, 107, 255, 0.12)",
  danger: "#d93b41",
  warn: "#b8730a",
  ok: "#1f8f5f",
  glass: "rgba(255, 255, 255, 0.52)",
  glassEdge: "rgba(255, 255, 255, 0.85)",
  glassShade: "rgba(12, 16, 26, 0.10)",
  shadow: "rgba(16, 22, 38, 0.16)",
  codeBg: "rgba(12, 16, 26, 0.055)",
};

export const dark: Palette = {
  bg: "#0d0f14",
  bgSunken: "#080a0e",
  fg: "#e9ecf3",
  fgDim: "#9aa2b4",
  fgMute: "#6a7185",
  line: "rgba(255, 255, 255, 0.10)",
  lineStrong: "rgba(255, 255, 255, 0.22)",
  accent: "#5b8cff",
  accentFg: "#0b1020",
  accentSoft: "rgba(91, 140, 255, 0.18)",
  danger: "#ff6369",
  warn: "#ffb224",
  ok: "#3dd68c",
  glass: "rgba(20, 23, 30, 0.56)",
  glassEdge: "rgba(255, 255, 255, 0.22)",
  glassShade: "rgba(0, 0, 0, 0.45)",
  shadow: "rgba(0, 0, 0, 0.45)",
  codeBg: "rgba(255, 255, 255, 0.06)",
};

/** 与主题无关的量：间距、圆角、时长。 */
export const scale = {
  /** 4px 基准。除了 1px 描边，所有间距都从这里取。 */
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 56, 72] as const,
  radius: { xs: 6, sm: 10, md: 14, lg: 20, xl: 28, pill: 999 } as const,
  /** 玻璃模糊半径。越大越"厚"，也越吃 GPU。 */
  blur: { thin: 8, base: 18, thick: 32, heavy: 48 } as const,
  /** 字体。`Segoe UI Variable` 是 Win11 的系统字体，回落到雅黑。 */
  font: {
    ui: '"Segoe UI Variable Text", "Segoe UI", system-ui, "Microsoft YaHei UI", "PingFang SC", sans-serif',
    display: '"Segoe UI Variable Display", "Segoe UI", system-ui, "Microsoft YaHei UI", sans-serif',
    mono: '"Cascadia Code", "JetBrains Mono", Consolas, "Courier New", monospace',
  },
  /** 字阶。 */
  text: { xs: 11, sm: 12, md: 13, base: 14, lg: 16, xl: 20, xxl: 26 } as const,
  /** 动效时长，毫秒。 */
  dur: { fast: 120, base: 200, slow: 320, lazy: 520 } as const,
  /** 缓动。`out` 用于进场，`inOut` 用于位移，`spring` 用于回弹。 */
  ease: {
    out: "cubic-bezier(0.22, 1, 0.36, 1)",
    inOut: "cubic-bezier(0.65, 0, 0.35, 1)",
    spring: "cubic-bezier(0.34, 1.56, 0.64, 1)",
  },
} as const;

function paletteVars(p: Palette): string {
  return [
    ["bg", p.bg],
    ["bg-sunken", p.bgSunken],
    ["fg", p.fg],
    ["fg-dim", p.fgDim],
    ["fg-mute", p.fgMute],
    ["line", p.line],
    ["line-strong", p.lineStrong],
    ["accent", p.accent],
    ["accent-fg", p.accentFg],
    ["accent-soft", p.accentSoft],
    ["danger", p.danger],
    ["warn", p.warn],
    ["ok", p.ok],
    ["glass", p.glass],
    ["glass-edge", p.glassEdge],
    ["glass-shade", p.glassShade],
    ["shadow", p.shadow],
    ["code-bg", p.codeBg],
  ]
    .map(([k, v]) => `  --bai-${k}: ${v};`)
    .join("\n");
}

function scaleVars(): string {
  const L: string[] = [];
  scale.space.forEach((v, i) => L.push(`  --bai-s${i}: ${v}px;`));
  for (const [k, v] of Object.entries(scale.radius)) L.push(`  --bai-r-${k}: ${v}px;`);
  for (const [k, v] of Object.entries(scale.blur)) L.push(`  --bai-blur-${k}: ${v}px;`);
  for (const [k, v] of Object.entries(scale.text)) L.push(`  --bai-t-${k}: ${v}px;`);
  for (const [k, v] of Object.entries(scale.dur)) L.push(`  --bai-dur-${k}: ${v}ms;`);
  for (const [k, v] of Object.entries(scale.ease)) L.push(`  --bai-ease-${k}: ${v};`);
  L.push(`  --bai-font: ${scale.font.ui};`);
  L.push(`  --bai-font-display: ${scale.font.display};`);
  L.push(`  --bai-font-mono: ${scale.font.mono};`);
  return L.join("\n");
}

/** 生成完整样式表。`install()` 会把它塞进 `<head>`。 */
export function css(selector = ":root"): string {
  return `${selector} {
${scaleVars()}
${paletteVars(light)}
  color-scheme: light;
}

${selector}[data-theme="dark"] {
${paletteVars(dark)}
  color-scheme: dark;
}

@media (prefers-reduced-motion: reduce) {
  ${selector} {
    --bai-dur-fast: 0ms;
    --bai-dur-base: 0ms;
    --bai-dur-slow: 0ms;
    --bai-dur-lazy: 0ms;
  }
}
`;
}

const STYLE_ID = "bai-tokens";

/** 注入令牌样式。重复调用是幂等的。 */
export function install(doc: Document = document): void {
  if (doc.getElementById(STYLE_ID)) return;
  const el = doc.createElement("style");
  el.id = STYLE_ID;
  el.textContent = css();
  doc.head.appendChild(el);
}

/** 主题模式。`auto` 跟随系统。 */
export type ThemeMode = "light" | "dark" | "auto";

/** 当前实际生效的深浅（`auto` 时看系统）。 */
export function resolve(mode: ThemeMode): "light" | "dark" {
  if (mode !== "auto") return mode;
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/** 把模式写到 `data-theme` 上。返回一个取消订阅函数。 */
export function apply(mode: ThemeMode, root: HTMLElement = document.documentElement): () => void {
  const set = () => root.setAttribute("data-theme", resolve(mode));
  set();
  if (mode !== "auto") return () => {};
  const mq = window.matchMedia("(prefers-color-scheme: dark)");
  mq.addEventListener("change", set);
  return () => mq.removeEventListener("change", set);
}

/** 供 JS 侧读令牌（画 canvas、算对比度时用）。 */
export function read(name: string, el: Element = document.documentElement): string {
  return getComputedStyle(el).getPropertyValue(`--bai-${name}`).trim();
}
