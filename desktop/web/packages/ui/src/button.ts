/**
 * 按钮。
 *
 * 三种视觉：
 *
 * - `glass`   —— 半透明玻璃，默认。适合工具栏、次级操作。
 * - `quiet`   —— 没有底板，只有 hover 时的浅色。适合图标按钮密集的地方。
 * - `accent`  —— 主色实心，但边缘仍有高光。适合"发送"这种唯一主动作。
 *
 * 始终是 `<button>`，键盘可达、屏幕阅读器能念。玻璃层是它内部的 `div`。
 */

import { GlassSurface } from "@baicodex/liquid";
import { icon, type IconName } from "./icon";

export type ButtonTone = "glass" | "quiet" | "accent" | "danger";
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonOptions {
  label?: string;
  icon?: IconName;
  tone?: ButtonTone;
  size?: ButtonSize;
  title?: string;
  disabled?: boolean;
  /** 图标放在文字右边。 */
  iconRight?: boolean;
  onClick?: (e: MouseEvent) => void;
}

export class Button {
  readonly el: HTMLButtonElement;
  private glass: GlassSurface | null = null;
  private labelEl: HTMLSpanElement | null = null;
  private iconEl: SVGElement | null = null;
  private home: HTMLElement;

  constructor(private o: ButtonOptions = {}) {
    const tone = o.tone ?? "glass";
    const size = o.size ?? "md";

    if (tone === "quiet") {
      // 不给玻璃：一屏几十个图标按钮，每个都开 backdrop-filter 会掉帧
      this.el = document.createElement("button");
      this.el.className = "ui-iconbtn";
      this.home = this.el;
    } else {
      this.glass = new GlassSurface(
        {
          radius: size === "sm" ? 8 : size === "lg" ? 14 : 10,
          blur: size === "sm" ? 10 : 12,
          depth: tone === "glass" ? 14 : 0,
          tone: 0.28,
          grain: 0.08,
          specular: 0.9,
          interactive: true,
        },
        "button",
      );
      this.el = this.glass.el as HTMLButtonElement;
      this.el.className = "ui-btn";
      this.home = this.glass.inner;
    }

    this.el.dataset.tone = tone;
    this.el.dataset.size = size;
    this.el.type = "button";
    if (o.title) this.el.title = o.title;
    if (o.disabled) this.el.disabled = true;

    if (tone === "accent") {
      this.el.style.setProperty("--lg-tint", "var(--bai-accent)");
      this.el.style.setProperty("--lg-spec", "0.42");
      this.el.style.setProperty("--lg-grain", "0.1");
    } else if (tone === "danger") {
      this.el.style.setProperty("--lg-tint", "color-mix(in srgb, var(--bai-danger) 16%, transparent)");
    }

    this.paint();

    if (o.onClick) this.el.addEventListener("click", o.onClick);
  }

  setLabel(text: string): this {
    this.o.label = text;
    this.paint();
    return this;
  }

  setIcon(name: IconName | null): this {
    this.o.icon = name ?? undefined;
    this.paint();
    return this;
  }

  setDisabled(v: boolean): this {
    this.el.disabled = v;
    return this;
  }

  setTone(tone: ButtonTone): this {
    this.el.dataset.tone = tone;
    return this;
  }

  private paint(): void {
    // 重建内容而不是打补丁：按钮里最多三个节点，重建比 diff 便宜也更不容易错
    this.home.textContent = "";
    const o = this.o;

    const iconNode = o.icon ? icon(o.icon, o.size === "sm" ? 14 : 16) : null;
    const labelNode = o.label ? document.createElement("span") : null;
    if (labelNode) labelNode.textContent = o.label!;

    this.iconEl = iconNode;
    this.labelEl = labelNode;

    if (o.iconRight) {
      if (labelNode) this.home.append(labelNode);
      if (iconNode) this.home.append(iconNode);
    } else {
      if (iconNode) this.home.append(iconNode);
      if (labelNode) this.home.append(labelNode);
    }
  }

  get label(): string {
    return this.labelEl?.textContent ?? "";
  }

  get iconNode(): SVGElement | null {
    return this.iconEl;
  }
}

/** 一步到位。 */
export function button(o: ButtonOptions): HTMLButtonElement {
  return new Button(o).el;
}

/** 纯图标按钮。默认 quiet，因为工具栏上一排玻璃块会显得很吵。 */
export function iconButton(o: Omit<ButtonOptions, "label" | "tone"> & { tone?: ButtonTone }): HTMLButtonElement {
  return new Button({ ...o, tone: o.tone ?? "quiet" }).el;
}
