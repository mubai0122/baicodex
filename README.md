# BaiAgent / baicodex

跑在手机本机的 AI Agent：模型直连，读写文件、执行命令全部在设备上完成，不经过中转服务器。

包名 `b.ai`，minSdk 24，Kotlin + Jetpack Compose，单 Activity。

## 能力

- 多供应商模型直连（OpenAI / Anthropic / Gemini 及兼容端点），流式输出、思考段折叠
- 工具调用：读写文件、`exec_shell`、工作区检索、定时任务、内置 Ubuntu（proot）、12306 查询
- 执行通道三档：普通 App 身份 / Shizuku / Root（su）
- 会话、角色卡、技能包、工作区绑定全部落本地 SQLite，不走云
- 设置六大分类：模型 / 对话 / 外观 / 统计 / 扩展 / 关于
- 应用内检查更新，直链下载后直接拉起系统安装器

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

## 协议

按 GPL-3.0 发布（`LICENSE` 文件待补）。
