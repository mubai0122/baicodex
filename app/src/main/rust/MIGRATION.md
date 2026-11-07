# 迁移说明：逻辑层归 Rust，Kotlin 只留界面

这份文档回答三个问题：**现在是什么结构**、**哪些文件没了 / 换成什么**、**还剩什么活**。

---

## 1. 结构

```
src/                       ← Rust 内核（122 文件 / 27030 行 / 227 个测试）
├── app.rs                 前端门面 Facade
├── core/
│   ├── base/              基础设施：错误、时间、文本、ID、路径、原子写、JSON、字节、系统
│   ├── security/          安全：策略、三层密钥、加密落盘、凭据、权限、零宽路由、沙箱、脱敏、审计
│   └── feature/           功能：模型、配置、存储、记忆、上下文、Agent、工具、技能、MCP、
│                          工作流、子智能体、人设、用量、图片、插件、JS
├── exec/                  系统执行：shell / root / shizuku / linux / 文件 / 工作区 / 无障碍 /
│                          剪贴板 / 通知 / 闹钟表达式 / SSH / APK / JS
├── net/                   HTTP / LLM / 搜索 / 12306 / A2A / MCP / 浏览器
├── ffi/                   跨语言边界（JSON 进出）+ JNI 导出
└── ui/                    终端前端（repl / tui），与安卓共用同一个内核

android/app/src/main/java/
├── b/ai/native/           ← 新增：唯一的 JNI 通道
│   ├── NativeBridge.kt      call / 事件流 / 错误类型 / 线程
│   ├── Kernel.kt            88 个 op 的类型化封装
│   └── AppEvent.kt          事件模型 + KernelException
├── b/ai/core/             ← 保留数据模型与平台胶水，逻辑对象改为薄门面
│   ├── Model.kt             保留（纯数据类：ChatMsg / SessionRow / Route / MsgMeta …）
│   ├── Perms.kt             保留（Context / Intent / Settings，属于平台 API）
│   ├── Agent.kt             重写：Compose 状态镜像 + 委托
│   ├── Cfg.kt               重写：行为参数→Rust，外观参数→SharedPreferences
│   └── Repo.kt              重写：SQL 全在 Rust，这里只做 JSON→data class
├── b/ai/ui/               ← 不动（53 个 Compose 文件）
└── b/ai/exec/             ← 只留 Android 服务与平台绑定（A11yService / NotiSvc / TaskReceiver 等）

android/app/src/main/cpp/
├── rust_bridge.h/.cpp     ← 新增：C++ 侧唯一的 C ABI 封装（约 200 行）
└── CMakeLists.txt         ← 可选：只有还要写 C++ 时才挂上
```

**一条硬规则**：`b/ai/ui/**` 不出现 `org.json`，不出现 SQL，不出现 HTTP。界面只读
`Agent` / `Cfg` / `Repo` 暴露的 data class，只调它们的方法。

---

## 2. Kotlin：删了什么，换成什么

### 2.1 直接删除（逻辑已在 Rust）

