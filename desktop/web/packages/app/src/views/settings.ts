/**
 * 设置。
 *
 * 五个分区，每个分区只做一件事。不做"全部设置塞一页"——那样滚三屏还找不到
 * "深色模式在哪"。
 *
 * 所有改动都立刻生效并落盘（`config_patch` 内部就存），没有"保存"按钮。
 * 有保存按钮的设置页一定会有人改完忘了点。
 */

import { GlassSurface } from "@baicodex/liquid";
import { kernel, type ToolInfo } from "@baicodex/bridge";
import { icon, toast, type IconName } from "@baicodex/ui";
import type { Section, Store } from "../state";
import { compact, money } from "../time";
import { openExternal } from "../host";

const NAV: { id: Section; label: string; icon: IconName }[] = [
  { id: "model", label: "模型", icon: "cpu" },
  { id: "appearance", label: "外观", icon: "sparkle" },
  { id: "tools", label: "工具", icon: "tool" },
  { id: "memory", label: "记忆", icon: "file" },
  { id: "about", label: "关于", icon: "globe" },
];

export function settings(store: Store): HTMLElement {
  const g = new GlassSurface({ radius: 20, blur: 16, depth: 24, tone: 0.42, grain: 0.12, specular: 0.85 });
  g.el.classList.add("settings");

  const nav = document.createElement("nav");
  nav.className = "set-nav";

  const body = document.createElement("div");
  body.className = "ui-scroll set-body";

  g.inner.append(nav, body);

  const buttons = new Map<Section, HTMLButtonElement>();
  for (const n of NAV) {
    const b = document.createElement("button");
    b.append(icon(n.icon, 15), document.createTextNode(n.label));
    b.addEventListener("click", () => store.patch({ section: n.id }));
    nav.append(b);
    buttons.set(n.id, b);
  }

  const back = document.createElement("button");
  back.append(icon("chevron-left", 15), document.createTextNode("返回"));
  back.style.marginTop = "auto";
  back.addEventListener("click", () => store.patch({ view: "chat" }));
  nav.append(back);

  let rendered = "";

  const paint = (s: Store["state"]): void => {
    if (s.view !== "settings") return;
    for (const [id, b] of buttons) b.dataset.on = String(id === s.section);

    const key = `${s.section}#${s.config ? JSON.stringify(s.config).length : 0}#${s.host?.dark}#${s.host?.backdrop}`;
    if (key === rendered) return;
    rendered = key;

    body.textContent = "";
    switch (s.section) {
      case "model":
        body.append(modelSection(s, store));
        break;
      case "appearance":
        body.append(appearanceSection(s, store));
        break;
      case "tools":
        body.append(toolsSection());
        break;
      case "memory":
        body.append(memorySection());
        break;
      case "about":
        body.append(aboutSection(s));
        break;
    }
  };

  paint(store.state);
  store.subscribe(paint);

  return g.el;
}

// —— 模型 ——

