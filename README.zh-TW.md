<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

[简体中文](README.md) · [English](README.en.md) · **繁體中文** · [日本語](README.ja.md)

**把 Android 當成自己身體的 AI Agent。** 它不只是聊天：你說「把後台清一下」「這個報錯幫我定位」「寫個腳本跑一下」，它會自己看螢幕、拆步驟、呼叫工具、寫程式、編譯建置，做完再把結果交回來。全是真執行，不是示範。

模型直連，對話、檔案、金鑰都留在本機，不經過任何中轉伺服器。

一套 Rust 核心，三種前端：**Android**、**Windows**、**終端**。

- 官網：<https://agent.ai.baiccb.cn>
- 下載：<https://github.com/mubai0122/baicodex/releases/latest>
- 開源免費：GPL-3.0，無內購、無廣告、無次數限制

> `agent` · `ai-agent` · `codex` · `claw` · `android` · `kotlin` · `rust` · `llm` · `local-first` · `mcp`

---

## 亮點

1. **Android 上的真 Agent，三檔權限發揮最強**
   一般 App 身分裝完就能用，掛上 Shizuku 免 Root 取得系統級能力，上 Root 徹底解鎖。三檔不是互斥開關，是同一張工具表下的三條通道，工具內部自動選路，彈窗標出這次實際走了哪條。
   <sub>一般身分的零寬檔案通道（讀寫任意 App 的 data）並非所有機型都支援，裝機後在 App 內實測為準。</sub>

2. **內建虛擬 Linux 沙箱，真能跑東西**
   proot + 自解包 Ubuntu 24.04 rootfs，裝在應用程式私有目錄，不動宿主機，跑壞了重設就是乾淨的。apt 裝相依套件、gcc / node 直接編譯，C / C++ / Rust / Go 都能跑；跑 Windows exe 也不用另找來源——arm64 倉庫自帶 `box64` 與 `wine 9.0`，裝完就能起，圖形程式再補 Xvfb / VNC 看畫面。手機上效能有限，跑 Android Studio 這類重型 IDE 是設想——但「能跑」這件事本身是真的。
   <sub>執行 guest 程式需要 Root / Shizuku 通道（targetSdk 34 下 SELinux 禁止從應用程式私有目錄 execve）。</sub>

3. **多智能體，可切換**
   角色卡換人設、場景、開場白、綁定模型，換角色等於換一個人格和一套說話方式；A2A 能跟別的 Agent 互相派活。
   <sub>3.1 大肥魚桌寵：常駐應用程式內的浮動形象，動畫 30fps 節流，不會一開就把整頁拖卡。</sub>

4. **絕對的安全**
   憑證以 AES-256-GCM 落盤，裝置金鑰由 Android Keystore（TEE / StrongBox）包裹，明文不出硬體；模型讀不到金鑰原文，出口統一脫敏，`sk-` / `ghp_` / PEM / Bearer 在寫日誌、回灌模型、匯出診斷包前全部抹掉。

5. **螢幕操作，無需 Root**
   無障礙服務直連：dump 節點樹、點擊、長按、滑動、輸入、按鍵、開 App、列 App，不需要 Shizuku 也能截圖。視覺 + 節點樹雙路，模型認不準就換一條。

6. **MCP 可玩性高**
   標準 JSON-RPC 2.0 over HTTP + REST 橋兩路都吃，Termux 裡的 termux-mcp、本機 mt-mcp、任何標準 MCP 服務直接連；反過來，別人也能把 baicodex 當 MCP 用。

7. **SSH / GitHub / Skill，擴充性拉滿**
   SSH 八件套（連線 / 列目錄 / 讀 / 寫 / 提權 / 執行 / 上傳 / 下載），sudo 密碼由工具餵給 `sudo -S` 不進命令列；GitHub 技能包走 REST API 查倉庫、issue / PR、讀檔案、提交改動；私有協定 skill 配獨立 JS 引擎（QuickJS），通用 skill 也相容。

8. **上下文壓縮，不降智不丟記憶**
   摘要切點退到「工具呼叫與其結果之間」的安全邊界，強制保留結論與檔案路徑；視窗比例 + 條數雙門檻才觸發，省不到 15% 就不壓；長期記憶單獨入庫，不隨壓縮消失。

9. **長期記憶**
   模型自己判斷值得長期記住的偏好與結論存進「記憶」，每輪隨系統提示下發，側邊欄可逐條刪。

10. **技術棧廣**
    Rust 核心 + C++ 原生層 + Kotlin / Compose 介面 + TypeScript / WebView2 桌面端，一套核心三端跑。

