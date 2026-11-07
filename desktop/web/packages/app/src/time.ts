/**
 * 时间显示。
 *
 * 内核给的一律是**毫秒时间戳**（`core/base/clock.rs` 的约定是毫秒进库、日期字符串
 * 给人看）。这里只做格式化，不做时区——`Intl` 会按系统时区来，正好。
 */

const MIN = 60_000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;

/** 14:32 */
export function clock(ms: number): string {
  if (!ms) return "";
  const d = new Date(ms);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 10月6日 */
export function monthDay(ms: number): string {
  if (!ms) return "";
  const d = new Date(ms);
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}

/**
 * 相对时间。
 *
 * 规则跟 `clock::humanize` 对齐：
 * 一分钟内"刚刚"，一小时内给分钟，当天给时刻，昨天给"昨天 HH:MM"，
 * 一周内给星期，再早给日期。超过 30 天只说日期——"97 天前"没人算得出来。
 */
export function humanize(ms: number): string {
  if (!ms) return "";
  const now = Date.now();
  const diff = now - ms;
  if (diff < 0) return clock(ms);
  if (diff < MIN) return "刚刚";
  if (diff < HOUR) return `${Math.floor(diff / MIN)} 分钟前`;

  const d = new Date(ms);
  const today = startOfDay(new Date(now));
  const day = startOfDay(d);
  const days = Math.round((today - day) / DAY);

  if (days === 0) return clock(ms);
  if (days === 1) return `昨天 ${clock(ms)}`;
  if (days < 7) return ["周日", "周一", "周二", "周三", "周四", "周五", "周六"][d.getDay()] ?? "";
  if (days < 30) return monthDay(ms);
  return `${d.getFullYear()}年${monthDay(ms)}`;
}

/** 时长：1.2s / 340ms / 2m14s */
export function duration(ms: number): string {
  if (ms <= 0) return "";
  if (ms < 1000) return `${Math.round(ms)}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)}s`;
  const m = Math.floor(ms / 60_000);
  const s = Math.round((ms % 60_000) / 1000);
  return `${m}m${s}s`;
}

/** 数字缩写：1.2k / 3.4M。内核缺字段时会传 undefined，先兜成 0，别让整页崩 */
export function compact(n: number | undefined): string {
  const v = Number.isFinite(n) ? (n as number) : 0;
  if (v < 1000) return String(v);
  if (v < 1_000_000) return `${(v / 1000).toFixed(v < 10_000 ? 1 : 0)}k`;
  return `${(v / 1_000_000).toFixed(1)}M`;
}

/** 金额：¥1.87。内核 `cost` / `money` 两种形状都接：数字自己格式化，字符串原样返回 */
export function money(n: number | string | undefined): string {
  if (typeof n === "string") return n || "¥0";
  if (!n || !Number.isFinite(n)) return "¥0";
  if (n < 0.01) return "<¥0.01";
  return `¥${n.toFixed(2)}`;
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** 会话分组用的天差。 */
export function dayDiff(ms: number): number {
  const now = Date.now();
  return Math.round((startOfDay(new Date(now)) - startOfDay(new Date(ms))) / DAY);
}