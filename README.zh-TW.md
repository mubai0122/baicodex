<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

[简体中文](README.md) · [English](README.en.md) · **繁體中文** · [日本語](README.ja.md)

**一個 Android Agent 智能體。** 它不只是聊天：你說「幫我做個軟體並建置」「這個報錯幫我定位」「寫個腳本跑一下」，它會自己看螢幕、拆步驟、呼叫工具、寫程式碼、編譯建置，做完再把結果交回給你。

**開源、便捷**：裝上就能跑，不需要電腦陪跑，支援原生 **MCP** 與 **米家** 生態，持續維護。

**隱私**：模型直連，對話、檔案、密鑰都留在本機，不經過任何中轉伺服器。

- 官網：<https://agent.ai.baiccb.cn>
- 教學（如何精通 AndroidAgent）：<https://agent.ai.baiccb.cn/docs/>

---

## 亮點

1. **Android Agent，無需電腦即可跑**
   一般 App 身分裝完就能用，掛 Shizuku 免 Root 拿系統級能力，上 Root 徹底解鎖。三檔不是互斥開關，同一張工具表下三條通道，工具內部自動選路。
   <sub>一般身分的零寬檔案通道（讀寫任意 App 的 data），並非所有機型都支援，裝機後在 App 內實測為準。</sub>

2. **內建 Linux 沙箱，能跑東西**
   proot + 自解包 Ubuntu 24.04 rootfs，裝在應用私有目錄，不動宿主機，跑壞了重置就是乾淨的。apt 裝依賴、gcc/node 直接編譯，C/C++/Rust/Go 都能跑；Windows exe 也能跑。理論上可以跑 Android Studio。

3. **多智能體，可切換**
   角色卡換人設、場景、開場白、綁定模型，換角色等於換一個人格和一套說話方式；支援多 Prompt 切換。

4. **絕對的安全**
   憑據加密落盤，裝置密鑰由 Android Keystore 包裹，明文不出硬體；模型讀不到密鑰原文，出口統一脫敏，開啟高強度加密後 `sk-`/`ghp_`/PEM/Bearer 在寫日誌、回灌模型、匯出診斷包前全部抹掉。

5. **螢幕操作，無需 Root**
   無障礙服務直連：dump 節點樹、點擊、長按、滑動、輸入、按鍵、開應用、列應用，不需要 Shizuku 和 Root 也能跑。

6. **MCP 可玩性高**
   標準 JSON-RPC 2.0 over HTTP + REST 橋，Termux 裡的 termux-mcp 等任何標準 MCP 服務直接連；反過來，別人也能把 baicodex 當 MCP 用。

7. **SSH / GitHub / Skill，擴充性拉滿**
   SSH 全套工具；GitHub 技能包全套工具；私有協定 skill（可玩性高）/ 通用協定 skill 均支援解析。

8. **上下文壓縮，不降智不丟記憶**
   安全邊界強制保留結論與檔案路徑等關鍵資訊；視窗比例 + 條數閾值觸發；長期記憶單獨入對話庫，不隨壓縮消失。

9. **長期記憶**
   模型判斷長期記住的偏好與結論存進「記憶」，每輪隨系統提示下發，側邊欄可逐條管理。

10. **技術棧廣**
    Rust 核心 + C++ 原生層 + Kotlin / Compose 介面 + TypeScript / WebView2 桌面端，npmCI 支援。

11. **米家、網易雲音樂、區域網路協作都是原生實作**
    米家互聯操作小米智慧家居，支援控制區域網路、雲端；網易雲音樂登入後可與米家互聯；同一 Wi-Fi 下的多台裝置用配對碼握手後可支援組建團隊、互傳檔案。

---

## 介面預覽

**手機淺色**　　**手機深色**

<p align="center">
  <img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261119" width="220" alt="手機 淺色">
  <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261119" width="220" alt="手機 深色">
</p>

---

## 它和普通 AI App 差在哪

- **真的動手，不只是回話**：Agent 工具呼叫，讀檔案、寫檔案、跑命令、點螢幕都是真執行，結果交給模型進行下一步。
- **權限三檔自選**：一般使用者也可解鎖大部分功能，Shizuku、Root 可發掘完整能力。
- **data 讀取**：在支援的裝置上把受限目錄對應成虛擬路徑，穿透到 data 目錄。
- **資料不出門**：對話、角色、技能、用量全落本機 SQLite。
- **本地推理**：支援用手機 GPU 跑大模型，`llama` 支援，完全本地。

---

## 能力

### 模型與對話

- 多模型設定切換：提供商 + BaseURL + Key + 模型 + 能力標記，OpenAI 介面均相容。
- 思考檔位、最大模式、溫度與附加參數；SSE 串流輸出，思考段可折疊。
- 訊息分支：重新生成 / 編輯重送都不刪舊回答，可切換。
- 語音輸入：說完直接送。
- 回答下方支援顯示 token、tok/s、首響耗時、供應商與模型等
- 支援注入目前時間、電量、位置、通知、裝置使用時長，更懂你。

### 工具與執行
### 不但有預設，還支援自己寫

