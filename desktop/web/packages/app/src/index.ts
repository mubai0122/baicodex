/**
 * `@baicodex/app` —— 业务界面。
 *
 * 只有这一层知道"会话""消息""工具调用"是什么。下面的 `ui` / `liquid` / `tokens`
 * 拿到任何一个别的产品里都能直接用。
 *
 * 依赖方向：
 *
 * ```text
 * app ──▶ ui ──▶ liquid ──▶ tokens
 *  └──────────────┴──────────▶ bridge（只拿类型与调用）
 * ```
 */

export { createApp } from "./app";
export type { AppHandle } from "./app";
export { Store } from "./state";
export type { AppState, Section, View } from "./state";
