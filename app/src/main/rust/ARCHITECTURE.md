# baicodex 桌面版架构

一句话：**Rust 是唯一的逻辑层**，Windows 原生宿主只管窗口与 WebView2，前端只管画。
三者之间只有一条 JSON 契约，由 `src/ffi/spec.rs` 单点定义并自动生成 TypeScript 类型。

---

## 1. 分层与依赖方向

```text
  web/apps/desktop            Vite 入口，产出 WebView2 直接加载的静态资源
        │
  @baicodex/app               业务视图（标题栏 / 侧栏 / 消息流 / 输入区 / 设置）
        │
  @baicodex/ui ──────────►  @baicodex/liquid ──────────► @baicodex/tokens
        │                     玻璃原语                      唯一写死颜色的地方
  @baicodex/bridge            JSON 契约：88 个 op 的类型化封装 + 事件
        ⇅ JSON（唯一跨语言通道）
  crates/host-win             Win32 / DWM / WebView2 / 托盘 / DPI / 窗口控制
        │ 依赖（单向）
  baicodex (src/)             唯一逻辑层：core/base → security → feature → app → ffi
        ▲
  crates/proto                读 ffi/spec.rs，生成 *.gen.ts

  依赖方向是单向的：宿主依赖内核，内核不认识宿主；前端依赖 bridge，bridge 不认识 app。
  任何反向 import 都是架构错误。
```

这么切的直接收益：再接一个平台（macOS / Linux / Android）只写宿主，逻辑层 0 改动；
加一个 op 只改 `spec.rs` 一处，忘了生成类型会在 CI 被 `check` 挡住。

---

## 2. 各层职责

### 2.1 Rust 内核 `src/` — 123 文件 / ~27,400 行

| 层 | 管什么 | 硬约束 |
|---|---|---|
| `core/base` | 错误码、时间、ID、原子写、文件系统护栏、脱敏基础 | 不认识业务 |
| `core/security` | 策略红线、三层密钥环、加密文件格式、日志脱敏、零宽路由 | 不依赖 `feature` |
| `core/feature` | 会话与消息树、上下文预算、记忆、工具治理、技能、MCP、工作流、用量 | 不依赖 `app` / `ffi` |
| `app` + `ffi` | `Facade` 门面 + 88 个 op 的 JSON dispatch | 唯一对外出口 |

跨语言契约固定为 `{"ok":true,"data":...}` / `{"ok":false,"error":{code,message,retryable}}`。
`ffi` 里的 `KERNEL` 是 `OnceLock<Kernel>` 进程单例，重复 `boot` 幂等；空数据根一律拒写。

### 2.2 契约生成器 `crates/proto`

`src/ffi/spec.rs` 是**唯一真源**：88 个 op 的名字 / 参数 / 必填 / 返回形状 / 说明，以及事件表。

```bash
cargo run -p baicodex-proto -- ts       # 生成 web/packages/bridge/src/{ops,events}.gen.ts
cargo run -p baicodex-proto -- check    # CI 用：磁盘上的生成物不是最新就失败
```

生成物提交进仓库，所以前端构建**不需要 Rust 工具链**。三道交叉校验防止漂移：

- 表里的 op 必须被 `dispatch` 认识（`boot` / `shutdown` 因会改进程级单例，改用源码静态断言）；
- `dispatch` 里的分支必须在表里声明（Rust 没有 match 反射，用源码扫描）；
- 内核的 `KINDS` 与生成器的 `EVENTS` 必须完全一致，多一个少一个都失败。

### 2.3 Windows 原生层 `crates/host-win` — 16 文件 / ~2,360 行

| 模块 | 职责 |
|---|---|
| `win/window.rs` | 无边框窗口、WndProc、消息循环、`GWLP_USERDATA` 挂状态 |
| `win/frame.rs` | `WM_NCCALCSIZE` / `WM_NCHITTEST` 命中测试，最大化时的边框补偿 |
| `win/dpi.rs` | Per-Monitor V2、`WM_DPICHANGED` 重排 |
| `backdrop/dwm.rs` | Win11 Mica / Acrylic、圆角、深色跟随 |
| `backdrop/accent.rs` | Win10 降级：`AccentPolicy` 毛玻璃 |
| `backdrop/version.rs` | 系统版本判定，决定走哪条底板路径 |
| `webview/mod.rs` | WebView2 透明宿主、环境创建、WebMessage 收发 |
| `bridge/mod.rs` | JSON 桥：窗口拖拽 / 最小化 / 关闭 / 宿主信息 / 外链 |
| `win/tray.rs` | 托盘图标与菜单 |
| `cli.rs` | 页面路径解析：`dist` → `--dev <url>` → `--web <dir>` |
| `app.rs` | 生命周期装配与退出清理 |

Linux 上用 mingw 交叉验证（MSVC target 也已安装）：

```bash
cargo check --workspace --target x86_64-pc-windows-gnu
```

### 2.4 npm 分层 `web/` — 35 个 TS 文件 / ~6,600 行

严格单向：`tokens ← liquid ← ui ← app ← desktop`，`bridge` 独立。

