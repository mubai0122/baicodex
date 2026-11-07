/**
 * 假内核。
 *
 * 用途只有一个：**在浏览器里把界面跑起来**。不是为了测业务逻辑（那是 Rust 单元
 * 测试的事），而是为了调样式、截图、给人演示——不用先编译一个 Windows exe。
 *
 * 假数据刻意做得像真的：有工具调用、有思考过程、有分支、有一条失败的工具结果。
 * 只有真实数据才会暴露"工具卡在深色主题下看不清"这类问题。
 */

import type { KernelEvent } from "./events.gen";
import type { AgentMode, ChatMsg, Pending, SessionRow, Snapshot, ToolInfo } from "./model";
import type { AnyEvent, Transport } from "./types";

const T0 = 1_760_000_000_000; // 固定时间，截图才稳定

function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}
/** 模式名白名单。config_patch 来自界面，不能把任意字符串直接塞进状态 */
function isMode(s: string): s is AgentMode {
  return s === "plan" || s === "standard" || s === "code" || s === "minimal" || s === "creator";
}

function msg(p: Partial<ChatMsg> & { id: number; role: ChatMsg["role"]; content: string }): ChatMsg {
  return {
    tool_calls: [],
    tool_call_id: null,
    tool_name: null,
    is_error: false,
    reasoning: "",
    images: [],
    files: [],
    meta: null,
    starred: false,
    parent_id: 0,
    b_idx: 1,
    b_total: 1,
    ...p,
  };
}

const SESSIONS: SessionRow[] = [
  {
    id: "s-1",
    title: "梳理 core 分层",
    category: "架构",
    descr: "",
    updated_at: T0 + 3_600_000,
    preview: "core/base 只做字符串、时间、文件这些谁都要用的活",
    count: 8,
    pinned: true,
    workspace: "D:\\code\\baicodex",
    persona: "",
  },
  {
    id: "s-2",
    title: "Liquid Glass 的折射怎么做",
    category: "设计",
    descr: "",
    updated_at: T0 - 86_400_000,
    preview: "位移贴图 + feDisplacementMap，边缘按 SDF 梯度偏折",
    count: 14,
    pinned: false,
    workspace: "",
    persona: "",
  },
  {
    id: "s-3",
    title: "把 Android 的 JNI 收口",
    category: "",
    descr: "",
    updated_at: T0 - 3 * 86_400_000,
    preview: "89 个 op 收敛成一个 NativeBridge.call",
    count: 22,
    pinned: false,
    workspace: "",
    persona: "",
  },
];

