/**
 * 图标。
 *
 * 全部内联 SVG，不引图标库。理由：一整套图标包动辄 200KB，而这里只需要 25 个；
 * 更重要的是**描边粗细要跟玻璃的视觉重量匹配**——1.6px 在 24 视口下比常见的
 * 2px 更"轻"，压得住半透明背景。
 *
 * 用 `currentColor`，颜色由 CSS 决定，不在 JS 里写死。
 */

export type IconName =
  | "send"
  | "stop"
  | "plus"
  | "search"
  | "settings"
  | "more"
  | "chevron-down"
  | "chevron-left"
  | "chevron-right"
  | "check"
  | "copy"
  | "edit"
  | "trash"
  | "star"
  | "pin"
  | "panel"
  | "sparkle"
  | "tool"
  | "alert"
  | "close"
  | "minus"
  | "square"
  | "restore"
  | "folder"
  | "image"
  | "file"
  | "refresh"
  | "branch"
  | "clock"
  | "globe"
  | "key"
  | "cpu";

const P: Record<IconName, string> = {
  send: '<path d="M21 3 10.5 13.5"/><path d="M21 3l-6.5 18-4-8-8-4L21 3z"/>',
  stop: '<rect x="7" y="7" width="10" height="10" rx="2.2" fill="currentColor" stroke="none"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.6-3.6"/>',
  settings:
    '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2.2"/><circle cx="9" cy="17" r="2.2"/>',
  more: '<circle cx="5" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.4" fill="currentColor" stroke="none"/>',
  "chevron-down": '<path d="M6 9l6 6 6-6"/>',
  "chevron-left": '<path d="M15 5l-7 7 7 7"/>',
  "chevron-right": '<path d="M9 5l7 7-7 7"/>',
  check: '<path d="M4.5 12.5l5 5L20 7"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2.5"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
  edit: '<path d="M4 20h4L20 8a2.5 2.5 0 0 0-3.5-3.5L4.5 16.5 4 20z"/>',
  trash: '<path d="M4 7h16M9.5 7V4.8h5V7M6.5 7l1 13h9l1-13"/>',
  star: '<path d="M12 3.6l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 17l-5.2 2.8 1-5.9-4.3-4.1 5.9-.8L12 3.6z"/>',
  pin: '<path d="M9 3h6l-.8 5.6L18 12H6l3.8-3.4L9 3z"/><path d="M12 12v8"/>',
  panel: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M9.5 4v16"/>',
  sparkle:
    '<path d="M12 3l1.7 5.1L19 10l-5.3 1.9L12 17l-1.7-5.1L5 10l5.3-1.9L12 3z"/><path d="M18.5 15.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7.7-2z"/>',
  tool: '<path d="M14.5 6.5a3.7 3.7 0 0 0 4.8 4.8l-8.6 8.6a2.6 2.6 0 0 1-3.7-3.7l8.6-8.6z"/><path d="M6 4.5l3 3"/>',
  alert: '<path d="M12 3.5l9 16H3l9-16z"/><path d="M12 9.5v5"/><circle cx="12" cy="17.2" r="1" fill="currentColor" stroke="none"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  minus: '<path d="M5 12h14"/>',
  square: '<rect x="5" y="5" width="14" height="14" rx="2"/>',
  restore: '<rect x="4" y="8" width="12" height="12" rx="2"/><path d="M8 8V6a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2"/>',
  folder: '<path d="M3 7.5A2.5 2.5 0 0 1 5.5 5h3.6l2 2.4h7.4A2.5 2.5 0 0 1 21 9.9v7.6a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-10z"/>',
  image: '<rect x="3" y="5" width="18" height="14" rx="2.5"/><circle cx="8.5" cy="10" r="1.8"/><path d="M21 16l-5-5-8 8"/>',
  file: '<path d="M6 3h8l4 4v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"/><path d="M14 3v4h4"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 3.5V8h-4.5"/>',
  branch: '<circle cx="6.5" cy="6" r="2.2"/><circle cx="6.5" cy="18" r="2.2"/><circle cx="17.5" cy="8.5" r="2.2"/><path d="M6.5 8.2v7.6"/><path d="M17.5 10.7v1.6a3.5 3.5 0 0 1-3.5 3.5H9"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.2v5.2l3.4 2"/>',
  globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17"/><path d="M12 3.5c2.6 3.4 2.6 13.6 0 17M12 3.5c-2.6 3.4-2.6 13.6 0 17"/>',
  key: '<circle cx="8" cy="8" r="3.6"/><path d="M10.6 10.6L19 19M16 16l2-2M18.5 18.5l2-2"/>',
  cpu: '<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M10 3v2M14 3v2M10 19v2M14 19v2M3 10h2M3 14h2M19 10h2M19 14h2"/>',
};

/** 造一个图标元素。默认 16px，跟正文行高对齐。 */
export function icon(name: IconName, size = 16, cls = ""): SVGSVGElement {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", "1.6");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");
  svg.setAttribute("aria-hidden", "true");
  if (cls) svg.setAttribute("class", cls);
  svg.innerHTML = P[name];
  return svg;
}
