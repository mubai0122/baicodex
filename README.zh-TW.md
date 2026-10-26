# BaiAgent / baicodex

[简体中文](README.md) · [English](README.en.md) · **繁體中文** · [日本語](README.ja.md)

跑在手機本機的 Android AI Agent。模型直連，讀寫檔案、執行指令、跑 Linux 全在裝置上完成，不經過任何中轉伺服器——對話、檔案和金鑰都只留在你自己的手機裡。

它也不只是一個聊天視窗。你說「把後台清一下」「查明天去上海的餘票」「這個報錯幫我定位」，它自己看螢幕、拆步驟、呼叫工具，一步步做完再回來告訴你結果。

套件名 `b.ai`，minSdk 24（Android 7.0，targetSdk 34），Kotlin + Jetpack Compose，單一 Activity，ABI 為 `arm64-v8a` / `armeabi-v7a`。

- 官網：<https://agent.ai.baiccb.cn>
- 下載：<https://github.com/mubai0122/baicodex/releases/latest>
- 開源免費：GPL-3.0，無內購、無廣告、無次數限制，程式碼全在這裡

## 介面預覽

| 手機 · 淺色 | 手機 · 深色 |
|:---:|:---:|
| <img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png" width="280" alt="手機 淺色"> | <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png" width="280" alt="手機 深色"> |

| 桌面 · 淺色 |
|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="860" alt="桌面 淺色"> |

| 桌面 · 深色 |
|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="860" alt="桌面 深色"> |

## 它和一般 AI App 差在哪

- **真的動手，不只是回話**：Agent 迴圈帶工具呼叫，讀檔案、寫檔案、跑 shell、跑 Linux、點螢幕都是真執行，結果回灌給模型再走下一步
- **權限三檔自選**：一般 App 身分就能用（無障礙 + 零寬檔案通道），要系統級能力掛 Shizuku，要徹底解鎖上 Root，工具內部自動選路並在彈窗裡標出實際走了哪條
- **零寬檔案通道**：`read_file` / `write_file` / `make_dir` / `delete_file` 能穿透到其他 App 的 data 目錄，不用先 root；`exec_shell` 走原路徑，不受這個變換影響
- **資料不出門**：工作階段、角色、技能、用量全落本機 SQLite，不上雲；只有你配置的模型介面和你主動用的搜尋、12306 會出網
- **可拆開改**：模型、搜尋來源、工具權限、技能包、思考檔位全都能在設定裡換，原始碼改完自己編

## 三種執行模式

| 模式 | 需要什麼 | 能做什麼 |
|---|---|---|
| 一般 App 身分 | 裝完就能用 | 對話、檔案讀寫、`exec_shell`（應用身分）、無障礙截圖 / 點擊 / 滑動 / 輸入、零寬讀 data、聯網工具 |
| Shizuku | 裝 Shizuku 並授權 | 免 Root 執行系統級 shell、`uiautomator dump`、`screencap`、後台應用管理、深讀 data |
| Root（su） | 裝置已 root | 系統目錄讀寫、pm 靜默安裝卸載、全域操作，工具內部優先走 su |

三種模式不是互斥開關，是同一個工具表下的三檔通道，隨時切，切完下一次呼叫就生效。

## 能力

### 模型與對話

- 多套模型配置：一條 = 供應商 + Base URL + Key + 模型 + 能力標記（視覺 / 工具呼叫 / 推理），隨時切，OpenAI 相容介面都能接
- 思考檔位（low / medium / high）、最大模式、溫度與附加參數；SSE 串流輸出，思考段可摺疊
- 訊息分支：重新生成 / 編輯重發不刪舊回答，長成第二支，聊天裡 `‹ 1/2 ›` 來回切（庫 v11 訊息樹）
- 永久記憶：模型自己判斷值得長期記住的偏好與結論存進「記憶」，側邊欄可逐條刪；沒刪就每輪隨系統提示下發（庫 v12）
- 上下文壓縮：視窗比例（預設 70%）+ 未壓縮條數雙閾值觸發，長對話不爆視窗
- 語音輸入，說完直接送；每次回答下方顯示 token、tok/s、首響耗時、供應商與模型

### 工具與執行

- 內建工具：檔案四件套、`exec_shell`、`exec_linux`、`screen`（螢幕操作）、`web_search`、12306 餘票 / 中轉 / 經停、記憶寫入、定時任務
- 搜尋來源三家可選：Tavily、Bing、Google CSE，Key 自己填，沒選就不把搜尋工具交給模型
- 執行通道三檔（一般 / Shizuku / Root），工具內部自動選路，彈窗標出實際路徑
- 工具權限逐條設定：詢問 / 允許 / 拒絕，高危工具單獨標記；極簡模式只留五個工具給弱模型兜底
- 計畫模式：唯讀指令放行，`exec_shell` 裡的寫操作一律判死，防止「先看看」變成「已經刪了」
- 自訂技能包：自己寫工具塞進去，模型按描述自己挑
- MCP 客戶端：JSON-RPC 2.0 over HTTP，連上的服務能力併入工具表與 @ 面板，工具名前綴 `mcp_服務_能力`

