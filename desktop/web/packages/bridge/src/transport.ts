/**
 * 传输层。
 *
 * 界面只跟 [`Transport`] 说话，不关心背后是 WebView2 还是假数据。三个实现：
 *
 * | 实现 | 用在哪 |
 * |---|---|
 * | `WebViewTransport` | 桌面端真实运行：`chrome.webview.postMessage` |
 * | `MockTransport` | 浏览器里开发/截图：内存假数据，会"演"流式输出 |
 * | `HttpTransport` | 调试：连一个把 `ffi::call` 暴露成 HTTP 的小服务 |
 *
 * ## 为什么请求要带 id
 *
 * `PostWebMessageAsJson` 是单向的，宿主不会把 return 值送回来。所以每条请求打
 * 一个自增 id，宿主原样带回，前端用 Map 把 Promise 接回去。超时没回的，直接
 * reject——不能让界面永远转圈。
 *
 * ## 为什么事件和回复走同一条通道
 *
 * 宿主只有一个 outbox。按 `id` 有没有值来区分：有 id 是回复，没 id 是事件。
 * 两条通道会让时序乱掉（先发的事件可能后到）。
 */

import { MockTransport } from "./mock";
import type { AnyEvent, Transport } from "./types";

export type { AnyEvent, HostEvent, Transport } from "./types";

interface Inflight {
  resolve: (v: unknown) => void;
  reject: (e: Error) => void;
  timer: number;
}

/** 默认超时。`send` 会跑到模型结束，给足十分钟。 */
const TIMEOUT_MS = 30_000;
const LONG_OPS = new Set(["send", "regenerate", "edit_resend", "compact", "backup"]);

// —— WebView2 ——

interface ChromeWebView {
  postMessage(msg: unknown): void;
  addEventListener(type: "message", cb: (e: { data: unknown }) => void): void;
  removeEventListener(type: "message", cb: (e: { data: unknown }) => void): void;
}

declare global {
  interface Window {
    chrome?: { webview?: ChromeWebView };
  }
}

export function hasWebView(): boolean {
  return typeof window !== "undefined" && !!window.chrome?.webview;
}

export class WebViewTransport implements Transport {
  readonly kind = "webview" as const;
  private seq = 0;
  private pending = new Map<number, Inflight>();
  private listeners = new Set<(ev: AnyEvent) => void>();
  private bridge: ChromeWebView;

  constructor() {
    const b = window.chrome?.webview;
    if (!b) throw new Error("不在 WebView2 里");
    this.bridge = b;
    b.addEventListener("message", this.onMessage);
  }

  invoke<T>(op: string, args: Record<string, unknown> = {}): Promise<T> {
    const id = ++this.seq;
    return new Promise<T>((resolve, reject) => {
      const ms = LONG_OPS.has(op) ? 10 * 60_000 : TIMEOUT_MS;
      const timer = window.setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`${op} 超时（${Math.round(ms / 1000)}s）`));
      }, ms);
      this.pending.set(id, { resolve: resolve as (v: unknown) => void, reject, timer });
      this.bridge.postMessage({ id, op, args });
    });
  }

  onEvent(fn: (ev: AnyEvent) => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  dispose(): void {
    this.bridge.removeEventListener("message", this.onMessage);
    for (const [, p] of this.pending) {
      clearTimeout(p.timer);
      p.reject(new Error("transport closed"));
    }
    this.pending.clear();
    this.listeners.clear();
  }

  private onMessage = (e: { data: unknown }): void => {
    const data = typeof e.data === "string" ? safeParse(e.data) : e.data;
    if (!data || typeof data !== "object") return;
    const obj = data as Record<string, unknown>;

    // 有 id → 回复；没 id → 事件
    if (typeof obj.id === "number") {
      const p = this.pending.get(obj.id);
      if (!p) return;
      this.pending.delete(obj.id);
      clearTimeout(p.timer);
      if (obj.ok === true) {
        p.resolve(obj.data);
      } else {
        const err = obj.error as { code?: string; message?: string } | undefined;
        p.reject(new Error(err?.message || "调用失败"));
      }
      return;
    }
    for (const fn of this.listeners) fn(obj as unknown as AnyEvent);
  };
}

function safeParse(s: string): unknown {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

// —— HTTP（调试用） ——

export class HttpTransport implements Transport {
  readonly kind = "http" as const;
  private listeners = new Set<(ev: AnyEvent) => void>();
  private es: EventSource | null = null;

  constructor(private base = "") {
    try {
      this.es = new EventSource(`${base}/events`);
      this.es.onmessage = (m) => {
        const ev = safeParse(m.data);
        if (ev) for (const fn of this.listeners) fn(ev as AnyEvent);
      };
    } catch {
      this.es = null;
    }
  }

  async invoke<T>(op: string, args: Record<string, unknown> = {}): Promise<T> {
    const r = await fetch(`${this.base}/call`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ op, args }),
    });
    const j = (await r.json()) as { ok: boolean; data?: T; error?: { message?: string } };
    if (!j.ok) throw new Error(j.error?.message || "调用失败");
    return j.data as T;
  }

  onEvent(fn: (ev: AnyEvent) => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  dispose(): void {
    this.es?.close();
    this.listeners.clear();
  }
}

// —— 选择 ——

let singleton: Transport | null = null;

/**
 * 拿当前环境的传输层。
 *
 * 顺序：WebView2 → URL 上带 `?http=` 的调试后端 → mock。
 */
export function createTransport(): Transport {
  if (singleton) return singleton;
  if (hasWebView()) {
    singleton = new WebViewTransport();
    return singleton;
  }
  const q = new URLSearchParams(location.search);
  const http = q.get("http");
  if (http) {
    singleton = new HttpTransport(http.replace(/\/$/, ""));
    return singleton;
  }
  // 动态引用：mock 只在浏览器里用，打进生产包也就几十 KB
  singleton = new MockTransport();
  return singleton;
}

/** 换一个（测试用）。 */
export function setTransport(t: Transport | null): void {
  singleton?.dispose();
  singleton = t;
}
