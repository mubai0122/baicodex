/**
 * 组件样式。
 *
 * 全部挂在 `.ui-` 前缀下，不碰全局标签选择器——这个包会被装进别人的页面，
 * `button { ... }` 那种写法是灾难。
 *
 * 尺寸只有一个来源：`tokens` 的 `--bai-*`。这里出现的 px 都是结构性的
 * （1px 描边、2px 偏移），不是"设计值"。
 */

export const css = `
/* —— 按钮 —— */

.ui-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: var(--bai-r-sm);
  color: var(--bai-fg);
  font: inherit;
  font-size: var(--bai-t-base);
  font-weight: 520;
  letter-spacing: 0.01em;
  cursor: pointer;
  white-space: nowrap;
  -webkit-user-select: none;
  user-select: none;
}
.ui-btn > .lg-inner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ui-btn[data-tone="quiet"] {
  box-shadow: none;
}
.ui-btn[data-tone="accent"] {
  color: var(--bai-accent-fg);
}
.ui-btn[data-tone="danger"] {
  color: var(--bai-danger);
}
.ui-btn[disabled] {
  opacity: 0.42;
  pointer-events: none;
}
.ui-btn[data-size="sm"] { height: 28px; padding: 0 10px; font-size: var(--bai-t-sm); border-radius: var(--bai-r-xs); }
.ui-btn[data-size="lg"] { height: 42px; padding: 0 20px; font-size: var(--bai-t-lg); border-radius: var(--bai-r-md); }

/* —— 图标按钮 —— */

.ui-iconbtn {
  width: 34px;
  height: 34px;
  padding: 0;
  border: 0;
  border-radius: var(--bai-r-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--bai-fg-dim);
  background: transparent;
  cursor: pointer;
  transition: background var(--bai-dur-fast) var(--bai-ease-out), color var(--bai-dur-fast) var(--bai-ease-out);
}
.ui-iconbtn:hover { background: var(--bai-line); color: var(--bai-fg); }
.ui-iconbtn:active { transform: scale(0.94); }
.ui-iconbtn[data-active="true"] { color: var(--bai-accent); background: var(--bai-accent-soft); }
.ui-iconbtn[data-size="sm"] { width: 26px; height: 26px; border-radius: var(--bai-r-xs); }
.ui-iconbtn[data-size="lg"] { width: 42px; height: 42px; }

/* —— 输入 —— */

.ui-input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--bai-fg);
  font: inherit;
  font-size: var(--bai-t-base);
  line-height: 1.55;
  resize: none;
  padding: 0;
}
.ui-input::placeholder { color: var(--bai-fg-mute); }

/* —— 标签 —— */

.ui-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
  padding: 0 7px;
  border-radius: var(--bai-r-xs);
  background: var(--bai-line);
  color: var(--bai-fg-dim);
  font-size: var(--bai-t-xs);
  font-weight: 500;
  letter-spacing: 0.02em;
}
.ui-tag[data-tone="accent"] { background: var(--bai-accent-soft); color: var(--bai-accent); }
.ui-tag[data-tone="danger"] { background: color-mix(in srgb, var(--bai-danger) 14%, transparent); color: var(--bai-danger); }
.ui-tag[data-tone="ok"] { background: color-mix(in srgb, var(--bai-ok) 14%, transparent); color: var(--bai-ok); }

.ui-kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 5px;
  border: 1px solid var(--bai-line);
  border-bottom-width: 2px;
  background: var(--bai-code-bg);
  color: var(--bai-fg-mute);
  font-family: var(--bai-font-mono);
  font-size: 10px;
  line-height: 1;
}

/* —— 滚动条 —— */

.ui-scroll {
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--bai-line-strong) transparent;
  overscroll-behavior: contain;
}
.ui-scroll::-webkit-scrollbar { width: 10px; height: 10px; }
.ui-scroll::-webkit-scrollbar-track { background: transparent; }
.ui-scroll::-webkit-scrollbar-thumb {
  background: var(--bai-line-strong);
  border-radius: 99px;
  border: 3px solid transparent;
  background-clip: content-box;
}
.ui-scroll::-webkit-scrollbar-thumb:hover { background-color: var(--bai-fg-mute); background-clip: content-box; }

/* —— Markdown —— */

.ui-md {
  font-size: var(--bai-t-base);
  line-height: 1.72;
  color: var(--bai-fg);
  word-break: break-word;
}
.ui-md > *:first-child { margin-top: 0; }
.ui-md > *:last-child { margin-bottom: 0; }
.ui-md p { margin: 0 0 0.72em; }
.ui-md h1, .ui-md h2, .ui-md h3, .ui-md h4 {
  margin: 1.15em 0 0.5em;
  font-family: var(--bai-font-display);
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.01em;
}
.ui-md h1 { font-size: 1.42em; }
.ui-md h2 { font-size: 1.24em; }
.ui-md h3 { font-size: 1.1em; }
.ui-md h4 { font-size: 1em; color: var(--bai-fg-dim); }
.ui-md ul, .ui-md ol { margin: 0 0 0.72em; padding-left: 1.35em; }
.ui-md li { margin: 0.18em 0; }
.ui-md li::marker { color: var(--bai-fg-mute); }
.ui-md a { color: var(--bai-accent); text-decoration: none; border-bottom: 1px solid color-mix(in srgb, var(--bai-accent) 34%, transparent); }
.ui-md a:hover { border-bottom-color: var(--bai-accent); }
.ui-md strong { font-weight: 620; }
.ui-md em { font-style: italic; color: var(--bai-fg-dim); }
.ui-md del { color: var(--bai-fg-mute); }
.ui-md blockquote {
  margin: 0.6em 0;
  padding: 0.1em 0 0.1em 0.9em;
  border-left: 2px solid var(--bai-line-strong);
  color: var(--bai-fg-dim);
}
.ui-md hr { border: 0; border-top: 1px solid var(--bai-line); margin: 1.2em 0; }
.ui-md code {
  font-family: var(--bai-font-mono);
  font-size: 0.9em;
  padding: 0.12em 0.36em;
  border-radius: 5px;
  background: var(--bai-code-bg);
}
.ui-md pre {
  margin: 0.7em 0;
  padding: 0;
  border-radius: var(--bai-r-sm);
  background: var(--bai-code-bg);
  overflow: hidden;
  border: 1px solid var(--bai-line);
}
.ui-md pre > .ui-code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 26px;
  padding: 0 6px 0 10px;
  font-size: var(--bai-t-xs);
  color: var(--bai-fg-mute);
  border-bottom: 1px solid var(--bai-line);
  background: color-mix(in srgb, var(--bai-fg) 3%, transparent);
}
.ui-md pre > code {
  display: block;
  padding: 10px 12px;
  background: none;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.62;
  tab-size: 2;
}
.ui-md table {
  width: 100%;
  border-collapse: collapse;
  margin: 0.7em 0;
  font-size: 0.95em;
}
.ui-md th, .ui-md td {
  border: 1px solid var(--bai-line);
  padding: 6px 10px;
  text-align: left;
}
.ui-md th { background: color-mix(in srgb, var(--bai-fg) 4%, transparent); font-weight: 600; }

/* 语法着色。故意用低饱和：正文才是主角。 */
.tok-kw { color: color-mix(in srgb, var(--bai-accent) 88%, var(--bai-fg)); }
.tok-str { color: var(--bai-ok); }
.tok-num { color: var(--bai-warn); }
.tok-com { color: var(--bai-fg-mute); font-style: italic; }
.tok-fn { color: color-mix(in srgb, var(--bai-accent) 55%, var(--bai-fg)); }
.tok-ty { color: color-mix(in srgb, var(--bai-warn) 55%, var(--bai-fg)); }

/* —— 轻提示 —— */

.ui-toasts {
  position: fixed;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  z-index: 900;
  pointer-events: none;
}
.ui-toast {
  pointer-events: auto;
  padding: 9px 16px;
  border-radius: var(--bai-r-pill);
  font-size: var(--bai-t-sm);
  color: var(--bai-fg);
  animation: ui-toast-in 260ms var(--bai-ease-out);
}
.ui-toast[data-leaving="true"] { animation: ui-toast-out 180ms var(--bai-ease-inOut) forwards; }
@keyframes ui-toast-in {
  from { opacity: 0; transform: translateY(10px) scale(0.96); }
  to { opacity: 1; transform: none; }
}
@keyframes ui-toast-out {
  to { opacity: 0; transform: translateY(6px) scale(0.97); }
}

/* —— 分隔与占位 —— */

.ui-sep { height: 1px; background: var(--bai-line); border: 0; margin: 8px 0; }
.ui-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--bai-fg-mute);
  font-size: var(--bai-t-sm);
  padding: 48px 20px;
  text-align: center;
}
`;

const STYLE_ID = "bai-ui";

export function install(doc: Document = document): void {
  if (doc.getElementById(STYLE_ID)) return;
  const el = doc.createElement("style");
  el.id = STYLE_ID;
  el.textContent = css;
  doc.head.appendChild(el);
}
