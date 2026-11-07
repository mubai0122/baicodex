import { defineConfig } from "vite";

/**
 * 构建配置。
 *
 * 两个必须的设定：
 *
 * - `base: "./"` —— 产物用 `file://` 加载，绝对路径 `/assets/x.js` 会指到盘符根目录。
 * - `target: "chrome120"` —— WebView2 跟着 Edge 走，120 是还可能有用户停留的底线。
 *   `backdrop-filter: url()` 需要 123，那部分在运行期检测，不支持就降级。
 */
export default defineConfig({
  base: "./",
  build: {
    target: "chrome120",
    outDir: "dist",
    emptyOutDir: true,
    assetsInlineLimit: 4096,
    cssCodeSplit: false,
    sourcemap: false,
    rollupOptions: {
      output: {
        // 一个 chunk 就够：这是一次性加载的桌面应用，不用做路由级拆分
        manualChunks: undefined,
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 4173,
    strictPort: true,
  },
});
