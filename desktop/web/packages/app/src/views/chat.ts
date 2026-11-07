/**
 * 消息流。
 *
 * ## 增量更新怎么做的
 *
 * 流式输出时 `delta` 每秒能来几十次。如果每次重建整棵消息树，长对话会肉眼可见
 * 地卡。所以拆成两条路：
 *
 * - **已落库的消息**：只在 `messages` 数组真的变了（新消息、内容改、收藏）时重建；
 * - **正在生成的那条**：单独一个元素，只更新它内部的 HTML，并且节流到 ~50ms。
 *
 * 判断"真的变了"用的是 id + 正文长度拼出来的 key。正文长度变了就说明内容改了，
 * 比深比较便宜。
 *
 * ## 工具卡为什么要合并
 *
 * 一次工具调用在数据上是两条消息：`assistant(tool_calls=[...])` 和
 * `tool(tool_call_id=...)`。分开渲染会出现"一个空卡片 + 一个结果卡片"，中间还
 * 夹着别的东西。这里按 `tool_call_id` 把结果塞回调用卡里，视觉上是一次操作。
 */

import { GlassSurface } from "@baicodex/liquid";
import { kernel, type ChatMsg } from "@baicodex/bridge";
import { icon, renderMarkdown, toast } from "@baicodex/ui";
import { openExternal } from "../host";
import type { Store } from "../state";
import { clock, duration } from "../time";

export interface ChatView {
  el: HTMLElement;
  scrollToBottom(smooth?: boolean): void;
}