---

## 介面預覽

**手機 · 淺色**　　**手機 · 深色**

<img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261108" width="280" alt="手機 淺色"> <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261108" width="280" alt="手機 深色">

**桌面 · 淺色**

<img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="860" alt="桌面 淺色">

**桌面 · 深色**

<img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="860" alt="桌面 深色">

---

## 它和一般 AI App 差在哪

- **真的動手，不只是回話**：Agent 迴圈帶工具呼叫，讀檔、寫檔、跑 shell、跑 Linux、點螢幕都是真執行，結果回灌給模型再走下一步
- **權限三檔自選**：一般 App 身分就能用（無障礙 + 零寬檔案通道），要系統級能力掛 Shizuku，要徹底解鎖上 Root，工具內部自動選路並在彈窗裡標出實際走了哪條
- **零寬繞過**：`read_file` / `write_file` / `make_dir` / `delete_file` 把受限目錄映射成虛擬路徑，能穿透到其他 App 的 data 目錄，不用先 root；`exec_shell` 走原路徑，不受這個變換影響
- **資料不出門**：對話、角色、技能、用量全落本機 SQLite，不上雲；只有你設定的模型介面和你主動用的搜尋、12306 會出網
- **一套核心多端跑**：Rust 核心同時餵 Android、Windows 和終端，介面各自寫，邏輯不分叉
- **可拆開改**：模型、搜尋來源、工具權限、技能包、思考檔位全都能在設定裡換，原始碼改完自己編

---

## 能力

### 模型與對話

- 多套模型設定：一條 = 供應商 + Base URL + Key + 模型 + 能力標記（視覺 / 工具呼叫 / 推理），隨時切，OpenAI 相容介面都能接
- 思考檔位（low / medium / high）、最大模式、溫度與附加參數；SSE 串流輸出，正文、思考段（`reasoning_content`）與 `tool_calls` 參數都是邊收邊渲染，思考段可折疊；伺服器忽略 `stream=true` 回傳整包時自動降級解析，不報錯
- 訊息分支：重新生成 / 編輯重送都不刪舊回答，長成第二支，聊天裡 `‹ 1/2 ›` 來回切（資料庫 v11 訊息樹）
- 永久記憶：模型自己判斷值得長期記住的偏好與結論存進「記憶」，側邊欄可逐條刪；沒刪就每輪隨系統提示下發（資料庫 v12）
- 上下文壓縮不丟記憶：視窗比例（預設 70%）+ 未壓縮條數雙門檻觸發，切點退到「工具呼叫與其結果之間」的安全邊界（切中間請求體會不合法），摘要裡強制留結論與改過的檔案路徑；省不到 15% 就不壓；長期記憶單獨入庫，不跟著摘要一起消失
- 語音輸入，說完直接送；每次回答下方顯示 token、tok/s、首響耗時、供應商與模型

### 工具與執行

- 內建工具：檔案五件套（讀 / 寫 / 局部改 / 建目錄 / 刪）、`exec_shell`、`linux_shell`、`screen`（螢幕操作）、`browser`、`http_fetch`、`apk`（反查簽章與 dex）、`js`（QuickJS 跑腳本，還能註冊新工具）、12306 餘票 / 中轉 / 經停、記憶寫入、定時任務
- SSH 一組八件：`ssh_connect` / `ssh_ls` / `ssh_read` / `ssh_write` / `ssh_sudo` / `ssh_exec` / `ssh_get` / `ssh_put`——主機與憑證存一次重用；內建 Ubuntu 缺 openssh-client 會自動補裝；sudo 密碼由工具餵給 `sudo -S`，不必也不許寫進命令
- GitHub 技能包：走 REST API 查倉庫 / issue / PR、讀倉庫檔案、新建或更新單一檔案，token 走 `$secret.github_token`，不需要本機 git、不用裝 gh
- Web 抓取三條路：`web_search`（搜尋來源三家可選：Tavily、Bing、Google CSE，Key 自己填，沒選就不把搜尋工具交給模型）、`http_fetch`（直發 HTTP，回傳狀態碼 + 正文前 8000 字）、`browser`（內建 WebView：open / text / links / find / click / input / scroll / eval）
- 執行通道三檔（一般 / Shizuku / Root），工具內部自動選路，彈窗標出實際路徑
- 工具權限逐條設定：詢問 / 允許 / 拒絕，高風險工具單獨標記；極簡模式只留五個工具給弱模型兜底
- 計畫模式：唯讀命令放行，`exec_shell` 裡的寫入一律判死，防止「先看看」變成「已經刪了」
- 自訂技能包：自己寫工具塞進去，模型按描述自己挑
- MCP 客戶端：標準 JSON-RPC 2.0 over HTTP（`tools/list` + `tools/call`），另有一路 REST 橋（`GET /tools` + `run`）——Termux 裡的 termux-mcp、本機 mt-mcp、以及任何標準 MCP 服務都能直接接；連上的能力併入工具表與 @ 面板，名稱前綴 `mcp_服務_能力`
- 工作流：多步任務串成一條流，每步跑一個分智能體，步驟之間不共用上下文
- A2A：按 1.0 規範實作 SendMessage / GetTask / ListTasks / CancelTask，方法名與狀態碼與規範一致——別的 Agent 能給你派活，你的 Agent 也能把活派出去

