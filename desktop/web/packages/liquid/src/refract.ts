/**
 * 折射：让玻璃边缘像真的透镜一样把背后的东西弯折。
 *
 * ## 原理
 *
 * 一块厚玻璃，中心是平的、边缘是斜的。光线穿过斜面会偏折，所以透过边缘看到的
 * 背景会被"拉"向玻璃中心。数学模型就是一张**位移贴图**：每个像素告诉渲染器
 * "这里该往哪个方向采样"。
 *
 * 位移贴图用圆角矩形的 SDF（有向距离场）生成：
 *
 * ```text
 * 中心区域  d 很小  → 位移 0
 * 边缘一圈  d ≈ 0   → 位移最大，方向沿 SDF 梯度（即外法线）
 * 圆角处    梯度是斜的 → 位移也是斜的，形成"透镜角"
 * ```
 *
 * ## 一次生成，按尺寸缓存
 *
 * 生成是逐像素的（192×192 约 4 万像素 × 5 次 SDF），跑到主线程上大约 3–6ms。
 * 所以：按 `宽×高×圆角` 缓存；同尺寸的按钮共用一张图；尺寸变化时用
 * ResizeObserver 节流重算。
 *
 * ## 分辨率
 *
 * 位移是低频信号，192px 的图拉伸到 1200px 宽完全看不出锯齿。用 1:1 只会白白
 * 多花 40 倍时间。
 */

const SVG_NS = "http://www.w3.org/2000/svg";

/** 缓存上限。超过就按插入顺序丢最老的（Map 保序）。 */
const CACHE_MAX = 96;
const cache = new Map<string, string>();

/** 一张已经生成好的位移图。 */
export interface DisplacementMap {
  /** `data:image/png;base64,...`，可直接塞进 `feImage` 的 href。 */
  href: string;
  /** 生成时的逻辑尺寸，调试用。 */
  width: number;
  height: number;
}

/**
 * 生成位移贴图。
 *
 * @param w 元素宽（CSS 像素）
 * @param h 元素高
 * @param radius 圆角
 * @param maxDim 贴图最长边，默认 192
 */
export function displacementMap(w: number, h: number, radius: number, maxDim = 192): DisplacementMap | null {
  const W = Math.max(2, Math.round(w));
  const H = Math.max(2, Math.round(h));
  if (W < 12 || H < 12) return null;

  const key = `${W}x${H}x${Math.round(radius)}`;
  const hit = cache.get(key);
  if (hit) {
    // 命中就刷新顺序，LRU 才有效
    cache.delete(key);
    cache.set(key, hit);
    return { href: hit, width: W, height: H };
  }

  const s = Math.min(1, maxDim / Math.max(W, H));
  const cw = Math.max(4, Math.round(W * s));
  const ch = Math.max(4, Math.round(H * s));
  const r = Math.min(radius * s, cw / 2, ch / 2);

  // 玻璃的"厚度"：边缘参与折射的带宽。太窄看不出透镜，太宽整块糊掉。
  const thickness = Math.max(4, Math.min(cw, ch) * 0.22, 6);

  const canvas = document.createElement("canvas");
  canvas.width = cw;
  canvas.height = ch;
  const ctx = canvas.getContext("2d", { willReadFrequently: false });
  if (!ctx) return null;
  const img = ctx.createImageData(cw, ch);
  const data = img.data;

  const hx = cw / 2;
  const hy = ch / 2;

  const sdf = (px: number, py: number): number => {
    const qx = Math.abs(px) - (hx - r);
    const qy = Math.abs(py) - (hy - r);
    const ax = qx > 0 ? qx : 0;
    const ay = qy > 0 ? qy : 0;
    const outside = Math.sqrt(ax * ax + ay * ay);
    const inside = Math.min(Math.max(qx, qy), 0);
    return outside + inside - r;
  };

  const e = 0.6;
  const inv = 1 / thickness;

  for (let y = 0; y < ch; y++) {
    const py = y + 0.5 - hy;
    for (let x = 0; x < cw; x++) {
      const px = x + 0.5 - hx;
      const i = (y * cw + x) * 4;
      const d = sdf(px, py);

      // 外部（被 border-radius 裁掉）——填中性，不位移
      if (d >= 0) {
        data[i] = 128;
        data[i + 1] = 128;
        data[i + 2] = 128;
        data[i + 3] = 255;
        continue;
      }
      // 内部深处——平的地方，不位移
      if (d < -thickness) {
        data[i] = 128;
        data[i + 1] = 128;
        data[i + 2] = 128;
        data[i + 3] = 255;
        continue;
      }

      // 梯度 = 外法线
      const gx = sdf(px + e, py) - sdf(px - e, py);
      const gy = sdf(px, py + e) - sdf(px, py - e);
      const len = Math.hypot(gx, gy) || 1;
      const nx = gx / len;
      const ny = gy / len;

      // 越靠边位移越强。用 smoothstep 让过渡自然，不要线性——线性在边缘会有硬折痕。
      const t = 1 + d * inv; // 0 在最外，1 在厚度内边界
      const k = t * t * (3 - 2 * t);

      data[i] = clamp8(128 + nx * 127 * k);
      data[i + 1] = clamp8(128 + ny * 127 * k);
      data[i + 2] = 128;
      data[i + 3] = 255;
    }
  }

  ctx.putImageData(img, 0, 0);
  const href = canvas.toDataURL("image/png");

  if (cache.size >= CACHE_MAX) {
    const oldest = cache.keys().next();
    if (!oldest.done) cache.delete(oldest.value);
  }
  cache.set(key, href);
  return { href, width: W, height: H };
}

