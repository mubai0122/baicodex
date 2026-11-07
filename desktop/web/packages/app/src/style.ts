/**
 * 布局样式。
 *
 * ## 为什么背景一定要"有东西"
 *
 * 玻璃的全部效果来自**它后面的东西被弯折、被模糊**。白底上的玻璃和灰底上的
 * 玻璃看起来一模一样——都是灰。所以窗口底下铺了一层极光：几个大半径的彩色
 * 光斑，缓慢漂移。强度压得很低（0.3 上下），它是背景不是主角，但没有它整块
 * 界面会立刻塌成一张 PPT。
 *
 * 光斑的颜色刻意避开了纯红纯绿：高饱和色经 `saturate(1.7)` 之后会溢出成荧光。
 *
 * ## 层级
 *
 * ```text
 * 极光（fixed，最底）
 *  ├─ 标题栏（透明，只吃 hover）
 *  ├─ 侧栏（一层玻璃）
 *  └─ 主区
 *      ├─ 消息流（透明，让极光透上来）
 *      └─ 输入区（玻璃 + 折射，视觉重心）
 * ```
 */

export const css = `
:root {
  color-scheme: light dark;
}

/* hidden 属性必须无条件生效。
 *
 * UA 样式表里是 [hidden] { display: none }，特异性 (0,1,0)。下面 .settings、
 * .tool-out 这些作者样式同样 (0,1,0) 却写了 display: grid/block，作者样式
 * 胜出 —— 结果设置面板永远显示、半透明地盖在消息流上，正文被冲淡，而且
 * elementFromPoint 命中的是面板背板而不是文字。这里用更高优先级兜住，
 * 以后新增组件也不用再记着补一条 .xxx[hidden]。
 * 注意：这段是模板字符串内部，注释里不能出现反引号。 */
[hidden] { display: none !important; }

* { box-sizing: border-box; }

html, body {
  margin: 0;
  height: 100%;
  overflow: hidden;
  background: transparent;
  color: var(--bai-fg);
  font-family: var(--bai-font);
  font-size: var(--bai-t-base);
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}

body {
  /* 没有 Mica 的机器上给个底，否则透明窗口后面什么都没有 */
  background: color-mix(in srgb, var(--bai-bg) 86%, transparent);
}

#root { height: 100%; }

/* —— 极光 —— */

.aurora {
  position: fixed;
  inset: -25%;
  z-index: 0;
  pointer-events: none;
  opacity: var(--aurora-op, 0.85);
  filter: blur(46px) saturate(1.25);
  transition: opacity 600ms var(--bai-ease-out);
}
.aurora i {
  position: absolute;
  display: block;
  border-radius: 50%;
  mix-blend-mode: normal;
  will-change: transform;
  animation: aurora-drift 34s ease-in-out infinite alternate;
}
.aurora i:nth-child(1) {
  width: 46%; height: 58%; left: 6%; top: 4%;
  background: radial-gradient(circle, rgba(86, 132, 255, 0.62), rgba(86, 132, 255, 0) 68%);
  animation-duration: 41s;
}
.aurora i:nth-child(2) {
  width: 40%; height: 50%; right: 4%; top: 0%;
  background: radial-gradient(circle, rgba(168, 118, 255, 0.5), rgba(168, 118, 255, 0) 68%);
  animation-duration: 37s;
  animation-delay: -8s;
}
.aurora i:nth-child(3) {
  width: 52%; height: 52%; right: 12%; bottom: 2%;
  background: radial-gradient(circle, rgba(64, 206, 186, 0.42), rgba(64, 206, 186, 0) 68%);
  animation-duration: 46s;
  animation-delay: -18s;
}
.aurora i:nth-child(4) {
  width: 34%; height: 40%; left: 18%; bottom: 6%;
  background: radial-gradient(circle, rgba(255, 148, 122, 0.3), rgba(255, 148, 122, 0) 68%);
  animation-duration: 33s;
  animation-delay: -4s;
}

[data-theme="dark"] .aurora { opacity: var(--aurora-op, 0.62); }
[data-theme="dark"] .aurora i:nth-child(1) { background: radial-gradient(circle, rgba(58, 92, 210, 0.55), transparent 68%); }
[data-theme="dark"] .aurora i:nth-child(2) { background: radial-gradient(circle, rgba(112, 68, 200, 0.5), transparent 68%); }
[data-theme="dark"] .aurora i:nth-child(3) { background: radial-gradient(circle, rgba(30, 132, 128, 0.42), transparent 68%); }
[data-theme="dark"] .aurora i:nth-child(4) { background: radial-gradient(circle, rgba(160, 74, 60, 0.3), transparent 68%); }

/* 系统底板很实时（solid），极光要更用力，否则整个窗口一片死灰 */
[data-backdrop="solid"] .aurora { opacity: 1; }

@keyframes aurora-drift {
  0%   { transform: translate3d(0, 0, 0) scale(1); }
  50%  { transform: translate3d(2.5%, -3%, 0) scale(1.08); }
  100% { transform: translate3d(-2%, 2.5%, 0) scale(0.96); }
}

/* —— 骨架 —— */

.app {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-rows: 42px 1fr;
  height: 100%;
  min-height: 0;
}

.app[data-view="settings"] .body { display: none; }

.body {
  display: grid;
  grid-template-columns: var(--sidebar-w, 268px) 1fr;
  min-height: 0;
  transition: grid-template-columns var(--bai-dur-slow) var(--bai-ease-out);
}
.app[data-sidebar="closed"] .body { grid-template-columns: 0 1fr; }

/* —— 标题栏 —— */

.titlebar {
  position: relative;
  z-index: 20;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 0 0 0 12px;
  -webkit-user-select: none;
  user-select: none;
}
.titlebar[data-drag="true"] .tb-title { cursor: default; }

.tb-left { display: flex; align-items: center; gap: 8px; min-width: 0; }
.tb-title {
  font-size: var(--bai-t-sm);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--bai-fg-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 42vw;
}
.tb-drag {
  height: 100%;
  min-height: 42px;
  cursor: default;
}
.tb-right { display: flex; align-items: center; height: 100%; }

/* 窗口按钮：贴边，方角，hover 才有底色——Windows 的惯例 */
.tb-win {
  width: 46px;
  height: 100%;
  border: 0;
  background: transparent;
  color: var(--bai-fg-dim);
  display: grid;
  place-items: center;
  cursor: default;
  transition: background var(--bai-dur-fast) linear, color var(--bai-dur-fast) linear;
}
.tb-win:hover { background: var(--bai-line); color: var(--bai-fg); }
.tb-win[data-close="true"]:hover { background: #c42b1c; color: #fff; }

/* —— 侧栏 —— */

.sidebar {
  position: relative;
  display: block;
  min-height: 0;
  height: 100%;
  margin: 0 0 10px 10px;
  border-radius: var(--bai-r-lg);
  overflow: hidden;
}
/*布局只能写在 .lg-inner 上：.lg 根里还有 back/tint/sheen/grain/edge 五个装饰层，
  在根上做 grid/flex 会把它们当成子项一起排 */
.sidebar > .lg-inner {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  height: 100%;
}

.sb-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 10px 6px;
}

.sb-search {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 10px;
  border-radius: var(--bai-r-xs);
  background: var(--bai-code-bg);
  border: 1px solid transparent;
  transition: border-color var(--bai-dur-fast);
}
.sb-search:focus-within { border-color: var(--bai-line-strong); }
.sb-search svg { color: var(--bai-fg-mute); flex: none; }
.sb-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: none;
  color: var(--bai-fg);
  font: inherit;
  font-size: var(--bai-t-sm);
}
.sb-search input::placeholder { color: var(--bai-fg-mute); }

.sb-list { min-height: 0; padding: 2px 8px 8px; }

.sb-group {
  padding: 10px 8px 4px;
  font-size: var(--bai-t-xs);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--bai-fg-mute);
}

.sb-item {
  position: relative;
  display: grid;
  gap: 2px;
  padding: 8px 10px;
  margin-bottom: 2px;
  border-radius: var(--bai-r-xs);
  cursor: default;
  transition: background var(--bai-dur-fast) var(--bai-ease-out);
}
.sb-item:hover { background: var(--bai-line); }
.sb-item[data-active="true"] { background: var(--bai-accent-soft); }
.sb-item[data-active="true"] .sb-title { color: var(--bai-accent); }
.sb-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--bai-t-base);
  font-weight: 520;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sb-sub {
  font-size: var(--bai-t-xs);
  color: var(--bai-fg-mute);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sb-foot {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 10px 10px;
}
.sb-usage {
  margin-left: auto;
  font-size: var(--bai-t-xs);
  color: var(--bai-fg-mute);
  font-variant-numeric: tabular-nums;
}

/* —— 主区 —— */

.main {
  display: grid;
  grid-template-rows: 1fr auto;
  min-height: 0;
  min-width: 0;
}

.stream {
  min-height: 0;
  padding: 8px 0 20px;
}
.stream-inner {
  width: min(760px, calc(100% - 56px));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 8px;
}

/* —— 内核报错条 —— */

.err-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(760px, calc(100% - 56px));
  margin: 0 auto 8px;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: var(--bai-t-sm);
  line-height: 1.5;
  color: var(--bai-danger);
  background: color-mix(in srgb, var(--bai-danger) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--bai-danger) 26%, transparent);
}
.err-bar[hidden] { display: none; }
.err-bar > span { flex: 1; min-width: 0; }
.err-bar > button {
  flex: none;
  padding: 4px 10px;
  border-radius: 7px;
  border: 1px solid color-mix(in srgb, var(--bai-danger) 40%, transparent);
  background: transparent;
  color: inherit;
  font-size: var(--bai-t-xs);
  cursor: pointer;
}

/* —— 下拉菜单（挂在 body 上，不受输入框层叠上下文限制） —— */

.chip-pop {
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.16);
}
.chip-pop .chip {
  height: auto;
  min-height: 32px;
  background: transparent;
  border-radius: 8px;
}
.chip-pop .chip[data-on="true"] { background: var(--bai-accent-soft); }

/* —— 消息 —— */

.msg { display: flex; flex-direction: column; gap: 6px; }
.msg[data-role="user"] { align-items: flex-end; }
.msg[data-role="assistant"], .msg[data-role="summary"] { align-items: stretch; }
.msg[data-role="tool"] { align-items: stretch; }

.bubble-user {
  max-width: min(560px, 88%);
  padding: 10px 14px;
  border-radius: 18px 18px 6px 18px;
  font-size: var(--bai-t-base);
  line-height: 1.62;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble-user > .lg-inner { white-space: pre-wrap; }

.bubble-ai {
  padding: 12px 16px;
  border-radius: 18px 18px 18px 6px;
  font-size: var(--bai-t-base);
}
.bubble-ai > .lg-inner { overflow-wrap: anywhere; }

.bubble-summary {
  align-self: center;
  max-width: 82%;
  text-align: center;
  font-size: var(--bai-t-sm);
  color: var(--bai-fg-mute);
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--bai-code-bg);
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--bai-t-xs);
  color: var(--bai-fg-mute);
  opacity: 0.62;
  transition: opacity var(--bai-dur-fast);
}
.msg:hover .msg-head { opacity: 1; }
.msg-body { position: relative; }

.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0.5;
  transition: opacity var(--bai-dur-fast);
}
.msg:hover .msg-actions { opacity: 1; }

.thinking {
  border-radius: var(--bai-r-sm);
  background: var(--bai-code-bg);
  padding: 8px 12px;
  font-size: var(--bai-t-sm);
  line-height: 1.65;
  color: var(--bai-fg-dim);
}
.thinking summary {
  cursor: default;
  list-style: none;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--bai-fg-mute);
  user-select: none;
}
.thinking summary::-webkit-details-marker { display: none; }
.thinking[open] summary { margin-bottom: 6px; }
.thinking .tb-chev { transition: transform var(--bai-dur-fast); }
.thinking[open] .tb-chev { transform: rotate(90deg); }

.branch-nav {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--bai-t-xs);
  color: var(--bai-fg-mute);
  font-variant-numeric: tabular-nums;
}
.branch-nav button {
  width: 18px; height: 18px; padding: 0; border: 0; border-radius: 4px;
  background: transparent; color: inherit; display: grid; place-items: center; cursor: pointer;
}
.branch-nav button:hover { background: var(--bai-line); }
.branch-nav button:disabled { opacity: 0.35; pointer-events: none; }

/* —— 工具卡 —— */

.tool-card {
  border-radius: var(--bai-r-sm);
  overflow: hidden;
  font-size: var(--bai-t-sm);
}
.tool-card > .lg-inner { display: grid; }
.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: default;
  user-select: none;
}
.tool-name {
  font-family: var(--bai-font-mono);
  font-size: var(--bai-t-sm);
  font-weight: 600;
}
.tool-args {
  flex: 1;
  min-width: 0;
  color: var(--bai-fg-mute);
  font-family: var(--bai-font-mono);
  font-size: var(--bai-t-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tool-out {
  border-top: 1px solid var(--bai-line);
  padding: 8px 12px;
  font-family: var(--bai-font-mono);
  font-size: 11.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
  color: var(--bai-fg-dim);
  background: color-mix(in srgb, var(--bai-fg) 2.5%, transparent);
}
.tool-out[data-error="true"] { color: var(--bai-danger); }
.tool-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--bai-fg-mute);
  flex: none;
}
.tool-dot[data-state="run"] { background: var(--bai-accent); animation: pulse 1.1s ease-in-out infinite; }
.tool-dot[data-state="ok"] { background: var(--bai-ok); }
.tool-dot[data-state="err"] { background: var(--bai-danger); }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.35; } }

/* —— 输入区 —— */

.composer-wrap {
  padding: 4px 0 18px;
}
.composer {
  position: relative;
  width: min(760px, calc(100% - 56px));
  margin: 0 auto;
  border-radius: var(--bai-r-lg);
}
.composer > .lg-inner {
  display: grid;
  gap: 2px;
  padding: 10px 10px 8px 14px;
}
.composer textarea {
  width: 100%;
  min-height: 24px;
  max-height: 208px;
  padding: 2px 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--bai-fg);
  font: inherit;
  font-size: var(--bai-t-base);
  line-height: 1.62;
  resize: none;
  overflow-y: auto;
}
.composer textarea::placeholder { color: var(--bai-fg-mute); }

.composer-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 30px;
}
.composer-bar .spacer { flex: 1; }

.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 9px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--bai-fg-dim);
  font: inherit;
  font-size: var(--bai-t-xs);
  font-weight: 500;
  cursor: pointer;
  transition: background var(--bai-dur-fast), color var(--bai-dur-fast);
}
.chip:hover { background: var(--bai-line); color: var(--bai-fg); }
.chip[data-on="true"] { color: var(--bai-accent); background: var(--bai-accent-soft); }

.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  padding: 0;
}
.send-btn[data-busy="true"] { --lg-tint: color-mix(in srgb, var(--bai-danger) 22%, var(--bai-glass)); }

/* 输入区下方的提示行 */
.composer-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 8px;
  font-size: var(--bai-t-xs);
  color: var(--bai-fg-mute);
}

/* —— 空状态 —— */

.welcome {
  display: grid;
  place-items: center;
  height: 100%;
  padding: 40px 24px;
}
.welcome-in {
  width: min(620px, 100%);
  display: grid;
  gap: 22px;
  text-align: center;
}
.welcome h1 {
  margin: 0;
  font-family: var(--bai-font-display);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, var(--bai-fg) 10%, color-mix(in srgb, var(--bai-accent) 70%, var(--bai-fg)) 90%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.welcome p { margin: 0; color: var(--bai-fg-dim); font-size: var(--bai-t-lg); line-height: 1.6; }
.welcome-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  text-align: left;
}
.wcard {
  padding: 12px 14px;
  border-radius: var(--bai-r-sm);
  background: var(--bai-code-bg);
  border: 1px solid var(--bai-line);
  cursor: default;
  transition: border-color var(--bai-dur-fast), transform var(--bai-dur-fast) var(--bai-ease-out);
}
.wcard:hover { border-color: var(--bai-line-strong); transform: translateY(-1px); }
.wcard b { display: block; font-size: var(--bai-t-base); font-weight: 600; margin-bottom: 3px; }
.wcard span { font-size: var(--bai-t-sm); color: var(--bai-fg-mute); line-height: 1.5; }

/* —— 待确认卡片 —— */

.ask {
  border-radius: var(--bai-r-md);
}
.ask > .lg-inner { display: grid; gap: 10px; padding: 14px; }
.ask-q { font-size: var(--bai-t-base); font-weight: 560; }
.ask-opts { display: flex; flex-wrap: wrap; gap: 8px; }
.ask-opt {
  padding: 7px 12px;
  border-radius: var(--bai-r-xs);
  border: 1px solid var(--bai-line-strong);
  background: transparent;
  color: var(--bai-fg);
  font: inherit;
  font-size: var(--bai-t-sm);
  cursor: pointer;
  transition: all var(--bai-dur-fast);
}
.ask-opt:hover { background: var(--bai-line); }
.ask-opt[data-on="true"] { border-color: var(--bai-accent); background: var(--bai-accent-soft); color: var(--bai-accent); }
.ask-actions { display: flex; gap: 8px; justify-content: flex-end; }

/* —— 设置 —— */

.settings {
  position: absolute;
  inset: 42px 0 0 0;
  z-index: 30;
  /*这里不能再设 grid-template-columns：.lg-inner 是唯一子项，会被按第一列 190px 摆，
    正文列就塌成一字宽。列划分只交给 .lg-inner 自己做 */
  display: block;
  margin: 0 10px 10px;
  border-radius: var(--bai-r-lg);
  overflow: hidden;
}
.settings > .lg-inner { display: grid; grid-template-columns: 208px minmax(0, 1fr); height: 100%; min-height: 0; }
/*导航列自己一条竖向分隔线，左右是同一块玻璃面上的两个区，不再是「实色栏 + 浮起卡」*/
.set-nav {
  display: flex; flex-direction: column; gap: 3px;
  padding: 20px 12px 18px;
  border-right: 1px solid color-mix(in srgb, var(--bai-line) 65%, transparent);
}
.set-nav button {
  display: flex; align-items: center; gap: 9px;
  height: 36px; padding: 0 11px; border: 0; border-radius: 9px;
  background: transparent; color: var(--bai-fg-dim); font: inherit; font-size: 13.5px;
  letter-spacing: 0.005em;
  cursor: pointer; text-align: left;
}
.set-nav button:hover { background: var(--bai-line); color: var(--bai-fg); }
.set-nav button[data-on="true"] { background: var(--bai-accent-soft); color: var(--bai-accent); }
/*返回键靠底，margin-top:auto 由 settings.ts 设，这里只管它比菜单项更弱一档*/
.set-nav button:last-child { color: var(--bai-fg-mute); }

.set-body { min-height: 0; padding: 30px 36px 56px; }
/*设置项按块分卡：玻璃面上再铺一层极淡的凹底 + 顶部内亮线，读作第二层玻璃*/
.set-body > section {
  max-width: 720px;
  padding: 22px 24px 8px;
  border-radius: 16px;
  background: var(--bai-code-bg);
  border: 1px solid var(--bai-line);
  box-shadow: inset 0 0.8px 0 rgba(255, 255, 255, 0.55);
}
.set-body h2 {
  margin: 0 0 7px;
  font-family: var(--bai-font-display);
  font-size: 21px;
  font-weight: 620;
  letter-spacing: -0.014em;
}
.set-body .sub { margin: 0 0 4px; color: var(--bai-fg-mute); font-size: 13px; line-height: 1.55; }
.set-row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 0;
  border-bottom: 1px solid color-mix(in srgb, var(--bai-line) 60%, transparent);
}
.set-row:last-child { border-bottom: 0; }
.set-row .k { flex: 1; min-width: 0; }
.set-row .k b { display: block; margin-bottom: 3px; font-size: 14px; font-weight: 580; }
.set-row .k span { font-size: 12.5px; line-height: 1.5; color: var(--bai-fg-mute); }

.seg {
  display: inline-flex;
  padding: 3px;
  border-radius: 9px;
  background: var(--bai-code-bg);
  gap: 2px;
}
.seg button {
  height: 26px; padding: 0 12px; border: 0; border-radius: 7px;
  background: transparent; color: var(--bai-fg-dim); font: inherit; font-size: var(--bai-t-xs);
  cursor: pointer;
}
/*选中项给一圈描边 + 下沉阴影，否则白底在小尺寸下边缘发虚、读不出「已选中」*/
.seg button[data-on="true"] {
  background: var(--bai-bg);
  color: var(--bai-fg);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--bai-line) 90%, transparent), 0 1px 4px var(--bai-shadow);
}

.kv { display: grid; grid-template-columns: 132px 1fr; gap: 8px 12px; font-size: var(--bai-t-sm); }
.kv dt { color: var(--bai-fg-mute); }
.kv dd { margin: 0; font-family: var(--bai-font-mono); font-size: var(--bai-t-xs); word-break: break-all; }

/* —— 滚动渐隐 —— */

.fade-top, .fade-bottom {
  position: absolute;
  left: 0; right: 0;
  height: 26px;
  pointer-events: none;
  z-index: 5;
  opacity: 0;
  transition: opacity var(--bai-dur-base);
}
.fade-top { top: 0; background: linear-gradient(to bottom, color-mix(in srgb, var(--bai-bg) 55%, transparent), transparent); }
.fade-bottom { bottom: 0; background: linear-gradient(to top, color-mix(in srgb, var(--bai-bg) 55%, transparent), transparent); }
.stream[data-scrolled="true"] + .fade-top, .stream[data-canmore="true"] ~ .fade-bottom { opacity: 1; }

/* —— 窄窗口 —— */

@media (max-width: 900px) {
  .body { grid-template-columns: 0 1fr; }
  .sidebar { position: absolute; z-index: 40; width: 260px; top: 42px; bottom: 10px; }
}

/* —— 双界面风格 ——
 *
 * 素雅（默认）：白灰配色 + 几团淡光斑 + 1px 边的实色卡。
 * 液态玻璃：data-ui="liquid" 时才吃折射与模糊。
 *
 * 两套共用同一份 DOM：切换只改根属性，不动 TS 结构。玻璃那六层在素雅下
 * 全部 display:none，backdrop-filter 也关掉——留着不用等于每张卡白烧一次
 * GPU 离屏合成。
 */

:root[data-ui="plain"] {
  --bai-plain-card: #ffffff;
  --bai-plain-sunken: #f3f4f7;
  --aurora-op: 0.42;
}

:root[data-ui="plain"][data-theme="dark"] {
  --bai-plain-card: #1b1e26;
  --bai-plain-sunken: #14171d;
  --aurora-op: 0.26;
}

:root[data-ui="plain"] body { background: var(--bai-bg); }

:root[data-ui="plain"] .lg > .lg-back {
  -webkit-backdrop-filter: none;
  backdrop-filter: none;
  background: var(--bai-plain-card);
}

:root[data-ui="plain"] .lg > .lg-tint,
:root[data-ui="plain"] .lg > .lg-sheen,
:root[data-ui="plain"] .lg > .lg-grain,
:root[data-ui="plain"] .lg > .lg-edge { display: none; }

/*素雅模式不要玻璃的悬浮阴影与 translateZ：卡片就贴在纸面上，一层1px边 +极浅投影*/
:root[data-ui="plain"] .lg {
  box-shadow: inset 0 0 0 1px var(--bai-line), 0 1px 2px rgba(16, 20, 26, 0.05);
  transform: none;
  will-change: auto;
}

/*背景只留三四团很淡的光斑，不要液态玻璃那种彩色漂移*/
:root[data-ui="plain"] .aurora {
  filter: blur(72px) saturate(0.55);
  opacity: var(--aurora-op, 0.42);
}
:root[data-ui="plain"] .aurora i { animation: none; }
:root[data-ui="plain"] .aurora i:nth-child(1) { background: radial-gradient(circle, rgba(96, 128, 184, 0.20), transparent 70%); }
:root[data-ui="plain"] .aurora i:nth-child(2) { background: radial-gradient(circle, rgba(140, 128, 190, 0.16), transparent 70%); }
:root[data-ui="plain"] .aurora i:nth-child(3) { background: radial-gradient(circle, rgba(120, 150, 150, 0.14), transparent 70%); }
:root[data-ui="plain"] .aurora i:nth-child(4) { background: radial-gradient(circle, rgba(180, 150, 140, 0.10), transparent 70%); }
`;