function modelSection(s: Store["state"], store: Store): HTMLElement {
  const box = document.createElement("section");
  box.append(head("模型", "改完立刻生效，不用重启。"));

  const cur = document.createElement("div");
  cur.className = "set-row";
  const k = document.createElement("div");
  k.className = "k";
  k.innerHTML = "<b>当前模型</b><span>来自配置档</span>";
  const v = document.createElement("div");
  v.style.fontFamily = "var(--bai-font-mono)";
  v.style.fontSize = "var(--bai-t-sm)";
  v.textContent = s.snapshot?.model || "未配置";
  cur.append(k, v);

  const mode = document.createElement("div");
  mode.className = "set-row";
  const mk = document.createElement("div");
  mk.className = "k";
  mk.innerHTML = "<b>工作模式</b><span>影响模型拿到哪些工具</span>";
  const seg = document.createElement("div");
  seg.className = "seg";
  for (const m of ["standard", "code", "plan", "minimal", "creator"] as const) {
    const b = document.createElement("button");
    b.textContent = { standard: "标准", code: "代码", plan: "计划", minimal: "极简", creator: "创造" }[m];
    b.dataset.on = String((s.config?.agent?.mode ?? "standard") === m);
    b.addEventListener("click", async () => {
      await kernel.configPatch({ agent: { mode: m } }).catch(() => null);
      await store.reload();
      toast(`已切到${b.textContent}`, { ms: 1200 });
    });
    seg.append(b);
  }
  mode.append(mk, seg);

  const usage = document.createElement("div");
  usage.className = "set-row";
  const uk = document.createElement("div");
  uk.className = "k";
  uk.innerHTML = "<b>累计用量</b><span>所有会话加起来</span>";
  const uv = document.createElement("div");
  uv.style.cssText = "font-variant-numeric:tabular-nums;font-size:var(--bai-t-sm)";
  uv.textContent = s.snapshot ? `${compact(s.snapshot.tokens)} tok · ${money(s.snapshot.cost)}` : "—";
  usage.append(uk, uv);

  const prompt = document.createElement("div");
  prompt.style.marginTop = "18px";
  prompt.append(head("系统提示词", "留空则用内置的。改了会影响之后的每一轮。"));
  const ta = document.createElement("textarea");
  ta.className = "ui-input";
  ta.style.cssText =
    "min-height:104px;padding:11px 13px;border-radius:11px;background:color-mix(in srgb,var(--bai-code-bg) 55%,transparent);" +
    "border:1px solid var(--bai-line);box-shadow:inset 0 1px 2px rgba(12,16,26,0.05);" +
    "font-size:13px;line-height:1.6";
  ta.value = s.config?.agent?.system_prompt ?? "";
  ta.placeholder = "（使用内置提示词）";
  let t = 0;
  ta.addEventListener("input", () => {
    clearTimeout(t);
    t = window.setTimeout(() => {
      void kernel.setSystemPrompt(ta.value).catch(() => null);
    }, 600);
  });
  prompt.append(ta);

  box.append(cur, mode, usage, prompt);
  return box;
}

// —— 外观 ——

