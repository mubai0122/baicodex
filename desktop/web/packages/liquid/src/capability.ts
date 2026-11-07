/**
 * 能力检测。
 *
 * 液态玻璃有一条长长的降级链，得先知道这台机器走到哪一档：
 *
 * ```text
 * 折射（backdrop-filter: url） + 模糊 + 高光 + 颗粒   ← 完整效果
 * 模糊 + 高光 + 颗粒                                ← 大部分场景
 * 半透明底色 + 高光                                  ← 老 WebView2
 * 实色                                              ← 最差
 * ```
 *
 * `CSS.supports("backdrop-filter", "url(#x)")` **不可信**：Chromium 在没有
 * SVG filter 可用时也会返回 true，然后渲染时静默丢掉这一项。所以这里额外看
 * 内核版本——滤镜链里的 `url()` 是 Chrome 123 才修好的。
 */

function chromiumMajor(): number {
  const ua = navigator.userAgent;
  // Edge 的 UA 里同时有 Chrome/ 和 Edg/，取更大的那个没有意义，
  // 两者版本号本来就同步；优先 Edg/ 更贴近 WebView2 的实际情况。
  const edg = /Edg\/(\d+)/.exec(ua);
  if (edg?.[1]) return Number(edg[1]);
  const chr = /Chrome\/(\d+)/.exec(ua);
  if (chr?.[1]) return Number(chr[1]);
  return 0;
}

function supports(prop: string, value: string): boolean {
  try {
    return CSS.supports(prop, value);
  } catch {
    return false;
  }
}

const major = chromiumMajor();

export interface Capabilities {
  /** 能不能做背景模糊。没有它整个效果不成立。 */
  backdrop: boolean;
  /** 能不能在 `backdrop-filter` 里串 SVG 位移滤镜（真折射）。 */
  refraction: boolean;
  /** `mask-composite: exclude`，用来画 1px 渐变描边。 */
  maskComposite: boolean;
  /** CSS Houdini 自定义属性注册，用于让 `--lg-*` 参与过渡。 */
  houdini: boolean;
  /** 用户要求减少动效。 */
  reducedMotion: boolean;
  /** 粗略的 GPU 能力档位，决定默认模糊半径。 */
  tier: "high" | "mid" | "low";
  /** 诊断用。 */
  chromium: number;
}

export const caps: Capabilities = {
  backdrop: supports("backdrop-filter", "blur(1px)") || supports("-webkit-backdrop-filter", "blur(1px)"),
  // 123 之前的 Chromium 对滤镜链里的 url() 处理有 bug：会把整条链丢掉
  refraction: major >= 123 && supports("backdrop-filter", "url(#lg-probe)"),
  maskComposite: supports("mask-composite", "exclude") || supports("-webkit-mask-composite", "xor"),
  houdini: typeof CSS !== "undefined" && "registerProperty" in CSS,
  reducedMotion: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false,
  tier: "high",
  chromium: major,
};

// 粗判档位：核显 / 低内存机器上默认别用 48px 的模糊，那是一帧几十毫秒的开销。
const mem = (navigator as { deviceMemory?: number }).deviceMemory ?? 8;
const cores = navigator.hardwareConcurrency ?? 8;
if (mem <= 4 || cores <= 4) caps.tier = "low";
else if (mem <= 8 || cores <= 8) caps.tier = "mid";

/** 按档位给一个安全的默认模糊半径。 */
export function defaultBlur(): number {
  return caps.tier === "low" ? 8 : caps.tier === "mid" ? 12 : 16;
}
