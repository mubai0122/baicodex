/**
 * 宿主调用。
 *
 * 前端要操作窗口（拖动、最大化、打开外链）时走这里。在浏览器里跑的时候这些
 * 调用会落空并返回 null——界面不该因为"没有窗口"就崩。
 */

import { kernel } from "@baicodex/bridge";

export async function hostCall<T = unknown>(op: string, args: Record<string, unknown> = {}): Promise<T | null> {
  try {
    return await kernel.transport.invoke<T>(op, args);
  } catch {
    return null;
  }
}

/** 是不是真的跑在桌面宿主里（而不是浏览器预览）。 */
export function inHost(): boolean {
  return kernel.transport.kind === "webview";
}

/** 拖动窗口。交给系统的非客户区拖动循环，这样才有贴边分屏和双击最大化。 */
export function dragWindow(): void {
  if (!inHost()) return;
  void hostCall("__host_drag");
}

/** 从某条边缩放。 */
export function resizeWindow(edge: "left" | "right" | "top" | "top-left" | "top-right" | "bottom" | "bottom-left" | "bottom-right"): void {
  if (!inHost()) return;
  void hostCall("__host_resize", { edge });
}

export function minimize(): void {
  void hostCall("__host_minimize");
}

export function toggleMaximize(): void {
  void hostCall("__host_maximize");
}

export function closeWindow(): void {
  void hostCall("__host_close");
}

export function openExternal(url: string): void {
  if (!/^https?:/i.test(url)) return;
  void hostCall("__host_open", { url });
}
