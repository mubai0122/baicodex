# BaiAgent / baicodex

**简体中文** · [English](README.en.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

跑在你自己设备上的 AI Agent。模型直连，读写文件、执行命令、跑 Linux 全在本机完成，不经过任何中转服务器——对话、文件和密钥都只留在你自己的机器里。

它也不只是一个聊天窗口。你说「把后台清一下」「查明天去上海的余票」「这个报错帮我定位」，它自己看屏幕、拆步骤、调工具，一步步做完再回来告诉你结果。

一套 Rust 内核，三种前端：**Android**、**Windows**、**终端**。

- 官网：<https://agent.ai.baiccb.cn>
- 下载：<https://github.com/mubai0122/baicodex/releases/latest>
- 开源免费：GPL-3.0，无内购、无广告、无次数限制，代码全在这里

> `agent` · `ai-agent` · `codex` · `claw` · `android` · `kotlin` · `rust` · `llm` · `local-first` · `mcp`

## 界面预览

| 手机 · 浅色 | 手机 · 深色 |
|:---:|:---:|
| <img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261108" width="280" alt="手机 浅色"> | <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261108" width="280" alt="手机 深色"> |

| 桌面 · 浅色 |
|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="860" alt="桌面 浅色"> |

| 桌面 · 深色 |
|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="860" alt="桌面 深色"> |

## 它和普通 AI App 差在哪

- **真的动手，不只是回话**：Agent 循环带工具调用，读文件、写文件、跑 shell、跑 Linux、点屏幕都是真执行，结果回灌给模型再走下一步
- **权限三档自选**：普通 App 身份就能用（无障碍 + 零宽文件通道），要系统级能力挂 Shizuku，要彻底解锁上 Root，工具内部自动选路并在弹窗里标出实际走了哪条
- **零宽文件通道**：`read_file` / `write_file` / `make_dir` / `delete_file` 能穿透到其他 App 的 data 目录，不用先 root；`exec_shell` 走原路径，不受这个变换影响
- **数据不出门**：会话、角色、技能、用量全落本地 SQLite，不上云；只有你配的模型接口和你主动用的搜索、12306 会出网
- **一套内核多端跑**：Rust 内核同时喂 Android、Windows 和终端，界面各自写，逻辑不分叉
- **可拆开改**：模型、搜索源、工具权限、技能包、思考档位全都能在设置里换，源码改完自己编

## 架构

```
app/src/main/rust/                Rust 内核（Cargo workspace，bin + cdylib + staticlib 都叫 baicodex）
  src/app.rs                      应用服务层：把下面几层装配成「一次会话」
  src/core/base/                  错误 / 时间 / 文本 / ID / 路径 / 原子文件 / JSON / 字节 / 系统
  src/core/security/              策略 / 数据保障 / 加密落盘 / 凭据 / 权限 / 零宽路由 / 沙箱 / 脱敏 / 审计
  src/core/feature/               模型 / 配置 / 存储 / 记忆 / 上下文 / Agent / 工具 / 技能 / MCP /
                                  工作流 / 子智能体 / 人设 / 用量 / 图片 / 插件 / JS
  src/exec/                       系统执行：shell / 文件 / 工作区 / root / shizuku / linux / ssh /
                                  无障碍 / 剪贴板 / 通知 / 闹钟 / 浏览器 / APK
  src/net/                        HTTP / LLM / 搜索 / 12306 / A2A
  src/ffi/                        跨语言契约：进出一律 JSON 字符串，失败也回 JSON
  src/ui/                         终端前端（关掉 tui feature 后内核不依赖任何终端库）
  crates/proto/                   前后端共享协议与代码生成
  crates/host-win/                Windows 宿主：无边框窗口 + DWM 液态玻璃 + WebView2
  android/                        Android 胶水（迁移中）

app/src/main/cpp/                 Android 侧 .so：CMake → libbaicodex.so
  core/base | core/security       与 Rust 同构的基础层与安全层
  ffi/                            JNI 导出（mask / guard / vault / edit / net / blur / js / dsl）
  image/ js/ net/ runtime/ tools/ 历史实现，正在迁往 Rust

app/src/main/kotlin/b/ai/         Android 界面与平台层
  agent/{run,tool,flow,memory,persona,sub}   Agent 循环、工具定义、工作流、记忆、人设、子智能体
  core/{cfg,data,media,misc,model,perm,pet,place,plugin,task}
  device/{shell(local,remote,root),fs,sys,web,app,pet}
  native/{media,net,script,security,text}    JNI 门面，失败一律退回 Kotlin 实现
  net/{api,doc,mcp,search}
  ui/{screen(chat,settings,stat,market,user,pet,misc),dialog,glass,input,overlay,theme,widget}

desktop/web/                      pnpm workspace
  apps/desktop                    Vite 入口，产出 WebView2 直接加载的静态资源
  packages/{app,ui,liquid,tokens,bridge}   界面、玻璃材质、设计变量、内核桥

desktop/legacy-kotlin/            旧 Compose 桌面端，仅作回退参考，不进构建

app/src/main/assets/linux/        内置 Linux 的 rootfs 与 proot
```

三条硬规则，写在 `src/lib.rs` 里也写在代码里：`core/base` 不认识业务；`core/security` 不认识模型；`core/feature` 不直接碰平台——要执行找 `exec`，要发请求找 `net`。

## 三种运行模式（Android）

| 模式 | 需要什么 | 能干什么 |
|---|---|---|
| 普通 App 身份 | 装完就能用 | 对话、文件读写、`exec_shell`（应用身份）、无障碍截屏 / 点击 / 滑动 / 输入、零宽读 data、联网工具 |
| Shizuku | 装 Shizuku 并授权 | 免 Root 执行系统级 shell、`uiautomator dump`、`screencap`、后台应用管理、深读 data |
| Root（su） | 设备已 root | 系统目录读写、pm 静默安装卸载、全局操作，工具内部优先走 su |

三种模式不是互斥开关，是同一个工具表下的三档通道，随时切，切完下一次调用就生效。

## 能力

### 模型与对话

- 多套模型配置：一条 = 提供商 + Base URL + Key + 模型 + 能力标记（视觉 / 工具调用 / 推理），随时切，OpenAI 兼容接口都能接
- 思考档位（low / medium / high）、最大模式、温度与附加参数；SSE 流式输出，思考段可折叠
- 消息分支：重新生成 / 编辑重发不删旧回答，长成第二支，聊天里 `‹ 1/2 ›` 来回切（库 v11 消息树）
- 永久记忆：模型自己判断值得长期记住的偏好与结论存进「记忆」，侧边栏可逐条删；没删就每轮随系统提示下发（库 v12）
- 上下文压缩：窗口比例（默认 70%）+ 未压缩条数双阈值触发，长对话不爆窗口
- 语音输入，说完直接发；每次回答下方显示 token、tok/s、首响耗时、供应商与模型

### 工具与执行

- 内置工具：文件四件套、`exec_shell`、`exec_linux`、`screen`（屏幕操作）、`web_search`、12306 余票 / 中转 / 经停、记忆写入、定时任务
- 搜索源三家可选：Tavily、Bing、Google CSE，Key 自己填，没选就不把搜索工具交给模型
- 执行通道三档（普通 / Shizuku / Root），工具内部自动选路，弹窗标出实际路径
- 工具权限逐条设置：询问 / 允许 / 拒绝，高危工具单独标记；极简模式只留五个工具给弱模型兜底
- 计划模式：只读命令放行，`exec_shell` 里的写操作一律判死，防止「先看看」变成「已经删了」
- 自定义技能包：自己写工具塞进去，模型按描述自己挑
- MCP 客户端：JSON-RPC 2.0 over HTTP，连上的服务能力并入工具表与 @ 面板，工具名前缀 `mcp_服务_能力`
- 工作流：多步任务串成一条流，每步跑一个分智能体，步骤之间不共享上下文
- A2A：按 1.0 规范实现 SendMessage / GetTask / ListTasks / CancelTask，方法名与状态码与规范一致

### 屏幕操作

- 无障碍服务直连：dump 节点树、点击、长按、滑动、输入、按键、开应用、列应用，不需要 Shizuku 也能截图
- 多步任务时屏幕顶部常驻操作胶囊，显示这一步在干什么，一条指令做完自动收起
- 视觉 + 节点树双路：模型能看截图，也能读控件文本和坐标，认不准就换一条路

### 内置 Linux

- proot + 自解包 rootfs，首次启动自动装好，不用 root、不动宿主机
- `apt` 装依赖、`gcc` / `node` 直接编译，C / C++ / Rust / Go 都能在手机上跑
- 与 Android 文件系统双向互通，`/sdcard` 和 `/storage` 直接挂进去
- 沙箱隔离，搞砸了随时重置，不污染系统

### 桌面端（Windows）

- 无边框窗口 + DWM 液态玻璃 + WebView2 渲染面，界面是 `desktop/web` 那套 TypeScript + Vite 代码
- 与手机同一份 Rust 内核，同一个版本号，工具表与配置结构一致
- 出包脚本 `scripts/desktop-build.sh`，产物 `desktop/dist/BaiAgent/{baicodex-desktop.exe, web/}`

### 界面与个性化

- 会话：置顶、长按多选、改名 / 删除、跨会话收藏、每会话独立草稿
- 角色卡（人设 / 场景 / 示例对话 / 开场白 / 绑定模型）与自定义技能包
- 插件广场：浏览、安装、上传、版本历史、评论、点赞收藏
- 外观：纯色 / 柔光渐变 / 点阵背景，液态玻璃卡片与底栏、质感按钮、全局字号、深色模式
- 桌宠、悬浮聊天窗、五语界面（简体中文 / 繁體中文 / English / 日本語 / Français）

### 数据与发布

- 会话、角色、技能、工作区绑定、用量账本全部落本地 SQLite（`bai.db` + `bai_stat.db` 双库），不走云
- 使用统计：总量 / 分类 / 模型维度 + 日期方格图，输入输出缓存三项单价可填、直接算钱
- 整库备份与恢复：一键导出库 / 配置 / 数据根小文件 / 头像为一个文件，导入后下次启动自动替换
- 应用内检查更新（`agent.ai.baiccb.cn/api/update`），直链下载后拉起系统安装器

## 新增功能

1.12.0 这一版补的是「存得稳、看得清」：

- **配置分片加密**：单个配置文件拆成 8 份分别加密保存，改一项设置只重写其中一份
- **加密备份**：导出的备份文件改为加密格式，旧的明文备份仍然能正常恢复
- **摘要三档**：简洁 / 详细 / 全面，字数上限由档位给，不再逐个字数选；摘要正文单独加密存放，会话里只留覆盖标记
- **摘要更懂上下文**：记录你的情绪、诉求与卡点并跨轮次保留；工具的调用轮数与次数、最常用工具会被统计进摘要
- **总结提示词两段式**：分成「最重要」与「当前任务」，设置里填的内容改为追加到内置规则之后，不再整体替换
- **包体从 68.66 MB 降到 40.71 MB**：移除包内预装的一整套 Linux 工具，内置 Linux 本体与 rootfs 照旧保留，功能不少

## 常见问题

**需要 Root 吗？**
不需要。普通 App 身份就能用绝大部分功能；要系统级 shell 挂 Shizuku（建议 Android 11+），要改系统目录才需要 Root。

**真的免费吗？**
是。GPL-3.0 开源，没有内购、订阅、广告或次数限制，也可以自己编一份。

**数据会上传吗？**
不会。会话、文件、密钥都在本机。首次启动有协议页，同意后才会上报匿名使用统计，拒绝就不报，功能不受影响。

**支持哪些 Android 版本？**
Android 7.0（API 24）及以上，targetSdk 34，编译用 SDK 37。APK 含 `arm64-v8a` 与 `armeabi-v7a`。

**桌面端在哪？**
Windows 版由 `rust/crates/host-win` + `desktop/web` 构建，产物挂在 Releases 里；Android 与 Windows 用同一份内核、同一个版本号。

## 构建

### Android

需要 JDK 17、Android SDK 37（`local.properties` 里写 `sdk.dir`）。

```bash
./gradlew assembleDebug     # debug：不混淆、不加密，日志可看
./gradlew assembleRelease   # release：R8 混淆 + 资源裁剪 + 字符串加密
```

release 会做三件事，都在 `app/proguard-rules.pro` 与 `app/build.gradle.kts` 里：

1. **R8 混淆**：除 `MainActivity` 与清单入口、第三方库必需项外全部改名，字典是自造的短名（`app/obfuscation_dict.txt`），并用 `-repackageclasses` 把包名也抹掉
2. **字符串加密**：StringFog，算法 AES，每条字符串独立随机 key
3. **日志清零**：release 里 `Log` / `println` 全部是空语句

字符串加密的密钥不进仓库，从 `local.properties` 读：

```properties
stringfog.key=你自己的一串密钥
baicodex.aes.key=凭据加密用的种子
```

没配也能构建，会退到占位串（占位串等于没加密，别用占位串发版）。

原生部分走 `externalNativeBuild` + CMake（`app/src/main/cpp/CMakeLists.txt`），不单独跑 cmake。

### 内核与桌面端

```bash
cd app/src/main/rust
cargo build --bin baicodex                 # 内核 + 终端前端

cd baiguard && cargo test                   # 数据保障层单测（独立 crate）

cd ../../../..                             # 回到仓库根
bash scripts/desktop-build.sh release      # Windows 出包
```

前端预览：

```bash
cd desktop/web
pnpm install
pnpm --filter @baicodex/desktop dev
```

## 目录速查

```
app/src/main/kotlin/b/ai/   Android 界面与平台层（agent / core / device / native / net / ui）
app/src/main/cpp/           Android 原生层（JNI、QuickJS、图像、原生执行）
app/src/main/rust/          Rust 内核（core / exec / net / ffi / crates / android）
app/src/main/assets/linux/  内置 Linux 的 rootfs 与 proot
desktop/web/                桌面端前端（pnpm workspace）
desktop/legacy-kotlin/      旧 Compose 桌面端，仅参考
scripts/                    构建与发版脚本
```

## 协议

GNU General Public License v3.0，全文见 [LICENSE](LICENSE)。

Copyright (C) 2026 沐白 (mubai0122)