const MESSAGES: ChatMsg[] = [
  msg({
    id: 1,
    role: "user",
    content: "帮我看下 src/core 下面有哪些模块，顺便说说这个分层是怎么想的。",
  }),
  msg({
    id: 2,
    role: "assistant",
    content: "",
    reasoning:
      "用户问的是目录结构和分层理由。先列一遍 core 下的目录，再按依赖方向解释。不要逐个文件念，抓三层的职责边界。",
    tool_calls: [{ id: "call_1", name: "fs_list", arguments: '{"path":"src/core","limit":40}' }],
    meta: { at: T0, ms: 2140, first_ms: 380, prompt_tokens: 1284, completion_tokens: 96, cached_tokens: 1024, provider: "anthropic", model: "claude-sonnet-4" },
  }),
  msg({
    id: 3,
    role: "tool",
    tool_call_id: "call_1",
    tool_name: "fs_list",
    content: 'base/\nsecurity/\nfeature/\nmod.rs',
    meta: { at: T0, ms: 12, first_ms: 0, prompt_tokens: 0, completion_tokens: 0, cached_tokens: 0, provider: "", model: "" },
  }),
  msg({
    id: 4,
    role: "assistant",
    content:
      "三层，依赖只能往下走：\n\n" +
      "- **`core/base`** — 错误、时间、文本、ID、路径、原子写、JSON。它不认识任何业务概念。\n" +
      "- **`core/security`** — 策略、密钥、加密落盘、权限、零宽路由、脱敏、审计。它不认识模型。\n" +
      "- **`core/feature`** — 模型、配置、存储、记忆、上下文、Agent、工具、技能、MCP、工作流、人设、用量。\n\n" +
      "这么切的原因是**变化频率不同**。base 一年动两次，security 跟着合规走，feature 每周都在加东西。混在一起，改个工具就要重跑全部安全测试。\n\n" +
      "```text\nfeature ──▶ security ──▶ base\n   │\n   └──▶ exec / net（平台差异挡在这里）\n```\n\n" +
      "还有一条：`feature` 不直接碰平台。要执行命令找 `exec`，要发请求找 `net`。所以同一套逻辑层能同时给 Android、终端、桌面用。",
    meta: { at: T0 + 40_000, ms: 6120, first_ms: 520, prompt_tokens: 1466, completion_tokens: 412, cached_tokens: 1024, provider: "anthropic", model: "claude-sonnet-4" },
  }),
  msg({
    id: 5,
    role: "user",
    content: "那 exec 和 net 为什么单独拿出来？放进 core 不行吗？",
  }),
  msg({
    id: 6,
    role: "assistant",
    content:
      "放进去会有两个具体问题。\n\n" +
      "**一是编译**。`exec` 里全是平台相关的：Android 的 Shizuku、root、无障碍；桌面的 shell、文件对话框。放 core 里，任何一处平台判断都会传染到上层。\n\n" +
      "**二是替换**。测试要跑一个「不真的执行命令」的版本，只要换 `exec` 的实现；如果把 `Command::new` 散在 feature 里，就得改十几处。\n\n" +
      "`net` 同理：模型供应商换协议、加代理、加重试，都发生在这一层，上层只看见 `llm::chat()`。",
    meta: { at: T0 + 120_000, ms: 4380, first_ms: 410, prompt_tokens: 1902, completion_tokens: 288, cached_tokens: 1466, provider: "anthropic", model: "claude-sonnet-4" },
  }),
];

const TOOLS: ToolInfo[] = [
  { name: "fs_list", label: "列目录", risk: "low", allowed: true, high_risk: false },
  { name: "fs_read", label: "读文件", risk: "low", allowed: true, high_risk: false },
  { name: "fs_write", label: "写文件", risk: "medium", allowed: true, high_risk: false },
  { name: "shell", label: "执行命令", risk: "high", allowed: true, high_risk: true },
  { name: "search", label: "联网搜索", risk: "low", allowed: true, high_risk: false },
  { name: "memory_save", label: "记一条", risk: "low", allowed: true, high_risk: false },
];

export class MockTransport implements Transport {
  readonly kind = "mock" as const;
  private listeners = new Set<(ev: AnyEvent) => void>();
  private seq = 1_000;
  private busy = false;
  private stream = "";
  private reason = "";
  private pending: Pending = { kind: "", tool: null, questions: [], plan: "" };
  /**当前会话消息：初值是演示数据，之后的每轮都往这里加，reload 才不会把新消息冲掉*/
  private messages: ChatMsg[] = [...MESSAGES];
  // 模式和模型要能被 config_patch 改掉，否则点完下拉 reload 回来还是原值，预览会自相矛盾
  private agentMode: AgentMode = "standard";
  private providerId = "openai";
  private providerModel = "gpt-6-sol";

  invoke<T>(op: string, args: Record<string, unknown> = {}): Promise<T> {
    return this.handle(op, args) as Promise<T>;
  }

