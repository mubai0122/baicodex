/**
 * 玻璃表面。
 *
 * 一块可以放进任何容器的"液态玻璃"。它自己不管布局，只负责把六层叠出来，并把
 * 尺寸变化、指针位置、按压状态翻译成 CSS 变量。
 *
 * ```ts
 * const g = new GlassSurface({ radius: 26, blur: 30, depth: 16 });
 * g.inner.append(button);
 * document.body.append(g.el);
 * ```
 *
 * ## 为什么不用自定义元素
 *
 * `<lg-surface>` 看着更"框架"，但会逼着用 Shadow DOM 或者 `:host` 选择器，
 * 结果是想改个圆角要从外面戳进去。用工厂类，拿到的是普通 `div`，任何现有样式
 * 都能作用上去，调试时 DevTools 里也是一棵普通树。
 *
 * ## 折射是异步的
 *
 * 生成位移图 + 解码 + 注册 SVG filter，三步都可能跨帧。所以第一帧是没有折射的，
 * 几十毫秒后才"长出来"。这比同步阻塞主线程好——后者会让整个页面卡一下。
 */

import { caps, defaultBlur } from "./capability";
import { displacementMap, registerFilter, unregisterFilter } from "./refract";
import { Spring } from "./spring";

export interface GlassOptions {
  /** 背景模糊半径，px。默认按机器档位取 12/20/28。 */
  blur?: number;
  /** 边缘折射位移，px。0 关掉折射。默认 14。 */
  depth?: number;
  /** 圆角，px。默认 20。 */
  radius?: number;
  /** 染色。传 CSS 颜色则原样用；不传则按 `tone` 自动生成。 */
  tint?: string;
  /** 染色浓度 0–1。越大越不透明。默认 0.5。 */
  tone?: number;
  /** 镜面高光强度 0–1。默认 0.85。 */
  specular?: number;
  /** 颗粒强度 0–1。默认 0.28。 */
  grain?: number;
  /** 响应按压。默认 false。 */
  interactive?: boolean;
  /** 是否做边缘折射。默认 true（能力允许时）。 */
  refract?: boolean;
  /** 内凹。输入框、凹槽用。默认 false。 */
  concave?: boolean;
}

type Resolved = Required<Omit<GlassOptions, "tint">> & { tint?: string };

function resolve(o: GlassOptions): Resolved {
  return {
    blur: o.blur ?? defaultBlur(),
    depth: o.depth ?? 14,
    radius: o.radius ?? 20,
    tone: o.tone ?? 0.5,
    specular: o.specular ?? 0.85,
    grain: o.grain ?? 0.28,
    interactive: o.interactive ?? false,
    refract: o.refract ?? true,
    concave: o.concave ?? false,
    tint: o.tint,
  };
}

export class GlassSurface {
  readonly el: HTMLElement;
  /** 内容容器。往里放东西。 */
  readonly inner: HTMLDivElement;

  private o: Resolved;
  private press: Spring;
  private ro: ResizeObserver | null = null;
  private raf = 0;
  private filterId: string | null = null;
  private dead = false;
  private lastW = 0;
  private lastH = 0;

  /**
   * @param opts 玻璃参数
   * @param tag 宿主标签。默认 `div`；按钮传 `button` 就能直接拿到按钮语义。
   */
  constructor(opts: GlassOptions = {}, tag: "div" | "button" | "span" = "div") {
    this.o = resolve(opts);

    const el = document.createElement(tag);
    el.className = "lg";
    el.innerHTML = [
      '<div class="lg-back"></div>',
      '<div class="lg-tint"></div>',
      '<div class="lg-sheen"></div>',
      '<div class="lg-grain"></div>',
      '<div class="lg-edge"></div>',
      '<div class="lg-inner"></div>',
    ].join("");
    this.el = el;
    this.inner = el.lastElementChild as HTMLDivElement;

    this.press = new Spring(520, 38, 1);
    this.press.onChange((v) => el.style.setProperty("--lg-press", v.toFixed(4)));

    this.apply();
    this.bind();

    if (typeof ResizeObserver !== "undefined") {
      this.ro = new ResizeObserver((entries) => {
        const box = entries[0]?.contentRect;
        if (!box) return;
        // 只有尺寸真的变了才重建位移图（高度动画会疯狂触发回调）
        if (Math.abs(box.width - this.lastW) < 1 && Math.abs(box.height - this.lastH) < 1) return;
        this.lastW = box.width;
        this.lastH = box.height;
        this.scheduleRefract();
      });
      this.ro.observe(el);
    }

    // 主题切换要重算染色
    this.themeWatcher = new MutationObserver(() => this.applyTint());
    this.themeWatcher.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  }

  private themeWatcher: MutationObserver;

  /** 改配置。只传要改的项。 */
  set(patch: Partial<GlassOptions>): void {
    this.o = resolve({ ...this.o, ...patch });
    this.apply();
    this.scheduleRefract();
  }

  /** 当前配置的只读快照。 */
  get options(): Readonly<Resolved> {
    return this.o;
  }