function clamp8(v: number): number {
  return v < 0 ? 0 : v > 255 ? 255 : v | 0;
}

// —— SVG filter 容器 ——

let defs: SVGDefsElement | null = null;
let seq = 0;

function ensureDefs(): SVGDefsElement {
  if (defs && defs.isConnected) return defs;
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("width", "0");
  svg.setAttribute("height", "0");
  svg.setAttribute("aria-hidden", "true");
  svg.style.position = "absolute";
  svg.style.width = "0";
  svg.style.height = "0";
  svg.style.overflow = "hidden";
  svg.style.pointerEvents = "none";
  defs = document.createElementNS(SVG_NS, "defs");
  svg.appendChild(defs);
  document.body.appendChild(svg);
  return defs;
}

/**
 * 注册一个折射滤镜，返回 filter id。
 *
 * `scale` 是边缘的最大位移像素数。8–14 是"薄玻璃"，18–26 是"厚玻璃"，
 * 超过 30 会让人觉得背景在流动，反而假。
 *
 * 返回 Promise 是因为 `feImage` 的图必须先解码完，否则第一帧会闪一下没折射的样子。
 */
export async function registerFilter(href: string, scale: number): Promise<string> {
  const id = `lg-refract-${++seq}`;
  const root = ensureDefs();

  // 预解码：data URI 也可能要几十毫秒
  await decode(href);

  const filter = document.createElementNS(SVG_NS, "filter");
  filter.setAttribute("id", id);
  // 留点余量，否则位移到边缘的像素会被 filter 区域裁掉，形成一圈硬边
  filter.setAttribute("x", "-12%");
  filter.setAttribute("y", "-12%");
  filter.setAttribute("width", "124%");
  filter.setAttribute("height", "124%");
  filter.setAttribute("color-interpolation-filters", "sRGB");

  const image = document.createElementNS(SVG_NS, "feImage");
  image.setAttribute("href", href);
  image.setAttribute("preserveAspectRatio", "none");
  image.setAttribute("x", "0");
  image.setAttribute("y", "0");
  image.setAttribute("width", "100%");
  image.setAttribute("height", "100%");
  image.setAttribute("result", "map");

  const disp = document.createElementNS(SVG_NS, "feDisplacementMap");
  disp.setAttribute("in", "SourceGraphic");
  disp.setAttribute("in2", "map");
  disp.setAttribute("scale", String(scale));
  disp.setAttribute("xChannelSelector", "R");
  disp.setAttribute("yChannelSelector", "G");

  filter.append(image, disp);
  root.appendChild(filter);
  return id;
}

/** 删掉不再使用的滤镜。元素销毁时调用，否则 defs 会越积越多。 */
export function unregisterFilter(id: string): void {
  const el = document.getElementById(id);
  el?.remove();
}

const decoded = new Set<string>();

function decode(src: string): Promise<void> {
  if (decoded.has(src)) return Promise.resolve();
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      decoded.add(src);
      resolve();
    };
    img.onerror = () => resolve();
    img.src = src;
  });
}

/** 清空缓存。主题切换、DPI 变化后可以调一次。 */
export function clearCache(): void {
  cache.clear();
  decoded.clear();
}
