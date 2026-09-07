<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-09-08

### Added
- **Git** — 对话页 ⋮ 菜单新增 Git 入口（仅当项目为 git 仓库时显示），独立 Git 页支持：
  - 仓库选择器（顶层仓库 + 子模块 + 嵌套仓库）、当前分支与工作区状态
  - 变更文件列表（含未跟踪文件内容）、提交历史（点击展开文件变更 + 逐文件 diff）
  - commit / push / pull（多 remote 可选）/ 切换分支 / 新建分支
  - AI 生成 commit message（优先云端 LLM、回退端侧模型，参考最近提交风格）
  - 常驻 PTY 会话复用，操作秒回
- **设计对齐**：会话状态彩色徽章、状态色收敛到语义 token、诊断页圆角/AMOLED 描边统一。

### Changed
- 端侧模型下载源从 GitHub Releases 切换为 **ModelScope**（国内直连更快），逐文件下载并校验 SHA-256；运行时优先从打包 assets 解压，无打包时走 ModelScope。
- 设置页 LLM「测试连接」改为发送真实单轮对话并回显模型回复，报错分类友好提示（中英双语）。

### Fixed
- 端侧模型已打包进 assets 但运行时从不使用（此前永远提示下载）。
- Git 数据「不是仓库」误判、PTY 命令发早被吞、并发操作互相覆盖导致的超时/空数据。

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
