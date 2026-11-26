# Baicodex 桌面端（Windows · MVP）

Compose Multiplatform（Kotlin）做界面，Rust 做 adb sidecar。目标是**最小可跑**：能列出手机、能把手机端配置拉回本机。

## 目录

```
windows/
├── composeApp/                  UI 与打包配置（Compose Desktop）
│   └── src/desktopMain/kotlin/b/desk/
│       ├── Main.kt              入口：开窗 + 套主题
│       ├── theme/Theme.kt       取自 Android 端 Color.kt 的浅色值（淡灰 + 白）
│       ├── ui/HomeScreen.kt     主界面（左操作卡 + 右日志卡）
│       └── adb/Sidecar.kt       Rust sidecar 的进程封装 + JSON 模型
│       └── adb/AdbLocator.kt    adb 自动探测（安装目录 → PATH）
├── rustcore/                   Rust sidecar（crate 名 baiadb）
│   └── src/main.rs              devices / import / version 三个子命令
├── gradle/ gradlew             Gradle Wrapper（复用仓库根同版本 9.0.0）
└── build.gradle.kts            插件版本集中声明
```

## 架构：为什么用 sidecar 而不是 JNI

| 方案 | 评价 |
|---|---|
| JNI / JNA 直接调 Rust | 需要跨平台编译 `.dll/.so/.dylib`，Windows 要 MSVC 工具链，首次接入成本高 |
| **Rust 编成 CLI，Kotlin 用 ProcessBuilder 调**（当前方案） | 零 FFI 复杂度，Windows/Linux/macOS 同一套代码，崩溃隔离，出问题能直接在终端复现 |

约定：`baiadb` 永远只往 stdout 打**一行 JSON**，成功失败都是 JSON（`ok` 字段），Kotlin 侧不依赖退出码。

```bash
baiadb version
baiadb devices [--adb <path>]
baiadb import --serial <S> --out <file> [--adb <path>] [--remote <path>]
```

## 构建

前置：JDK 21、Rust（cargo）。

```bash
cd windows

# 只编 Kotlin（快，验证编译）
./gradlew :composeApp:compileKotlinDesktop

# 编 Rust sidecar 并复制进资源
cd rustcore && cargo build --release && cd ..
./gradlew :composeApp:buildRust

# 本机跑（需要图形环境，Windows/Linux 桌面）
./gradlew :composeApp:run

# Windows 上出安装包（.exe / .msi）
./gradlew :composeApp:packageExe
./gradlew :composeApp:packageMsi
```

产物：`composeApp/build/compose/binaries/main/exe/`。

> **重要**：`.exe` 只能在 **Windows** 上产出（jpackage 不支持交叉打包）。
> 在 Android/Termux 上最多只能 `compileKotlinDesktop`；要 exe 请用 GitHub Actions 的 `windows-latest`（见 `.github/workflows/desk-windows.yml`）或在 Windows 机器上执行上面的命令。

## 内置 adb

`adb.exe` 不随仓库提交。两种方式：

1. **手动**：把 [platform-tools](https://dl.google.com/android/repository/platform-tools-latest-windows.zip) 解压到 `composeApp/src/desktopMain/resources/bin/adb/`，`createDistributable` 会整体复制到安装目录 `bin/adb/`，运行时 `AdbLocator` 自动识别。
2. **CI**：workflow 里已自动下载 platform-tools 并放到该目录。

找不到时界面会提示，也可以在「ADB 路径」输入框里手填。

## 配置导入（当前是占位）

`baiadb import` 目前做的是：

```
adb -s <serial> exec-out cat <remote>  →  写到本地 ~/Baicodex/config-<时间>.json
```

`remote` 默认值在 `rustcore/src/main.rs` 的 `REMOTE_DEFAULT`：

```rust
const REMOTE_DEFAULT: &str = "/data/local/tmp/baicodex-config.json";
```

**这个路径需要按 Android 端真实的配置位置改**：

- 若是单文件 → 改这个常量即可；
- 若是加密 8 分片目录 → 需要改成 `exec-out tar -c <dir>` 打包拉取，再在 Kotlin 侧解包。

## 已实现 / 未实现

- [x] 窗口 + 淡灰白主题（继承 Android 端色板）
- [x] adb 自动探测 + 手填覆盖
- [x] 列设备（serial / model / state），选中一台
- [x] Rust sidecar 进程调用、超时、错误透传
- [x] 日志面板（带时间戳、可清空）
- [ ] 配置文件真实路径与格式（**待确认**）
- [ ] 分片/加密库导入（tar 打包 + 解密）
- [ ] 配置写入/同步回手机
- [ ] 深色主题