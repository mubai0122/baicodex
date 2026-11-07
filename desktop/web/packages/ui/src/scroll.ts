/**
 * 滚动容器。
 *
 * 存在的理由只有一个：**"贴在底部"和"用户往上翻了"要区分开**。消息流里，如果
 * 用户正在往回看历史，新来的流式增量不应该把他拽回底部——那是这个界面最招人烦
 * 的行为之一。
 *
 * 判据用 32px 的容差，不是 0：亚像素滚动、分数缩放、滚动条的惯性都会让
 * `scrollTop + clientHeight` 差几个像素，用 0 会导致"明明在底部却不自动滚"。
 */

export interface ScrollArea {
  el: HTMLDivElement;
  /** 滚到底。`smooth` 用于用户主动操作，自动跟随用 false。 */
  toBottom(smooth?: boolean): void;
  /** 是否贴在底部（32px 容差内）。 */
  atBottom(): boolean;
  /** 内容变化后调用：贴底就跟着走，翻历史就不动。 */
  follow(): void;
  destroy(): void;
}

const SLACK = 32;

export function scrollArea(classes = ""): ScrollArea {
  const el = document.createElement("div");
  el.className = `ui-scroll ${classes}`.trim();

  let pinned = true;

  const onScroll = (): void => {
    pinned = el.scrollHeight - el.scrollTop - el.clientHeight < SLACK;
  };
  el.addEventListener("scroll", onScroll, { passive: true });

  return {
    el,
    atBottom: () => el.scrollHeight - el.scrollTop - el.clientHeight < SLACK,
    toBottom(smooth = false) {
      pinned = true;
      el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
    },
    follow() {
      if (pinned) el.scrollTop = el.scrollHeight;
    },
    destroy() {
      el.removeEventListener("scroll", onScroll);
    },
  };
}
