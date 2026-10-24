# BaiAgent / baicodex

跑在手机本机的 AI Agent：模型直连，读写文件、执行命令全部在设备上完成，不经过中转服务器。

包名 `b.ai`，minSdk 24（targetSdk 34），Kotlin + Jetpack Compose，单 Activity。

- 官网：<https://agent.ai.baiccb.cn>
- 下载：<https://github.com/mubai0122/baicodex/releases/latest>

## 能力

### 模型与对话
- 多套模型配置：一条 = 提供商 + Base URL + Key + 模型 + 能力（视觉 / 工具调用 / 推理），随时切
- 思考档位（low / medium / high）、最大模式、温度与附加参数；SSE 流式输出、思考段折叠
- 消息分支：重新生成 / 编辑重发不删旧回答，长成第二支，聊天里 `‹ 1/2 ›` 来回切（库 v11 消息树）
- 永久记忆：模型自己判断值得长期记住的偏好与结论存进「记忆」，侧边栏可逐条删除；没删就每轮随系统提示下发（库 v12）
- 上下文压缩按窗口比例（默认 70%）+ 未压缩条数双阈值触发
- 每次回答下方显示 token、tok/s、首响耗时、供应商与模型

### 工具与执行
- 工具调用：读写文件、`exec_shell`、工作区检索、12306 余票 / 中转、定时任务、MCP 与 REST 桥
- 执行通道三档：普通 App 身份 / Shizuku / Root（su），工具内部自动选路，弹窗标出实际路径
- 工具权限逐条设置：询问 / 允许 / 拒绝；高危工具单独标记
- 内置 Ubuntu（proot + 自解包 rootfs）：apt、gcc、node 都能在手机上跑，与 Android 文件系统互通
- MCP 客户端：JSON-RPC 2.0 over HTTP，能力并入工具表与 @ 面板

### 界面与个性化
- 会话：置顶、长按多选、改名 / 删除、跨会话收藏、每会话独立草稿
- 角色卡（人设 / 场景 / 示例对话 / 开场白 / 绑定模型）与自定义技能包
- 外观：纯色 / 柔光渐变 / 点阵背景，液态玻璃卡片与底栏、质感按钮、全局字号、深色模式
- 五语界面：简体中文 / 繁體中文 / English / 日本語 / Français

### 数据与发布
- 会话、角色、技能、工作区绑定、用量账本全部落本地 SQLite（`bai.db` + `bai_stat.db` 双库），不走云
- 使用统计：总量 / 分类 / 模型维度 + 日期方格图，输入输出缓存三项单价可填、直接算钱
- 整库备份与恢复：一键导出库 / 配置 / 数据根小文件 / 头像为 zip，导入后下次启动自动替换
- 应用内检查更新（`agent.ai.baiccb.cn/api/update`），直链下载后拉起系统安装器

## 构建

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
```

没配也能构建，会退到占位串（占位串等于没加密，别用占位串发版）。

## 目录

```
app/src/main/kotlin/b/ai/
  agent/    Agent 循环与工具定义
  core/     配置、数据库、工作区、统计、语音
  exec/     shell / root / shizuku 通道，定时任务
  net/      LLM 客户端、搜索、上报、检查更新
  ui/       Compose 界面
app/src/main/assets/linux/   内置 Ubuntu 的 rootfs 与 proot
scripts/   构建与素材脚本
```

## 界面预览

| 手机 · 浅色 | 手机 · 深色 |
|:---:|:---:|
| <img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png" width="240" alt="手机 浅色"> | <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png" width="240" alt="手机 深色"> |

| 桌面 · 浅色 | 桌面 · 深色 |
|:---:|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="430" alt="桌面 浅色"> | <img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="430" alt="桌面 深色"> |

## 协议

GNU General Public License v3.0，全文见 [LICENSE](LICENSE)。

Copyright (C) 2026 沐白 (mubai0122)