  onEvent(fn: (ev: AnyEvent) => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  dispose(): void {
    this.listeners.clear();
  }

  private emit(ev: KernelEvent | AnyEvent): void {
    // 会话消息要落进 this.messages：send 结束前端会 reload，不记下来新消息会被 MESSAGES 冲掉
    if (ev.type === "message") {
      const m = ev.message;
      if (!this.messages.some((x) => x.id === m.id)) this.messages.push(m);
    }
    if (ev.type === "user_message") {
      this.messages.push(msg({ id: ev.id, role: "user", content: ev.text, images: ev.images ?? [] }));
    }
    for (const fn of this.listeners) fn(ev as AnyEvent);
  }

  private async handle(op: string, args: Record<string, unknown>): Promise<unknown> {
    switch (op) {
      case "__host_info":
        return {
          kind: "host_info",
          version: "2.0.0",
          os: "windows",
          platform: "win32",
          arch: "x86_64",
          backdrop: "mica",
          tintAlpha: 0.3,
          dark: document.documentElement.getAttribute("data-theme") === "dark",
          rounded: true,
          windows: "10.0.22631",
          windowsName: "Windows 11 (build 22631)",
          dpi: window.devicePixelRatio || 1,
          dev: true,
          devUrl: "mock",
          title: "baicodex",
        };
      case "snapshot":
        return {
          session: "s-1",
          title: "梳理 core 分层",
          busy: this.busy,
          status: this.busy ? "正在生成" : "",
          waiting: this.pending.kind,
          stream: this.stream,
          reason: this.reason,
          running: [],
          error: null,
          mode: this.agentMode,
          work_mode: "normal",
          model: this.providerModel,
          tokens: 128_400,
          cost: "¥1.87",
        } satisfies Snapshot;
      case "messages":
        return this.messages;
      case "sessions":
        return SESSIONS;
      case "pending":
        return this.pending;
      case "tools":
        return TOOLS;
      case "usage":
        return {
          calls: 312,
          prompt: 96_400,
          cached: 41_200,
          completion: 32_000,
          cost: 1.87,
          money: "¥1.87",
        };
      case "config_get":
        return {
          agent: { mode: this.agentMode, work_mode: "normal" },
          provider: {
            id: this.providerId,
            model: this.providerModel,
            base_url: "https://api.deepseek.com/v1",
            auth: "bearer",
            // 与真内核 `config_get` 的 provider.list 同形状，模型菜单吃这份数据
            list: [
              {
                id: "openai",
                name: "OpenAI",
                base_url: "https://api.openai.com/v1",
                auth: "bearer",
                model: "gpt-6-sol",
              },
              {
                id: "deepseek",
                name: "DeepSeek",
                base_url: "https://api.deepseek.com/v1",
                auth: "bearer",
                model: "deepseek-chat",
              },
              {
                id: "moonshot",
                name: "月之暗面",
                base_url: "https://api.moonshot.cn/v1",
                auth: "bearer",
                model: "moonshot-v1-8k",
              },
              {
                id: "anthropic",
                name: "Anthropic（兼容网关）",
                base_url: "https://api.anthropic.com/v1",
                auth: "bearer",
                model: "claude-3-5-sonnet",
              },
              {
                id: "ollama",
                name: "Ollama（本地）",
                base_url: "http://127.0.0.1:11434/v1",
                auth: "bearer",
                model: "qwen2.5:7b",
              },
            ],
          },
        };
      case "config_patch": {
        const patch = (args.patch ?? {}) as {
          agent?: { mode?: string };
          provider?: { id?: string; model?: string };
        };
        if (typeof patch.agent?.mode === "string" && isMode(patch.agent.mode)) this.agentMode = patch.agent.mode;
        if (typeof patch.provider?.id === "string") this.providerId = patch.provider.id;
        if (typeof patch.provider?.model === "string") this.providerModel = patch.provider.model;
        // 回一份完整配置，界面 reload 后拿到的就是新值
        return this.handle("config_get", {});
      }
      case "skills":
        return { docs: ["commit", "review"], packs: ["core", "web"] };
      case "send":
        return this.doSend(String(args.text ?? ""));
      case "stop":
        this.busy = false;
        this.emit({ type: "busy", busy: false });
        return null;
      case "confirm_tool": {
        const allow = args.allow === true;
        this.pending = { kind: "", tool: null, questions: [], plan: "" };
        void this.finishToolDemo(allow);
        return null;
      }
      case "regenerate":
        return { text: "", rounds: 1, stopped: false };
      case "new_session": {
        // 新会话真的清空：否则界面上点「新对话」后旧消息还在
        this.messages = [];
        this.stream = "";
        this.reason = "";
        this.pending = { kind: "", tool: null, questions: [], plan: "" };
        return true;
      }
      case "open_session":
      case "delete_session":
      case "rename_session":
        return true;
      case "star_message":
        return true;
      case "export_md":
        return "# 梳理 core 分层\n\n……";
      default:
        return null;
    }
  }
  private async doSend(text: string): Promise<unknown> {
    if (this.busy) throw new Error("正忙");
    this.busy = true;
    const uid = ++this.seq;
    this.emit({ type: "busy", busy: true });
    this.emit({ type: "user_message", id: uid, text, images: [] });
    this.emit({ type: "status", text: "正在思考" });
    await sleep(240);

    // 提到「工具」就演一遍真实的多轮工具调用：低风险直跑 + 高风险等确认
    if (/工具|调用|目录|执行|shell/i.test(text)) return this.doToolDemo(uid);


    const reason = "先确认他问的是哪一层：看关键词是「为什么」，不是「是什么」，所以要给取舍，不是列目录。";
    this.reason = "";
    for (const c of chunk(reason, 6)) {
      this.reason += c;
      this.emit({ type: "delta", content: "", reason: c });
      await sleep(12);
    }

    const reply =
      "因为这两层是**唯一**会因为平台不同而改写的部分。\n\n" +
      "把它们单独拿出来以后，`core` 里剩下的代码在所有端上是同一份：Android 上跑的是它，Windows 上跑的也是它，单元测试里还是它。\n\n" +
      "判断某个文件该放哪一层，我只问一句：**换个平台要不要重写？** 要，就进 `exec` / `net`；不要，才配进 `core`。";
    this.stream = "";
    for (const c of chunk(reply, 3)) {
      this.stream += c;
      this.emit({ type: "delta", content: c, reason: "" });
      await sleep(16);
    }

    const aid = ++this.seq;
    const m = msg({
      id: aid,
      role: "assistant",
      content: reply,
      reasoning: this.reason,
      parent_id: 999,
      meta: { at: Date.now(), ms: 2400, first_ms: 240, prompt_tokens: 2010, completion_tokens: 176, cached_tokens: 1902, provider: "anthropic", model: "claude-sonnet-4" },
    });
    this.emit({ type: "message", message: m });
    this.stream = "";
    this.reason = "";
    this.busy = false;
    this.emit({ type: "busy", busy: false });
    return { text: reply, rounds: 1, tools: 0, stopped: false, prompt_tokens: 2010, completion_tokens: 176 };
  }

  /**工具演示：低风险直跑、高风险停下来等确认，确认后才补结果与收尾话*/
  private async doToolDemo(uid: number): Promise<unknown> {
    const reason = "他问目录，先真读一遍，不要凭记忆答；顺手跑一次构建，正好演示高风险工具怎么拦。";
    this.reason = "";
    for (const c of chunk(reason, 8)) {
      this.reason += c;
      this.emit({ type: "delta", content: "", reason: c });
      await sleep(10);
    }

    const callList = { id: "call_list", name: "fs_list", arguments: '{"path":"src/core","limit":40}' };
    const callShell = { id: "call_shell", name: "shell", arguments: '{"cmd":"cargo build --bin baicodex","cwd":"/root/bc"}' };
    this.emit({
      type: "message",
      message: msg({
        id: ++this.seq,
        role: "assistant",
        content: "",
        reasoning: this.reason,
        parent_id: uid,
        tool_calls: [callList, callShell],
        meta: { at: Date.now(), ms: 980, first_ms: 240, prompt_tokens: 1980, completion_tokens: 74, cached_tokens: 1800, provider: "anthropic", model: "claude-sonnet-4" },
      }),
    });
    this.reason = "";

    const s1 = ++this.seq;
    this.emit({ type: "tool_start", seq: s1, name: "fs_list", args: callList.arguments, from_sub: false });
    this.emit({ type: "status", text: "fs_list 正在执行" });
    await sleep(760);
    this.emit({ type: "tool_end", seq: s1 });
    this.emit({
      type: "message",
      message: msg({
        id: ++this.seq,
        role: "tool",
        content: "base/\nsecurity/\nfeature/\nllm/\nmod.rs",
        tool_call_id: callList.id,
        tool_name: "fs_list",
        meta: { at: Date.now(), ms: 24, first_ms: 0, prompt_tokens: 0, completion_tokens: 0, cached_tokens: 0, provider: "", model: "" },
      }),
    });
    await sleep(320);

    // shell 是高风险：不自己跑，转成待确认卡，等 confirm_tool
    this.pending = {
      kind: "confirm",
      tool: { name: "shell", args: callShell.arguments, route: "sandbox", high_risk: true },
      questions: [],
      plan: "",
    };
    this.emit({ type: "confirm_request" });
    this.busy = false;
    this.emit({ type: "busy", busy: false });
    this.emit({ type: "status", text: "等待确认：shell" });
    return { text: "", rounds: 1, tools: 1, stopped: false, prompt_tokens: 1980, completion_tokens: 74 };
  }

  /**确认后用真实结果收尾：拒绝也回一条 tool 错误，让界面看到「没执行」*/
  private async finishToolDemo(allow: boolean): Promise<void> {
    const seq = ++this.seq;
    this.emit({ type: "tool_start", seq, name: "shell", args: '{"cmd":"cargo build --bin baicodex","cwd":"/root/bc"}', from_sub: false });
    if (allow) {
      this.emit({ type: "status", text: "shell 正在执行" });
      await sleep(1400);
      this.emit({ type: "tool_end", seq });
      this.emit({
        type: "message",
        message: msg({
          id: ++this.seq,
          role: "tool",
          content: "   Compiling baicodex v0.1.0 (/root/bc)\n    Finished dev [unoptimized] target(s) in 42.18s",
          tool_call_id: "call_shell",
          tool_name: "shell",
          meta: { at: Date.now(), ms: 42180, first_ms: 0, prompt_tokens: 0, completion_tokens: 0, cached_tokens: 0, provider: "", model: "" },
        }),
      });
    } else {
      this.emit({ type: "tool_end", seq });
      this.emit({
        type: "message",
        message: msg({
          id: ++this.seq,
          role: "tool",
          content: "用户拒绝了这次调用，未执行",
          tool_call_id: "call_shell",
          tool_name: "shell",
          is_error: true,
          meta: { at: Date.now(), ms: 0, first_ms: 0, prompt_tokens: 0, completion_tokens: 0, cached_tokens: 0, provider: "", model: "" },
        }),
      });
    }

    const reply = allow
      ? "两个都跑完了。\n\n`src/core` 下面五层：`base` / `security` / `feature` / `llm`，加一个只放声明的 `mod.rs`。构建 42 秒通过。\n\n需要我把 `llm` 这一层的职责再拆细吗？"
      : "构建那条你已经拒了，所以只拿到目录结果。\n\n`src/core` 下五层：`base` / `security` / `feature` / `llm`，加 `mod.rs`。要跑构建的话下次点「允许」，或者设成「本次会话都允许」。";
    this.stream = "";
    for (const c of chunk(reply, 3)) {
      this.stream += c;
      this.emit({ type: "delta", content: c, reason: "" });
      await sleep(14);
    }
    this.emit({
      type: "message",
      message: msg({
        id: ++this.seq,
        role: "assistant",
        content: reply,
        parent_id: 0,
        meta: { at: Date.now(), ms: 2600, first_ms: 210, prompt_tokens: 2240, completion_tokens: 168, cached_tokens: 1980, provider: "anthropic", model: "claude-sonnet-4" },
      }),
    });
    this.stream = "";
    this.busy = false;
    this.emit({ type: "busy", busy: false });
    this.emit({ type: "status", text: "" });
  }
}

/** 按"字"切，模拟 token 流。中文按字符切最自然。 */
function chunk(s: string, n: number): string[] {
  const out: string[] = [];
  for (let i = 0; i < s.length; i += n) out.push(s.slice(i, i + n));
  return out;
}