| 原文件 | 现在在 Rust 的哪里 |
|---|---|
| `core/Db.kt` `core/StatDb.kt` `core/Repo.kt` | `core/feature/storage/*`（建表、迁移、消息树、递归 CTE） |
| `core/Vault.kt` `core/Guard.kt` | `core/security/vault` `core/security/guard` `core/security/secret` |
| `core/Skill.kt` `core/SkillPack.kt` `core/Dsl.kt` `core/Js.kt` | `core/feature/skill/*`、`core/feature/js`、`exec/js` |
| `core/Mcp.kt` `core/Flows.kt` `core/Persona.kt` `core/Profiles.kt` | `core/feature/{mcp,workflow,persona}`、`config/profile` |
| `core/Memory.kt` `core/Usage.kt` `core/Prices.kt` | `core/feature/{memory,usage}` |
| `core/Search.kt` `core/Stations.kt` | `net/{search,rail}` |
| `core/TextEdit.kt` `core/Dsl.kt` | `exec/file_ops`（按行匹配 + 缩进对齐） |
| `core/Blur.kt` `core/Images.kt` `core/Attach.kt` | `core/feature/image`、`exec/a11y`（截图） |
| `core/Backup.kt` | ffi op `backup`（打包 db + 配置） |
| `core/A2aNodes.kt` | `net/a2a` |
| `core/Bai.kt`（18 行的转发壳） | `app::Facade` |
| `agent/Agent.kt` `agent/Tools.kt` `agent/SubAgent.kt` `agent/FlowRunner.kt` | `core/feature/agent/*`、`tool/dispatch`、`subagent` |
| `exec/Shell.kt` `Root.kt` `Shiz.kt` `ShizukuShell.kt` `Router.kt` `FileOps.kt` | `exec/{shell,root,shizuku}`、`security/route`、`exec/file_ops` |
| `exec/Ssh.kt` `exec/Browser.kt` `exec/Capcule.kt` `exec/Capsule.kt` | `exec/ssh`、`net/browser`、`exec/capsule` |
| `exec/A2aSrv.kt` `exec/Workspace.kt` | `net/a2a`、`exec/workspace` |
| `net/LlmClient.kt` `net/Net.kt` `net/Auth.kt` `net/Tavily.kt` `net/Bing.kt` `net/GoogleCse.kt` `net/Rail.kt` | `net/{llm,client,http,search,rail}` |
| `net/Update.kt` `net/Plugins.kt` `net/Feedback.kt` `net/Isuse.kt` `net/TosDoc.kt` `net/Consent.kt` | `core/feature/plugin` + `net/http`（清单/接口调用） |
| `core/Place.kt` `core/Env.kt` `core/Paths.kt` | `exec/device`、`core/base/{dirs,sys,path}` |
| `core/Store.kt` `core/Catalog.kt` `core/Brands.kt` `core/Cards.kt` `core/Pastes.kt` | `core/feature/config` + `net/http` |
| `core/Waifu.kt` `core/PetBalance.kt` `core/PetLines.kt` `core/PetSay.kt` | `core/feature/{persona,usage}` |

### 2.2 保留（属于平台 API 或纯数据）

| 文件 | 为什么留 |
|---|---|
| `core/Model.kt` `core/AgentMode.kt` `core/Lang.kt` `core/Effort.kt` `core/Brands.kt`(常量部分) | 纯数据类 / 枚举，两边都要用 |
| `core/Perms.kt` | `Context`、`Intent`、`Settings`，只能在 Android 侧做 |
| `core/Noti.kt` `exec/NotiSvc.kt` `exec/TaskReceiver.kt` `exec/Alarms.kt` `exec/Pet.kt` `exec/PetGrant.kt` `exec/FChat.kt` `exec/A11y.kt`(Service 部分) | 系统服务、广播接收器、悬浮窗 |
| `core/Audio.kt` `core/Speak.kt` `core/Voice.kt` `core/AsrPresets.kt` `core/PetSound.kt` `core/Avatar.kt` | `MediaPlayer` / `AudioRecord` / TTS 绑定 |
| `core/ApkInstall.kt` `core/PluginInstall.kt` `core/PluginNote.kt` `core/Schedules.kt` | `PackageInstaller` / `AlarmManager` / 文件选择器 |
| `core/PetLines.kt` `core/PetSay.kt` `exec/Diff.kt` `exec/Toolchain.kt` | 纯 UI 文案与展示用 diff |

### 2.3 重写（薄门面）

| 文件 | 现在的职责 |
|---|---|
| `b/ai/native/NativeBridge.kt` | JNI 调用、事件流转 Flow、错误类型、线程约束 |
| `b/ai/native/Kernel.kt` | 88 个 op 的类型化封装（配置/会话/消息/工具/记忆/技能/MCP/工作流/用量/凭据/文件/设备/插件） |
| `b/ai/core/Agent.kt` | Compose 状态镜像；事件→状态；动作→`Kernel` |
| `b/ai/core/Cfg.kt` | 行为参数读写 Rust；外观参数存 SP |
| `b/ai/core/Repo.kt` | JSON→data class；SQL 一行都没有 |

### 2.4 事件流

内核不再通过回调把整条消息推给界面，而是推**结构化事件**：

| 事件 | 界面该做什么 |
|---|---|
| `delta` | 往当前气泡追加正文/思考 |
| `user_message` | 用返回的 id 与正文画用户气泡（不要自己先画） |
| `message` | 消息已落库 → 重新拉一次列表（去重靠 id） |
| `tool_start` / `tool_end` | 画转圈；seq 是配对键 |
| `confirm` / `ask` / `plan` | 拉 `pending` op 拿详情，弹对应面板 |
| `error` | 直接显示 `message`，不要自己拼文案 |
| `sessions` | 刷新会话列表 |
| `usage` | 刷新用量页 |

