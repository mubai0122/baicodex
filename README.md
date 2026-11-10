<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

**简体中文** · [English](README.en.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

**把 Android 当成自己身体的 AI Agent。** 它不只是聊天：你说「把后台清一下」「这个报错帮我定位」「写个脚本跑一下」，它会自己看屏幕、拆步骤、调工具、写代码、编译构建，做完再把结果交回来。全是真执行，不是演示。

模型直连，对话、文件、密钥都留在本机，不经过任何中转服务器。

一套 Rust 内核，三种前端：**Android**、**Windows**、**终端**。

- 官网：<https://agent.ai.baiccb.cn>
- 教程（如何精通 AndroidAgent）：<https://agent.ai.baiccb.cn/docs/>
- 下载：<https://github.com/mubai0122/baicodex/releases/latest>
- 开源协议：AGPL-3.0，无内购、无广告、无次数限制；闭源集成与 SaaS 部署需商业许可

> `agent` · `ai-agent` · `codex` · `claw` · `android` · `kotlin` · `rust` · `llm` · `local-first` · `mcp` · `mijia` · `netease-music` · `lan-collaboration`

---

## 亮点

1. **Android 上的真 Agent，三档权限发挥最强**
   普通 App 身份装完就能用，挂 Shizuku 免 Root 拿系统级能力，上 Root 彻底解锁。三档不是互斥开关，是同一张工具表下的三条通道，工具内部自动选路，弹窗标出这次实际走了哪条。
   <sub>普通身份的零宽文件通道（读写任意 App 的 data）并非所有机型都支持，装机后在 App 内实测为准。</sub>

2. **内置虚拟 Linux 沙箱，真能跑东西**
   proot + 自解包 Ubuntu 24.04 rootfs，装在应用私有目录，不动宿主机，跑坏了重置就是干净的。apt 装依赖、gcc / node 直接编译，C / C++ / Rust / Go 都能跑；跑 Windows exe 也不用另找源——arm64 仓库自带 `box64` 与 `wine 9.0`，装完就能起，图形程序再补 Xvfb / VNC 看画面。手机上性能有限，跑 Android Studio 这类重型 IDE 是设想——但「能跑」这件事本身是真的。
   <sub>执行 guest 程序需要 Root / Shizuku 通道（targetSdk 34 下 SELinux 禁止从应用私有目录 execve）。</sub>

3. **多智能体，可切换**
   角色卡换人设、场景、开场白、绑定模型，换角色等于换一个人格和一套说话方式；A2A 能跟别的 Agent 互相派活。
   <sub>3.1 大肥鱼桌宠：常驻应用内的浮动形象，动画 30fps 节流，不会一开就把整页拖卡。</sub>

4. **绝对的安全**
   凭据 AES-256-GCM 落盘，设备密钥由 Android Keystore（TEE / StrongBox）包裹，明文不出硬件；模型读不到密钥原文，出口统一脱敏，`sk-` / `ghp_` / PEM / Bearer 在写日志、回灌模型、导出诊断包前全部抹掉。

5. **屏幕操作，无需 Root**
   无障碍服务直连：dump 节点树、点击、长按、滑动、输入、按键、开应用、列应用，不需要 Shizuku 也能截图。视觉 + 节点树双路，模型认不准就换一条。

6. **MCP 可玩性高**
   标准 JSON-RPC 2.0 over HTTP + REST 桥两路都吃，Termux 里的 termux-mcp、本机 mt-mcp、任何标准 MCP 服务直接连；反过来，别人也能把 baicodex 当 MCP 用。

7. **SSH / GitHub / Skill，扩展性拉满**
   SSH 八件套（连接 / 列目录 / 读 / 写 / 提权 / 执行 / 上传 / 下载），sudo 密码由工具喂给 `sudo -S` 不进命令行；GitHub 技能包走 REST API 查仓库、issue / PR、读文件、提交改动；私有协议 skill 配独立 JS 引擎（QuickJS），通用 skill 也兼容。

8. **上下文压缩，不降智不丢记忆**
   摘要切点退到「工具调用与其结果之间」的安全边界，强制保留结论与文件路径；窗口比例 + 条数双阈值才触发，省不到 15% 就不压；长期记忆单独入库，不随压缩消失。

9. **长期记忆**
   模型自己判断值得长期记住的偏好与结论存进「记忆」，每轮随系统提示下发，侧边栏可逐条删。

10. **技术栈广**
    Rust 内核 + C++ 原生层 + Kotlin / Compose 界面 + TypeScript / WebView2 桌面端，一套内核三端跑。

11. **米家、网易云音乐、局域网协作都是原生实现**
    米家走自己的 C 层密码学（AES-128-CBC / MD5 / SHA-1，带标准向量自检），控制链路局域网优先、云端兜底；网易云音乐走 weapi 加密通道（两次 AES-128-CBC + 模幂），验证码登录、cookie 回填与登录态校验全在本地完成；同一 Wi-Fi 下的多台设备用 6 位配对码握手后互相派活、互传文件。三套工具躺在同一张工具表里：查设备、调空调、清缓存、把日志派给隔壁那台机器，一句话里就能串完。

---

## 界面预览

**手机 · 浅色**　　**手机 · 深色**

<img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261108" width="280" alt="手机 浅色"> <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261108" width="280" alt="手机 深色">

**桌面 · 浅色**

<img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="860" alt="桌面 浅色">

**桌面 · 深色**

<img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="860" alt="桌面 深色">

---

## 它和普通 AI App 差在哪

- **真的动手，不只是回话**：Agent 循环带工具调用，读文件、写文件、跑 shell、跑 Linux、点屏幕都是真执行，结果回灌给模型再走下一步
- **权限三档自选**：普通 App 身份就能用（无障碍 + 零宽文件通道），要系统级能力挂 Shizuku，要彻底解锁上 Root，工具内部自动选路并在弹窗里标出实际走了哪条
- **零宽绕过**：`read_file` / `write_file` / `make_dir` / `delete_file` 把受限目录映射成虚拟路径，能穿透到其他 App 的 data 目录，不用先 root；`exec_shell` 走原路径，不受这个变换影响
- **数据不出门**：会话、角色、技能、用量全落本地 SQLite，不上云；只有你配的模型接口和你主动用的搜索、12306 会出网
- **一套内核多端跑**：Rust 内核同时喂 Android、Windows 和终端，界面各自写，逻辑不分叉
- **可拆开改**：模型、搜索源、工具权限、技能包、思考档位全都能在设置里换，源码改完自己编

---

## 能力

### 模型与对话

- 多套模型配置：一条 = 提供商 + Base URL + Key + 模型 + 能力标记（视觉 / 工具调用 / 推理），随时切，OpenAI 兼容接口都能接
- 思考档位（low / medium / high）、最大模式、温度与附加参数；SSE 流式输出，正文、思考段（`reasoning_content`）与 `tool_calls` 参数都是边收边渲染，思考段可折叠；服务端忽略 `stream=true` 返回整包时自动降级解析，不报错
- 消息分支：重新生成 / 编辑重发都不删旧回答，长成第二支，聊天里 `‹ 1/2 ›` 来回切（库 v11 消息树）
- 永久记忆：模型自己判断值得长期记住的偏好与结论存进「记忆」，侧边栏可逐条删；没删就每轮随系统提示下发（库 v12）
- 上下文压缩不丢记忆：窗口比例（默认 70%）+ 未压缩条数双阈值触发，切点退到「工具调用与其结果之间」的安全边界（切中间请求体会非法），摘要里强制留结论与改动过的文件路径；省不到 15% 就不压；长期记忆单独入库，不跟着摘要一起消失
- 语音输入，说完直接发；每次回答下方显示 token、tok/s、首响耗时、供应商与模型

### 工具与执行

- 内置工具：文件五件套（读 / 写 / 局部改 / 建目录 / 删）、`exec_shell`、`linux_shell`、`screen`（屏幕操作）、`browser`、`http_fetch`、`apk`（反查签名与 dex）、`js`（QuickJS 跑脚本，还能注册新工具）、12306 余票 / 中转 / 经停、记忆写入、定时任务
- SSH 一组八件：`ssh_connect` / `ssh_ls` / `ssh_read` / `ssh_write` / `ssh_sudo` / `ssh_exec` / `ssh_get` / `ssh_put`——主机与凭据存一次复用；内置 Ubuntu 缺 openssh-client 会自动补装；sudo 密码由工具喂给 `sudo -S`，不许也不必要写进命令
- GitHub 技能包：走 REST API 查仓库 / issue / PR、读仓库文件、新建或更新单个文件，token 走 `$secret.github_token`，不需要本地 git、不用装 gh
- Web 抓取两条路：`web_search`（搜索源三家可选：Tavily、Bing、Google CSE，Key 自己填，没选就不把搜索工具交给模型）、`http_fetch`（直发 HTTP，返回状态码 + 正文前 8000 字）
- 内置浏览器：`browser` 是真 WebView，不是爬虫，读的就是浏览器渲染完的页面——`open` 打开并回正文，`text` / `links` / `find` 取内容、列链接、按文字找元素，`click` / `input` / `scroll` 按文字点按、填表、翻页，`eval` 直接跑 JS，外加 `back` / `forward` / `reload` / `url` / `close`；要登录、要跑 JS 才出内容的页面走这一条
- 执行通道三档（普通 / Shizuku / Root），工具内部自动选路，弹窗标出实际路径
- 工具权限逐条设置：询问 / 允许 / 拒绝，高危工具单独标记；极简模式只留五个工具给弱模型兜底
- 计划模式：只读命令放行，`exec_shell` 里的写操作一律判死，防止「先看看」变成「已经删了」
- 自定义技能包：自己写工具塞进去，模型按描述自己挑
- MCP 客户端：标准 JSON-RPC 2.0 over HTTP（`tools/list` + `tools/call`），另有一路 REST 桥（`GET /tools` + `run`）——Termux 里的 termux-mcp、本机 mt-mcp、以及任何标准 MCP 服务都能直接接；连上的能力并入工具表与 @ 面板，名字前缀 `mcp_服务_能力`
- 工作流：多步任务串成一条流，每步跑一个分智能体，步骤之间不共享上下文
- A2A：按 1.0 规范实现 SendMessage / GetTask / ListTasks / CancelTask，方法名与状态码与规范一致——别的 Agent 能给你派活，你的 Agent 也能把活派出去

### 米家与智能家居

- 报文密码学在原生 C 层：AES-128-CBC 与 MD5 / SHA-1，带标准向量自检，米家专区页直接显示自检结果与本次构建是否已注入接入身份
- 三块子页：登录与账号（授权页取 code 换 token、登录态与退出）、设备（台账 / 扫描 / 手动添加 / 属性读写）、通道（区域、局域网优先、超时与诊断）
- 工具五个：`mijia_devices`、`mijia_scan`、`mijia_read`、`mijia_write`、`mijia_action`；写属性与调动作算高危工具，要二次确认，计划模式下直接禁掉
- 控制链路局域网优先：局域网通得了就走局域网，不通再回落云端
- 局域网传输下沉到 C++：UDP 广播探测与单播请求应答在原生层，探测包、报文头与 JSON 取数由 Rust 提供，带 cargo 单测

### 音乐（网易云）

- 登录走 weapi 加密通道：两次 AES-128-CBC 在 C 层 OpenSSL 里做，随机密钥走模幂；手机验证码登录，cookie 校验通过才回填
- 登录后真去查一次账号拿昵称，cookie 失效自动回滚并提示重新获取，不装成「已登录」
- 平台侧：启用与优先级（一次只启用一家）、音质档位、缓存占用与清理，登录态进加密库
- 工具三个：`music_platforms`、`music_login`（`verify` / `sms` / `code` 三个动作，验证码流程模型自己跑完）、`music_cache_clear`
- 与米家共用同一张工具表：一句话里可以「看登录还在不在 → 清缓存 → 把客厅空调调到 26 度」，模型自己排顺序；取歌与播放还没做，音乐侧目前管的是平台、登录与缓存

### 局域网团队协作

- UDP 广播发现 + 6 位配对码握手：配对码只上行摘要，不上行明文码
- 已配对设备台账与探活；配对握手时交换接活端口与凭据，两台设备各自的大模型可以互相派活
- 局域网配置页：发现开关、本机名称与配对码、扫描与配对、已配对设备移除、传输端口与并发、热点设置入口
- `lan` 工具：查状态、开接活服务、扫描、配对、解除、看对端能力、派任务与取消
- 同一 Wi-Fi 下多台设备互传文件，端口与并发自己定

### 屏幕操作

- 无障碍服务直连：dump 节点树、点击、长按、滑动、输入、按键、开应用、列应用，不需要 Shizuku 也能截图
- 多步任务时屏幕顶部常驻操作胶囊，显示这一步在干什么，一条指令做完自动收起
- 视觉 + 节点树双路：模型能看截图，也能读控件文本和坐标，认不准就换一条路

### 内置 Linux

- **虚拟沙盒**：proot + 自解包 Ubuntu 24.04 rootfs，装在应用私有目录，不动宿主机，跑坏了重开就是干净的
- `apt` 装依赖、`gcc` / `node` 直接编译，C / C++ / Rust / Go 都能在手机上跑；arm64 的 noble 仓库自带 `box64` 与 `wine 9.0`，装完可以跑 Windows exe，图形程序再补 Xvfb / VNC 看画面
- 与 Android 文件系统双向互通，`/sdcard` 和 `/storage` 直接挂进去
- 跟路径沙箱是两道闸：沙箱管「这个路径准不准动」（敏感系统文件、`..`、软链都在动手前拦），proot 管「动坏了也伤不到系统」
- **现状**：执行 guest 程序需要 Root / Shizuku 通道（targetSdk 34 下 SELinux 禁止从应用私有目录 `execve`）

### 桌面端（Windows）

- 无边框窗口 + DWM 液态玻璃 + WebView2 渲染面，前端是 TypeScript + Vite
- 与手机同一份 Rust 内核，同一个版本号，工具表与配置结构一致
- 桌面端前端已从本仓库剥离、单独维护，仓库里只留与它共用的内核

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

---

## 三种运行模式（Android）

| 模式 | 需要什么 | 能干什么 |
|---|---|---|
| 普通 App 身份 | 装完就能用 | 对话、文件读写、`exec_shell`（应用身份）、无障碍截屏 / 点击 / 滑动 / 输入、零宽读 data、联网工具 |
| Shizuku | 装 Shizuku 并授权 | 免 Root 执行系统级 shell、`uiautomator dump`、`screencap`、后台应用管理、深读 data |
| Root（su） | 设备已 root | 系统目录读写、pm 静默安装卸载、全局操作，工具内部优先走 su |

三种模式不是互斥开关，是同一个工具表下的三档通道，随时切，切完下一次调用就生效。

<sub>注：普通身份的零宽文件通道受机型 / 系统限制，部分机型可用、部分不行，以 App 内实测为准。内置 Linux 沙箱需要 Root / Shizuku 通道。</sub>

---

## 安全

三层密钥（Keystore 包裹 + HKDF 半密钥 + 数据密钥），凭据 AES-256-GCM 落盘（文件头魔数 `baicodexencryption`），API Key、账号、SSH 与 GitHub 凭据、技能密钥各存一份密文；模型读不到密钥原文，出口统一脱敏；未解锁时凭据退到明文 JSON + 0600，界面直接标注「未加密」，不假装安全。

---

## 常见问题

**需要 Root 吗？**
不需要。普通 App 身份就能用绝大部分功能；要系统级 shell 挂 Shizuku（建议 Android 11+），要改系统目录才需要 Root。内置 Linux 沙箱是例外——需要 Root / Shizuku 通道才能执行里面的程序。

**真的免费吗？**
是。AGPL-3.0 开源，没有内购、订阅、广告或次数限制，也可以自己编一份。要把它集成进闭源产品、或作为 SaaS 对外提供想豁免 AGPL 的开源义务，需先取得商业许可，见下方「协议」。

**数据会上传吗？**
不会。会话、文件、密钥都在本机。首次启动有协议页，同意后才会上报匿名使用统计，拒绝就不报，功能不受影响。

**支持哪些 Android 版本？**
Android 7.0（API 24）及以上，targetSdk 34，编译用 SDK 37。APK 含 `arm64-v8a` 与 `armeabi-v7a`。

---

## 开发者

### 扩展方式

- **技能包（Skill）**：自己写工具塞进去，模型按描述自己挑；配独立 JS 引擎（QuickJS），能跑脚本、也能注册新工具
- **插件市场**：浏览、安装、上传、版本历史、评论、点赞收藏
- **MCP**：标准 JSON-RPC 2.0 over HTTP + REST 桥两路，termux-mcp / mt-mcp / 任意标准 MCP 服务直接连
- **A2A**：按 1.0 规范实现 SendMessage / GetTask / ListTasks / CancelTask，与别的 Agent 互相派活
- **工作流**：多步任务串成一条流，每步跑一个分智能体

### 目录速查

```
app/src/main/kotlin/b/ai/   Android 界面与平台层（agent / core / device / native / net / ui）
app/src/main/cpp/           Android 原生层（JNI、QuickJS、图像、原生执行、米家密码学）
app/src/main/rust/          Rust 内核（core / exec / net / ffi / crates / android）
app/src/main/assets/linux/  内置 Linux 的 rootfs 与 proot
gradle/                     构建脚本（含 seeds.gradle.kts：种子注入解析与索引表）
.work/script/               构建与发版脚本（本目录不进版本库，克隆后需自备）
```

### 协议

GNU Affero General Public License v3.0（AGPL-3.0），全文见 LICENSE。

Copyright (C) 2026 沐白 (mubai0122)

> 生效说明：v1.14.2 及之后的版本采用 AGPL-3.0；此前发布的版本（含 v1.14.1）按 GPL-3.0 发布，已授予的授权不可撤销。

**商业许可声明**

本项目采用 AGPL-3.0 协议。如需在闭源产品中集成或作为 SaaS 服务部署（豁免 AGPL 开源义务），请联系 <bai@baiccb.cn> 获取商业许可授权。

> 商业许可仅覆盖本项目自有的 Kotlin / Rust / C++ / TypeScript 代码。`app/src/main/assets/linux/` 下的 proot 与 Ubuntu rootfs 属第三方组件，按各自许可（GPL / LGPL 等）分发，不在商业许可范围内。

---

<details>
<summary><b>构建（开发者展开）</b></summary>

**Android**

需要 JDK 17、Android SDK 37（`local.properties` 里写 `sdk.dir`）。

```bash
./gradlew assembleDebug     # debug：不混淆、不加密，日志可看
./gradlew assembleRelease   # release：R8 混淆 + 资源裁剪 + 字符串加密
```

字符串加密的密钥与接入凭据不进仓库，从 `local.properties` 读，模板见 `local.properties.todo`：

```properties
stringfog.key=你自己的一串密钥
baicodex.aes.key=凭据加密用的种子
miot.client.id=米家接入 clientId
seed.netease.preset=网易云 weapi 预置串
```

没配也能构建，会退到占位串（占位串等于没加密，别用占位串发版）。米家与网易云那几项属公开客户端口径，不配也能跑；协作 token（`seed.a2a.token`）与固定配对码（`seed.lan.code`）默认不写进包，自用构建才加 `-PallowSeedCred=1` 注入。

**内核与桌面端**

```bash
cd app/src/main/rust
cargo build --bin baicodex                 # 内核 + 终端前端

cd baiguard && cargo test                  # 数据保障层单测（独立 crate）
```

前端源码与桌面出包不在本仓库（桌面端前端已剥离单独维护），这里只编内核与终端前端。

</details>