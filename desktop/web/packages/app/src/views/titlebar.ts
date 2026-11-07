/**
 * 标题栏。
 *
 * 无边框窗口的标题栏要自己画，顺带承担三件事：
 *
 * 1. **拖动**：`-webkit-app-region: drag` 在 WebView2 里不生效。改成 pointerdown
 *    时告诉宿主"开始拖动"，宿主用 `SendMessage(WM_NCLBUTTONDOWN, HTCAPTION)` 交给
 *    系统的非客户区循环——这样才能有 Aero Snap 和双击最大化。
 * 2. **窗口按钮**：贴边、方角、hover 才亮。这是 Windows 的肌肉记忆，做成圆角
 *    胶囊会让人找不到。
 * 3. **状态**：会话标题、正在生成时的呼吸点。
 */

import { icon } from "@baicodex/ui";
import type { Store } from "../state";
import { closeWindow, dragWindow, minimize, toggleMaximize } from "../host";

export function titlebar(store: Store): HTMLElement {
  const el = document.createElement("header");
  el.className = "titlebar";

  const left = document.createElement("div");
  left.className = "tb-left";

  const toggle = document.createElement("button");
  toggle.className = "ui-iconbtn";
  toggle.title = "侧栏 (Ctrl+B)";
  toggle.append(icon("panel", 16));
  toggle.addEventListener("click", () => store.patch({ sidebar: !store.state.sidebar }));

  const title = document.createElement("div");
  title.className = "tb-title";

  left.append(toggle, title);

  // 中间这块纯粹用来接拖动。宽度撑满，避免只有标题能拖。
  const drag = document.createElement("div");
  drag.className = "tb-drag";

  const right = document.createElement("div");
  right.className = "tb-right";

  const mkWin = (name: "minus" | "square" | "close", label: string, fn: () => void, close = false) => {
    const b = document.createElement("button");
    b.className = "tb-win";
    b.title = label;
    b.dataset.close = String(close);
    b.append(icon(name, 14));
    b.addEventListener("click", fn);
    return b;
  };

  right.append(
    mkWin("minus", "最小化", minimize),
    mkWin("square", "最大化", toggleMaximize),
    mkWin("close", "关闭", closeWindow, true),
  );

  el.append(left, drag, right);

  // —— 拖动 ——
  const onDown = (e: PointerEvent): void => {
    if (e.button !== 0) return;
    const t = e.target as HTMLElement;
    if (t.closest("button")) return;
    dragWindow();
  };
  drag.addEventListener("pointerdown", onDown);
  el.addEventListener("pointerdown", (e) => {
    // 双击最大化：交给宿主判断更准（要处理贴边还原），这里只发一次
    if (e.button === 0 && (e.target as HTMLElement).closest(".tb-drag")) {
      if (e.detail === 2) toggleMaximize();
    }
  });

  const paint = (s: Store["state"]): void => {
    const name = s.snapshot?.title?.trim() || "baicodex";
    title.textContent = name;
    title.title = name;
    el.dataset.drag = "true";
    toggle.dataset.active = String(s.sidebar);
  };
  paint(store.state);
  store.subscribe(paint);

  return el;
}
