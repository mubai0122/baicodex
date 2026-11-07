/**
 * 输入区。
 *
 * 界面上唯一一块"厚玻璃"：模糊 30、折射 16、内凹。它值得这么重，因为它是整个
 * 应用里你唯一会主动去点的地方——视觉重心放在这里，眼睛不用找。
 *
 * ## 几个必须做对的细节
 *
 * - **输入法**：中文打字时按 Enter 是在选词，不是发送。判据是 `isComposing` 和
 *   `keyCode === 229`（老版 Safari/WebView 用后者）。
 * - **自适应高度**：先设 `auto` 再读 `scrollHeight`，否则只会越撑越高。
 * - **发送后清空**：先清空再等 Promise，不然模型慢的时候输入框里一直挂着上一句。
 */

import { GlassSurface } from "@baicodex/liquid";
import { kernel, type AgentMode } from "@baicodex/bridge";
import { icon, toast } from "@baicodex/ui";
import type { Store } from "../state";

const MODES: { id: AgentMode; label: string; hint: string }[] = [
  { id: "standard", label: "标准", hint: "全部工具，按需使用" },
  { id: "code", label: "代码", hint: "专注读写文件、跑命令" },
  { id: "plan", label: "计划", hint: "先给方案，确认后再执行" },
  { id: "minimal", label: "极简", hint: "只给最小工具集" },
  { id: "creator", label: "创造", hint: "偏向写作与生成" },
];

