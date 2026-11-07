// 由 `cargo run -p baicodex-proto` 生成，不要手改。
// 契约在 src/ffi/spec.rs；改完重新生成，`-- check` 会挡住忘记生成的情况。

import type { ChatMsg, SessionRow } from "./model";

/// 内核推给界面的事件。按 `type` 判别。
export type KernelEvent =
  | { type: "status"; text: string }
  | { type: "delta"; content: string; reason: string }
  | { type: "user_message"; id: number; text: string; images: string[] }
  | { type: "message"; message: ChatMsg }
  | { type: "tool_start"; seq: number; name: string; args: string; from_sub: boolean }
  | { type: "tool_end"; seq: number }
  | { type: "busy"; busy: boolean }
  | { type: "sessions"; list: SessionRow[] }
  | { type: "notice"; text: string }
  | { type: "confirm_request" }
  | { type: "ask_request" }
  | { type: "plan_request" }
  | { type: "usage"; prompt: number; completion: number }
  | { type: "error"; code: string; message: string }
  | { type: "quit" }
;

export type EventType = KernelEvent["type"];

/// 事件说明，给调试面板用。
export const EVENT_DOC: Readonly<Record<EventType, string>> = {
  status: "状态栏一句话",
  delta: "流式增量，正文与思考分开",
  user_message: "用户消息已落库",
  message: "一条消息已落库，按 id 去重",
  tool_start: "工具开始",
  tool_end: "工具结束",
  busy: "忙闲变化",
  sessions: "会话列表变化",
  notice: "一次性提示",
  confirm_request: "需要确认工具调用",
  ask_request: "需要用户作答",
  plan_request: "计划待确认",
  usage: "用量变化",
  error: "出错了，message 已转成人话",
  quit: "退出",
};

/// 窄化：`if (isEvent(ev, "delta"))` 后 ev 就带上 content/reason。
export function isEvent<T extends EventType>(
  ev: KernelEvent,
  type: T,
): ev is Extract<KernelEvent, { type: T }> {
  return ev.type === type;
}
