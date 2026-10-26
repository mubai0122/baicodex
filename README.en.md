# BaiAgent / baicodex

[简体中文](README.md) · **English** · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

An Android AI agent that runs entirely on your phone. The model is called directly; reading and writing files, running commands and running Linux all happen on the device, with no relay server in between — your chats, files and API keys never leave your phone.

It is not just a chat window either. Say "clear the background apps", "check tomorrow's tickets to Shanghai" or "help me find what this error is", and it looks at the screen, breaks the task into steps, calls tools, and comes back with the result.

Package `b.ai`, minSdk 24 (Android 7.0, targetSdk 34), Kotlin + Jetpack Compose, single Activity, ABIs `arm64-v8a` / `armeabi-v7a`.

- Website: <https://agent.ai.baiccb.cn>
- Download: <https://github.com/mubai0122/baicodex/releases/latest>
- Free and open source: GPL-3.0, no in-app purchases, no ads, no usage limits, all code is here

## Screenshots

| Phone · light | Phone · dark |
|:---:|:---:|
| <img src="https://agent.ai.baiccb.cn/show/phone-clip-light.png" width="280" alt="phone light"> | <img src="https://agent.ai.baiccb.cn/show/phone-clip-dark.png" width="280" alt="phone dark"> |

| Desktop · light |
|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-light.png" width="860" alt="desktop light"> |

| Desktop · dark |
|:---:|
| <img src="https://agent.ai.baiccb.cn/show/laptop-dark.png" width="860" alt="desktop dark"> |

## How it differs from a normal AI app

- **It actually does things, not just replies**: the agent loop carries tool calls — reading files, writing files, running shell, running Linux and tapping the screen are all real execution, and results are fed back to the model for the next step
- **Three privilege tiers, your choice**: plain app identity is enough to start (accessibility + zero-width file channel), attach Shizuku for system-level capability, go Root for everything; the tool layer picks the route itself and marks which one was used in the confirmation dialog
- **Zero-width file channel**: `read_file` / `write_file` / `make_dir` / `delete_file` can reach other apps' data directories without root; `exec_shell` always uses the original path and is unaffected by this transform
- **Data stays home**: sessions, personas, skills and usage all live in local SQLite, nothing is uploaded; only the model endpoint you configure plus the search and 12306 tools you use on purpose will go online
- **Everything can be swapped**: models, search providers, tool permissions, skill packs and thinking levels are all configurable in settings, and you can rebuild from source yourself

## Three run modes

| Mode | What it needs | What it can do |
|---|---|---|
| Plain app identity | Install and go | Chat, file read/write, `exec_shell` (app identity), accessibility screenshot / tap / swipe / input, read other apps' data via the zero-width channel, network tools |
| Shizuku | Install Shizuku and grant it | System-level shell without root, `uiautomator dump`, `screencap`, background app management, deep reads into app data |
| Root (su) | Device already rooted | Read/write system directories, silent install/uninstall via pm, global operations; the tool layer prefers su |

These are not mutually exclusive switches but three channels under the same tool table — switch any time and the next call uses the new route.

## Capabilities

### Models and conversation

- Multiple model profiles: one profile = provider + base URL + key + model + capability flags (vision / tool calling / reasoning); switch at any time, any OpenAI-compatible endpoint works
- Thinking levels (low / medium / high), max mode, temperature and extra parameters; SSE streaming with a collapsible reasoning block
- Message branches: regenerate or edit-and-resend without deleting the old answer — a second branch grows and `‹ 1/2 ›` lets you flip between them (message tree, DB v11)
- Permanent memory: the model decides which preferences and conclusions are worth keeping, stores them in "memory", you can delete them one by one from the drawer; unless deleted, they are sent with the system prompt every turn (DB v12)
- Context compaction: triggered by a window ratio (70% by default) plus an uncompacted-message threshold, so long chats do not blow the window
- Voice input, speak and send; every answer shows tokens, tok/s, time to first token, provider and model

### Tools and execution

