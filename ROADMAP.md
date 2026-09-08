<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Roadmap

> 本文件是方向性规划，**非承诺**。优先级与时间可能调整。
> 状态标记：✅ 已发布 · 🚧 进行中 · ⏳ 计划中
> 相关 issue 用 `#编号` 链接；版本用 GitHub Milestone 跟踪。

## 架构约定

- **纯 Android 客户端，不改后端**。
- **常驻 PTY**：连接服务器时建立一条持久终端会话，Git、文件编辑、硬件信息、重启等操作
  **共用同一条 PTY**，避免重复 shell 启动开销。
- 已有 REST API（`/config`、`/session/status`、`/global/health` 等）优先用 REST；REST 没有的
  能力统一走常驻 PTY 跑 shell 命令。

## Now — 1.1.0

### 服务器与服务管理 — [#16](https://github.com/hiylo/opencode/issues/16)

- [ ] 常驻 PTY 提升为「连接级共享会话」（当前是 Git 页作用域）
- [ ] 服务器基本信息：CPU / 内存 / 磁盘（PTY 跑 `free` / `df` / `/proc`）
- [ ] 服务基本信息：版本号、运行中的活跃会话（`/global/health` + `/session/status`）
- [ ] 服务配置查看与修改（`GET/PATCH /config`、`/global/config`）
- [ ] 服务重启（PTY 跑重启命令，可操作性取决于服务端部署方式/权限）
- [ ] 服务器健康监控仪表盘

### 文件编辑 — [#17](https://github.com/hiylo/opencode/issues/17)

- [ ] 文件浏览器支持编辑与保存（写入走常驻 PTY，如 `cat > path` / `tee`）
- [ ] 撤销 / 重做、保存冲突提示

### Git 深化 — [#18](https://github.com/hiylo/opencode/issues/18)

- [ ] 选择性暂存（勾选文件 / hunk）
- [ ] `fetch` / `stash` / `tag`
- [ ] 提交历史加载更多（分页）
- [ ] 未推送提交提示

### 端侧模型扩展 — [#19](https://github.com/hiylo/opencode/issues/19)

- [ ] 代码补全（inline completion）
- [ ] 对话总结 / 代码解释

### 聊天体验 — [#20](https://github.com/hiylo/opencode/issues/20)

- [ ] Markdown 增强（mermaid 图、图片内联、代码块复制/行号/运行）
- [ ] 消息操作（编辑重发、重新生成、停止生成）
- [ ] 会话内搜索

## Later — Backlog

- [ ] server 端 git REST 端点（可选优化，非必须） — [#21](https://github.com/hiylo/opencode/issues/21)
- [ ] 多设备会话同步与推送 — [#22](https://github.com/hiylo/opencode/issues/22)
- [ ] 工程质量（单元测试、性能、无障碍、平板/折叠屏适配） — [#23](https://github.com/hiylo/opencode/issues/23)
