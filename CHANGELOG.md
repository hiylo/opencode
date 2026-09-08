<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-09-08

### Added
- **服务器与服务管理** — 连接级共享 PTY 会话；服务器管理页展示服务信息（版本/活跃会话）、系统信息（CPU/内存/磁盘/负载）；服务配置查看与修改（`/config`）；服务重启。
- **服务商管理** — 添加/编辑/删除自定义服务商，增删模型（模型 ID + 名称），保存走 `PATCH /config` 并自动保留 apiKey 等字段。
- **Git 页** — 对话页 ⋮ 菜单入口（仅当项目为 git 仓库时显示），仓库选择器（顶层 + 子模块 + 嵌套仓库）、分支/状态、变更列表（含未跟踪文件内容）、提交历史（展开文件变更 + 逐文件 diff）、commit/push/pull（多 remote 选择）/切换分支/新建分支、AI 生成 commit message（云端优先、端侧回退）。
- **Git 深化** — 选择性暂存（勾选文件）、`fetch`/`stash`/`tag`、提交历史分页加载更多、未推送提交提示。
- **文件编辑** — 文件预览支持编辑与保存（写入走共享 PTY，base64 分块）。
- **聊天体验** — 消息操作重构为 ⋮ 下拉菜单（复制/重新生成/编辑/总结/撤销）；对话总结（消息级 + 会话级，云端 LLM 优先、端侧模型回退、流式展示、可随时关闭）。
- **设计对齐** — 会话状态彩色徽章、状态色收敛到语义 token、诊断页圆角/AMOLED 描边统一。

### Fixed
- Git 首次进入偶发误判「不是仓库」（目录解析/PTY 竞争），已加自动重试。
- 服务商保存/删除提示本地化（原硬编码英文）。
- 技术债：云端总结/commit message token 上限、服务商配置并发写窗口、大文件单条 base64 命令超限。

## [1.0.1] - 2026-09-08

### Changed
- 端侧模型下载源从 GitHub Releases 切换为 **ModelScope**（国内直连更快），逐文件下载并校验 SHA-256；运行时优先从打包 assets 解压，无打包时走 ModelScope。
- 设置页 LLM「测试连接」改为发送真实单轮对话并回显模型回复，报错分类友好提示（中英双语）。

### Fixed
- 端侧模型已打包进 assets 但运行时从不使用（此前永远提示下载）。

## [1.0.0] - 2026-09-07

### Added
- Native Material 3 chat interface with GFM Markdown, code blocks, syntax highlighting, and copy actions.
- Real-time message streaming with auto-scroll.
- **On-device suggestions** — next-step prompts generated fully offline by a bundled MNN model
  (Qwen3.5-0.8B), with an optional external OpenAI-compatible LLM provider for higher quality and
  silent fallback to the on-device model.
- **Bundled model auto-extraction** — the on-device model ships in `assets/models/` and is extracted
  on first use with progress; when a build has no bundled weights the model is downloaded from
  **ModelScope** (per-file, SHA-256 verified), shown in Settings with prepare/download progress.
- **ModelScope download source** — replaces the GitHub Release zip for model distribution (fast
  inside mainland China), with an app-tuned `config.json` (12 threads, low precision, thinking off).
- **Settings download entry** — a "on-device model" item in Settings with status, progress and a
  download button.
- **Accent color** — six brand accent palettes (indigo/violet/cyan/green/amber/red) applied
  app-wide across Light/Dark/AMOLED and included in settings sync.
- **Grouped settings cards** — settings sections rendered as rounded cards (12dp, surfaceContainer,
  1dp outline in AMOLED) matching the design system.
- **Conversation links** — same-origin links open inside an in-app WebView with Basic Auth; external
  links stay in the system browser, avoiding credential leaks.
- **Friendly LLM connection test** — Test connection sends a real single-turn conversation and
  shows the model's reply, mapping auth/404/4xx/5xx/timeout/DNS/connect/URL errors to friendly,
  bilingual (zh/en) messages.
- External LLM provider configuration screen (Base URL / model / API key stored in the Android Keystore).
- Adaptive two-pane layout on foldables and tablets (session list + chat side by side).
- Session management: search, favorite, categorize, fork, compact, share, export, and delete.
- Completed-but-unread sessions pinned to the top of the session list.
- Multi-server connection with stable reconnection and status polling.
- Terminal mode with PTY over WebSocket.
- Secure in-app updates verified by SHA-256 and signing certificate.
- DESIGN.md — the app's Material 3 design system document.

### Changed
- Suggestion generation moved from a server round-trip to on-device inference.
- Session list sorted by the last user message time instead of response time, eliminating
  re-ordering while streaming.
- Model download source switched from GitHub Releases to ModelScope.
- Removed the Termux local-runtime feature.

### Fixed
- Crash on JNI streaming callback due to R8-obfuscated interface class names.
- Native crash (Scudo) when a coroutine timeout cancelled a blocking MNN generation.
- Bundled model never being used at runtime (code only looked at the download path).
- Completed sessions not being removed from the list after delete.
- Parent sessions not showing as busy while their sub-agent children were running.
- Notification tasks channel producing no sound on some devices (channel now binds the system
  default notification sound).
