<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

**简体中文** · [English](README.en.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

**把 Android 当成自己身体的 AI Agent。** 普通身份就能零宽穿透读写任何 App 的 data，挂上 Shizuku 或 Root 直接进系统底层，Linux 想跑什么跑什么——全是真执行，不是演示。

它不止会聊天。你说「把后台清一下」「查明天去上海的余票」「这个报错帮我定位」，它自己看屏幕、拆步骤、调工具，做完再把结果交回来。模型直连，对话、文件、密钥都留在本机，不经过任何中转服务器。

一套 Rust 内核，三种前端：**Android**、**Windows**、**终端**。

- 官网：<https://agent.ai.baiccb.cn>
- 下载：<https://github.com/mubai0122/baicodex/releases/latest>
- 开源免费：GPL-3.0，无内购、无广告、无次数限制，代码全在这里

> `agent` · `ai-agent` · `codex` · `claw` · `android` · `kotlin` · `rust` · `llm` · `local-first` · `mcp`

## 一眼看完的几个重点

| 能力 | 一句话 |
|---|---|
| **三档模式** | 普通 App 身份 / Shizuku / Root 是同一张工具表下的三条通道，工具自己选路并在弹窗里标出这次实际走了哪条 |
| **零宽绕过** | 受限目录映射成虚拟路径，`read_file` / `write_file` / `make_dir` / `delete_file` 普通身份就能读写别的 App 的 data，不用先 root |
| **虚拟沙箱** | 内置 proot Linux + 路径沙箱双闸：rootfs 搞坏了随时重置，软链和敏感系统文件在动手前就拦下，宿主机一点不脏 |
| **角色卡** | 人设、场景、示例对话、开场白、绑定模型，头像可跟角色走；换角色等于换一个人格和一套说话方式 |
| **技能与插件** | 自己写工具的 Skill 包、插件市场、MCP 接入三条路，能力装进去就有，不用等 App 发版 |
| **上下文总结** | 摘要分简洁 / 详细 / 全面三档，提示词分「最重要」与「当前任务」两段，长对话不越聊越傻 |
| **UI** | 液态玻璃贯穿卡片、输入区、悬浮底栏与弹窗，深色、壁纸、全局字号、圆角与胶囊两套版面都能调 |
| **可玩性** | MCP 标准 JSON-RPC 与 REST 桥都吃（`termux-mcp`、`mt-mcp` 这类直接连），A2A 能跟别的 Agent 互相派活，再加桌宠与悬浮聊天窗 |

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
- **零宽绕过**（零宽文件通道）：`read_file` / `write_file` / `make_dir` / `delete_file` 把受限目录映射成虚拟路径，能穿透到其他 App 的 data 目录，不用先 root；`exec_shell` 走原路径，不受这个变换影响
- **数据不出门**：会话、角色、技能、用量全落本地 SQLite，不上云；只有你配的模型接口和你主动用的搜索、12306 会出网
- **一套内核多端跑**：Rust 内核同时喂 Android、Windows 和终端，界面各自写，逻辑不分叉
- **可拆开改**：模型、搜索源、工具权限、技能包、思考档位全都能在设置里换，源码改完自己编

## 为什么它更强

不是为了把功能列表堆长，而是几处关键取舍做得更彻底：

- **上下文是真在管，不是攒够了就砍**：摘要分简洁 / 详细 / 全面三档，提示词分「最重要」与「当前任务」两段——跨轮次不缩减的那段永远在，可压缩的那段才动；压缩要同时满足窗口比例与条数双阈值才触发，长对话不会越聊越傻
- **答复可以分支**：重新生成、编辑重发都不删旧回答，长成第二支，聊天里 `‹ 1/2 ›` 来回切，正确答案留在原处
- **执行走真通道**：工具内部自动在 普通 / Shizuku / Root 三档里选路，并在弹窗里标出实际走了哪条；判不清就不糊弄，直接告诉你原因
- **弱模型也能用**：工具权限逐条可设，极简模式只留五个工具，配合思考档位，小模型不至于被工具表压垮
- **密钥与配置分开加密存**：API Key、账号与 SSH、技能密钥各存一份密文，配置也拆片加密，改一项只重写一片；换机器导出备份也是加密的
- **一套内核跑三端**：Android、Windows、终端共用同一套逻辑，不存在「手机修了电脑没修」
- **流畅是抠出来的**：玻璃质感只在真弹窗打开时降档（抽屉不算），桌宠动画按 30fps 节流，离屏渲染节点错峰建立——这些不是文案，是代码里的硬约束

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
- MCP 客户端：标准 JSON-RPC 2.0 over HTTP（`tools/list` + `tools/call`），另有一路 REST 桥（`GET /tools` + `run`）——Termux 里的 `termux-mcp`、本机 `mt-mcp`、以及任何标准 MCP 服务都能直接接；连上的能力并入工具表与 @ 面板，名字前缀 `mcp_服务_能力`
- 工作流：多步任务串成一条流，每步跑一个分智能体，步骤之间不共享上下文
- A2A：按 1.0 规范实现 SendMessage / GetTask / ListTasks / CancelTask，方法名与状态码与规范一致——别的 Agent 能给你派活，你的 Agent 也能把活派出去

### 屏幕操作

- 无障碍服务直连：dump 节点树、点击、长按、滑动、输入、按键、开应用、列应用，不需要 Shizuku 也能截图
- 多步任务时屏幕顶部常驻操作胶囊，显示这一步在干什么，一条指令做完自动收起
- 视觉 + 节点树双路：模型能看截图，也能读控件文本和坐标，认不准就换一条路

### 内置 Linux

- **虚拟沙盒**：proot + 自解包 rootfs，首次启动自动装好，不用 root、不动宿主机，跑坏了重开就是干净的
- `apt` 装依赖、`gcc` / `node` 直接编译，C / C++ / Rust / Go 都能在手机上跑
- 与 Android 文件系统双向互通，`/sdcard` 和 `/storage` 直接挂进去
- 跟路径沙箱是两道闸：沙箱管「这个路径准不准动」（敏感系统文件、`..`、软链都在动手前拦），proot 管「动坏了也伤不到系统」

### 桌面端（Windows）

- 无边框窗口 + DWM 液态玻璃 + WebView2 渲染面，界面是 `desktop/web` 那套 TypeScript + Vite 代码
- 与手机同一份 Rust 内核，同一个版本号，工具表与配置结构一致
- 出包脚本 `scripts/desktop-build.sh`，产物 `desktop/dist/BaiAgent/{baicodex-desktop.exe, web/}`

### 界面与个性化

- **外观材质**：纯色 / 柔光渐变 / 点阵背景，可换自定义壁纸（切图时先出缩略图再上真图，冷启动不闪白）
- **液态玻璃**：卡片、输入框、悬浮底栏、弹窗走同一套玻璃材质，可整体关闭退回白卡；另有质感按钮风格，与玻璃互斥，开哪个都行
- **版面与字号**：全局字号独立调，消息气泡有圆角与胶囊两套版面，聊天头像显示可关
- **深色模式**：可切，玻璃、壁纸与文字色跟着一起换
- **角色卡**：人设、场景、示例对话、开场白、绑定模型；聊天头像可选「跟当前角色卡」或另选一张自己传图
- **桌宠**：常驻应用内的浮动形象，动画按 30fps 节流，不会一开就把整页拖卡
- **悬浮聊天窗**：在别的 App 上层直接聊，宽高、字号、透明度、位置都能拖，和主界面共用同一份会话
- **会话管理**：置顶、长按多选、改名 / 删除、跨会话收藏、每会话独立草稿
- **插件广场**：浏览、安装、上传、版本历史、评论、点赞收藏
- **五语界面**：简体中文 / 繁體中文 / English / 日本語 / Français

### 数据与发布

- 会话、角色、技能、工作区绑定、用量账本全部落本地 SQLite（`bai.db` + `bai_stat.db` 双库），不走云
- 使用统计：总量 / 分类 / 模型维度 + 日期方格图，输入输出缓存三项单价可填、直接算钱
- 整库备份与恢复：一键导出库 / 配置 / 数据根小文件 / 头像为一个文件，导入后下次启动自动替换
- 应用内检查更新（`agent.ai.baiccb.cn/api/update`），直链下载后拉起系统安装器

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