| 包 | 职责 | 依赖 |
|---|---|---|
| `@baicodex/tokens` | 唯一允许写死颜色数值的地方；`light` / `dark` 调色板 → CSS 变量 | 无 |
| `@baicodex/liquid` | 液态玻璃引擎 | tokens |
| `@baicodex/bridge` | 契约：生成的 op/event 类型、Transport（WebView2 / HTTP / Mock）、领域模型 | 无 |
| `@baicodex/ui` | 通用组件：按钮、图标、Markdown、Toast、滚动 | tokens, liquid |
| `@baicodex/app` | 业务视图 + 状态 Store | bridge, ui, liquid, tokens |
| `@baicodex/desktop` | Vite 入口 | app, bridge |

浏览器里没有 WebView2 时自动落 `MockTransport`，所以 `pnpm dev` 能脱离宿主独立开发界面。

---

## 3. Liquid Glass 怎么落地

`packages/liquid/src/` 把玻璃拆成可单独调的原语：

- **层叠顺序**（从下到上）：`lg-back`（`backdrop-filter` 模糊 + 折射）→ `lg-tint`（染色，可读性兜底）→ `lg-sheen`（镜面高光，`screen` 混合，跟鼠标走）→ `lg-grain`（噪点，`soft-light`）→ `lg-edge`（1px 锥形描边，上下亮两侧暗＝厚度）→ `lg-inner`（内容，`z-index: 5`）。
- **折射**：`refract.ts` 生成 SVG `feDisplacementMap`，把滤镜 URL 追加到 `--lg-filter`，不是简单的 `blur()`。
- **动效**：`spring.ts` 用弹簧给凝胶按压曲线，不用 `transition`。
- **降级**：`capability.ts` 检测 `backdrop-filter` / `mask-composite`；不支持时退到半透明底 + 高光，而不是把玻璃整个关掉。
- **背景必须有东西**：玻璃的全部效果来自"后面的东西被弯折"。白底上的玻璃看起来就是灰，所以 `app/style.ts` 铺了一层缓慢漂移的极光光斑。

一个真实的坑已经修掉：`hidden` 属性是 UA 样式 `[hidden]{display:none}`，会被作者样式
`.settings{display:grid}` 覆盖，导致设置面板永远半透明地盖在消息流上、正文被冲淡。现在
`app/style.ts` 顶部用 `[hidden]{display:none !important}` 兜住所有组件。

---

## 4. 构建与运行

```bash
# —— 前端 ——
pnpm -C web install
pnpm -C web dev                 # http://localhost:5173，浏览器里用 Mock 内核
pnpm -C web build               # → web/apps/desktop/dist
pnpm -C web typecheck

# —— 契约 ——
cargo run -p baicodex-proto -- ts
cargo run -p baicodex-proto -- check

# —— 内核 ——
cargo test --lib -p baicodex
cargo clippy --all-targets --all-features

# —— Windows 宿主 ——
cargo check --workspace --target x86_64-pc-windows-gnu   # Linux 交叉验证
cargo run -p baicodex-host-win --release -- --dev http://localhost:5173   # Windows 上
cargo run -p baicodex-host-win --release                 # 加载 dist 静态资源
```

宿主找页面的顺序：显式 `--web` → exe 同级 `web/` → `web/dist` → `web/apps/desktop/dist`；
开发时用 `--dev` 直接连 vite。找不到 `index.html` 会明确报错并列出找过哪些路径。

---

## 5. 本次验证结果

| 项 | 结果 |
|---|---|
| `cargo test --lib -p baicodex` | **232 passed / 0 failed**，连续 3 次稳定 |
| `cargo check --workspace --target x86_64-pc-windows-gnu` | 通过 |
| `cargo run -p baicodex-proto -- check` | 契约生成物是最新的 |
| `pnpm -r exec tsc --noEmit` | 0 error |
| `pnpm -C web build` | 105.5 KB / gzip 34.6 KB，0 warning |
| 浏览器渲染 | 无 console error，液态玻璃 / 极光 / 各视图正常 |

修掉的三个真实缺陷（都是验证阶段发现的，不是编译错误）：

1. **测试并发污染**：`KERNEL` 是进程单例，`spec` 遍历 op 时会执行 `boot`（空 base 占住单例）和
   `shutdown`，与 `kernel_roundtrip` 并发时随机失败。现在加 `ffi::test_lock()` 串行锁（与
   `guard` 模块同一套做法），契约校验跳过生命周期 op 改用静态断言。
2. **设置面板永不隐藏**：见上一节，导致消息正文被半透明层冲淡。
3. **Vite 分包警告**：`chat.ts` 对 `host` 的动态导入与别处静态导入冲突，已统一为静态导入。

---

## 6. 遗留工作

- `android/app/src/main/java/.../ui/` 下 53 个 Kotlin 文件仍调用旧逻辑对象，约 300 处调用点
  待机械替换为 `Kernel` / `Agent` / `Repo` / `Cfg`（`MIGRATION.md` 已按调用量排好清单）。
- 原 C++ 27 个文件在 Rust 侧已有对应实现，需按 `rust_bridge` 逐个摘除并接入 Gradle/CMake。
