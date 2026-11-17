# Quick Monitor

Server-side admin spectate and **read-only** inventory inspection for Fabric servers.
**No client mod required — vanilla clients work.**

Built for Minecraft **26.2** · Fabric Loader 0.19.5+ · Java 25+.

> **Server-side only.** Do not install this mod on clients; it does nothing there.

## Features

- `/monitor <player>` — puts the admin into spectator mode with the camera bound to the target;
- **Live container mirror** — whatever the target opens (chest, barrel, hopper, villager trades…) is shown to the admin as a read-only copy; any click is cancelled and the real content is re-sent;
- **Live inventory view** — when the target moves items in their own inventory, a 5-row window opens in the classic layout: main inventory (3 rows), armour + offhand, hotbar on its own bottom row;
- **Action bar readout** — target health, armour and held item, refreshed whenever they change;
- **Clean exit dialog** — press `Shift` or run `/monitor stop`, then choose:
  - *Restore mode & position* — back to exactly where and how you were before monitoring;
  - *Stay in spectator* — remain a spectator where you are and keep watching manually.

## Requirements

| Item | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | any 26.2 build |
| Java | 25 or newer |

## Installation

1. Install Fabric Loader on the **server**;
2. Drop `fabric-api-*.jar` and `quick-monitor-*.jar` into the server's `mods/` folder;
3. Start the server.

Nothing needs to be installed on players' clients.

## Commands & permissions

| Command | Effect | Permission |
|---|---|---|
| `/monitor <player>` | Start monitoring | GAMEMASTERS (OP level 2+) |
| `/monitor stop` | Open the exit dialog | GAMEMASTERS |
| `/monitor stop restore` | Exit and restore mode + position | GAMEMASTERS |
| `/monitor stop spectate` | Exit and stay in spectator | GAMEMASTERS |

All commands require operator permission. Regular players cannot start monitoring or see the mirror by any means.

## Safety & privacy

- **Read-only by design.** Target inventories and containers are copied into mirror menus; clicks on a mirror are cancelled and the authoritative content is re-sent to the admin.
- **The admin's own inventory is borrowed, not consumed.** On start it is saved to `config/quick-monitor/watch-inventory.json`, replaced with a display copy of the target's items, and restored on exit, disconnect or server shutdown. If the server crashes mid-session, the snapshot is restored the next time the admin joins.
- **No network access.** The mod makes no outbound connections and collects no telemetry.
- **Audit trail.** Every start and exit is appended to `config/quick-monitor/audit-YYYY-MM-DD.jsonl` (admin, target, reason, exit mode).
- **Disconnect-safe.** If the admin disconnects mid-session, the original game mode is restored before player data is saved.

## Configuration

The mod creates `config/quick-monitor/` on first use:

| File | Purpose |
|---|---|
| `watch-inventory.json` | crash-safe snapshot of the admin's inventory while monitoring |
| `audit-YYYY-MM-DD.jsonl` | one JSON line per start/exit |

## Building from source

Requires JDK 25 and an existing Fabric 26.2 server installation to compile against.

```powershell
.\build.ps1 -ServerDir 'C:\path\to\fabric-server' -Jdk 'C:\path\to\jdk-25'
```

The jar is written to `build/`.

## License

MIT — see [LICENSE](LICENSE).