export function composer(store: Store): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "composer-wrap";

  const g = new GlassSurface({
    radius: 20,
    blur: 14,
    depth: 22,
    tone: 0.34,
    grain: 0.1,
    specular: 0.9,
    concave: true,
  });
  g.el.classList.add("composer");

  const ta = document.createElement("textarea");
  ta.className = "ui-input";
  ta.rows = 1;
  ta.placeholder = "说点什么…";
  ta.spellcheck = false;

  const bar = document.createElement("div");
  bar.className = "composer-bar";

  // —— 模式 chip ——
  const modeChip = document.createElement("button");
  modeChip.className = "chip";
  modeChip.title = "工作模式";

  // —— 模型 chip ——
  const modelChip = document.createElement("button");
  modelChip.className = "chip";
  // 点击行为在下面接上（要等两个菜单都建好）

  const spacer = document.createElement("div");
  spacer.className = "spacer";

  // —— 发送 ——
  const sendWrap = new GlassSurface(
    { radius: 10, blur: 10, depth: 0, tone: 0.9, grain: 0.06, specular: 0.5, interactive: true },
    "button",
  );
  const send = sendWrap.el as HTMLButtonElement;
  send.className = "ui-btn send-btn";
  send.type = "button";
  send.title = "发送 (Enter)";
  send.dataset.busy = "false";

  bar.append(modeChip, modelChip, spacer, send);
  g.inner.append(ta, bar);

  const hint = document.createElement("div");
  hint.className = "composer-hint";
  hint.append(kbd("Enter"), txt("发送"), kbd("Shift"), txt("+"), kbd("Enter"), txt("换行"));

  wrap.append(g.el, hint);

  let sending = false;

  // —— 自适应高度 ——
  const resize = (): void => {
    ta.style.height = "auto";
    ta.style.height = `${Math.min(208, Math.max(24, ta.scrollHeight))}px`;
  };
  // 打字要同时刷高度和发送按钮的可用态，否则按钮一直是灰的
  ta.addEventListener("input", () => {
    resize();
    paintSend();
  });

  // —— 发送 ——
  const doSend = async (): Promise<void> => {
    const text = ta.value.trim();
    if (!text || sending) return;
    if (store.state.snapshot?.busy) {
      toast("还在生成，先停下再发", { ms: 1600 });
      return;
    }
    sending = true;
    ta.value = "";
    resize();
    paintSend();
    try {
      await kernel.send(text);
      await store.reload();
    } catch (e) {
      toast(e instanceof Error ? e.message : "发送失败", { tone: "danger", ms: 3200 });
      // 失败原因存在 snapshot.error 里，不 reload 就永远不显示（toast 几秒后就没了）
      await store.reload().catch(() => {});
    } finally {
      sending = false;
      paintSend();
      ta.focus();
    }
  };

  const doStop = async (): Promise<void> => {
    try {
      await kernel.stop();
      toast("已停止", { ms: 1400 });
    } catch {
      /* 停了就停了 */
    }
  };

  send.addEventListener("click", () => {
    if (store.state.snapshot?.busy) void doStop();
    else void doSend();
  });

  ta.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    if (e.shiftKey) return;
    // 输入法组合中：这个 Enter 是选词
    if (e.isComposing || e.keyCode === 229) return;
    e.preventDefault();
    if (store.state.snapshot?.busy) return;
    void doSend();
  });

  // —— 两个弹出菜单：模式与模型共用同一套壳，各自一份玻璃 ——
  const modePop = popup(modeChip, 232);
  const modelPop = popup(modelChip, 300);

  for (const m of MODES) {
    const b = menuItem(m.label, m.hint);
    b.dataset.mode = m.id;
    b.addEventListener("click", async () => {
      await kernel.configPatch({ agent: { mode: m.id } }).catch(() => null);
      modePop.close();
      await store.reload();
      paintMode(store.state);
    });
    modePop.inner.append(b);
  }

  modeChip.addEventListener("click", (e) => {
    e.stopPropagation();
    modelPop.close();
    modePop.toggle();
  });
  modelChip.addEventListener("click", (e) => {
    e.stopPropagation();
    modePop.close();
    modelPop.toggle();
  });
  // 点空白处收起；菜单项自己的 click 先跑，这里只是兜底
  document.addEventListener("click", () => {
    modePop.close();
    modelPop.close();
  });

  // 模型列表来自内核的 provider.list：切 provider 要连 base_url/auth 一起改，
  // 只改 model 会让请求打到旧域名上
  let modelSig = "";
  const rebuildModels = (s: Store["state"]): void => {
    const list = s.config?.provider?.list ?? [];
    const curId = s.config?.provider?.id ?? "";
    const curModel = s.config?.provider?.model ?? "";
    const sig = list.map((p) => `${p.id}:${p.model}`).join("|") + `#${curId}#${curModel}`;
    if (sig === modelSig) return;
    modelSig = sig;

    modelPop.inner.textContent = "";
    for (const p of list) {
      const b = menuItem(p.name, p.model);
      b.dataset.provider = p.id;
      b.dataset.on = String(p.id === curId);
      b.addEventListener("click", async () => {
        modelPop.close();
        await kernel
          .configPatch({
            provider: {
              id: p.id,
              base_url: p.base_url,
              auth: p.auth ?? "bearer",
              model: p.model,
            },
          })
          .catch(() => null);
        await store.reload();
        paintMode(store.state);
        toast(`已切到 ${p.name}`, { ms: 1400 });
      });
      modelPop.inner.append(b);
    }
    const more = menuItem("更多设置…", "密钥与参数");
    more.dataset.more = "1";
    more.addEventListener("click", () => {
      modelPop.close();
      store.patch({ view: "settings", section: "model" });
    });
    modelPop.inner.append(more);
  };

  // —— 状态同步 ——

  function paintSend(): void {
    const busy = Boolean(store.state.snapshot?.busy) || sending;
    const empty = !ta.value.trim();
    send.dataset.busy = String(busy);
    send.disabled = !busy && empty;
    send.title = busy ? "停止 (Esc)" : "发送 (Enter)";
    send.textContent = "";
    const inner = sendWrap.inner;
    inner.textContent = "";
    if (busy) {
      inner.append(icon("stop", 14));
    } else {
      inner.append(icon("send", 15));
    }
    send.style.setProperty("--lg-tint", busy ? "color-mix(in srgb, var(--bai-danger) 26%, var(--bai-glass))" : "var(--bai-accent)");
    send.style.color = busy ? "var(--bai-danger)" : "var(--bai-accent-fg)";
  }

  function paintMode(s: Store["state"]): void {
    const mode = s.config?.agent?.mode ?? "standard";
    const m = MODES.find((x) => x.id === mode) ?? MODES[0]!;
    modeChip.textContent = "";
    modeChip.append(icon("sparkle", 13), txt(m.label));
    modeChip.dataset.on = String(mode !== "standard");
    for (const b of modePop.inner.querySelectorAll<HTMLElement>("[data-mode]")) {
      b.dataset.on = String(b.dataset.mode === mode);
    }
    for (const b of modelPop.inner.querySelectorAll<HTMLElement>("[data-provider]")) {
      b.dataset.on = String(b.dataset.provider === (s.config?.provider?.id ?? ""));
    }

    const model = s.snapshot?.model ?? s.config?.provider?.model ?? "未配置";
    modelChip.textContent = "";
    modelChip.append(icon("cpu", 13), txt(shortModel(model)));
    modelChip.title = model;
  }

  const paint = (s: Store["state"]): void => {
    paintMode(s);
    paintSend();
    rebuildModels(s);
  };
  paint(store.state);
  store.subscribe(paint);

  // Esc 停止
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && store.state.snapshot?.busy) void doStop();
  });

  // 进入页面就聚焦
  queueMicrotask(() => ta.focus());

  return wrap;
}

