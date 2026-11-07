/**
 * `@baicodex/ui` —— 通用组件。
 *
 * 这一层的判断标准：**换个产品还能不能用**。能，就放这里；只有这个对话应用才
 * 需要（消息气泡、会话列表、工具卡），放 `@baicodex/app`。
 *
 * 依赖只往下：`ui → liquid → tokens`。
 */

export { Button, button, iconButton } from "./button";
export type { ButtonOptions, ButtonSize, ButtonTone } from "./button";

export { icon } from "./icon";
export type { IconName } from "./icon";

export { render as renderMarkdown, mount as mountMarkdown, highlight, escapeHtml } from "./markdown";

export { toast } from "./toast";
export type { ToastOptions } from "./toast";

export { scrollArea } from "./scroll";
export type { ScrollArea } from "./scroll";

export { install as installStyles, css } from "./style";