export function chat(store: Store): ChatView {
  const wrap = document.createElement("div");
  wrap.className = "stream ui-scroll";

  const inner = document.createElement("div");
  inner.className = "stream-inner";
  wrap.append(inner);

  let msgKey = "";
  let streamNode: HTMLElement | null = null;
  let streamCard: GlassSurface | null = null;
  let streamText = "";
  let streamReason = "";
  let streamTimer = 0;

  // 代码块的复制按钮：容器上委托一次，别在每个代码块上绑
  wrap.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-copy]");
    if (!btn) return;
    const text = decodeURIComponent(btn.dataset.copy ?? "");
    void navigator.clipboard?.writeText(text).then(
      () => toast("已复制", { ms: 1200 }),
      () => toast("复制失败", { tone: "danger" }),
    );
  });

  // 外链一律交给系统浏览器
  wrap.addEventListener("click", (e) => {
    const a = (e.target as HTMLElement).closest<HTMLAnchorElement>("a[href]");
    if (!a) return;
    e.preventDefault();
    openExternal(a.href);
  });

  const paint = (s: Store["state"]): void => {
    // 纯文本比 + 待确认 + 运行中工具：只算消息的话，确认请求到达时消息没变，卡片永远不出现
    const key = [
      s.messages.map((m) => `${m.id}:${m.content.length}:${m.starred ? 1 : 0}`).join(","),
      s.pending.kind,
      s.pending.tool?.name ?? "",
      s.pending.tool?.args.length ?? 0,
      s.running.size,
    ].join("|");
    if (key !== msgKey) {
      msgKey = key;
      rebuild(s);
    }
    scheduleStream();
  };

  function rebuild(s: Store["state"]): void {
    const stick = atBottom();
    inner.textContent = "";
    streamNode = null;
    streamCard = null;

    if (!s.messages.length) {
      inner.append(welcome(s));
      return;
    }

    // 工具结果按 tool_call_id 索引，稍后塞回调用卡
    const results = new Map<string, ChatMsg>();
    for (const m of s.messages) {
      if (m.role === "tool" && m.tool_call_id) results.set(m.tool_call_id, m);
    }

    for (const m of s.messages) {
      if (m.role === "tool" && m.tool_call_id && results.has(m.tool_call_id)) {
        // 已经在调用卡里渲染过了
        continue;
      }
      const node = messageNode(m, s, results);
      if (node) inner.append(node);
    }

    // 待确认 / 待作答
    const ask = pendingNode(s);
    if (ask) inner.append(ask);

    if (stick) queueMicrotask(() => wrap.scrollTo({ top: wrap.scrollHeight }));

    // 纯等待中（还没有任何 token）
    if (s.snapshot?.busy && !s.stream && !s.reason) {
      inner.append(typingNode());
    }
  }

  function scheduleStream(): void {
    if (streamTimer) return;
    streamTimer = window.setTimeout(() => {
      streamTimer = 0;
      //读 store 里的最新值：定时器排队期间消息可能已经到了，用捕获的旧 state 会把流式卡重建成残留
      const cur = store.state;
      renderStream(cur.stream, cur.reason);
    }, 50);
  }

  function renderStream(text: string, reason: string): void {
    if (text === streamText && reason === streamReason && streamNode) return;
    streamText = text;
    streamReason = reason;

    if (!text && !reason) {
      streamNode?.remove();
      streamNode = null;
      return;
    }

    if (!streamNode) {
      streamNode = document.createElement("div");
      streamNode.className = "msg";
      streamNode.dataset.role = "assistant";
      streamNode.dataset.live = "true";
      inner.append(streamNode);
    }

    const stick = atBottom();
    streamNode.textContent = "";

    if (reason) streamNode.append(thinkingNode(reason, true, text ? false : true));
    if (text) {
      //复用同一个实例：每 50ms 换一次文本，重建玻璃会把 ResizeObserver 和位移图一起重来
      if (!streamCard) streamCard = aiCard("", false);
      streamCard.inner.innerHTML = renderMarkdown(text);
      streamNode.append(streamCard.el);
    } else if (reason) {
      streamNode.append(typingNode());
    }

    if (stick) wrap.scrollTop = wrap.scrollHeight;
  }

  function atBottom(): boolean {
    return wrap.scrollHeight - wrap.scrollTop - wrap.clientHeight < 40;
  }

  const off = store.subscribe(paint);
  paint(store.state);

  return {
    el: wrap,
    scrollToBottom(smooth = false) {
      wrap.scrollTo({ top: wrap.scrollHeight, behavior: smooth ? "smooth" : "auto" });
    },
  };

  // —— 节点构造 ——

  function messageNode(m: ChatMsg, s: Store["state"], results: Map<string, ChatMsg>): HTMLElement | null {
    if (m.role === "user") return userNode(m, s);
    if (m.role === "summary") return summaryNode(m);
    if (m.role === "tool") return toolResultNode(m);
    return assistantNode(m, s, results);
  }

  function userNode(m: ChatMsg, s: Store["state"]): HTMLElement {
    const root = document.createElement("div");
    root.className = "msg";
    root.dataset.role = "user";

    const g = new GlassSurface({
      radius: 18,
      blur: 12,
      depth: 18,
      tone: 0.3,
      grain: 0.1,
      specular: 0.9,
    });
    g.el.classList.add("bubble-user");
    g.el.style.borderBottomRightRadius = "6px";
    g.inner.textContent = m.content;

    const head = document.createElement("div");
    head.className = "msg-head";
    head.append(text(clock(m.meta?.at ?? 0)));
    if (m.b_total > 1) head.append(branchNav(m, s));
    head.append(actions(m, s));

    root.append(head, g.el);
    return root;
  }

  function assistantNode(m: ChatMsg, s: Store["state"], results: Map<string, ChatMsg>): HTMLElement {
    const root = document.createElement("div");
    root.className = "msg";
    root.dataset.role = "assistant";

    const head = document.createElement("div");
    head.className = "msg-head";
    head.append(text(m.meta ? clock(m.meta.at) : ""));
    if (m.meta?.ms) head.append(text("·"), text(duration(m.meta.ms)));
    if (m.meta?.completion_tokens) head.append(text("·"), text(`${m.meta.completion_tokens} tok`));
    if (m.b_total > 1) head.append(branchNav(m, s));
    head.append(actions(m, s));
    root.append(head);

    if (m.reasoning) root.append(thinkingNode(m.reasoning, false, false));

    if (m.content) root.append(aiCard(m.content, true).el);

    for (const call of m.tool_calls) {
      root.append(toolCallNode(call.name, call.arguments, results.get(call.id)));
    }

    return root;
  }

  function aiCard(content: string, refract: boolean): GlassSurface {
    const g = new GlassSurface({
      radius: 18,
      blur: 13,
      depth: refract ? 20 : 0,
      tone: 0.28,
      grain: 0.09,
      specular: 0.85,
    });
    g.el.classList.add("bubble-ai");
    g.inner.classList.add("ui-md");
    if (content) g.inner.innerHTML = renderMarkdown(content);
    return g;
  } //助手正文的玻璃卡。流式那条 depth=0：高度每个 token 都在变，重建位移图不值当

  function summaryNode(m: ChatMsg): HTMLElement {
    const el = document.createElement("div");
    el.className = "bubble-summary";
    el.textContent = m.content || "上下文已压缩";
    el.title = "这里之前的历史被折叠成了摘要，仍然在消息树里";
    return el;
  }

  function toolResultNode(m: ChatMsg): HTMLElement {
    return toolCallNode(m.tool_name || "tool", "", m);
  }

  function toolCallNode(name: string, args: string, result?: ChatMsg): HTMLElement {
    const g = new GlassSurface({
      radius: 10,
      blur: 9,
      depth: 0,
      tone: 0.2,
      grain: 0.08,
      specular: 0.5,
    });
    g.el.classList.add("tool-card");

    const head = document.createElement("div");
    head.className = "tool-head";

    const dot = document.createElement("span");
    dot.className = "tool-dot";
    dot.dataset.state = result ? (result.is_error ? "err" : "ok") : "run";

    const nm = document.createElement("span");
    nm.className = "tool-name";
    nm.textContent = name;

    const ar = document.createElement("span");
    ar.className = "tool-args";
    ar.textContent = compactArgs(args);

    head.append(dot, nm, ar);

    let out: HTMLElement | null = null;
    if (result?.content) {
      out = document.createElement("div");
      out.className = "tool-out";
      out.dataset.error = String(result.is_error);
      out.textContent = truncate(result.content, 4000);
      out.hidden = true;

      const chev = icon("chevron-right", 12);
      chev.classList.add("tb-chev");
      chev.style.marginLeft = "auto";
      chev.style.opacity = "0.5";
      head.append(chev);

      head.style.cursor = "pointer";
      head.addEventListener("click", () => {
        out!.hidden = !out!.hidden;
        chev.style.transform = out!.hidden ? "" : "rotate(90deg)";
      });
    }

    g.inner.append(head);
    if (out) g.inner.append(out);
    return g.el;
  }

  function thinkingNode(reason: string, live: boolean, withSpinner: boolean): HTMLElement {
    const d = document.createElement("details");
    d.className = "thinking";
    if (!live) d.open = false;

    const sum = document.createElement("summary");
    const chev = icon("chevron-right", 12);
    chev.classList.add("tb-chev");
    sum.append(chev, text(live ? "正在思考" : "思考过程"));
    if (live && withSpinner) sum.append(spinner());

    const body = document.createElement("div");
    body.textContent = reason;

    d.append(sum, body);
    return d;
  }

  function typingNode(): HTMLElement {
    const el = document.createElement("div");
    el.className = "msg";
    el.dataset.role = "assistant";
    el.dataset.typing = "true";

    const box = document.createElement("div");
    box.className = "msg-head";
    box.style.opacity = "1";
    box.append(spinner());
    const label = text("正在生成");
    label.style.fontSize = "var(--bai-t-xs)";
    box.append(label);
    el.append(box);
    return el;
  }

  function actions(m: ChatMsg, s: Store["state"]): HTMLElement {
    const box = document.createElement("div");
    box.className = "msg-actions";

    if (m.content) {
      box.append(
        mini("copy", "复制", async () => {
          await navigator.clipboard?.writeText(m.content);
          toast("已复制", { ms: 1200 });
        }),
      );
    }

    box.append(
      mini("star", m.starred ? "取消收藏" : "收藏", async () => {
        await kernel.starMessage(m.id, !m.starred);
        await store.reload();
      }, m.starred),
    );

    // 只有最后一条助手消息能重新生成
    const lastAssistant = [...s.messages].reverse().find((x) => x.role === "assistant");
    if (lastAssistant?.id === m.id && !s.snapshot?.busy) {
      box.append(
        mini("refresh", "重新生成", async () => {
          await kernel.regenerate();
          await store.reload();
        }),
      );
    }

    if (m.role === "user" && !s.snapshot?.busy) {
      box.append(
        mini("edit", "编辑并重发", async () => {
          const next = window.prompt("修改后重新发送", m.content);
          if (next == null || next === m.content) return;
          await kernel.editResend(m.id, next);
          await store.reload();
        }),
      );
    }

    return box;
  }

  function branchNav(m: ChatMsg, _s: Store["state"]): HTMLElement {
    const box = document.createElement("span");
    box.className = "branch-nav";

    const prev = document.createElement("button");
    prev.append(icon("chevron-left", 11));
    prev.disabled = m.b_idx <= 1;

    const label = document.createElement("span");
    label.textContent = `${m.b_idx}/${m.b_total}`;

    const next = document.createElement("button");
    next.append(icon("chevron-right", 11));
    next.disabled = m.b_idx >= m.b_total;

    const go = async (dir: -1 | 1) => {
      const sib = await kernel.siblings(m.id);
      const idx = sib.list.findIndex((x) => x.id === m.id);
      const target = sib.list[idx + dir];
      if (!target) return;
      await kernel.switchBranch(target.id);
      await store.reload();
    };
    prev.addEventListener("click", () => void go(-1));
    next.addEventListener("click", () => void go(1));

    box.append(prev, label, next);
    return box;
  }

  function pendingNode(s: Store["state"]): HTMLElement | null {
    const p = s.pending;
    if (!p.kind) return null;

    const g = new GlassSurface({ radius: 14, blur: 14, depth: 18, tone: 0.36, grain: 0.1, specular: 0.8 });
    g.el.classList.add("ask");

    if (p.kind === "confirm" && p.tool) {
      const q = document.createElement("div");
      q.className = "ask-q";
      q.textContent = `要执行 ${p.tool.name} 吗？`;
      const desc = document.createElement("div");
      desc.className = "tool-out";
      desc.style.maxHeight = "140px";
      desc.textContent = truncate(p.tool.args, 1200);

      const row = document.createElement("div");
      row.className = "ask-actions";

      const deny = document.createElement("button");
      deny.className = "ask-opt";
      deny.textContent = "拒绝";
      deny.addEventListener("click", async () => {
        await kernel.confirmTool(false, false);
        await store.refreshPending();
      });

      const allow = document.createElement("button");
      allow.className = "ask-opt";
      allow.dataset.on = "true";
      allow.textContent = p.tool.high_risk ? "允许（高风险）" : "允许";
      allow.addEventListener("click", async () => {
        await kernel.confirmTool(true, false);
        await store.refreshPending();
      });

      const always = document.createElement("button");
      always.className = "ask-opt";
      always.textContent = "本次会话都允许";
      always.addEventListener("click", async () => {
        await kernel.confirmTool(true, true);
        await store.refreshPending();
      });

      row.append(deny, allow, always);
      g.inner.append(q, desc, row);
      return g.el;
    }

    if (p.kind === "ask") {
      const answers: string[] = [];
      for (const q of p.questions) {
        const t = document.createElement("div");
        t.className = "ask-q";
        t.textContent = q.question;
        g.inner.append(t);

        const opts = document.createElement("div");
        opts.className = "ask-opts";
        for (const o of q.options) {
          const b = document.createElement("button");
          b.className = "ask-opt";
          b.textContent = o.label;
          b.title = o.desc;
          b.addEventListener("click", () => {
            if (q.multi) {
              b.dataset.on = b.dataset.on === "true" ? "false" : "true";
            } else {
              for (const sib of opts.querySelectorAll<HTMLElement>(".ask-opt")) sib.dataset.on = "false";
              b.dataset.on = "true";
            }
          });
          opts.append(b);
        }
        g.inner.append(opts);
        answers.push("");
      }

      const row = document.createElement("div");
      row.className = "ask-actions";
      const ok = document.createElement("button");
      ok.className = "ask-opt";
      ok.dataset.on = "true";
      ok.textContent = "提交";
      ok.addEventListener("click", async () => {
        // 每个问题取选中的第一个（多选拼逗号）
        const picked: string[] = [];
        const groups = g.inner.querySelectorAll(".ask-opts");
        for (const grp of groups) {
          const on = [...grp.querySelectorAll<HTMLElement>(".ask-opt")].filter((x) => x.dataset.on === "true");
          picked.push(on.map((x) => x.textContent ?? "").join(","));
        }
        await kernel.answerAsk(picked.length ? picked : [""]);
        await store.refreshPending();
      });
      row.append(ok);
      g.inner.append(row);
      return g.el;
    }

    if (p.kind === "plan" && p.plan) {
      const t = document.createElement("div");
      t.className = "ask-q";
      t.textContent = "计划待确认";
      const body = document.createElement("div");
      body.className = "ui-md";
      body.innerHTML = renderMarkdown(p.plan);
      const row = document.createElement("div");
      row.className = "ask-actions";

      const no = document.createElement("button");
      no.className = "ask-opt";
      no.textContent = "不做了";
      no.addEventListener("click", async () => {
        await kernel.answerPlan("dismiss");
        await store.refreshPending();
      });

      const yes = document.createElement("button");
      yes.className = "ask-opt";
      yes.dataset.on = "true";
      yes.textContent = "按计划执行";
      yes.addEventListener("click", async () => {
        await kernel.answerPlan("confirm");
        await store.refreshPending();
      });

      row.append(no, yes);
      g.inner.append(t, body, row);
      return g.el;
    }

    return null;
  }

  function welcome(s: Store["state"]): HTMLElement {
    const el = document.createElement("div");
    el.className = "welcome";
    const box = document.createElement("div");
    box.className = "welcome-in";

    const h = document.createElement("h1");
    h.textContent = "今天想做什么？";

    const p = document.createElement("p");
    p.textContent = `${s.snapshot?.model ?? "模型"} 已就绪，工具全在本地跑。`;

    const cards = document.createElement("div");
    cards.className = "welcome-cards";
    const samples: [string, string][] = [
      ["理清一个项目的结构", "从目录到分层，先给结论再给证据"],
      ["读一大段日志找问题", "搜索 + 读文件 + 归纳，不靠猜"],
      ["把这周的改动整理成周报", "按模块分组，只留真正变了的"],
      ["写一个能跑的脚本", "写完直接执行，报错自己修"],
    ];
    for (const [title, desc] of samples) {
      const c = document.createElement("div");
      c.className = "wcard";
      const b = document.createElement("b");
      b.textContent = title;
      const sp = document.createElement("span");
      sp.textContent = desc;
      c.append(b, sp);
      c.addEventListener("click", () => {
        const ta = document.querySelector<HTMLTextAreaElement>(".composer textarea");
        if (!ta) return;
        ta.value = title;
        ta.focus();
      });
      cards.append(c);
    }

    box.append(h, p, cards);
    el.append(box);
    return el;
  }
}