function txt(s: string): HTMLSpanElement {
  const el = document.createElement("span");
  el.textContent = s;
  return el;
}

function kbd(s: string): HTMLElement {
  const el = document.createElement("span");
  el.className = "ui-kbd";
  el.textContent = s;
  return el;
}

function shortModel(m: string): string {
  const s = m.split("/").pop() ?? m;
  return s.length > 22 ? `${s.slice(0, 20)}…` : s;
}

/** 输入框上方的玻璃下拉。模式和模型各用一份，互斥打开。 */
interface Popup {
  inner: HTMLElement;
  open(): void;
  close(): void;
  toggle(): void;
  isOpen(): boolean;
}

function popup(anchor: HTMLElement, minWidth: number): Popup {
  const root = document.createElement("div");
  root.className = "chip-pop";
  root.style.cssText = `position:fixed;display:none;z-index:200;min-width:${minWidth}px`;
  // 挂 body 而不是 composer 内部：composer 的玻璃有 transform + isolation + backdrop-filter，
  // 后代玻璃采不到页面背景，菜单会变成透明，下面消息的文字直接透上来
  const shell = new GlassSurface({ radius: 12, blur: 18, depth: 16, tone: 0.9, grain: 0.1, specular: 0.6 });
  const inner = document.createElement("div");
  inner.style.cssText = "padding:6px;display:grid;gap:2px;max-height:320px;overflow:auto";
  shell.inner.append(inner);
  root.append(shell.el);
  document.body.append(root);

  const place = (): void => {
    const r = anchor.getBoundingClientRect();
    root.style.left = `${Math.round(r.left)}px`;
    root.style.bottom = `${Math.round(window.innerHeight - r.top + 8)}px`;
  };
  window.addEventListener("resize", () => {
    if (inner.offsetParent) place();
  });

  let open = false;
  const api: Popup = {
    inner,
    open() {
      open = true;
      place();
      root.style.display = "block";
    },
    close() {
      open = false;
      root.style.display = "none";
    },
    toggle() {
      if (open) api.close();
      else api.open();
    },
    isOpen() {
      return open;
    },
  };
  return api;
}

/** 下拉里的一行：左标题、右说明。选中态靠 `data-on`，样式在 app 的 style.ts 里。 */
function menuItem(label: string, hint?: string): HTMLButtonElement {
  const b = document.createElement("button");
  b.className = "chip";
  b.style.cssText = "width:100%;min-height:32px;justify-content:flex-start;padding:0 10px;gap:8px;text-align:left";
  b.append(txt(label));
  if (hint) {
    const h = document.createElement("span");
    // 说明文字不许换行：一换行就会顶出 32px 的行高，和下一项叠在一起
    h.style.cssText =
      "margin-left:auto;font-size:11px;opacity:.55;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:58%";
    h.textContent = hint;
    b.append(h);
  }
  return b;
} //下拉项