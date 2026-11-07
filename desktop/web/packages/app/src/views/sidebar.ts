/**
 * 侧栏：会话列表。
 *
 * 分组规则跟人找东西的方式对齐——先看置顶，再看今天，然后往前翻。不做"按字母
 * 排序"，会话标题是自然语言，字母序没有任何意义。
 *
 * 整块是一面玻璃。它是界面上面积最大的玻璃，所以折射强度给得很克制（6px）：
 * 侧栏贴着窗口左边，太强的折射会让左边缘看起来像在流动。
 */

import { GlassSurface } from "@baicodex/liquid";
import { kernel, type SessionRow } from "@baicodex/bridge";
import { icon } from "@baicodex/ui";
import type { Store } from "../state";
import { compact, dayDiff, humanize } from "../time";

interface Group {
  label: string;
  items: SessionRow[];
}

function group(list: SessionRow[]): Group[] {
  const pinned: SessionRow[] = [];
  const buckets = new Map<string, SessionRow[]>();

  for (const s of list) {
    if (s.pinned) {
      pinned.push(s);
      continue;
    }
    const d = dayDiff(s.updated_at);
    const label = d <= 0 ? "今天" : d === 1 ? "昨天" : d < 7 ? "本周" : d < 30 ? "这个月" : "更早";
    const b = buckets.get(label);
    if (b) b.push(s);
    else buckets.set(label, [s]);
  }

  const out: Group[] = [];
  if (pinned.length) out.push({ label: "置顶", items: pinned });
  for (const label of ["今天", "昨天", "本周", "这个月", "更早"]) {
    const items = buckets.get(label);
    if (items?.length) out.push({ label, items });
  }
  return out;
}

export function sidebar(store: Store): HTMLElement {
  const g = new GlassSurface({
    radius: 20,
    blur: 15,
    depth: 20,
    tone: 0.26,
    grain: 0.1,
    specular: 0.85,
  });
  g.el.classList.add("sidebar");
  g.el.dataset.sidebar = "true";

  // —— 头 ——
  const head = document.createElement("div");
  head.className = "sb-head";

  const search = document.createElement("label");
  search.className = "sb-search";
  search.append(icon("search", 14));
  const input = document.createElement("input");
  input.placeholder = "搜索会话";
  input.spellcheck = false;
  search.append(input);

  const add = document.createElement("button");
  add.className = "ui-iconbtn";
  add.title = "新会话 (Ctrl+N)";
  add.append(icon("plus", 16));
  add.addEventListener("click", async () => {
    await kernel.newSession();
    await store.reload();
    store.patch({ view: "chat" });
  });

  head.append(search, add);

  // —— 列表 ——
  const list = document.createElement("div");
  list.className = "ui-scroll sb-list";

  // —— 脚 ——
  const foot = document.createElement("div");
  foot.className = "sb-foot";

  const setBtn = document.createElement("button");
  setBtn.className = "ui-iconbtn";
  setBtn.title = "设置";
  setBtn.append(icon("settings", 16));
  setBtn.addEventListener("click", () => store.patch({ view: "settings" }));

  const usage = document.createElement("div");
  usage.className = "sb-usage";

  foot.append(setBtn, usage);

  g.inner.append(head, list, foot);

  // —— 渲染 ——

  let lastKey = "";
  let filter = "";

  const paint = (s: Store["state"]): void => {
    const key =
      s.sessions.map((x) => `${x.id}:${x.updated_at}:${x.pinned ? 1 : 0}:${x.title}`).join("|") +
      `#${s.snapshot?.session ?? ""}#${filter}`;
    if (key === lastKey) return;
    lastKey = key;

    const q = filter.trim().toLowerCase();
    const view = q
      ? s.sessions.filter(
          (x) => x.title.toLowerCase().includes(q) || x.preview.toLowerCase().includes(q),
        )
      : s.sessions;

    list.textContent = "";
    if (!view.length) {
      const empty = document.createElement("div");
      empty.className = "ui-empty";
      empty.textContent = q ? "没有匹配的会话" : "还没有会话";
      list.append(empty);
      return;
    }

    for (const grp of group(view)) {
      const h = document.createElement("div");
      h.className = "sb-group";
      h.textContent = grp.label;
      list.append(h);

      for (const it of grp.items) {
        const row = document.createElement("div");
        row.className = "sb-item";
        row.dataset.active = String(it.id === s.snapshot?.session);

        const title = document.createElement("div");
        title.className = "sb-title";
        if (it.pinned) {
          const pin = icon("pin", 11);
          pin.style.opacity = "0.6";
          title.append(pin);
        }
        title.append(document.createTextNode(it.title.trim() || "新会话"));

        const sub = document.createElement("div");
        sub.className = "sb-sub";
        sub.textContent = it.preview || `${it.count} 条 · ${humanize(it.updated_at)}`;

        row.append(title, sub);
        row.title = it.preview || it.title;
        row.addEventListener("click", async () => {
          if (it.id === s.snapshot?.session) return;
          await kernel.openSession(it.id);
          await store.reload();
          store.patch({ view: "chat" });
        });
        row.addEventListener("contextmenu", (e) => {
          e.preventDefault();
          void kernel.pinSession(it.id, !it.pinned).then(() => store.reload());
        });

        list.append(row);
      }
    }
  };

  input.addEventListener("input", () => {
    filter = input.value;
    // 强刷一次
    lastKey = "";
    paint(store.state);
  });

  // 用量走单独一次拉取，不跟着会话列表刷新
  void kernel
    .usage()
    .then((u) => {
      usage.textContent = `${compact(u.prompt + u.completion)} tok · ${u.money || "¥0"}`;
      usage.title = `共 ${u.calls} 次调用，命中缓存 ${compact(u.cached)} tok`;
    })
    .catch(() => {});

  paint(store.state);
  store.subscribe(paint);

  return g.el;
}