- Built-in tools: the four file tools, `exec_shell`, `exec_linux`, `screen` (on-screen operation), `web_search`, 12306 seat / transfer / stop lookup, memory writes, scheduled tasks
- Three search providers: Tavily, Bing, Google CSE — bring your own key; if none is configured the search tool is not handed to the model at all
- Three execution channels (plain / Shizuku / Root), the tool layer picks the route and the dialog shows the actual path
- Per-tool permissions: ask / allow / deny, with high-risk tools flagged separately; minimal mode exposes only five tools as a fallback for weaker models
- Plan mode: read-only commands pass, any write inside `exec_shell` is rejected, so "let me have a look" cannot turn into "already deleted"
- Custom skill packs: write your own tools, the model picks them by description
- MCP client: JSON-RPC 2.0 over HTTP, connected server capabilities join the tool table and the @ panel, tool names are prefixed `mcp_server_capability`

### On-screen operation

- Direct accessibility service: dump the node tree, tap, long-press, swipe, input, key events, launch apps, list apps — screenshots work without Shizuku
- A capsule stays on top during multi-step tasks showing what the current step is, and collapses by itself when the instruction is done
- Vision plus node tree: the model can read screenshots as well as control text and coordinates, switching routes when one is uncertain

### Built-in Linux

- proot + self-extracting rootfs, installed automatically on first launch, no root needed and the host system is untouched
- `apt` for dependencies, `gcc` / `node` compile directly; C / C++ / Rust / Go all run on the phone
- Two-way interop with the Android filesystem, `/sdcard` and `/storage` are mounted straight in
- Sandboxed: reset any time without polluting the system

### UI and personalization

- Sessions: pin, long-press multi-select, rename / delete, cross-session stars, per-session drafts
- Persona cards (persona / scenario / example dialogue / greeting / bound model) and custom skill packs
- Appearance: solid / soft gradient / dot-matrix background, liquid-glass cards and bottom bar, tactile buttons, global font scale, dark mode
- Five UI languages: 简体中文 / 繁體中文 / English / 日本語 / Français

### Data and release

- Sessions, personas, skills, workspace bindings and the usage ledger all live in local SQLite (two databases: `bai.db` + `bai_stat.db`), never in the cloud
- Usage statistics: totals / by category / by model plus a date grid, fill in unit prices for input, output and cache to get real cost
- Full backup and restore: export database / config / small data files / avatars as a zip, it is swapped in on next launch after import
- In-app update check (`agent.ai.baiccb.cn/api/update`), direct-link download then the system installer is launched

## FAQ

**Do I need root?**

No. Plain app identity covers most features; attach Shizuku (Android 11+ recommended) for system-level shell, and only modifying system directories needs root.

**Is it really free?**

Yes. GPL-3.0 open source, no purchases, subscriptions, ads or usage limits, and you can build your own copy.

**Is my data uploaded?**

No. Chats, files and keys stay on the device. The first launch shows a consent screen; anonymous usage statistics are only reported after you agree, declining changes nothing about the features.

**Which Android versions are supported?**

Android 7.0 (API 24) and up, targetSdk 34, built with SDK 37. The APK contains `arm64-v8a` and `armeabi-v7a`.

## Build

Requires JDK 17 and Android SDK 37 (`sdk.dir` in `local.properties`).

```bash
./gradlew assembleDebug     # debug: no obfuscation, no string encryption, readable logs
./gradlew assembleRelease   # release: R8 obfuscation + resource shrinking + string encryption
```

Release does three things, configured in `app/proguard-rules.pro` and `app/build.gradle.kts`:

1. **R8 obfuscation**: everything except `MainActivity`, the manifest entry points and what third-party libraries need is renamed, using a custom short-name dictionary (`app/obfuscation_dict.txt`), and `-repackageclasses` wipes the package names too
2. **String encryption**: StringFog with AES, each string gets its own random key
3. **Log removal**: `Log` / `println` become empty statements in release

The string-encryption key is not in the repository, it is read from `local.properties`:

```properties
stringfog.key=your-own-key
```

Builds work without it, falling back to a placeholder string (a placeholder means no encryption — never ship a build made with it).

## Layout

```
app/src/main/kotlin/b/ai/
  agent/    agent loop and tool definitions
  core/     config, database, workspace, stats, speech
  exec/     shell / root / shizuku channels, accessibility, scheduled tasks
  net/      LLM client, search, reporting, update check
  ui/       Compose UI
app/src/main/assets/linux/   rootfs and proot for the built-in Ubuntu
scripts/   build and asset scripts
```

## License

GNU General Public License v3.0, full text in [LICENSE](LICENSE).

Copyright (C) 2026 沐白 (mubai0122)