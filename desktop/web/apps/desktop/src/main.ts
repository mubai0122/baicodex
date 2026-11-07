/**
 * 桌面端入口。
 *
 * 就三行正事：找容器、建应用、把启动页收起来。所有失败都要变成一句人话显示在
 * 屏幕上——桌面应用的 devtools 默认是关的，控制台里的红字用户永远看不到。
 */

import { createApp } from "@baicodex/app";
import { hasWebView } from "@baicodex/bridge";

const root = document.getElementById("root");
const boot = document.getElementById("boot");

if (!root) {
  throw new Error("index.html 里缺少 #root");
}

function fail(err: unknown): void {
  const msg = err instanceof Error ? err.message : String(err);
  if (boot) {
    boot.dataset.failed = "true";
    boot.innerHTML = "";
    const box = document.createElement("div");
    box.style.cssText = "max-width:520px;text-align:center;line-height:1.7;font-size:13px";
    box.textContent = `启动失败：${msg}`;
    boot.append(box);
  }
  console.error(err);
}

createApp(root)
  .then((handle) => {
    // 预置主题，避免首屏闪一下
    const t = localStorage.getItem("bai.theme");
    if (t === "light" || t === "dark") document.documentElement.dataset.theme = t;

    boot?.setAttribute("data-ready", "true");
    setTimeout(() => boot?.remove(), 320);

    // 浏览器预览时在标题上标一下，免得以为连的是真内核
    if (!hasWebView()) {
      document.title = "baicodex · 预览模式";
      console.info("[baicodex] 没有检测到 WebView2，正在用假数据渲染。");
    }

    // 开发期把句柄挂到 window 上，方便在控制台里翻状态
    if (import.meta.env.DEV) {
      (window as unknown as { __bai: unknown }).__bai = handle;
    }
  })
  .catch(fail);

window.addEventListener("error", (e) => {
  if (e.error) fail(e.error);
});
window.addEventListener("unhandledrejection", (e) => {
  fail(e.reason);
});

// 阻止拖拽文件时浏览器直接打开它——那是默认行为，会毁掉当前页面
window.addEventListener("dragover", (e) => e.preventDefault());
window.addEventListener("drop", (e) => e.preventDefault());
