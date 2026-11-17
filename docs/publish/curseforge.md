# Quick Monitor — CurseForge 项目页文案

> 用法：CurseForge 项目描述的 **Markdown** 框里粘贴下面的正文；上方元数据按 checklist.md 填。

---

## Quick Monitor

Admin spectate and **read-only** inventory inspection for Fabric servers.
**Server-side only — vanilla clients work, no client mod needed.**

Minecraft **26.2** · Fabric Loader 0.19.5+ · Fabric API · Java 25+

### What it does

- `/monitor <player>` — spectator camera bound to the target
- Live, read-only mirror of whatever container the target opens
- Live 5-row inventory view in the classic layout (main inventory / armour + offhand / hotbar)
- Action bar shows the target's health, armour and held item
- `Shift` or `/monitor stop` opens an exit dialog: restore your mode & position, or stay in spectator

### Requirements

- Minecraft 26.2
- Fabric Loader 0.19.5+
- Fabric API (26.2)
- Java 25+

### Install

1. Install Fabric Loader on the server
2. Put `fabric-api-*.jar` and `quick-monitor-*.jar` into `mods/`
3. Start the server — nothing on the client

### Commands

| Command | Permission |
|---|---|
| `/monitor <player>` | GAMEMASTERS |
| `/monitor stop` | GAMEMASTERS |
| `/monitor stop restore` | GAMEMASTERS |
| `/monitor stop spectate` | GAMEMASTERS |

### Safety

- Mirrors are read-only; clicks are cancelled and re-synced
- The admin's inventory is snapshotted to disk before being replaced, and restored on exit / disconnect / shutdown
- No outbound connections, no telemetry
- Audit log in `config/quick-monitor/`

### License

MIT
