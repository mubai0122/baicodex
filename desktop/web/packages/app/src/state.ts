/**
 * 应用状态。
 *
 * 一个约 150 行的 store，不引任何状态库。理由：这个界面的状态**只有一个方向**
 * ——内核推事件，界面跟着变。没有"用户操作直接改本地状态再同步回去"那种双向流，
 * 也就用不上 reducer / effect / 中间件那一套。
 *
 * 唯一的本地状态是纯 UI 的：侧栏开合、当前视图、设置页停在哪个分区。这些不进
 * 内核，也不该进。
 *
 * ## 流式文本为什么不进 messages
 *
 * 正在生成的那段字还没有 id，也不是一条已落库的消息。塞进 `messages` 会让
 * "按 id 去重""分支导航"这些逻辑全都要加一个"半成品"分支。所以它单独占两个
 * 字段（`stream` / `reason`），等 `message` 事件到了再合并。
 */

import { kernel, type AnyEvent, type ChatMsg, type ConfigDoc, type HostInfo, type Pending, type SessionRow, type Snapshot } from "@baicodex/bridge";
import { apply as applyTheme, type ThemeMode } from "@baicodex/tokens";

export type View = "chat" | "settings";
export type Section = "model" | "appearance" | "tools" | "memory" | "about";

export interface AppState {
  host: HostInfo | null;
  snapshot: Snapshot | null;
  messages: ChatMsg[];
  sessions: SessionRow[];
  pending: Pending;
  config: ConfigDoc | null;
  /** 正在流式输出的正文。 */
  stream: string;
  /** 正在流式输出的思考。 */
  reason: string;
  status: string;
  error: string | null;
  /** 侧栏是否展开。 */
  sidebar: boolean;
  view: View;
  section: Section;
  ready: boolean;
  /** 正在跑的工具，按 seq。 */
  running: Map<number, { name: string; args: string; out?: string; ok?: boolean }>;
}

const EMPTY_PENDING: Pending = { kind: "", tool: null, questions: [], plan: "" };

export class Store {
  state: AppState = {
    host: null,
    snapshot: null,
    messages: [],
    sessions: [],
    pending: EMPTY_PENDING,
    config: null,
    stream: "",
    reason: "",
    status: "",
    error: null,
    sidebar: window.innerWidth > 900,
    view: "chat",
    section: "model",
    ready: false,
    running: new Map(),
  };

  private subs = new Set<(s: AppState) => void>();
  private off: (() => void) | null = null;
  private themeOff: (() => void) | null = null;

  subscribe(fn: (s: AppState) => void): () => void {
    this.subs.add(fn);
    return () => this.subs.delete(fn);
  }

  patch(p: Partial<AppState>): void {
    this.state = { ...this.state, ...p };
    for (const fn of this.subs) fn(this.state);
  }

  /** 首次装载：把该拉的都拉一遍。 */
  async boot(): Promise<void> {
    // 宿主信息：拿不到就是浏览器预览
    let host: HostInfo | null = null;
    try {
      host = await kernel.transport.invoke<HostInfo>("__host_info");
    } catch {
      host = null;
    }

    this.applyHost(host);

    const [snapshot, messages, sessions, config, pending] = await Promise.all([
      safe(() => kernel.snapshot()),
      safe(() => kernel.messages()),
      safe(() => kernel.sessions()),
      safe(() => kernel.configGet()),
      safe(() => kernel.pending()),
    ]);

    this.patch({
      host,
      snapshot: snapshot ?? null,
      messages: messages ?? [],
      sessions: sessions ?? [],
      config: config ?? null,
      pending: pending ?? EMPTY_PENDING,
      ready: true,
      stream: snapshot?.stream ?? "",
      reason: snapshot?.reason ?? "",
    });

    this.bind();
  }

  dispose(): void {
    this.off?.();
    this.themeOff?.();
    this.subs.clear();
  }

  private applyHost(host: HostInfo | null): void {
    const dark = host?.dark ?? window.matchMedia("(prefers-color-scheme: dark)").matches;
    const saved = localStorage.getItem("bai.theme") as ThemeMode | null;
    const mode: ThemeMode = saved ?? (dark ? "dark" : "light");
    this.themeOff?.();
    this.themeOff = applyTheme(mode);
    if (host) {
      document.documentElement.dataset.backdrop = host.backdrop;
      document.documentElement.style.setProperty("--aurora-op", host.backdrop === "mica" ? "0.55" : "1");
    }
  }

