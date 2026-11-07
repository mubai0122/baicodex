/**
 * 液态玻璃的样式。
 *
 * ## 层叠顺序（从下到上）
 *
 * ```text
 * lg-back    backdrop-filter：模糊 + 折射。真正"看见背景"的一层
 * lg-tint    染色。决定玻璃是偏白还是偏黑，也是可读性的兜底
 * lg-sheen   镜面高光。跟着鼠标走，模拟光源
 * lg-grain   颗粒。真实玻璃不是绝对平整的，没有噪点会像塑料
 * lg-edge    1px 锥形描边。上下亮、两侧暗，这是"厚度"的来源
 * lg-inner   内容
 * ```
 *
 * ## 三个容易翻车的点
 *
 * 1. `backdrop-filter` 的元素必须有**自己的** `border-radius`，靠父级
 *    `overflow: hidden` 裁是不行的——模糊区域还是方的，四个角会露出直角。
 * 2. `transform` 会把元素提升为独立合成层。这对 `backdrop-filter` 是必要的
 *    （否则滚动时会闪烁），但也会让 `position: fixed` 的子元素定位失效。
 * 3. `mix-blend-mode` 要求父级建立层叠上下文，靠 `isolation: isolate` 兜住，
 *    不然高光会跟页面外面的东西混色。
 */

const NOISE_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><filter id="n"><feTurbulence type="fractalNoise" baseFrequency="0.82" numOctaves="3" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/></filter><rect width="160" height="160" filter="url(#n)" opacity="0.55"/></svg>`;

export const NOISE_URL = `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(NOISE_SVG)}")`;

export const css = `
.lg {
  position: relative;
  isolation: isolate;
  border-radius: var(--lg-r, 20px);
  /* 三层阴影：贴地的一层给"压住"的感觉，扩散的一层给悬浮，内亮线给厚度 */
  box-shadow:
    0 1px 2px var(--lg-shade, rgba(10, 14, 24, 0.14)),
    0 12px 32px -10px var(--lg-shadow, rgba(10, 14, 24, 0.30)),
    inset 0 0.8px 0 var(--lg-edge, rgba(255, 255, 255, 0.92)),
    inset 0 -0.8px 0 rgba(255, 255, 255, 0.22);
  transform: translateZ(0);
  will-change: transform;
  backface-visibility: hidden;
}

.lg > .lg-back {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  z-index: 0;
  /* 真正干活的一行。JS 会在末尾追加 url(#折射滤镜) */
  -webkit-backdrop-filter: var(--lg-filter, blur(var(--lg-blur, 24px)) saturate(var(--lg-sat, 1.7)) brightness(var(--lg-bright, 1.03)));
  backdrop-filter: var(--lg-filter, blur(var(--lg-blur, 24px)) saturate(var(--lg-sat, 1.7)) brightness(var(--lg-bright, 1.03)));
  /* 没有 backdrop-filter 时至少有个半透明底，不至于完全透明 */
  background: var(--lg-fallback, transparent);
}

.lg > .lg-tint {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  z-index: 1;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.10), rgba(255, 255, 255, 0) 50%),
    var(--lg-tint, rgba(255, 255, 255, 0.5));
  pointer-events: none;
}

.lg > .lg-sheen {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  z-index: 2;
  background:
    radial-gradient(
      120% 80% at var(--lg-mx, 30%) var(--lg-my, -6%),
      rgba(255, 255, 255, 0.46),
      rgba(255, 255, 255, 0.10) 44%,
      transparent 72%
    );
  opacity: var(--lg-spec, 0.85);
  mix-blend-mode: screen;
  pointer-events: none;
  transition: opacity var(--bai-dur-base, 200ms) ease;
}

.lg > .lg-grain {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  z-index: 3;
  background-image: ${NOISE_URL};
  background-size: 160px 160px;
  opacity: var(--lg-grain, 0.28);
  mix-blend-mode: soft-light;
  pointer-events: none;
}

.lg > .lg-edge {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  z-index: 4;
  padding: 1px;
  background: conic-gradient(
    from var(--lg-angle, 200deg),
    rgba(255, 255, 255, 0) 0deg,
    rgba(255, 255, 255, 0.95) 34deg,
    rgba(255, 255, 255, 0.14) 86deg,
    rgba(255, 255, 255, 0) 148deg,
    rgba(255, 255, 255, 0.52) 214deg,
    rgba(255, 255, 255, 0.06) 292deg,
    rgba(255, 255, 255, 0) 360deg
  );
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  mask-composite: exclude;
  pointer-events: none;
  opacity: var(--lg-edge-op, 1);
}

.lg > .lg-inner {
  position: relative;
  z-index: 5;
}

/* 可交互的玻璃：按下时整块收缩，靠 JS 的弹簧给曲线，不用 transition */
.lg[data-interactive="true"] {
  cursor: pointer;
  transform: scale(var(--lg-press, 1)) translateZ(0);
}
.lg[data-interactive="true"]:focus-visible {
  outline: 2px solid var(--bai-accent, #2f6bff);
  outline-offset: 3px;
}

/* 内凹（输入框）：边缘暗、中心亮的反相，看起来像被按下去 */
.lg[data-concave="true"] > .lg-edge {
  background: conic-gradient(
    from var(--lg-angle, 200deg),
    rgba(0, 0, 0, 0) 0deg,
    rgba(0, 0, 0, 0.28) 38deg,
    rgba(0, 0, 0, 0) 148deg,
    rgba(255, 255, 255, 0.42) 214deg,
    rgba(0, 0, 0, 0) 360deg
  );
}
.lg[data-concave="true"] {
  box-shadow:
    inset 0 1px 2px var(--lg-shade, rgba(10, 14, 24, 0.18)),
    inset 0 -0.5px 0 var(--lg-edge, rgba(255, 255, 255, 0.5)),
    0 1px 0 rgba(255, 255, 255, 0.04);
}

/* 没有 backdrop-filter 能力的机器：直接给实色，别让人看不清字 */
.lg[data-fallback="solid"] > .lg-back {
  background: var(--lg-solid, rgba(240, 242, 248, 0.94));
}

@media (prefers-reduced-motion: reduce) {
  .lg[data-interactive="true"] {
    transform: none !important;
  }
}
`;

const STYLE_ID = "bai-liquid";

/** 注入样式。幂等。 */
export function install(doc: Document = document): void {
  if (doc.getElementById(STYLE_ID)) return;
  const el = doc.createElement("style");
  el.id = STYLE_ID;
  el.textContent = css;
  doc.head.appendChild(el);
}