function appearanceSection(s: Store["state"], store: Store): HTMLElement {
  // 主题开关直接改根节点属性，不走 store——这里不需要整页重绘

  const box = document.createElement("section");
  box.append(head("外观", "玻璃的厚度是调出来的，不是拍的。"));

  // 主题
  const theme = document.createElement("div");
  theme.className = "set-row";
  const tk = document.createElement("div");
  tk.className = "k";
  tk.innerHTML = "<b>主题</b><span>跟随系统时，系统换色会立刻跟</span>";
  const seg = document.createElement("div");
  seg.className = "seg";
  const saved = (localStorage.getItem("bai.theme") as "light" | "dark" | "auto" | null) ?? (s.host?.dark ? "dark" : "light");
  for (const m of ["light", "dark", "auto"] as const) {
    const b = document.createElement("button");
    b.textContent = { light: "浅色", dark: "深色", auto: "跟随" }[m];
    b.dataset.on = String(saved === m);
    b.addEventListener("click", () => {
      store.setTheme(m);
      for (const x of seg.querySelectorAll<HTMLElement>("button")) x.dataset.on = "false";
      b.dataset.on = "true";
    });
    seg.append(b);
  }
  theme.append(tk, seg);

  // 界面风格：素雅是默认，液态玻璃要自己开
  const styleRow = document.createElement("div");
  styleRow.className = "set-row";
  const sk = document.createElement("div");
  sk.className = "k";
  sk.innerHTML = "<b>界面风格</b><span>素雅是白灰配色加几团淡光斑，卡片实底；液态玻璃才有折射、镜面高光与漂移极光</span>";
  const sseg = document.createElement("div");
  sseg.className = "seg";
  const curUi = localStorage.getItem("bai.ui") === "liquid" ? "liquid" : "plain";
  for (const [v, label] of [["plain", "素雅"], ["liquid", "液态玻璃"]] as const) {
    const b = document.createElement("button");
    b.textContent = label;
    b.dataset.on = String(curUi === v);
    b.addEventListener("click", () => {
      localStorage.setItem("bai.ui", v);
      document.documentElement.dataset.ui = v;
      for (const x of sseg.querySelectorAll<HTMLElement>("button")) x.dataset.on = "false";
      b.dataset.on = "true";
    });
    sseg.append(b);
  }
  styleRow.append(sk, sseg);

  // 极光
  const aurora = document.createElement("div");
  aurora.className = "set-row";
  const ak = document.createElement("div");
  ak.className = "k";
  ak.innerHTML = "<b>背景光晕</b><span>玻璃后面得有东西可折，纯色背景上玻璃是看不出来的</span>";
  const aseg = document.createElement("div");
  aseg.className = "seg";
  const cur = localStorage.getItem("bai.aurora") ?? "0.7";
  for (const [v, label] of [["1", "强"], ["0.7", "中"], ["0.35", "弱"], ["0", "关"]] as const) {
    const b = document.createElement("button");
    b.textContent = label;
    b.dataset.on = String(cur === v);
    b.addEventListener("click", () => {
      localStorage.setItem("bai.aurora", v);
      document.documentElement.style.setProperty("--aurora-op", v);
      for (const x of aseg.querySelectorAll<HTMLElement>("button")) x.dataset.on = "false";
      b.dataset.on = "true";
    });
    aseg.append(b);
  }
  aurora.append(ak, aseg);

  // 玻璃表现
  const glass = document.createElement("div");
  glass.className = "set-row";
  const gk = document.createElement("div");
  gk.className = "k";
  gk.innerHTML = "<b>玻璃折射</b><span>关掉能省一点 GPU，低配机器上更稳；素雅风格下这一项不起作用</span>";
  const gseg = document.createElement("div");
  gseg.className = "seg";
  const off = localStorage.getItem("bai.refract") === "0";
  for (const [v, label] of [["1", "开"], ["0", "关"]] as const) {
    const b = document.createElement("button");
    b.textContent = label;
    b.dataset.on = String((v === "0") === off);
    b.addEventListener("click", () => {
      localStorage.setItem("bai.refract", v);
      document.documentElement.dataset.refract = v === "1" ? "on" : "off";
      for (const x of gseg.querySelectorAll<HTMLElement>("button")) x.dataset.on = "false";
      b.dataset.on = "true";
      toast("重开窗口后完全生效", { ms: 1800 });
    });
    gseg.append(b);
  }
  glass.append(gk, gseg);

  box.append(theme, styleRow, aurora, glass);

  const note = document.createElement("p");
  note.style.cssText = "margin-top:20px;font-size:12px;color:var(--bai-fg-mute);line-height:1.7";
  note.textContent =
    `当前底板：${s.host?.backdrop ?? "未知"}。` +
    (s.host?.backdrop === "mica"
      ? "Mica 是桌面壁纸的模糊采样，最省电，但不会跟窗口后面的内容变化。"
      : s.host?.backdrop === "acrylic"
        ? "Acrylic 会实时采样窗口后面的内容，拖窗口时能看见背景在动。"
        : "这台机器没有系统级毛玻璃，界面用实色底加自绘高光。");
  box.append(note);
  return box;
}

// —— 工具 ——

function toolsSection(): HTMLElement {
  const box = document.createElement("section");
  box.append(head("工具", "模型能调用的东西。高风险工具每次都会问。"));

  const list = document.createElement("div");
  list.className = "ui-empty";
  list.textContent = "正在读取…";
  box.append(list);

  void kernel
    .tools()
    .then((tools: ToolInfo[]) => {
      list.className = "";
      list.textContent = "";
      for (const t of tools) {
        const row = document.createElement("div");
        row.className = "set-row";
        const k = document.createElement("div");
        k.className = "k";
        const b = document.createElement("b");
        b.textContent = t.label || t.name;
        const sp = document.createElement("span");
        sp.textContent = t.name;
        sp.style.fontFamily = "var(--bai-font-mono)";
        k.append(b, sp);

        const tag = document.createElement("span");
        tag.className = "ui-tag";
        tag.dataset.tone = t.high_risk ? "danger" : t.risk === "medium" ? "accent" : "ok";
        tag.textContent = t.high_risk ? "高风险" : t.risk === "medium" ? "中" : "低";

        const st = document.createElement("span");
        st.className = "ui-tag";
        st.textContent = t.allowed ? "已启用" : "当前模式禁用";

        row.append(k, tag, st);
        list.append(row);
      }
      if (!tools.length) list.textContent = "没有可用工具";
    })
    .catch(() => {
      list.textContent = "读不到工具列表";
    });

  return box;
}

// —— 记忆 ——