### 螢幕操作

- 無障礙服務直連：dump 節點樹、點擊、長按、滑動、輸入、按鍵、開 App、列 App，不需要 Shizuku 也能截圖
- 多步任務時螢幕頂端常駐操作膠囊，顯示這一步在做什麼，一條指令做完自動收起
- 視覺 + 節點樹雙路：模型能看截圖，也能讀控制項文字和座標，認不準就換一條路

### 內建 Linux

- **虛擬沙箱**：proot + 自解包 Ubuntu 24.04 rootfs，裝在應用程式私有目錄，不動宿主機，跑壞了重開就是乾淨的
- `apt` 裝相依套件、`gcc` / `node` 直接編譯，C / C++ / Rust / Go 都能在手機上跑；arm64 的 noble 倉庫自帶 `box64` 與 `wine 9.0`，裝完可以跑 Windows exe，圖形程式再補 Xvfb / VNC 看畫面
- 與 Android 檔案系統雙向互通，`/sdcard` 和 `/storage` 直接掛進去
- 跟路徑沙箱是兩道閘：沙箱管「這個路徑准不准動」（敏感系統檔案、`..`、軟連結都在動手前攔），proot 管「動壞了也傷不到系統」
- **現況**：執行 guest 程式需要 Root / Shizuku 通道（targetSdk 34 下 SELinux 禁止從應用程式私有目錄 `execve`）

### 桌面端（Windows）

- 無邊框視窗 + DWM 液態玻璃 + WebView2 渲染面，介面是 `desktop/web` 那套 TypeScript + Vite 程式碼
- 與手機同一份 Rust 核心，同一個版本號，工具表與設定結構一致

### 介面與個人化

- **外觀材質**：純色 / 柔光漸層 / 點陣背景，可換自訂桌布（切圖時先出縮圖再上真圖，冷啟動不閃白）
- **液態玻璃**：卡片、輸入框、懸浮底欄、彈窗走同一套玻璃材質，可整體關閉退回白卡；另有質感按鈕風格，與玻璃互斥，開哪個都行
- **版面與字級**：全域字級獨立調，訊息泡泡有圓角與膠囊兩套版面，聊天頭像顯示可關
- **深色模式**：可切，玻璃、桌布與文字色跟著一起換
- **角色卡**：人設、場景、示例對話、開場白、綁定模型；聊天頭像可選「跟目前角色卡」或另選一張自己傳圖
- **桌寵**：常駐應用程式內的浮動形象，動畫按 30fps 節流，不會一開就把整頁拖卡
- **懸浮聊天窗**：在別的 App 上層直接聊，寬高、字級、透明度、位置都能拖，和主介面共用同一份對話
- **對話管理**：置頂、長按多選、改名 / 刪除、跨對話收藏、每個對話獨立草稿
- **外掛廣場**：瀏覽、安裝、上傳、版本歷史、留言、按讚收藏
- **五語介面**：简体中文 / 繁體中文 / English / 日本語 / Français

### 資料與發佈

- 對話、角色、技能、工作區綁定、用量帳本全部落本機 SQLite（`bai.db` + `bai_stat.db` 雙庫），不走雲
- 使用統計：總量 / 分類 / 模型維度 + 日期方格圖，輸入輸出快取三項單價可填、直接算錢
- 整庫備份與還原：一鍵匯出資料庫 / 設定 / 資料根小檔案 / 頭像為一個檔案，匯入後下次啟動自動替換
- App 內檢查更新（`agent.ai.baiccb.cn/api/update`），直鏈下載後拉起系統安裝器

---

## 三種執行模式（Android）