- 檔案操作：讀、寫、局部改、建目錄、刪）
- 命令：exec_shell、linux_shell、螢幕操作、瀏覽器、網路、app 逆向包、QuickJS、12306 查票方案、定時任務
- SSH：連線、列目錄、讀取、寫入、提權執行、執行命令、下載、上傳。憑據可複用。
- GitHub：查倉庫、issue、PR、讀倉庫檔案、新建或更新單個檔案、提交等。
- Web：多引擎搜尋、網站內容檢視、直發 HTTP
- 內建瀏覽器：BrowserWebView，讀瀏覽器渲染好的頁面，打開並回正文、取內容、列連結、按文字點按、填表、翻頁，跑 JS，過驗證。
- 工具權限設定：詢問/允許/拒絕
- 計畫模式：唯讀命令放行，不允許對檔案做任何操作，允許輸出計畫稽核。
- 自訂技能包：大模型幫你寫技能包。
- 工作流：多步任務並行多流執行
- A2A：支援其它 Agent 互聯

### 米家與智慧家居

- 工具：`mijia_devices`、`mijia_scan`、`mijia_read`、`mijia_write`、`mijia_action`

### 音樂

- 工具：`music_platforms`、`music_login`（`verify` / `sms` / `code` ）

*支援與米家共用工具：一句話裡可以：放歌 → 寫程式碼 → 把客廳空調調到 26 度*

### 區域網路團隊協作

- UDP 廣播發現、配對碼握手
- 已配對裝置探活
- 區域網路設定
- `lan` 工具：查狀態、開接活服務、掃描、配對、解除、看對端能力、派任務與取消
- 同一 Wi-Fi 下多台裝置互傳檔案，埠與並行可設定。

### 螢幕操作

- 無障礙服務直連，可接管螢幕。
- 多步任務時螢幕頂部常駐操作膠囊
- 視覺節點樹雙路：模型能看截圖也能讀控制項文字和座標

### 內建 Linux

- **虛擬沙箱**：非本機跑，不怕執行 `rm /*`
- 與 Android 檔案系統雙向互通，允許額外稽核確認。

### 介面與個人化

- **外觀材質**：純色 / 柔光漸層 / 點陣背景，自訂桌布
- **液態玻璃**：卡片、輸入框、懸浮底欄、彈窗支援液態玻璃
- **版面與字號**：字型等均支援自訂
- **深色模式**：支援自訂主題與配色
- **桌寵**：大肥魚 ovo
- **懸浮聊天窗**：在別的 App 上層直接聊，支援設定樣式
- **對話管理**：置頂、長按多選、改名、刪除、跨對話收藏、每對話獨立草稿
- **外掛廣場**：瀏覽、安裝、上傳、版本歷史、評論
- **五語介面**：簡體中文 / 繁體中文 / English / 日本語 / Français

### 資料與發布

- 本機 SQLite 雙庫，正在開發端對端加密的雲端備份
- 使用統計：總量、金額 + 日期方格圖、統計圖
- 整庫備份與還原：支援加密備份與還原

---

## 三種執行模式（Android）

| 模式 | 需要什麼 | 能幹什麼 |
|---|---|---|
| 一般 App 身分 | 裝完就能用 | 對話、檔案讀寫、執行命令、螢幕操作、零寬讀 data、聯網工具等 |
| Shizuku | Shizuku 授權 | 系統級 shell、`uiautomator dump`、`screencap`、後台應用管理、深讀 data |
| Root（su） | 裝置已 root | 系統目錄讀寫、pm 靜默安裝解除安裝、全域操作，工具內部優先走 su |

---

## 安全

最高三層密鑰，憑據 AES-256-GCM 加密，API Key、帳號、SSH 與 GitHub 憑據、技能密鑰等均存密文；模型無法讀取密鑰，支援輸出脫敏

---

## 開發者

### 目錄速查

```
app/src/main/kotlin/b/ai/   Android 介面與平台層（agent / core / device / native / net / ui）
app/src/main/cpp/           Android 原生層（JNI、QuickJS、圖像、原生執行、米家密碼學）
app/src/main/rust/          Rust 核心：單個 crate，core（base/security/platform/feature 分塊）+ ffi 按域拆
app/src/main/assets/linux/  內建 Linux 的 rootfs 與 proot
gradle/                     建置腳本（含 seeds.gradle.kts：種子注入解析與索引表）
.work/script/               建置與發版腳本（本目錄不進版本庫，複製後需自備）
```

### 協議

GNU Affero General Public License v3.0（AGPL-3.0），全文見 LICENSE。

Copyright (C) 2026 沐白 (mubai0122)

**商業授權聲明**

本專案採用 AGPL-3.0 協議。

> 商業授權僅涵蓋本專案自有的 Kotlin / Rust / C++ / TypeScript 程式碼。`app/src/main/assets/linux/` 下的 proot 與 Ubuntu rootfs 屬第三方元件，按各自授權（GPL / LGPL 等）分發。

---

<details>
  <summary><b>建置（開發者展開）</b></summary>

**Android**

JAVA 17 以上、Android SDK 37 以上

```bash
./gradlew assembleDebug   # debug
./gradlew assembleRelease # release
```

字串加密的金鑰與憑據從 `local.properties` 讀，範本見 `local.properties.todo`

沒配也能建置，支援自動退到佔位串
`seed.lan.code` 預設不進包，自用建置才加 `-PallowSeedCred=1` 注入。

**核心與桌面端**

```bash
cd app/src/main/rust
cargo build --bin baicodex   # 核心
cargo test --release    # 核心測試 all
```

</details>