  /** 切主题。写进 localStorage，下次启动还是这个。 */
  setTheme(mode: ThemeMode): void {
    localStorage.setItem("bai.theme", mode);
    this.themeOff?.();
    this.themeOff = applyTheme(mode);
    this.patch({});
  }

  private bind(): void {
    this.off?.();
    this.off = kernel.onEvent((ev) => this.onEvent(ev as AnyEvent));
  }

  private onEvent(ev: AnyEvent): void {
    switch (ev.type) {
      case "delta": {
        this.patch({
          stream: this.state.stream + (ev.content ?? ""),
          reason: this.state.reason + (ev.reason ?? ""),
        });
        break;
      }
      case "status":
        this.patch({ status: ev.text });
        break;
      case "busy": {
        const snap = this.state.snapshot;
        this.patch({
          snapshot: snap ? { ...snap, busy: ev.busy } : snap,
          // 一轮结束时清掉残留的流式缓冲
          stream: ev.busy ? this.state.stream : "",
          reason: ev.busy ? this.state.reason : "",
        });
        break;
      }
      case "user_message": {
        if (this.state.messages.some((m) => m.id === ev.id)) break;
        this.patch({
          messages: [
            ...this.state.messages,
            {
              id: ev.id,
              role: "user",
              content: ev.text,
              tool_calls: [],
              tool_call_id: null,
              tool_name: null,
              is_error: false,
              reasoning: "",
              images: ev.images ?? [],
              files: [],
              meta: null,
              starred: false,
              parent_id: 0,
              b_idx: 1,
              b_total: 1,
            },
          ],
          stream: "",
          reason: "",
        });
        break;
      }
      case "message": {
        const m = ev.message;
        const rest = this.state.messages.filter((x) => x.id !== m.id);
        this.patch({ messages: [...rest, m].sort((a, b) => a.id - b.id), stream: "", reason: "" });
        break;
      }
      case "tool_start": {
        const running = new Map(this.state.running);
        running.set(ev.seq, { name: ev.name, args: ev.args });
        this.patch({ running });
        break;
      }
      case "tool_end": {
        const running = new Map(this.state.running);
        const cur = running.get(ev.seq);
        if (cur) running.set(ev.seq, { ...cur, ok: true });
        this.patch({ running });
        break;
      }
      case "sessions":
        this.patch({ sessions: ev.list });
        break;
      case "notice":
        this.patch({ status: ev.text });
        break;
      case "error":
        this.patch({ error: ev.message });
        break;
      case "confirm_request":
      case "ask_request":
      case "plan_request":
        void this.refreshPending();
        break;
      case "usage":
        void kernel.snapshot().then((s) => this.patch({ snapshot: s })).catch(() => {});
        break;
      case "host_theme": {
        const h = this.state.host;
        if (h) this.patch({ host: { ...h, dark: ev.dark } });
        if (!localStorage.getItem("bai.theme")) this.setTheme(ev.dark ? "dark" : "light");
        break;
      }
      default:
        break;
    }
  }

  async refreshPending(): Promise<void> {
    try {
      const pend = await kernel.pending();
      const snap = this.state.snapshot;
      this.patch({
        pending: pend,
        snapshot: snap ? { ...snap, waiting: pend.kind } : snap,
      });
    } catch {
      /* 内核还没准备好，忽略 */
    }
  }

  /** 重新拉一遍消息与会话。切会话、删除后调。 */
  async reload(): Promise<void> {
    const [snapshot, messages, sessions] = await Promise.all([
      safe(() => kernel.snapshot()),
      safe(() => kernel.messages()),
      safe(() => kernel.sessions()),
    ]);
    this.patch({
      snapshot: snapshot ?? this.state.snapshot,
      messages: messages ?? this.state.messages,
      sessions: sessions ?? this.state.sessions,
      stream: "",
      reason: "",
    });
  }
}

async function safe<T>(f: () => Promise<T>): Promise<T | null> {
  try {
    return await f();
  } catch {
    return null;
  }
}
