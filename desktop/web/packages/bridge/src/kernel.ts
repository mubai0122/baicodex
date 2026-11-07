/**
 * 类型化的内核调用。
 *
 * 每个方法就是一条 `{op, args}`。不在这里做任何业务判断——参数合不合法由 Rust
 * 侧校验，错误码原样抛给调用方。这一层只提供两个东西：**方法名补全**和**返回类型**。
 *
 * 90 个 op 一个不少。少写一个的后果是"要用的时候发现没有，于是绕过 Kernel 直接
 * postMessage"——一次两次之后，协议就散在十几个文件里了。
 *
 * op 名单来自 `ops.gen.ts`（Rust 的 `ffi::spec::OPS` 生成）。改 Rust 那边，
 * 跑 `cargo run -p baicodex-proto` 就同步了。
 */

import type { KernelEvent } from "./events.gen";
import { isEvent } from "./events.gen";
import type {
  AskQuestion,
  AgentMode,
  ChatMsg,
  Pending,
  SendResult,
  SessionRow,
  Snapshot,
  ToolInfo,
  UsageReport,
  WorkMode,
} from "./model";
import { createTransport, type AnyEvent, type Transport } from "./transport";

export interface ConfigDoc {
  agent: {
    mode: AgentMode;
    work_mode: WorkMode;
    system_prompt: string;
    max_rounds: number;
    temperature: number;
  };
  provider: {
    model: string;
    profile_id?: string;
    profiles?: Profile[];
    /** 下面几项是内核 `config_get` 的真实返回，切模型要用 */
    id?: string;
    base_url?: string;
    auth?: string;
    no_stream?: boolean;
    key_slots?: number;
    /** 内置提供商预设表（含默认模型与能力），模型切换菜单的数据源 */
    list?: PresetProvider[];
  };
  tool: {
    shell_timeout_s: number;
    auto_allow: string[];
    deny: string[];
  };
  memory: { max_items: number; recall_top: number };
  ui: Record<string, unknown>;
  [k: string]: unknown;
}

export interface Profile {
  id: string;
  name: string;
  provider: string;
  model: string;
  base_url: string;
  api_key_ref: string;
  [k: string]: unknown;
}

/** 内置提供商预设。`config_get` 的 `provider.list` 里的一项。 */
export interface PresetProvider {
  id: string;
  name: string;
  base_url: string;
  model: string;
  auth?: string;
  custom?: boolean;
  caps?: {
    vision: boolean;
    tools: boolean;
    reasoning: boolean;
    context: number;
    reasoning_model: boolean;
  };
  [k: string]: unknown;
}

export class Kernel {
  constructor(private readonly t: Transport = createTransport()) {}

  /** 换传输层（测试用）。 */
  get transport(): Transport {
    return this.t;
  }

  /** 订阅事件。返回取消函数。 */
  onEvent(fn: (ev: AnyEvent) => void): () => void {
    return this.t.onEvent(fn);
  }

  /** 只要某一种事件，其余过滤掉。 */
  on<T extends KernelEvent["type"]>(
    type: T,
    fn: (ev: Extract<KernelEvent, { type: T }>) => void,
  ): () => void {
    return this.t.onEvent((ev) => {
      if (isEvent(ev as KernelEvent, type)) fn(ev as Extract<KernelEvent, { type: T }>);
    });
  }

  dispose(): void {
    this.t.dispose();
  }

  private call<T>(op: string, args: Record<string, unknown> = {}): Promise<T> {
    return this.t.invoke<T>(op, args);
  }

  // —— 生命周期 ——

  boot(base = ""): Promise<{ version: string; base: string }> {
    return this.call("boot", { base });
  }

  shutdown(): Promise<null> {
    return this.call("shutdown");
  }

  version(): Promise<{ version: string; arch: string; os: string; js: string }> {
    return this.call("version");
  }

  // —— 对话 ——

  send(text: string, images: string[] = [], files: string[] = []): Promise<SendResult> {
    return this.call("send", { text, images, files });
  }

  regenerate(): Promise<{ text: string; rounds: number; stopped: boolean }> {
    return this.call("regenerate");
  }

  stop(): Promise<null> {
    return this.call("stop");
  }

  snapshot(): Promise<Snapshot> {
    return this.call("snapshot");
  }

  messages(): Promise<ChatMsg[]> {
    return this.call("messages");
  }

  sessions(): Promise<SessionRow[]> {
    return this.call("sessions");
  }

  newSession(): Promise<null> {
    return this.call("new_session");
  }

  openSession(id: string): Promise<null> {
    return this.call("open_session", { id });
  }

  deleteSession(id: string): Promise<boolean> {
    return this.call("delete_session", { id });
  }

  renameSession(id: string, title: string): Promise<boolean> {
    return this.call("rename_session", { id, title });
  }

