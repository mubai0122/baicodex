/**
 * `@baicodex/bridge` —— 界面与 Rust 内核之间唯一的一层。
 *
 * 依赖方向：
 *
 * ```text
 * app ──▶ ui ──▶ liquid ──▶ tokens
 *  └────────┴────▶ bridge （只依赖类型，不依赖上面任何一层）
 * ```
 *
 * `bridge` 是唯一知道"op 叫什么、事件长什么样"的包。`ui` 和 `app` 只消费它导出的
 * 类型与方法，不自己拼 JSON。
 */

export { Kernel, kernel } from "./kernel";
export type { ConfigDoc, Profile } from "./kernel";

export { createTransport, setTransport, hasWebView, WebViewTransport, HttpTransport } from "./transport";
export { MockTransport } from "./mock";
export type { Transport, AnyEvent, HostEvent } from "./types";

export { OP_NAMES, OPS, accepts } from "./ops.gen";
export type { Op, OpMeta, RetKind } from "./ops.gen";

export { isEvent, EVENT_DOC } from "./events.gen";
export type { KernelEvent, EventType } from "./events.gen";

export type {
  AgentMode,
  AskOption,
  AskQuestion,
  ChatMsg,
  HostInfo,
  MsgMeta,
  Pending,
  PendingTool,
  Role,
  Route,
  RunningTool,
  SendResult,
  SessionRow,
  Snapshot,
  ToolCall,
  ToolInfo,
  UsageReport,
  WorkMode,
} from "./model";