  destroy(): void {
    if (this.dead) return;
    this.dead = true;
    this.ro?.disconnect();
    this.themeWatcher.disconnect();
    this.press.stop();
    if (this.raf) cancelAnimationFrame(this.raf);
    if (this.filterId) unregisterFilter(this.filterId);
    this.el.remove();
  }

  private apply(): void {
    const s = this.el.style;
    const o = this.o;
    s.setProperty("--lg-r", `${o.radius}px`);
    s.setProperty("--lg-blur", `${o.blur}px`);
    s.setProperty("--lg-spec", String(o.specular));
    s.setProperty("--lg-grain", String(o.grain));
    this.el.dataset.interactive = String(o.interactive);
    this.el.dataset.concave = String(o.concave);
    // 老机器没有 backdrop-filter，直接给实色底
    this.el.dataset.fallback = caps.backdrop ? "none" : "solid";

    this.applyTint();
    // 基础滤镜。折射就绪后会在这条后面追加 url(#...)
    s.setProperty("--lg-filter", `blur(${o.blur}px) saturate(1.7) brightness(1.03)`);
  }

  private applyTint(): void {
    if (this.o.tint) {
      this.el.style.setProperty("--lg-tint", this.o.tint);
      return;
    }
    const dark = document.documentElement.getAttribute("data-theme") === "dark";
    const rgb = dark ? "20, 23, 30" : "255, 255, 255";
    this.el.style.setProperty("--lg-tint", `rgba(${rgb}, ${this.o.tone})`);
    this.el.style.setProperty("--lg-solid", dark ? "rgba(18, 21, 27, 0.96)" : "rgba(244, 246, 250, 0.97)");
  }

  // —— 折射 ——

  private scheduleRefract(): void {
    if (this.dead) return;
    if (!this.o.refract || !caps.refraction || this.o.depth <= 0) return;
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = requestAnimationFrame(() => {
      this.raf = 0;
      void this.buildRefract();
    });
  }

  private async buildRefract(): Promise<void> {
    if (this.dead) return;
    const r = this.el.getBoundingClientRect();
    if (r.width < 24 || r.height < 24) return;

    const map = displacementMap(r.width, r.height, this.o.radius);
    if (!map) return;

    // 方向：凹面把背景往外推，凸面往里吸
    const scale = this.o.concave ? -this.o.depth : this.o.depth;
    const id = await registerFilter(map.href, scale);
    if (this.dead) {
      unregisterFilter(id);
      return;
    }
    if (this.filterId && this.filterId !== id) unregisterFilter(this.filterId);
    this.filterId = id;
    this.el.style.setProperty(
      "--lg-filter",
      `blur(${this.o.blur}px) saturate(1.7) brightness(1.03) url(#${id})`,
    );
  }

  // —— 交互 ——

  private bind(): void {
    const el = this.el;
    el.addEventListener("pointerdown", this.onDown);
    el.addEventListener("pointerup", this.onUp);
    el.addEventListener("pointercancel", this.onUp);
    el.addEventListener("pointerleave", this.onLeave);
    el.addEventListener("pointermove", this.onMove, { passive: true });
  }

  private onDown = (e: PointerEvent): void => {
    if (!this.o.interactive) return;
    this.el.setPointerCapture?.(e.pointerId);
    this.press.to(this.o.concave ? 0.985 : 0.968);
    this.el.style.setProperty("--lg-spec", String(Math.min(1, this.o.specular * 1.4)));
    this.moveSheen(e);
  };

  private onUp = (): void => {
    if (!this.o.interactive) return;
    this.press.to(1);
    this.el.style.setProperty("--lg-spec", String(this.o.specular));
  };

  private onLeave = (): void => {
    if (!this.o.interactive) return;
    this.el.style.setProperty("--lg-mx", "28%");
    this.el.style.setProperty("--lg-my", "0%");
    this.el.style.setProperty("--lg-angle", "200deg");
  };

  private onMove = (e: PointerEvent): void => {
    if (!this.o.interactive) return;
    this.moveSheen(e);
  };

  private moveSheen(e: PointerEvent): void {
    const r = this.el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return;
    const x = ((e.clientX - r.left) / r.width) * 100;
    const y = ((e.clientY - r.top) / r.height) * 100;
    this.el.style.setProperty("--lg-mx", `${x.toFixed(2)}%`);
    this.el.style.setProperty("--lg-my", `${y.toFixed(2)}%`);

    // 高光边跟着指针绕一圈：模拟一个固定光源被玻璃边缘反射
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;
    const deg = (Math.atan2(e.clientY - cy, e.clientX - cx) * 180) / Math.PI + 90;
    this.el.style.setProperty("--lg-angle", `${deg.toFixed(1)}deg`);
  }
}

/** 一步到位：造好、可选地填内容、返回元素。 */
export function glass(opts: GlassOptions = {}, ...children: (Node | string)[]): HTMLElement {
  const g = new GlassSurface(opts);
  if (children.length) g.inner.append(...children);
  return g.el;
}
