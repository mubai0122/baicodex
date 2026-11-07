/**
 * 组装。
 *
 * 这里不做任何业务判断，只负责三件事：**注入样式、按顺序建视图、把键盘快捷键
 * 接上**。任何"如果用户……就……"的逻辑都应该在 `state.ts` 或 Rust 里，不在这。
 *
 * ## 挂载顺序
 *
 * ```text
 * tokens（CSS 变量）
 *   → liquid（玻璃的样式与 SVG filter 容器）
 *   → ui（组件样式）
 *   → app（布局样式）
 * ```
 *
 * 顺序有意义：后注入的样式能覆盖前面的。app 的布局必须最后，否则组件的
 * `height: 34px` 会盖掉布局里的 `height: 100%`。
 */

import { install as installTokens } from "@baicodex/tokens";
import { install as installLiquid } from "@baicodex/liquid";
import { installStyles as installUi } from "@baicodex/ui";
import { kernel } from "@baicodex/bridge";

import { css as appCss } from "./style";
import { Store } from "./state";
import { titlebar } from "./views/titlebar";
import { sidebar } from "./views/sidebar";
import { chat } from "./views/chat";
import { composer } from "./views/composer";
import { settings } from "./views/settings";

export interface AppHandle {
  store: Store;
  destroy(): void;
}

export async function createApp(root: HTMLElement): Promise<AppHandle> {
  installTokens();
  installLiquid();
  installUi();
  const style = document.createElement("style");
  style.id = "bai-app";
  style.textContent = appCss;
  document.head.append(style);

  const store = new Store();

  // —— 骨架 ——
  const app = document.createElement("div");
  app.className = "app";

  const aurora = document.createElement("div");
  aurora.className = "aurora";
  for (let i = 0; i < 4; i++) aurora.append(document.createElement("i"));

  const bar = titlebar(store);

  const bodyEl = document.createElement("div");
  bodyEl.className = "body";

  const side = sidebar(store);
  const main = document.createElement("main");
  main.className = "main";

  const view = chat(store);
  const input = composer(store);

  // 内核的 snapshot.error 以前没人显示：真内核报「还没有填 API Key」，界面照样静默
  const errBar = document.createElement("div");
  errBar.className = "err-bar";
  errBar.hidden = true;
  const errText = document.createElement("span");
  const errGo = document.createElement("button");
  errGo.textContent = "去设置";
  errGo.addEventListener("click", () => store.patch({ view: "settings" }));
  errBar.append(errText, errGo);

  main.append(view.el, errBar, input);

  bodyEl.append(side, main);

  const set = settings(store);
  set.hidden = true;

  app.append(bar, bodyEl, set);
  root.append(aurora, app);

  // —— 状态 → 外壳 ——
  const paint = (s: Store["state"]): void => {
    app.dataset.view = s.view;
    app.dataset.sidebar = s.sidebar ? "open" : "closed";
    set.hidden = s.view !== "settings";
    if (s.host) app.dataset.backdrop = s.host.backdrop;

    const err = s.snapshot?.error ?? "";
    errBar.hidden = !err || s.view === "settings";
    if (err) errText.textContent = err;
  };
  paint(store.state);
  const off = store.subscribe(paint);

  // —— 快捷键 ——
  const keys = (e: KeyboardEvent): void => {
    const mod = e.ctrlKey || e.metaKey;
    if (mod && e.key.toLowerCase() === "b") {
      e.preventDefault();
      store.patch({ sidebar: !store.state.sidebar });
      return;
    }
    if (mod && e.key.toLowerCase() === "n") {
      e.preventDefault();
      void kernel.newSession().then(() => store.reload());
      return;
    }
    if (mod && e.key === ",") {
      e.preventDefault();
      store.patch({ view: store.state.view === "settings" ? "chat" : "settings" });
      return;
    }
    if (e.key === "Escape" && store.state.view === "settings") {
      store.patch({ view: "chat" });
    }
  };
  window.addEventListener("keydown", keys);

  // —— 启动 ——
  await store.boot();
  paint(store.state);
  view.scrollToBottom();

  // 首次进入时把上次的极光强度、折射开关恢复
  const auroraOp = localStorage.getItem("bai.aurora");
  if (auroraOp) document.documentElement.style.setProperty("--aurora-op", auroraOp);
  document.documentElement.dataset.refract = localStorage.getItem("bai.refract") === "0" ? "off" : "on";
  // 界面风格：默认素雅（白灰 + 光斑），要液态玻璃得自己开
  document.documentElement.dataset.ui = localStorage.getItem("bai.ui") === "liquid" ? "liquid" : "plain";

  return {
    store,
    destroy() {
      off();
      window.removeEventListener("keydown", keys);
      store.dispose();
      kernel.dispose();
      app.remove();
      aurora.remove();
      style.remove();
    },
  };
}