function memorySection(): HTMLElement {
  const box = document.createElement("section");
  box.append(head("记忆", "跨会话保留的事实。模型自己会记，你也可以删。"));

  const add = document.createElement("div");
  add.style.cssText = "display:flex;gap:8px;margin-bottom:14px";
  const input = document.createElement("input");
  input.className = "ui-input";
  input.placeholder = "手动记一条，比如：项目用 pnpm，不要用 npm";
  input.style.cssText = "height:34px;padding:0 12px;border-radius:9px;background:var(--bai-code-bg);border:1px solid var(--bai-line)";
  const btn = document.createElement("button");
  btn.className = "ui-btn";
  btn.style.cssText = "height:34px;padding:0 14px;border-radius:9px;background:var(--bai-accent);color:var(--bai-accent-fg)";
  btn.textContent = "添加";
  btn.addEventListener("click", async () => {
    const v = input.value.trim();
    if (!v) return;
    input.value = "";
    await kernel.memorySave(v).catch(() => null);
    void load();
  });
  add.append(input, btn);

  const list = document.createElement("div");
  list.textContent = "正在读取…";

  const load = async (): Promise<void> => {
    try {
      const rows = await kernel.memoryList();
      list.textContent = "";
      if (!rows.length) {
        list.className = "ui-empty";
        list.textContent = "还没有记忆";
        return;
      }
      list.className = "";
      for (const r of rows) {
        const row = document.createElement("div");
        row.className = "set-row";
        const k = document.createElement("div");
        k.className = "k";
        const b = document.createElement("b");
        b.textContent = r.text;
        b.style.fontWeight = "450";
        k.append(b);
        const del = document.createElement("button");
        del.className = "ui-iconbtn";
        del.dataset.size = "sm";
        del.title = "删除";
        del.append(icon("trash", 13));
        del.addEventListener("click", async () => {
          await kernel.memoryDelete(r.id).catch(() => null);
          void load();
        });
        row.append(k, del);
        list.append(row);
      }
    } catch {
      list.textContent = "读不到记忆";
    }
  };
  void load();

  box.append(add, list);
  return box;
}

// —— 关于 ——

function aboutSection(s: Store["state"]): HTMLElement {
  const box = document.createElement("section");
  box.append(head("关于", "一套 Rust 逻辑层，三个壳：终端、Android、Windows。"));

  const dl = document.createElement("dl");
  dl.className = "kv";
  const rows: [string, string][] = [
    ["版本", s.host?.version ?? "—"],
    ["平台", s.host?.windowsName ?? "浏览器预览"],
    ["架构", s.host?.arch ?? "—"],
    ["底板", s.host?.backdrop ?? "—"],
    ["缩放", s.host ? `${Math.round((s.host.dpi ?? 1) * 100)}%` : "—"],
    ["渲染", navigator.userAgent.includes("Edg") ? "WebView2" : "Chromium"],
  ];
  for (const [k, v] of rows) {
    const dt = document.createElement("dt");
    dt.textContent = k;
    const dd = document.createElement("dd");
    dd.textContent = v;
    dl.append(dt, dd);
  }

  const links = document.createElement("div");
  links.style.cssText = "display:flex;gap:8px;margin-top:18px";
  const repo = document.createElement("button");
  repo.className = "ui-btn";
  repo.textContent = "项目主页";
  repo.style.cssText = "height:32px;padding:0 14px;border-radius:9px;background:var(--bai-code-bg)";
  repo.addEventListener("click", () => openExternal("https://github.com/mubai0122/baicodex"));
  links.append(repo);

  const arch = document.createElement("p");
  arch.style.cssText = "margin-top:22px;font-size:12px;line-height:1.75;color:var(--bai-fg-mute)";
  arch.textContent =
    "界面只做两件事：把内核的事件画出来，把你的操作翻译成 op。所有业务判断——工具能不能跑、" +
    "上下文怎么裁、密钥怎么解——都在 Rust 里。换一个壳（Android / 终端）不需要重写任何一条规则。";

  box.append(dl, links, arch);
  return box;
}

// —— 小工具 ——

function head(title: string, sub: string): HTMLElement {
  const f = document.createDocumentFragment();
  const h = document.createElement("h2");
  h.textContent = title;
  const p = document.createElement("p");
  p.className = "sub";
  p.textContent = sub;
  f.append(h, p);
  const wrap = document.createElement("div");
  wrap.append(f);
  return wrap;
}