| 模式 | 需要什麼 | 能做什麼 |
|---|---|---|
| 一般 App 身分 | 裝完就能用 | 對話、檔案讀寫、`exec_shell`（應用程式身分）、無障礙截圖 / 點擊 / 滑動 / 輸入、零寬讀 data、網路工具 |
| Shizuku | 裝 Shizuku 並授權 | 免 Root 執行系統級 shell、`uiautomator dump`、`screencap`、背景 App 管理、深讀 data |
| Root（su） | 裝置已 root | 系統目錄讀寫、pm 靜默安裝卸載、全域操作，工具內部優先走 su |

三種模式不是互斥開關，是同一個工具表下的三檔通道，隨時切，切完下一次呼叫就生效。

<sub>註：一般身分的零寬檔案通道受機型 / 系統限制，部分機型可用、部分不行，以 App 內實測為準。內建 Linux 沙箱需要 Root / Shizuku 通道。</sub>

---

## 安全

三層金鑰（Keystore 包裹 + HKDF 半金鑰 + 資料金鑰），憑證以 AES-256-GCM 落盤（檔頭魔數 `baicodexencryption`），API Key、帳號、SSH 與 GitHub 憑證、技能金鑰各存一份密文；模型讀不到金鑰原文，出口統一脫敏；未解鎖時憑證退到明文 JSON + 0600，介面直接標註「未加密」，不假裝安全。

---

## 常見問題

**需要 Root 嗎？**
不需要。一般 App 身分就能用絕大部分功能；要系統級 shell 掛 Shizuku（建議 Android 11+），要改系統目錄才需要 Root。內建 Linux 沙箱是例外——需要 Root / Shizuku 通道才能執行裡面的程式。

**真的免費嗎？**
是。GPL-3.0 開源，沒有內購、訂閱、廣告或次數限制，也可以自己編一份。

**資料會上傳嗎？**
不會。對話、檔案、金鑰都在本機。首次啟動有協議頁，同意後才會上報匿名使用統計，拒絕就不報，功能不受影響。

**支援哪些 Android 版本？**
Android 7.0（API 24）及以上，targetSdk 34，編譯用 SDK 37。APK 含 `arm64-v8a` 與 `armeabi-v7a`。

---

## 開發者

### 擴充方式

- **技能包（Skill）**：自己寫工具塞進去，模型按描述自己挑；配獨立 JS 引擎（QuickJS），能跑腳本、也能註冊新工具
- **外掛廣場**：瀏覽、安裝、上傳、版本歷史、留言、按讚收藏
- **MCP**：標準 JSON-RPC 2.0 over HTTP + REST 橋兩路，termux-mcp / mt-mcp / 任意標準 MCP 服務直接連
- **A2A**：按 1.0 規範實作 SendMessage / GetTask / ListTasks / CancelTask，與別的 Agent 互相派活
- **工作流**：多步任務串成一條流，每步跑一個分智能體

### 目錄速查

```
app/src/main/kotlin/b/ai/   Android 介面與平台層（agent / core / device / native / net / ui）
app/src/main/cpp/           Android 原生層（JNI、QuickJS、影像、原生執行）
app/src/main/rust/          Rust 核心（core / exec / net / ffi / crates / android）
app/src/main/assets/linux/  內建 Linux 的 rootfs 與 proot
desktop/web/                桌面端前端（pnpm workspace）
scripts/                    建置與發版腳本
```

### 授權

GNU General Public License v3.0，全文見 LICENSE。

Copyright (C) 2026 沐白 (mubai0122)

---

<details>
<summary><b>建置（開發者展開）</b></summary>

**Android**

需要 JDK 17、Android SDK 37（`local.properties` 裡寫 `sdk.dir`）。

```bash
./gradlew assembleDebug     # debug：不混淆、不加密，日誌可看
./gradlew assembleRelease   # release：R8 混淆 + 資源裁剪 + 字串加密
```

字串加密的金鑰不進倉庫，從 `local.properties` 讀：

```properties
stringfog.key=你自己的一串金鑰
baicodex.aes.key=憑證加密用的種子
```

沒設也能建置，會退到佔位字串（佔位字串等於沒加密，別用佔位字串發版）。

**核心與桌面端**

```bash
cd app/src/main/rust
cargo build --bin baicodex                 # 核心 + 終端前端

cd baiguard && cargo test                  # 資料保障層單元測試（獨立 crate）

cd ../../../..                             # 回到倉庫根
bash scripts/desktop-build.sh release      # Windows 出包
```

前端預覽：

```bash
cd desktop/web
pnpm install
pnpm --filter @baicodex/desktop dev
```

</details>