/**
 * 传输层的类型契约。
 *
 * 单独一个文件是为了打破循环：`transport.ts` 要引 `mock.ts` 的假实现，而
 * `mock.ts` 又要实现 `Transport` 接口。把接口抽出来，两边都只依赖它。
 *
 * TypeScript 是结构化类型，其实不抽也能过——但那会让 `import` 图变成一团，
 * 打包器的循环检测也会报警。
 */

import type { KernelEvent } from "./events.gen";

/** 宿主推的、不属于内核事件的那几条。 */
export type HostEvent =
  | { type: "host_theme"; dark: boolean }
  | { type: "host_info"; [k: string]: unknown };

export type AnyEvent = KernelEvent | HostEvent;

export interface Transport {
  readonly kind: "webview" | "mock" | "http";
  invoke<T = unknown>(op: string, args?: Record<string, unknown>): Promise<T>;
  onEvent(fn: (ev: AnyEvent) => void): () => void;
  dispose(): void;
}
