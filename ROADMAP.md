<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Roadmap

> This file is directional planning, **not a commitment**. Priorities and timing may change.
> Status markers: ✅ Released · 🚧 In progress · ⏳ Planned
> Related issues link with `#number`; versions are tracked with GitHub Milestones.

## Architecture conventions

- **A universal client — never change the official server, only improve the client.**
- **Persistent PTY**: when connecting to a server, establish one long-lived terminal session that
  Git, file editing, hardware info, restart, and other operations **share**, avoiding repeated
  shell startup overhead.
- Prefer existing REST APIs (`/config`, `/session/status`, `/global/health`, etc.); anything REST
  cannot do goes through the persistent PTY running shell commands.

## 1.1.0 (Released ✅)

### Server & service management — [#16](https://github.com/hiylo/opencode/issues/16)

- [x] Persistent PTY promoted to a "connection-level shared session" (was previously scoped to the Git page)
- [x] Server basics: CPU / memory / disk (PTY runs `free` / `df` / `/proc`)
- [x] Service basics: version, active sessions (`/global/health` + `/session/status`)
- [x] Service config view & edit (`GET/PATCH /config`, `/global/config`)
- [x] Service restart (PTY runs the restart command; feasibility depends on deployment/permissions)
- [x] Server health monitoring dashboard

### File editing — [#17](https://github.com/hiylo/opencode/issues/17)

- [x] File browser supports edit & save (writes go through the persistent PTY, e.g. `cat > path` / `tee`)
- [x] Undo / redo, save-conflict prompt

### Git deepening — [#18](https://github.com/hiylo/opencode/issues/18)

- [x] Selective staging (check files / hunks)
- [x] `fetch` / `stash` / `tag`
- [x] Commit history load-more (pagination)
- [x] Unpushed-commit indicator

### On-device model expansion — [#19](https://github.com/hiylo/opencode/issues/19)

- [x] Code completion (inline completion)
- [x] Conversation summary / code explanation

### Chat experience — [#20](https://github.com/hiylo/opencode/issues/20)

- [x] Markdown enhancements (mermaid diagrams, inline images, code-block copy/line numbers/run)
- [x] Message actions (edit & resend, regenerate, stop)
- [x] In-session search

## Now — 1.2.0

> Everything below is a **pure client** change — the opencode server is untouched.
> Exceptions: voice input (relies on ASR capability) and image understanding (relies on the model
> supporting vision); neither involves server-side code.

### Sessions & project management

- [ ] Session search / filter (by title, directory, time)
- [ ] Batch archive / delete
- [ ] Session pinning with drag-to-reorder
- [ ] Collapsible project groups, quick entry for recent projects

### Chat experience

- [ ] Edit & resend after editing a message
- [ ] Message quoting / reply (@ a message)
- [ ] Long-press message menu: copy / re-edit & resend / quote & reply
- [ ] Markdown table rendering
- [ ] One-tap code-block copy + language label + long-code collapse
- [ ] Streaming typewriter optimization, resume on reconnect
- [ ] Send feedback: vibration / sound after sending, typing-cursor animation while generating
- [ ] Timestamp grouping: message dividers by time (today / yesterday / earlier)
- [ ] Scroll-to-bottom button polish: unread-new-message red dot
- [ ] Empty state: new-session onboarding placeholder (quick commands / suggestions)
- [ ] Voice input (ASR: uses Android's built-in `SpeechRecognizer`, free / offline / no third-party
  network service)
- [ ] Image understanding (multimodal: depends on whether the selected model supports vision; the
  image entry is enabled only for vision-capable models, otherwise show "model does not support
  vision")

### Agents / tools

- [ ] Permission-request cards + "always allow"
- [ ] Todo list with live progress bars
- [ ] Sub-agent tree / timeline view
- [ ] Custom Slash command quick panel

### Servers & connectivity

- [ ] Multiple servers online simultaneously, one-tap switching
- [ ] SSH tunnel direct connection, connection health monitoring
- [ ] Message pagination / history-loading optimization (load-older already exists)

### Localization & experience

- [ ] Independent Dark / AMOLED theme toggles, follow-system
- [ ] Finer-grained font size / line height
- [ ] Global search: title / directory / time (across sessions and projects)
- [ ] Session export (Markdown / JSON)

## Later — Backlog

- [ ] Full-text message search (local FTS, data-size TBD)
- [ ] Engineering quality (unit tests, performance, accessibility, tablet/foldable adaptation) — [#23](https://github.com/hiylo/opencode/issues/23)