  searchSessions(q: string): Promise<string[]> {
    return this.call("search_sessions", { q });
  }

  ensureSession(): Promise<null> {
    return this.call("ensure_session");
  }

  clearCurrent(): Promise<null> {
    return this.call("clear_current");
  }

  compact(): Promise<{ saved: number; summary: string }> {
    return this.call("compact");
  }

  exportMarkdown(): Promise<string> {
    return this.call("export_md");
  }

  // —— 消息级 ——

  pinSession(id: string, on: boolean): Promise<boolean> {
    return this.call("pin_session", { id, on });
  }

  archiveSession(id: string, on: boolean): Promise<boolean> {
    return this.call("archive_session", { id, on });
  }

  starMessage(id: number, on: boolean): Promise<boolean> {
    return this.call("star_message", { id, on });
  }

  deleteMessage(id: number): Promise<boolean> {
    return this.call("delete_message", { id });
  }

  editMessage(id: number, text: string): Promise<boolean> {
    return this.call("edit_message", { id, text });
  }

  editResend(id: number, text: string, images: string[] = [], files: string[] = []): Promise<SendResult> {
    return this.call("edit_resend", { id, text, images, files });
  }

  sessionStats(id: string): Promise<UsageReport> {
    return this.call("session_stats", { id });
  }

  setSessionPersona(id: string, persona: string): Promise<boolean> {
    return this.call("set_session_persona", { id, persona });
  }

  setSessionWorkspace(id: string, path: string): Promise<boolean> {
    return this.call("set_session_workspace", { id, path });
  }

  siblings(id: number): Promise<{ list: ChatMsg[]; idx: number; total: number }> {
    return this.call("siblings", { id });
  }

  switchBranch(id: number): Promise<null> {
    return this.call("switch_branch", { id });
  }

  starred(limit = 50): Promise<ChatMsg[]> {
    return this.call("starred", { limit });
  }

  // —— 配置 ——

  configGet(): Promise<ConfigDoc> {
    return this.call("config_get");
  }

  configPatch(patch: Record<string, unknown>): Promise<ConfigDoc> {
    return this.call("config_patch", { patch });
  }

  configSave(): Promise<null> {
    return this.call("config_save");
  }

  systemPrompt(): Promise<string> {
    return this.call("system_prompt");
  }

  setSystemPrompt(text: string): Promise<null> {
    return this.call("set_system_prompt", { text });
  }

  // —— 工具 ——

  tools(): Promise<ToolInfo[]> {
    return this.call("tools");
  }

  toolCall(name: string, args: Record<string, unknown> = {}): Promise<{ ok: boolean; text: string; route: string; meta: unknown }> {
    return this.call("tool_call", { name, args });
  }

  confirmTool(allow: boolean, permanent = false): Promise<null> {
    return this.call("confirm_tool", { allow, permanent });
  }

  answerAsk(answers: string[]): Promise<null> {
    return this.call("answer_ask", { answers });
  }

  answerPlan(verdict: "confirm" | "revise" | "dismiss", note = ""): Promise<null> {
    return this.call("answer_plan", { verdict, note });
  }

  pending(): Promise<Pending> {
    return this.call("pending");
  }

  // —— 记忆 ——

  memoryList(): Promise<{ id: number; text: string; at: number }[]> {
    return this.call("memory_list");
  }

  memorySave(text: string): Promise<{ id: number }> {
    return this.call("memory_save", { text });
  }

  memoryDelete(id: number): Promise<boolean> {
    return this.call("memory_delete", { id });
  }

  memorySearch(q: string): Promise<{ id: number; text: string; at: number }[]> {
    return this.call("memory_search", { q });
  }

  // —— 技能 / MCP / 工作流 ——

  skills(): Promise<{ docs: string[]; packs: string[] }> {
    return this.call("skills");
  }

  skillGet(name: string): Promise<{ name: string; body: string }> {
    return this.call("skill_get", { name });
  }

  skillDelete(name: string): Promise<boolean> {
    return this.call("skill_delete", { name });
  }

  packList(): Promise<{ id: string; name: string; on: boolean; count: number }[]> {
    return this.call("pack_list");
  }

  packToggle(id: string, on: boolean): Promise<boolean> {
    return this.call("pack_toggle", { id, on });
  }

  mcpList(): Promise<{ id: string; name: string; url: string; on: boolean; tools: number }[]> {
    return this.call("mcp_list");
  }

  mcpAdd(name: string, url: string, token = "", kind = "http"): Promise<{ id: string }> {
    return this.call("mcp_add", { name, url, token, kind });
  }

  mcpRemove(id: string): Promise<boolean> {
    return this.call("mcp_remove", { id });
  }

  mcpToggle(id: string, on: boolean): Promise<boolean> {
    return this.call("mcp_toggle", { id, on });
  }

