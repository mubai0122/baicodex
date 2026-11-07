/**
 * 轻提示。
 *
 * 一条消息 2.4 秒后自己走。不做"点掉"——能点掉的东西用户会下意识去点，反而
 * 打断操作。
 *
 * 同时最多 3 条，多的挤掉最老的。这不是洁癖：连续失败时 20 条红字叠在一起，
 * 用户只会觉得"坏了"，看不出到底哪里错。
 */

import { GlassSurface } from "@baicodex/liquid";

let host: HTMLDivElement | null = null;
const live: HTMLElement[] = [];
const MAX = 3;

function container(): HTMLDivElement {
  if (host?.isConnected) return host;
  host = document.createElement("div");
  host.className = "ui-toasts";
  document.body.appendChild(host);
  return host;
}

export interface ToastOptions {
  /** 毫秒。0 表示不自动消失。 */
  ms?: number;
  tone?: "default" | "danger" | "ok";
}

export function toast(text: string, opts: ToastOptions = {}): void {
  const box = container();
  const g = new GlassSurface({ radius: 999, blur: 16, depth: 18, tone: 0.8, grain: 0.1, specular: 0.8 });
  const el = g.el;
  el.className = "ui-toast";
  el.dataset.tone = opts.tone ?? "default";
  el.textContent = text;

  if (opts.tone === "danger") el.style.setProperty("--lg-tint", "color-mix(in srgb, var(--bai-danger) 20%, var(--bai-glass))");
  if (opts.tone === "ok") el.style.setProperty("--lg-tint", "color-mix(in srgb, var(--bai-ok) 18%, var(--bai-glass))");

  box.appendChild(el);
  live.push(el);
  while (live.length > MAX) {
    const old = live.shift();
    if (old) remove(old);
  }

  const ms = opts.ms ?? 2400;
  if (ms > 0) {
    setTimeout(() => {
      const i = live.indexOf(el);
      if (i >= 0) live.splice(i, 1);
      remove(el);
    }, ms);
  }
}

function remove(el: HTMLElement): void {
  el.dataset.leaving = "true";
  setTimeout(() => el.remove(), 200);
}
