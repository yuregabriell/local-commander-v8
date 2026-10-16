# Changelog

## V8.1 — 2026-10-16

### Added
- Persistent terminal sessions with start/send/read/stop lifecycle.
- Clipboard read/write on Windows.
- Bounded binary file transfer helpers.
- Agent Mode task state with task IDs, steps, pause/resume, checkpoints and completion.
- Structured browser snapshots with interactive element inventory.
- Dashboard indicators for Agent Mode and terminal sessions.
- Persistent crash diagnostics under `runtime/logs/`.

### Improved
- First-run launcher now keeps the terminal open on fatal errors.
- Python embeddable import handling for sibling modules.
- Windows clipboard pointer handling on 64-bit systems.
- First-run browser/help flow starts after the dashboard is initialized.
- Step 6 now displays: `Preparando navegador automatizado, aguarde...`.

### Compatibility
- ChatGPT remains the intelligence/planning layer; no local LLM or local API key is required.
- Existing V8 relay actions remain compatible.
- V8.1 local actions are implemented in the desktop agent; direct ChatGPT invocation still depends on the hosted MCP relay exposing those actions.

### Verified
Release regression suite passed 11/11 local tests. End-to-end Windows tests previously confirmed files, shell/processes, Git, browser automation, desktop screenshot, Agent Mode, persistent terminal, binary transfer and clipboard.