### 螢幕操作

- 無障礙服務直連：dump 節點樹、點擊、長按、滑動、輸入、按鍵、開應用、列應用，不需要 Shizuku 也能截圖
- 多步任務時螢幕頂部常駐操作膠囊，顯示這一步在幹什麼，一條指令做完自動收起
- 視覺 + 節點樹雙路：模型能看截圖，也能讀控件文字和座標，認不準就換一條路

### 內建 Linux

- proot + 自解包 rootfs，首次啟動自動裝好，不用 root、不動宿主機
- `apt` 裝依賴、`gcc` / `node` 直接編譯，C / C++ / Rust / Go 都能在手機上跑
- 與 Android 檔案系統雙向互通，`/sdcard` 和 `/storage` 直接掛進去
- 沙箱隔離，搞砸了隨時重置，不污染系統

### 介面與個人化

- 工作階段：置頂、長按多選、改名 / 刪除、跨工作階段收藏、每個工作階段獨立草稿
- 角色卡（人設 / 場景 / 範例對話 / 開場白 / 綁定模型）與自訂技能包
- 外觀：純色 / 柔光漸層 / 點陣背景，液態玻璃卡片與底欄、質感按鈕、全域字號、深色模式
- 五語介面：简体中文 / 繁體中文 / English / 日本語 / Français

### 資料與發佈

- 工作階段、角色、技能、工作區綁定、用量帳本全部落本機 SQLite（`bai.db` + `bai_stat.db` 雙庫），不走雲
- 使用統計：總量 / 分類 / 模型維度 + 日期方格圖，輸入輸出快取三項單價可填、直接算錢
- 整庫備份與還原：一鍵匯出庫 / 配置 / 資料根小檔案 / 頭像為 zip，匯入後下次啟動自動替換
- 應用內檢查更新（`agent.ai.baiccb.cn/api/update`），直鏈下載後拉起系統安裝器

## 常見問題

**需要 Root 嗎？**

不需要。一般 App 身分就能用絕大部分功能；要系統級 shell 掛 Shizuku（建議 Android 11+），要改系統目錄才需要 Root。

**真的免費嗎？**

是。GPL-3.0 開源，沒有內購、訂閱、廣告或次數限制，也可以自己編一份。

**資料會上傳嗎？**

不會。工作階段、檔案、金鑰都在本機。首次啟動有協議頁，同意後才會上報匿名使用統計，拒絕就不報，功能不受影響。

**支援哪些 Android 版本？**

Android 7.0（API 24）及以上，targetSdk 34，編譯用 SDK 37。APK 含 `arm64-v8a` 與 `armeabi-v7a`。

## 建置

需要 JDK 17、Android SDK 37（`local.properties` 裡寫 `sdk.dir`）。

```bash
./gradlew assembleDebug     # debug：不混淆、不加密，日誌可看
./gradlew assembleRelease   # release：R8 混淆 + 資源裁剪 + 字串加密
```

release 會做三件事，都在 `app/proguard-rules.pro` 與 `app/build.gradle.kts` 裡：

1. **R8 混淆**：除 `MainActivity` 與清單入口、第三方庫必需項外全部改名，字典是自造的短名（`app/obfuscation_dict.txt`），並用 `-repackageclasses` 把套件名也抹掉
2. **字串加密**：StringFog，演算法 AES，每條字串獨立隨機 key
3. **日誌清零**：release 裡 `Log` / `println` 全部是空敘述

字串加密的金鑰不進倉庫，從 `local.properties` 讀：

```properties
stringfog.key=你自己的一串金鑰
```

沒配也能建置，會退到佔位串（佔位串等於沒加密，別用佔位串發版）。

## 目錄

```
app/src/main/kotlin/b/ai/
  agent/    Agent 迴圈與工具定義
  core/     配置、資料庫、工作區、統計、語音
  exec/     shell / root / shizuku 通道，無障礙，定時任務
  net/      LLM 客戶端、搜尋、上報、檢查更新
  ui/       Compose 介面
app/src/main/assets/linux/   內建 Ubuntu 的 rootfs 與 proot
scripts/   建置與素材腳本
```

## 授權

GNU General Public License v3.0，全文見 [LICENSE](LICENSE)。

Copyright (C) 2026 沐白 (mubai0122)