  mcpRefresh(id: string): Promise<{ tools: number }> {
    return this.call("mcp_refresh", { id });
  }

  flows(): Promise<{ id: string; name: string; steps: number }[]> {
    return this.call("flows");
  }

  flowSave(flow: Record<string, unknown>): Promise<{ id: string }> {
    return this.call("flow_save", { patch: flow });
  }

  flowDelete(id: string): Promise<boolean> {
    return this.call("flow_delete", { id });
  }

  // —— 用量 ——

  usage(): Promise<UsageReport> {
    return this.call("usage");
  }

  usageDays(): Promise<{ day: string; tokens: number; money: number }[]> {
    return this.call("usage_days");
  }

  usageSession(id: string): Promise<UsageReport> {
    return this.call("usage_session", { id });
  }

  // —— 人设 / 配置档 / 供应商 ——

  personas(): Promise<{ id: string; name: string; prompt: string }[]> {
    return this.call("personas");
  }

  personaSave(p: Record<string, unknown>): Promise<string> {
    return this.call("persona_save", { patch: p });
  }

  personaDelete(id: string): Promise<boolean> {
    return this.call("persona_delete", { id });
  }

  profiles(): Promise<{ list: Profile[]; current: string }> {
    return this.call("profiles");
  }

  profileSave(p: Record<string, unknown>): Promise<string> {
    return this.call("profile_save", { patch: p });
  }

  profileUse(id: string): Promise<ConfigDoc> {
    return this.call("profile_use", { id });
  }

  profileDelete(id: string): Promise<boolean> {
    return this.call("profile_delete", { id });
  }

  providers(): Promise<{ id: string; name: string; kind: string; models: string[] }[]> {
    return this.call("providers");
  }

  providerSave(p: Record<string, unknown>): Promise<string> {
    return this.call("provider_save", { patch: p });
  }

  providerDelete(id: string): Promise<boolean> {
    return this.call("provider_delete", { id });
  }

  // —— 工作区 / 设备 ——

  workspace(): Promise<{ path: string; exists: boolean; entries: number }> {
    return this.call("workspace");
  }

  workspaceBind(path: string): Promise<{ path: string }> {
    return this.call("workspace_bind", { path });
  }

  device(): Promise<Record<string, unknown>> {
    return this.call("device");
  }

  // —— 备份 / 凭据 ——

  backup(): Promise<{ path: string; size: number }> {
    return this.call("backup");
  }

  vaultKeys(bucket: string): Promise<string[]> {
    return this.call("vault_keys", { bucket });
  }

  vaultPut(bucket: string, key: string, value: string): Promise<boolean> {
    return this.call("vault_put", { bucket, key, value });
  }

  vaultDelete(bucket: string, key: string): Promise<boolean> {
    return this.call("vault_delete", { bucket, key });
  }

  // —— 文件 ——

  fsList(path: string, recurse = false, limit = 200): Promise<{ name: string; path: string; dir: boolean; size: number }[]> {
    return this.call("fs_list", { path, recurse, limit });
  }

  fsRead(path: string): Promise<{ text: string; size: number; truncated: boolean }> {
    return this.call("fs_read", { path });
  }

  fsWrite(path: string, content: string): Promise<{ path: string; size: number }> {
    return this.call("fs_write", { path, content });
  }

  fsDelete(path: string): Promise<boolean> {
    return this.call("fs_delete", { path });
  }

  fsInfo(path: string): Promise<{ path: string; dir: boolean; size: number; mtime: number }> {
    return this.call("fs_info", { path });
  }

  fsSearch(path: string, pattern: string, ext = "", max = 100, ignoreCase = false): Promise<{ path: string; line: number; text: string }[]> {
    return this.call("fs_search", { path, pattern, ext, max, ignore_case: ignoreCase });
  }

  // —— 系统 ——

  clipboardGet(): Promise<{ text: string }> {
    return this.call("clipboard", { action: "get" });
  }

  clipboardSet(text: string): Promise<null> {
    return this.call("clipboard", { action: "set", text });
  }

  notify(title: string, body: string): Promise<null> {
    return this.call("notify", { title, body });
  }

  apkInfo(action: string, path = "", keyword = "", limit = 20): Promise<Record<string, unknown>> {
    return this.call("apk_info", { action, path, keyword, limit });
  }

  // —— 插件 ——

  pluginList(): Promise<{ id: string; name: string; version: string }[]> {
    return this.call("plugin_list");
  }

  pluginInstall(path: string, id: string): Promise<{ files: number }> {
    return this.call("plugin_install", { path, id });
  }

  pluginRemove(id: string): Promise<boolean> {
    return this.call("plugin_remove", { id });
  }
}

/** 进程内共享一个实例。 */
export const kernel = new Kernel();

export { isEvent };
export type { AnyEvent, Transport };
export type { KernelEvent };
export type { AskQuestion };