---

## 3. C++：27 个文件 → 1 个头文件

| 原文件 | 现在 |
|---|---|
| `tools/dsl.cpp/.h` | `core/feature/skill/pack.rs`（模板渲染 + 静态 lint + 占位符校验） |
| `tools/edit.cpp/.h` | `exec/file_ops.rs::edit`（按行匹配、忽略缩进、命中多处报错） |
| `image/blur.cpp/.h` | 移到 Kotlin（`RenderScript`/`RenderEffect` 属渲染层，Rust 不做像素） |
| `js/qjs.cpp` `js/bridge.cpp` | `exec/js.rs`（rquickjs 内嵌；没有就退化到系统 `qjs`/`node`） |
| `net/http.cpp` | `net/{client,http}.rs` |
| `runtime/secret.cpp` | `core/security/secret/*`（v1/v2 格式逐字节兼容） |
| `runtime/guard.cpp` | `core/security/guard/*`（同 HKDF 参数、同片段值） |
| `runtime/budget.cpp` | `core/feature/context/budget.rs` |
| `jni/*_jni.cpp`（8 个） | `ffi/jni.rs`（只导出 3 个方法）+ `ffi/mod.rs` 的 C ABI |

保留的只有 `rust_bridge.h/.cpp`：C++ 侧的 JSON 封装，约 200 行，无业务逻辑。

---

## 4. 构建

Rust 产物是 `libbaicodex.so`，直接放进 `jniLibs`：

```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 \
  -o android/app/src/main/jniLibs \
  build --release --no-default-features --features js
```

`build.gradle.kts` 里：

```kotlin
android {
    sourceSets["main"].jniLibs.srcDir("src/main/jniLibs")
    // 纯 Kotlin 前端：不要开 externalNativeBuild（见 CMakeLists.txt 顶部说明）
    packaging { jniLibs { useLegacyPackaging = false } }
}
```

启动顺序（`App.onCreate`）：

```kotlin
Cfg.init(this)                                   // 只开 SharedPreferences
Agent.start(filesDir.resolve("baicode").path)    // boot + 订阅事件
lifecycleScope.launch { Cfg.load() }             // 从内核拉行为参数
```

---

## 5. 还差什么

Rust 侧已经完整可用。剩下的是 Kotlin 侧的**机械替换**：还有 17 个逻辑对象在
`ui/` 里有调用点（约 300 处），需要按上表逐个换成 `Kernel.xxx` 或删除。

| 对象 | UI 调用点数 | 建议 |
|---|---|---|
| `Plugins` | 52 | 全部换成 `Kernel.plugins()/installPlugin()/removePlugin()` |
| `Feedback` | 40 | 换成 `net/http` 的 op（或先保留，它只是一次 POST） |
| `Workspace` | 35 | 换成 `Kernel.workspace()/bindWorkspace()/fsList()` |
| `Profiles` | 31 | 换成 `Kernel.profiles()/saveProfile()/useProfile()` |
| `Tools` | 22 | 换成 `Kernel.tools()/callTool()` |
| `Perms` | 18 | **保留**（平台 API） |
| `Alarms` | 16 | **保留**（AlarmManager），只把表达式解析换成 `exec/alarms` 的规则 |
| `Auth` | 12 | 换成 `Kernel.vaultPut/vaultKeys` + `net/http` |
| `Skills` | 10 | 换成 `Kernel.skills()/skillGet()/skillDelete()` |
| `Router` | 10 | 只剩展示用（路由决策在 Rust），读 `snapshot` 里的字段 |
| `Repo` | 7 | 已重写，直接可用 |
| `Update` `Providers` `Paths` `Avatar` `Images` `AgentMode` `Noti` `Mcp` `PetSound` | 各 2–6 | 前六个换 `Kernel`，`Noti`/`PetSound`/`Avatar` 保留（平台） |

替换原则只有一条：**界面代码里不允许出现判断**。看到 `if (x > 0) { ...请求... }`
就说明这段逻辑该搬回 Rust，而不是在 Kotlin 里换个写法。
