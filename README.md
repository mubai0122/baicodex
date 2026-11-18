<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

**简体中文** · [English](README.en.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

**一个 Android Agent 智能体。** 它不只是聊天：你说「帮我做个软件并构建」「这个报错帮我定位」「写个脚本跑一下」，它会自己看屏幕、拆步骤、调工具、写代码、编译构建，做完再把结果交回来。

**开源、便捷**：装上就能跑，不需要电脑陪跑，支持原生 **MCP** 与 **米家** 生态，持续维护。

**隐私**：模型直连，对话、文件、密钥都留在本机，不经过任何中转服务器。

- 官网：<https://agent.ai.baiccb.cn>
- 教程（如何精通 AndroidAgent）：<https://agent.ai.baiccb.cn/docs/>

---

## 亮点

1. **Android Agent，无需电脑即可跑**
   普通 App 身份装完就能用，挂 Shizuku 免 Root 拿系统级能力，上 Root 彻底解锁。三档不是互斥开关，同一张工具表下三条通道，工具内部自动选路。
   <sub>普通身份的零宽文件通道（读写任意 App 的 data），并非所有机型都支持，装机后在 App 内实测为准。</sub>

2. **内置Linux沙箱，能跑东西**
   proot + 自解包 Ubuntu 24.04 rootfs，装在应用私有目录，不动宿主机，跑坏了重置就是干净的。apt 装依赖、gcc/node 直接编译，C/C++/Rust/Go都能跑；Windows exe 也能跑。理论上可以跑Android Studio。

3. **多智能体，可切换**
   角色卡换人设、场景、开场白、绑定模型，换角色等于换一个人格和一套说话方式；支持多Prompt切换。

4. **绝对的安全**
   凭据加密落盘，设备密钥由Android Keystore包裹，明文不出硬件；模型读不到密钥原文，出口统一脱敏，开启高强度加密够的`sk-`/`ghp_`/PEM/Bearer在写日志、回灌模型、导出诊断包前全部抹掉。

5. **屏幕操作，无需 Root**
   无障碍服务直连：dump节点树、点击、长按、滑动、输入、按键、开应用、列应用，不需要 Shizuku和Root也能跑。

6. **MCP 可玩性高**
   标准 JSON-RPC 2.0 over HTTP + REST 桥，Termux 里的 termux-mcp等任何标准 MCP 服务直接连；反过来，别人也能把 baicodex 当 MCP 用。

7. **SSH / GitHub / Skill，扩展性拉满**
   SSH 全套工具；GitHub 技能包全套工具；私有协议 skill（可玩性高）/ 通用协议 skill 均支持解析。

8. **上下文压缩，不降智不丢记忆**
   安全边界强制保留结论与文件路径等关键信息；窗口比例 + 条数阈值触发；长期记忆单独入对话库，不随压缩消失。

9. **长期记忆**
   模型判断长期记住的偏好与结论存进「记忆」，每轮随系统提示下发，侧边栏可逐条管理。

10. **技术栈广**
    Rust 内核 + C++ 原生层 + Kotlin / Compose 界面 + TypeScript / WebView2 桌面端，npmCI支持。

11. **米家、网易云音乐、局域网协作都是原生实现**
    米家互联操作小米智能家居，支持控制局域网、云端；网易云音乐登录后可与米家互联；同一 Wi-Fi 下的多台设备用配对码握手后可支持组建团队、互传文件。

---

## 界面预览

**手机浅色**　　**手机深色**

<img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261108" width="280" alt="手机 浅色"> <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261108" width="280" alt="手机 深色">

---

## 它和普通 AI App 差在哪

- **真的动手，不只是回话**：Agent 工具调用，读文件、写文件、跑命令、点屏幕都是真执行，结果给模型进行下一步。
- **权限三档自选**：普通用户也可解锁大部分功能，Shizuku、Root可发掘完整能力。
- **data读取**：在支持的设备上把受限目录映射成虚拟路径，穿透到 data 目录。
- **数据不出门**：会话、角色、技能、用量全落本地 SQLite。
- **本地推理**：支持用手机GPU跑大模型，`llama`支持，完全本地。

---

## 能力

### 模型与对话

- 多模型配置切换：提供商 + BaseURL + Key + 模型 + 能力标记，OpenAI接口均兼容。
- 思考档位、最大模式、温度与附加参数；SSE 流式输出，思考段可折叠。
- 消息分支：重新生成 / 编辑重发都不删旧回答，可切换。
- 语音输入：说完直接发。
- 回答下方支持显示 token、tok/s、首响耗时、供应商与模型等
- 支持注入当前时间、电量、位置、通知、设备使用时长，更懂你。
 

### 工具与执行
# 不但有默认，还支持自己写

- 文件操作：读、写、局部改、建目录、删）
- 命令：exec_shell、linux_shell、屏幕操作、浏览器、网络、app逆向包、QuickJS、12306查票方案、定时任务
- SSH：连接、列目录、读取、写入、提权执行、执行命令、下载、上传。凭据可复用。
- GitHub：查仓库、issue、PR、读仓库文件、新建或更新单个文件、提交等。
- Web：多引擎搜索、网站内容查看、直发 HTTP
- 内置浏览器：BrowserWebView，读浏览器渲染好的页面，打开并回正文、取内容、列链接、按文字点按、填表、翻页，跑 JS，过验证。
- 工具权限设置：询问/允许/拒绝
- 计划模式：只读命令放行，不允许对文件做任何操作，允许输出计划审计。
- 自定义技能包：大模型帮你写技能包。
- 工作流：多步任务并行多流执行
- A2A：支持其它 Agent 互联

### 米家与智能家居

- 工具：`mijia_devices`、`mijia_scan`、`mijia_read`、`mijia_write`、`mijia_action`

### 音乐

- 工具：`music_platforms`、`music_login`（`verify` / `sms` / `code` ）

*支持与米家共用工具：一句话里可以：放歌 → 写代码 → 把客厅空调调到 26 度*

### 局域网团队协作

- UDP 广播发现、配对码握手
- 已配对设备探活
- 局域网配置
- `lan` 工具：查状态、开接活服务、扫描、配对、解除、看对端能力、派任务与取消
- 同一 Wi-Fi 下多台设备互传文件，端口与并发可配置。

### 屏幕操作

- 无障碍服务直连，可接管屏幕。
- 多步任务时屏幕顶部常驻操作胶囊
- 视觉节点树双路：模型能看截图也能读控件文本和坐标

### 内置 Linux

- **虚拟沙箱**：非本机跑，不怕执行`rm /*`
- 与 Android 文件系统双向互通，允许额外审计确认。

### 界面与个性化

- **外观材质**：纯色 / 柔光渐变 / 点阵背景，自定义壁纸
- **液态玻璃**：卡片、输入框、悬浮底栏、弹窗支持液态玻璃
- **版面与字号**：字体等均支持自定义
- **深色模式**：支持自定义主题与配色
- **桌宠**：大肥鱼ovo
- **悬浮聊天窗**：在别的 App 上层直接聊，支持配置样式
- **会话管理**：置顶、长按多选、改名、删除、跨会话收藏、每会话独立草稿
- **插件广场**：浏览、安装、上传、版本历史、评论
- **五语界面**：简体中文 / 繁體中文 / English / 日本語 / Français

### 数据与发布

- 本地 SQLite 双库，正在开发端对端加密的云端备份
- 使用统计：总量、金额 + 日期方格图、统计图
- 整库备份与恢复：支持加密备份与恢复

---

## 三种运行模式（Android）

| 模式 | 需要什么 | 能干什么 |
|---|---|---|
| 普通 App 身份 | 装完就能用 | 对话、文件读写、执行命令、屏幕操作、零宽读 data、联网工具等 |
| Shizuku | Shizuku 授权 | 系统级 shell、`uiautomator dump`、`screencap`、后台应用管理、深读 data |
| Root（su） | 设备已 root | 系统目录读写、pm 静默安装卸载、全局操作，工具内部优先走 su |

---

## 安全

最高三层密钥，凭据 AES-256-GCM 加密，API Key、账号、SSH 与 GitHub 凭据、技能密钥等均存密文；模型无法读取密钥，支持输出脱敏

---

## 开发者

### 目录速查

```
app/src/main/kotlin/b/ai/   Android 界面与平台层（agent / core / device / native / net / ui）
app/src/main/cpp/           Android 原生层（JNI、QuickJS、图像、原生执行、米家密码学）
app/src/main/rust/          Rust 内核：单个 crate，core（base/security/platform/feature 分块）+ ffi 按域拆
app/src/main/assets/linux/  内置 Linux 的 rootfs 与 proot
gradle/                     构建脚本（含 seeds.gradle.kts：种子注入解析与索引表）
.work/script/               构建与发版脚本（本目录不进版本库，克隆后需自备）
```

### 协议

GNU Affero General Public License v3.0（AGPL-3.0），全文见 LICENSE。

Copyright (C) 2026 沐白 (mubai0122)


**商业许可声明**

本项目采用 AGPL-3.0 协议。

> 商业许可仅覆盖本项目自有的 Kotlin / Rust / C++ / TypeScript 代码。`app/src/main/assets/linux/` 下的 proot 与 Ubuntu rootfs 属第三方组件，按各自许可（GPL / LGPL 等）分发。

---

<details>
<summary><b>构建（开发者展开）</b></summary>

**Android**

JAVA 17以上、Android SDK 37以上

```bash
./gradlew assembleDebug   # debug
./gradlew assembleRelease # release
```

字符串加密的密钥与凭据从`local.properties`读，模板见 `local.properties.todo`

没配也能构建，支持自动退到占位串
`seed.lan.code`默认不进包，自用构建才加 `-PallowSeedCred=1` 注入。

**内核与桌面端**

```bash
cd app/src/main/rust
cargo build --bin baicodex   # 内核
cargo test --release    # 内核测试all
```

</details>
