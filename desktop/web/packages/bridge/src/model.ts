/**
 * 与内核对齐的数据形状。
 *
 * 每一个字段名都能在 Rust 侧找到出处：
 *
 * | 这里 | Rust |
 * |---|---|
 * | `ChatMsg` | `core::feature::model::message::ChatMsg` |
 * | `SessionRow` | `core::feature::model::session::SessionRow` |
 * | `RunningTool` | `core::feature::app::state::RunningTool` |
 * | `Pending` | `ffi::dispatch` 的 `pending` 分支 |
 * | 事件 | `core::feature::app::event::AppEvent`（`events.gen.ts` 自动生成） |
 *
 * 类型全部按 serde 的默认序列化写：Rust 的 `Option<T>` 是 `T | null`，
 * `i64`/`u32` 是 `number`。改 Rust 结构体时这里要一起改——没有代码生成兜底，
 * 因为 serde 的结构体布局在编译期拿不到。
 */

export type Role = "system" | "user" | "assistant" | "tool" | "summary";

/** 实际走通的执行通道。工具结果里带，界面上标出来。 */
export type Route = "sandbox" | "all_files" | "zero_width" | "shizuku" | "root";

export type AgentMode = "plan" | "standard" | "code" | "minimal" | "creator";

export type WorkMode = "normal" | "shizuku" | "root";

export interface ToolCall {
  id: string;
  name: string;
  /** 模型给的原始字符串，**可能不是合法 JSON**。 */
  arguments: string;
}

export interface MsgMeta {
  at: number;
  ms: number;
  /** 首字延迟。0 表示没记录。 */
  first_ms: number;
  prompt_tokens: number;
  completion_tokens: number;
  cached_tokens: number;
  provider: string;
  model: string;
}

export interface ChatMsg {
  id: number;
  role: Role;
  content: string;
  tool_calls: ToolCall[];
  tool_call_id: string | null;
  tool_name: string | null;
  is_error: boolean;
  reasoning: string;
  images: string[];
  files: string[];
  meta: MsgMeta | null;
  starred: boolean;
  /** 0 表示挂在会话根上。 */
  parent_id: number;
  /** 同一父节点下的第几个分支，从 1 开始。 */
  b_idx: number;
  b_total: number;
}

export interface SessionRow {
  id: string;
  title: string;
  category: string;
  descr: string;
  updated_at: number;
  preview: string;
  count: number;
  pinned: boolean;
  workspace: string;
  persona: string;
}

export interface RunningTool {
  seq: number;
  name: string;
  args: string;
  from_sub: boolean;
}

/** 一次全量状态。`snapshot` op 的返回。 */
export interface Snapshot {
  session: string | null;
  title: string;
  busy: boolean;
  status: string;
  /** 在等用户做什么。空串表示没在等。 */
  waiting: "" | "confirm" | "ask" | "plan";
  /** 正在流式输出的正文。 */
  stream: string;
  /** 正在流式输出的思考。 */
  reason: string;
  running: RunningTool[];
  error: string | null;
  mode: AgentMode;
  work_mode: WorkMode;
  model: string;
  tokens: number;
  /** 内核给的是已经格式化好的人读字符串（如 "¥1.87"），不是数字 */
  cost: string;
}

export interface PendingTool {
  name: string;
  args: string;
  route: Route;
  high_risk: boolean;
}

export interface AskOption {
  label: string;
  desc: string;
}

export interface AskQuestion {
  id: string;
  question: string;
  multi: boolean;
  options: AskOption[];
}

export interface Pending {
  kind: "" | "confirm" | "ask" | "plan";
  tool: PendingTool | null;
  questions: AskQuestion[];
  plan: string;
}

export interface ToolInfo {
  name: string;
  label: string;
  risk: "low" | "medium" | "high";
  allowed: boolean;
  high_risk: boolean;
}

export interface UsageReport {
  calls: number;
  /** 输入 token（内核字段名就是 prompt，没有 _tokens 后缀） */
  prompt: number;
  /** 命中缓存的输入 token，省钱的部分 */
  cached: number;
  /** 输出 token */
  completion: number;
  /** 折算成钱，数字 */
  cost: number;
  /** 已格式化的人读金额，直接显示 */
  money: string;
}

export interface SendResult {
  text: string;
  rounds: number;
  tools: number;
  stopped: boolean;
  prompt_tokens: number;
  completion_tokens: number;
}

/** 宿主信息。**只有桌面端有**，浏览器预览时是 mock 值。 */
export interface HostInfo {
  kind: "host_info";
  version: string;
  os: string;
  platform: string;
  arch: string;
  /** 系统底板类型：mica / mica-alt / acrylic / blur / solid。 */
  backdrop: string;
  /** 前端该用的玻璃底色不透明度。 */
  tintAlpha: number;
  dark: boolean;
  rounded: boolean;
  windows: string;
  windowsName: string;
  dpi: number;
  dev: boolean;
  devUrl: string | null;
  title: string;
  width?: number;
  height?: number;
}