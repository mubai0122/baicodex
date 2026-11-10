<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

[简体中文](README.md) · **English** · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

**An AI agent that treats Android as its own body.** It does more than chat: say "clear the background apps", "find out what this error is", or "write a script and run it", and it looks at the screen, breaks the work into steps, calls tools, writes code, builds it, and hands the result back. Everything really executes — no demos.

The model is called directly; chats, files and keys stay on your device. No relay server in between.

One Rust core, three front ends: **Android**, **Windows**, **terminal**.

- Website: <https://agent.ai.baiccb.cn>
- Download: <https://github.com/mubai0122/baicodex/releases/latest>
- Licence: AGPL-3.0, no IAP, no ads, no usage limits; closed-source integration and SaaS deployment require a commercial licence

> `agent` · `ai-agent` · `codex` · `claw` · `android` · `kotlin` · `rust` · `llm` · `local-first` · `mcp` · `mijia` · `netease-music` · `lan-collaboration`

---

## Highlights

1. **A real agent on Android, three permission tiers**
   Works out of the box as a normal app; add Shizuku for system-level power without root; go Root to unlock everything. The three tiers are not exclusive switches — they are three channels behind one tool table, routed automatically per call and shown in the popup.
   <sub>The zero-width file channel (reading/writing any app's `data`) is not supported on every device — check inside the app after installing.</sub>

2. **A built-in virtual Linux sandbox that really runs things**
   proot plus a self-extracting Ubuntu 24.04 rootfs, unpacked into the app's private directory; the host system stays untouched, and a broken sandbox resets to a clean one. `apt` for dependencies, `gcc` / `node` compile directly, C / C++ / Rust / Go all run on the phone. Windows `.exe` needs no third-party repo either — the arm64 `noble` archive ships `box64` and `wine 9.0`, so it installs and runs; for GUI programs add Xvfb / VNC to see the screen. Performance on a phone is limited — running Android Studio is a thought experiment — but "it runs" is literally true.
   <sub>Executing guest programs needs the Root / Shizuku channel (under targetSdk 34 SELinux forbids `execve` from the app's private directory).</sub>

3. **Multiple agents, switchable**
   Character cards change the persona, scenario, opening line and bound model — switching a card is switching a personality and a way of speaking. A2A lets agents hand tasks to each other.
   <sub>3.1 Big Fat Fish desktop pet: a floating figure living inside the app, animation throttled to 30fps so it never drags the whole page down.</sub>

4. **Security, taken seriously**
   Credentials are stored with AES-256-GCM; the device key is wrapped by Android Keystore (TEE / StrongBox) and never leaves the hardware in plaintext. The model never sees the raw key, and everything leaving the app is redacted — `sk-`, `ghp_`, PEM and Bearer tokens are scrubbed before writing logs, feeding tool output back to the model, or exporting a diagnostics bundle.

5. **Screen control without root**
   Direct accessibility access: dump the node tree, tap, long-press, swipe, type, send keys, launch apps, list apps — screenshots work without Shizuku. Vision and node tree are two paths; when the model is unsure, it switches.

6. **MCP with real range**
   Standard JSON-RPC 2.0 over HTTP and a REST bridge, both supported: `termux-mcp` in Termux, a local `mt-mcp`, or any standard MCP server connects directly. The other way round works too — other agents can use baicodex as an MCP server.

7. **SSH / GitHub / Skills, extensibility everywhere**
   Eight SSH tools (connect / list / read / write / sudo / exec / download / upload) with the sudo password fed through `sudo -S` instead of the command line; a GitHub skill package over the REST API for repos, issues / PRs, file reads and single-file commits; proprietary protocol skills get their own JS engine (QuickJS), and general skills are compatible too.

8. **Context compaction that keeps both brains and memory**
   The cut point retreats to a safe boundary "between a tool call and its result", and summaries always keep conclusions and file paths; it only triggers when both the window ratio and the message count exceed their thresholds, and skips the work if it would save less than 15%; long-term memory lives in its own store and never disappears with a summary.

9. **Long-term memory**
   The model decides what is worth remembering (preferences, conclusions) and saves it to "Memory"; it rides along with the system prompt each turn and can be deleted row by row from the sidebar.

10. **A wide tech stack**
    Rust core + C++ native layer + Kotlin / Compose UI + TypeScript / WebView2 desktop, one core across three platforms.

11. **Mijia, NetEase Cloud Music and LAN collaboration are all native**
    Mijia runs on its own C-layer crypto (AES-128-CBC / MD5 / SHA-1 with standard test vectors), LAN first and cloud as the fallback; NetEase Cloud Music goes through the weapi encrypted channel (two AES-128-CBC rounds plus modular exponentiation), with SMS login, cookie backfill and login-state checks all done locally; devices on the same Wi-Fi pair with a 6-digit code and then hand work and files to each other. All three tool sets sit in one table: check devices, set the AC, clear the cache, or hand a log to the machine next door — one sentence, and the model orders the steps.

---

## Screenshots

**Phone · light**　　**Phone · dark**

<img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261108" width="280" alt="Phone light"> <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261108" width="280" alt="Phone dark">

**Desktop · light**

<img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="860" alt="Desktop light">

**Desktop · dark**

<img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="860" alt="Desktop dark">

---

## How it differs from a normal AI app

- **It acts, not just replies**: the agent loop carries tool calls — reading and writing files, running shell, running Linux, tapping the screen all really execute, and results are fed back to the model before the next step
- **Three permission tiers, your choice**: a normal app identity is enough (accessibility + zero-width file channel), Shizuku adds system-level power, Root unlocks everything; routing is automatic and the popup shows which channel was used
- **Zero-width bypass**: `read_file` / `write_file` / `make_dir` / `delete_file` map restricted directories onto virtual paths, so data directories of other apps are reachable without root first; `exec_shell` keeps the original path and is not affected
- **Data stays home**: chats, characters, skills and usage all live in local SQLite, never in a cloud; only the model endpoints you configure and the search / 12306 features you use go online
- **One core, many front ends**: the same Rust core backs Android, Windows and the terminal; UIs are written separately, logic never forks
- **Tear it apart**: model, search source, tool permissions, skill packages and thinking level are all switchable in settings — or change the source and build your own

---

## Capabilities

### Models and chat

- Multiple model profiles: one row = provider + base URL + key + model + capability flags (vision / tool use / reasoning); switch any time, any OpenAI-compatible endpoint works
- Thinking level (low / medium / high), max mode, temperature and extra parameters; SSE streaming renders the body, the reasoning stream (`reasoning_content`) and `tool_calls` arguments as they arrive, with the reasoning section collapsible; if the server ignores `stream=true` and returns a whole body, parsing degrades gracefully instead of failing
- Message branching: regenerate or edit-and-resend never deletes the old answer — it grows a second branch, switchable with `‹ 1/2 ›` in the chat (message tree, DB v11)
- Permanent memory: the model decides what is worth keeping (preferences, conclusions) and stores it in "Memory"; deletable row by row from the sidebar; until deleted it rides along with the system prompt every turn (DB v12)
- Context compaction without losing memory: window ratio (70% by default) plus uncompacted message count, both thresholds required; the cut point retreats to the safe boundary "between a tool call and its result" (cutting mid-pair makes the request invalid); summaries always keep conclusions and changed file paths; skipped when it would save less than 15%; long-term memory lives separately and survives compaction
- Voice input sends on release; every reply footer shows tokens, tok/s, first-token latency, provider and model

### Tools and execution

- Built-in tools: the file five (read / write / edit / mkdir / delete), `exec_shell`, `linux_shell`, `screen` (screen control), `browser`, `http_fetch`, `apk` (signing cert and dex inspection), `js` (QuickJS scripts, which can also register new tools), 12306 tickets / transfers / stops, memory writes, scheduled tasks
- Eight SSH tools: `ssh_connect` / `ssh_ls` / `ssh_read` / `ssh_write` / `ssh_sudo` / `ssh_exec` / `ssh_get` / `ssh_put` — host and credentials stored once and reused; the built-in Ubuntu auto-installs `openssh-client` when missing; the sudo password is fed to `sudo -S`, never written into the command
- GitHub skill package: over the REST API, inspect repos / issues / PRs, read repo files, create or update a single file; the token comes from `$secret.github_token` — no local git, no `gh` install
- Two web paths: `web_search` (Tavily, Bing or Google CSE; bring your own key — if none is selected the tool is not handed to the model), `http_fetch` (raw HTTP, returns status code plus the first 8000 chars)
- Built-in browser: `browser` is a real WebView, not a scraper — it reads the rendered page. `open` loads and returns the body, `text` / `links` / `find` read content, list links and locate elements by text, `click` / `input` / `scroll` tap by label, fill forms and page down, `eval` runs JS directly, plus `back` / `forward` / `reload` / `url` / `close`. Pages that need a login or only render with JS go this way
- Three execution channels (normal / Shizuku / Root), routed automatically inside the tools, with the chosen path shown in the popup
- Per-tool permissions: ask / allow / deny, high-risk tools marked separately; minimal mode keeps only five tools so weak models are not buried
- Plan mode: read-only commands pass, writes inside `exec_shell` are rejected outright — "just take a look" cannot become "already deleted"
- Custom skill packages: write your own tools, drop them in, the model picks by description
- MCP client: standard JSON-RPC 2.0 over HTTP (`tools/list` + `tools/call`), plus a REST bridge (`GET /tools` + `run`) — `termux-mcp` in Termux, a local `mt-mcp`, or any standard MCP server connects; tools are merged into the tool table and the @ panel, named `mcp_server_tool`
- Workflows: multi-step tasks chained into one flow, one sub-agent per step, contexts not shared between steps
- A2A: SendMessage / GetTask / ListTasks / CancelTask per the 1.0 spec, method names and status codes matching the spec — other agents can hand you work, yours can hand work out

### Mijia and smart home

- Packet crypto lives in the native C layer: AES-128-CBC plus MD5 / SHA-1 with standard test vectors; the Mijia page shows the self-check result and whether this build carries an injected identity
- Three sub-pages: account (authorize page → code → token, login state, logout), devices (ledger / scan / manual add / property read-write), channel (region, LAN-first, timeout and diagnostics)
- Five tools: `mijia_devices`, `mijia_scan`, `mijia_read`, `mijia_write`, `mijia_action`; property writes and actions count as high-risk, so they ask for confirmation and are disabled in plan mode
- LAN first: the local network is used whenever it answers, the cloud is the fallback
- LAN transport moved down to C++: UDP broadcast discovery and unicast request/response run in native code; probe packets, headers and JSON payloads come from Rust, covered by cargo unit tests

### Music (NetEase Cloud)

- Login goes through the weapi encrypted channel: two AES-128-CBC rounds inside C-layer OpenSSL, the random key handled by modular exponentiation; SMS-code login, and the cookie is only stored after it verifies
- After login it really queries the account once for the nickname; an expired cookie rolls back and asks you to fetch it again instead of pretending to be logged in
- Platform side: enable and priority (one platform at a time), quality tiers, cache size and cleanup, login state kept in the encrypted store
- Three tools: `music_platforms`, `music_login` (`verify` / `sms` / `code`, so the model can drive the whole SMS flow), `music_cache_clear`
- Sharing one tool table with Mijia: one sentence can check whether the login is still valid, clear the cache and set the living-room AC to 26 °C — the model orders the steps itself. Fetching and playing songs is not implemented yet; the music side covers platforms, login and cache

### LAN team collaboration

- UDP broadcast discovery plus a 6-digit pairing-code handshake; only a digest of the code is sent, never the plaintext
- Paired-device ledger with liveness checks; the handshake exchanges the work port and credentials, so the models on two devices can hand tasks to each other
- LAN settings page: discovery toggle, local name and pairing code, scan and pair, remove paired devices, transfer port and concurrency, hotspot shortcut
- `lan` tool: status, start accepting work, scan, pair, unpair, peer capabilities, dispatch and cancel tasks
- File transfer between devices on the same Wi-Fi, with configurable port and concurrency

### Screen control

- Direct accessibility access: dump the node tree, tap, long-press, swipe, type, send keys, launch apps, list apps; screenshots work without Shizuku
- On multi-step tasks a small capsule stays at the top of the screen showing what this step is doing, and disappears when the instruction finishes
- Vision plus node tree: the model can look at screenshots and read widget text and coordinates; when one is unreliable it switches to the other

### Built-in Linux

- **Virtual sandbox**: proot + a self-extracting Ubuntu 24.04 rootfs in the app's private directory; the host system is untouched and a broken sandbox resets clean
- `apt` for dependencies, `gcc` / `node` compile directly, C / C++ / Rust / Go run on the phone; the arm64 `noble` archive ships `box64` and `wine 9.0`, so Windows `.exe` runs too, with Xvfb / VNC added for GUI programs
- Two-way access to the Android filesystem: `/sdcard` and `/storage` are mounted in
- Two gates together with the path sandbox: the sandbox decides whether a path may be touched at all (sensitive system files, `..`, symlinks are stopped before any action), while proot makes sure breaking things cannot damage the host
- **Status**: executing guest programs needs the Root / Shizuku channel (under targetSdk 34 SELinux forbids `execve` from the app's private directory)

### Desktop (Windows)

- Borderless window + DWM liquid glass + WebView2 rendering surface; the UI is TypeScript + Vite
- The desktop front end is maintained outside this repo; only the shared core lives here
- Same Rust core as the phone, same version number, same tool table and config structure

### Look and feel

- **Surface material**: solid colour / soft gradient / dot pattern, plus custom wallpapers (a thumbnail appears first, then the full image, so cold starts never flash white)
- **Liquid glass**: cards, input box, floating tab bar and dialogs all use one glass material, switchable off to plain white cards; a separate tactile button style exists and is mutually exclusive with glass
- **Layout and type**: global font size is independent; message bubbles come in rounded and capsule layouts; chat avatars can be hidden
- **Dark mode**: switchable; glass, wallpaper and text colours follow
- **Character cards**: persona, scenario, sample dialogue, opening line, bound model; chat avatars can follow the current card or use your own image
- **Desktop pet**: a floating figure inside the app, animation throttled to 30fps so opening it never stutters the page
- **Floating chat window**: chat on top of other apps; width, height, font size, opacity and position are all draggable; shares the same session as the main UI
- **Session management**: pin, long-press multi-select, rename / delete, cross-session favourites, per-session drafts
- **Plugin store**: browse, install, upload, version history, comments, likes and favourites
- **Five UI languages**: Simplified Chinese / Traditional Chinese / English / Japanese / French

### Data and releases

- Sessions, characters, skills, workspace bindings and the usage ledger all live in local SQLite (`bai.db` + `bai_stat.db`, two databases), never in a cloud
- Usage stats: totals / by category / by model, plus a calendar heatmap; input, output and cache prices are editable and turned into money directly
- Full backup and restore: export database / config / small files from the data root / avatars into one file; on import it replaces everything on the next start
- In-app update check (`agent.ai.baiccb.cn/api/update`), direct download and the system installer

---

## Three execution modes (Android)

| Mode | What it needs | What it can do |
|---|---|---|
| Normal app identity | Nothing, install and go | Chat, file read/write, `exec_shell` (app identity), accessibility screenshot / tap / swipe / type, zero-width reads of `data`, network tools |
| Shizuku | Shizuku installed and authorised | System-level shell without root, `uiautomator dump`, `screencap`, background app management, deeper reads of `data` |
| Root (su) | A rooted device | System directory read/write, silent `pm` install/uninstall, global operations; tools prefer `su` |

The three modes are not exclusive switches — they are three channels behind one tool table, switchable at any time and effective from the next call.

<sub>Note: the zero-width file channel as a normal app depends on the device and OS build — some work, some do not; verify inside the app. The built-in Linux sandbox needs the Root / Shizuku channel.</sub>

---

## Security

Three key layers (Keystore-wrapped + HKDF half-key + data key), credentials stored with AES-256-GCM (file header magic `baicodexencryption`); API keys, account, SSH and GitHub credentials and skill secrets each get their own ciphertext; the model never sees raw keys, and everything leaving the app is redacted; when the vault is locked, credentials fall back to plaintext JSON with 0600 — and the UI says "not encrypted" instead of pretending otherwise.

---

## FAQ

**Do I need root?**
No. A normal app identity covers most features; add Shizuku for a system-level shell (Android 11+ recommended), and Root only to modify system directories. The built-in Linux sandbox is the exception — it needs the Root / Shizuku channel to execute programs inside it.

**Is it really free?**
Yes. AGPL-3.0, no IAP, subscriptions, ads or usage limits, and you can build your own copy. Embedding it in a closed-source product or running it as a SaaS without the AGPL source obligations requires a commercial licence — see Licensing below.

**Does any data get uploaded?**
No. Chats, files and keys stay on the device. The first launch shows a consent page; anonymous usage stats are only sent if you agree, and declining changes nothing.

**Which Android versions?**
Android 7.0 (API 24) and up, targetSdk 34, built with SDK 37. The APK ships `arm64-v8a` and `armeabi-v7a`.

---

## Developers

### Extending it

- **Skill packages**: write your own tools, drop them in, the model picks by description; a dedicated JS engine (QuickJS) can run scripts and register new tools
- **Plugin store**: browse, install, upload, version history, comments, likes and favourites
- **MCP**: standard JSON-RPC 2.0 over HTTP plus a REST bridge, so `termux-mcp` / `mt-mcp` / any standard MCP server connects
- **A2A**: SendMessage / GetTask / ListTasks / CancelTask per the 1.0 spec, handing tasks to and from other agents
- **Workflows**: multi-step tasks chained into one flow, one sub-agent per step

### Where things live

```
app/src/main/kotlin/b/ai/   Android UI and platform layer (agent / core / device / native / net / ui)
app/src/main/cpp/           Android native layer (JNI, QuickJS, image, native exec)
app/src/main/rust/          Rust core (core / exec / net / ffi / crates / android)
app/src/main/assets/linux/  Rootfs and proot for the built-in Linux
gradle/                     Build scripts (incl. seeds.gradle.kts: seed injection)
.work/script/               Build and release scripts (not in the repo; bring your own)
```

### Licence

GNU Affero General Public License v3.0 (AGPL-3.0), full text in LICENSE.

Copyright (C) 2026 沐白 (mubai0122)

**Commercial licensing**

This project is released under AGPL-3.0. Embedding it in a closed-source product, or deploying it as a SaaS service (i.e. waiving the AGPL source obligations), requires a commercial licence. Contact <bai@baiccb.cn>.

> The commercial licence covers only the project's own Kotlin / Rust / C++ / TypeScript code. proot and the Ubuntu rootfs under `app/src/main/assets/linux/` are third-party components distributed under their own licences (GPL / LGPL), and are not covered.

---

<details>
<summary><b>Build (for developers)</b></summary>

**Android**

JDK 17 and Android SDK 37 are required (set `sdk.dir` in `local.properties`).

```bash
./gradlew assembleDebug     # debug: no obfuscation, no encryption, readable logs
./gradlew assembleRelease   # release: R8 obfuscation + resource shrinking + string encryption
```

The string-encryption key never enters the repo; it is read from `local.properties`:

```properties
stringfog.key=your own key
baicodex.aes.key=seed for credential encryption
```

Building works without them — it falls back to placeholder strings (placeholder means no encryption; do not ship with them).

**Core**

```bash
cd app/src/main/rust
cargo build --bin baicodex                 # core + terminal front end

cd baiguard && cargo test                  # unit tests for the data guard layer (separate crate)
```

Front-end source and the Windows package are not in this repo (the desktop front end is maintained separately); this only builds the core and the terminal front end.

</details>