<p align="center"><img src="docs/logo.png" width="128" alt="BaiAgent logo"></p>

# BaiAgent / baicodex

[简体中文](README.md) · **English** · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

**An Android Agent.** It does more than chat: tell it "build me an app and compile it", "help me locate this error", or "write a script and run it", and it will read the screen, break down steps, call tools, write code, and build — then hand the result back to you.

**Open-source and convenient**: it runs right after install, no PC required. Native **MCP** and **Mi Home (米家)** ecosystem support, under active maintenance.

**Privacy**: the model connects directly. Conversations, files, and keys all stay on your device — no relay server involved.

- Website: <https://agent.ai.baiccb.cn>
- Tutorial (How to Master AndroidAgent): <https://agent.ai.baiccb.cn/docs/>

---

## Highlights

1. **Android Agent, runs without a PC**
   Works as a normal app right after install. Attach Shizuku for system-level capabilities without Root, or go Root to unlock everything. The three tiers are not mutually exclusive switches — one tool table, three channels, auto-routed internally.
   <sub>The normal-identity zero-width file channel (read/write any app's data) is not supported on all devices — verify in-app after install.</sub>

2. **Built-in Linux sandbox, actually runs things**
   proot + self-extracting Ubuntu 24.04 rootfs, installed in the app's private directory without touching the host. Break it and a reset gives you a clean slate. `apt` for dependencies, compile directly with gcc/node — C/C++/Rust/Go all run; Windows exe runs too. Theoretically can run Android Studio.

3. **Multi-agent, switchable**
   Character cards swap persona, scenario, greeting, and bound model — switching characters means switching a personality and a speaking style. Multi-prompt switching supported.

4. **Serious security**
   Credentials are encrypted at rest; the device key is wrapped by Android Keystore, plaintext never leaves the hardware. The model cannot read raw keys. Outbound output is uniformly sanitized — with strong encryption enabled, `sk-`/`ghp_`/PEM/Bearer are all stripped before writing logs, feeding back into the model, or exporting diagnostics.

5. **Screen automation, no Root needed**
   Direct AccessibilityService connection: dump the node tree, tap, long-press, swipe, type, key events, open apps, list apps — all without Shizuku or Root.

6. **Highly playable MCP**
   Standard JSON-RPC 2.0 over HTTP + REST bridge. Connect any standard MCP service like termux-mcp in Termux; conversely, others can use baicodex as an MCP too.

7. **SSH / GitHub / Skill — extensibility maxed out**
   Full SSH toolset; full GitHub skill-pack toolset; both private-protocol skills (highly playful) and general-protocol skills are parsed.

8. **Context compression without losing intelligence or memory**
   The safety boundary force-keeps key info like conclusions and file paths. Triggered by window ratio + message-count threshold. Long-term memory is stored separately and doesn't vanish on compression.

9. **Long-term memory**
   Preferences and conclusions the model deems worth remembering are stored in "memory", injected with each system prompt, and manageable one by one in the sidebar.

10. **Broad tech stack**
    Rust core + C++ native layer + Kotlin / Compose UI + TypeScript / WebView2 desktop, npmCI supported.

11. **Mi Home, NetEase Cloud Music, and LAN collaboration are all natively implemented**
    Mi Home controls Xiaomi smart devices over LAN and cloud; NetEase Cloud Music can interlink with Mi Home after login; multiple devices on the same Wi-Fi can form a team and transfer files after a pairing-code handshake.

---

## UI Preview

**Phone (light)**　　**Phone (dark)**

<img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png?v=20261108" width="280" alt="Phone light"> <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png?v=20261108" width="280" alt="Phone dark">

---

## How it differs from a normal AI app

- **It actually does things, not just talks**: agent tool calls — reading/writing files, running commands, tapping the screen — are real executions, with results fed back for the next step.
- **Three-tier permissions, your choice**: normal users unlock most features; Shizuku and Root unlock the full set.
- **data access**: on supported devices, restricted directories are mapped to virtual paths to reach into the data directory.
- **Data never leaves**: conversations, characters, skills, and usage all live in local SQLite.
- **On-device inference**: run large models on the phone GPU, `llama` supported, fully local.

---

## Capabilities

### Models & Conversation

- Multi-model config switching: provider + BaseURL + Key + model + capability flags, any OpenAI-compatible API works.
- Reasoning effort, max mode, temperature and extra params; SSE streaming output with collapsible thinking blocks.
- Message branching: regenerate / edit-and-resend never delete old answers; switch between them.
- Voice input: just speak and send.
- Answers can show token, tok/s, first-response latency, provider and model, etc.
- Inject current time, battery, location, notifications, and device usage time so it understands you better.

### Tools & Execution
### Comes with defaults, and lets you write your own

- File ops: read, write, partial edit, mkdir, delete
- Commands: exec_shell, linux_shell, screen automation, browser, network, app reverse-engineering, QuickJS, 12306 ticket lookup, scheduled tasks
- SSH: connect, list dir, read, write, elevate & execute, run commands, download, upload. Credentials are reusable.
- GitHub: query repos, issues, PRs, read repo files, create or update single files, commit, etc.
- Web: multi-engine search, site content viewing, direct HTTP
- Built-in browser: BrowserWebView reads rendered pages — open and return body text, extract content, list links, tap by text, fill forms, paginate, run JS, pass verification.
- Tool permission settings: ask / allow / deny
- Plan mode: read-only commands allowed, no file operations permitted, plan audit output allowed.
- Custom skill packs: the model writes skill packs for you.
- Workflows: multi-step tasks run in parallel across multiple streams.
- A2A: interoperate with other Agents.

### Mi Home & Smart Home

- Tools: `mijia_devices`, `mijia_scan`, `mijia_read`, `mijia_write`, `mijia_action`

### Music

- Tools: `music_platforms`, `music_login` (`verify` / `sms` / `code`)

*Shares tools with Mi Home: in one sentence you can — play a song → write code → set the living-room AC to 26 °C.*

### LAN Team Collaboration

- UDP broadcast discovery, pairing-code handshake
- Liveness check for paired devices
- LAN configuration
- `lan` tool: check status, start the accept-work service, scan, pair, unpair, view peer capabilities, assign/cancel tasks
- Transfer files between multiple devices on the same Wi-Fi; ports and concurrency are configurable.

### Screen Automation

- Direct AccessibilityService connection; can take over the screen.
- A persistent action capsule stays at the top during multi-step tasks.
- Dual-path visual node tree: the model can see screenshots and read control text and coordinates.

### Built-in Linux

- **Virtual sandbox**: doesn't run on the host, so `rm /*` is nothing to fear.
- Bidirectional interop with the Android filesystem, with extra audit confirmation allowed.

### UI & Personalization

- **Surface materials**: solid color / soft gradient / dot-matrix background, custom wallpaper
- **Liquid glass**: cards, input box, floating dock, and popups support liquid glass
- **Layout & font size**: fonts and more are customizable
- **Dark mode**: custom themes and color schemes
- **Desktop pet**: a big chubby fish, ovo
- **Floating chat window**: chat right on top of other apps, style configurable
- **Session management**: pin, long-press multi-select, rename, delete, cross-session favorites, per-session drafts
- **Plugin plaza**: browse, install, upload, version history, comments
- **Five languages**: 简体中文 / 繁體中文 / English / 日本語 / Français

### Data & Release

- Local dual SQLite databases; end-to-end encrypted cloud backup in development
- Usage stats: totals, amount + date heatmap, charts
- Full-database backup & restore: encrypted backup and restore supported

---

## Three Run Modes (Android)

| Mode | Requires | Can do |
|---|---|---|
| Normal app identity | Works right after install | Chat, file read/write, run commands, screen automation, zero-width data read, network tools, etc. |
| Shizuku | Shizuku authorization | System-level shell, `uiautomator dump`, `screencap`, background app management, deep data read |
| Root (su) | Device is rooted | System directory read/write, silent pm install/uninstall, global operations; tools prefer su internally |

---

## Security

Up to three key layers, credentials encrypted with AES-256-GCM. API keys, accounts, SSH and GitHub credentials, and skill secrets are all stored as ciphertext. The model cannot read keys; output sanitization is supported.

---

## Developers

### Directory Quick Reference

```
app/src/main/kotlin/b/ai/   Android UI & platform layer (agent / core / device / native / net / ui)
app/src/main/cpp/           Android native layer (JNI, QuickJS, image, native exec, Mi Home crypto)
app/src/main/rust/          Rust core: single crate, core (base/security/platform/feature blocks) + ffi split by domain
app/src/main/assets/linux/  Built-in Linux rootfs and proot
gradle/                     Build scripts (incl. seeds.gradle.kts: seed injection parsing & index table)
.work/script/               Build & release scripts (excluded from the repo; provide your own after cloning)
```

### License

GNU Affero General Public License v3.0 (AGPL-3.0); see LICENSE for the full text.

Copyright (C) 2026 沐白 (mubai0122)

**Commercial Licensing Statement**

This project uses the AGPL-3.0 license.

> The commercial license only covers this project's own Kotlin / Rust / C++ / TypeScript code. The proot and Ubuntu rootfs under `app/src/main/assets/linux/` are third-party components distributed under their respective licenses (GPL / LGPL, etc.).

---

<details>
  <summary><b>Build (developers expand)</b></summary>

**Android**

JAVA 17+, Android SDK 37+

```bash
./gradlew assembleDebug   # debug
./gradlew assembleRelease # release
```

String-encryption keys and credentials are read from `local.properties`; see `local.properties.todo` for the template.

Builds without configuration too, auto-falling back to placeholder strings.
`seed.lan.code` is not packaged by default; add `-PallowSeedCred=1` for personal builds to inject it.

**Core & Desktop**

```bash
cd app/src/main/rust
cargo build --bin baicodex   # core
cargo test --release    # core tests (all)
```

</details>