// —— 小工具 ——

function text(s: string): HTMLSpanElement {
  const el = document.createElement("span");
  el.textContent = s;
  return el;
}

function mini(name: Parameters<typeof icon>[0], title: string, fn: () => void | Promise<void>, on = false): HTMLButtonElement {
  const b = document.createElement("button");
  b.className = "ui-iconbtn";
  b.dataset.size = "sm";
  b.title = title;
  b.dataset.active = String(on);
  b.append(icon(name, 13));
  b.addEventListener("click", (e) => {
    e.stopPropagation();
    void fn();
  });
  return b;
}

function spinner(): HTMLElement {
  const el = document.createElement("span");
  el.style.cssText =
    "width:10px;height:10px;border-radius:50%;border:1.5px solid currentColor;border-top-color:transparent;display:inline-block;animation:spin .8s linear infinite;opacity:.6";
  if (!document.getElementById("spin-kf")) {
    const st = document.createElement("style");
    st.id = "spin-kf";
    st.textContent = "@keyframes spin{to{transform:rotate(360deg)}}";
    document.head.append(st);
  }
  return el;
}

/** 参数太长就砍掉中间，保留开头能看出调什么。 */
function compactArgs(args: string): string {
  const s = args.replace(/\s+/g, " ").trim();
  if (!s || s === "{}") return "";
  return s.length > 96 ? `${s.slice(0, 92)}…` : s;
}

function truncate(s: string, n: number): string {
  return s.length > n ? `${s.slice(0, n)}\n…（共 ${s.length} 字）` : s;
}