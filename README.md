# Quick Monitor

Server-side admin spectate and **read-only** inventory inspection for Fabric servers.
No client mod required — vanilla clients work.

Built for Minecraft **26.2** (Fabric Loader 0.19.5+, Java 25+).

## What it does

- `/monitor <player>` puts the admin into spectator mode with the camera bound to the target;
- the target's open container is mirrored as a **read-only** view (clicks are cancelled and re-synced);
- the admin's own hotbar/inventory is temporarily swapped for a **copy** of the target's, purely for display;
- the target's held item is shown in the action bar;
- press **Shift** or run `/monitor stop` to leave, then choose:
  - **Restore mode & position** — back to where and how you were before monitoring;
  - **Stay in spectator** — remain a spectator where you are and keep watching manually.

Everything the admin sees is a snapshot copy. The target's inventory and containers are never modified.

## Requirements

| Item | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5+ |
| Fabric API | any 26.2 build |
| Java | 25+ |

## Installation

1. Install Fabric Loader on the **server**;
2. Drop `fabric-api-*.jar` and `quick-monitor-*.jar` into the server's `mods/` folder;
3. Start the server. Do **not** install this mod on clients — it is server-side only.

## Commands & permissions

| Command | Effect | Permission |
|---|---|---|
| `/monitor <player>` | Start monitoring | GAMEMASTERS (OP level 2+) |
| `/monitor stop` | Open the exit dialog | GAMEMASTERS |
| `/monitor stop restore` | Exit and restore mode + position | GAMEMASTERS |
| `/monitor stop spectate` | Exit and stay in spectator | GAMEMASTERS |

Only operators can use these commands; regular players cannot trigger monitoring by any means.

## Safety & privacy

- **Read-only**: target inventories and containers are copied into a mirror menu; clicks on the mirror are cancelled.
- **Admin inventory is temporary**: the admin's real inventory is saved to `config/quick-monitor/watch-inventory.json` when monitoring starts and restored on exit, disconnect, or server shutdown.
- **No network access**: the mod makes no outbound connections and sends no telemetry.
- **Audit trail**: every start/exit is appended to `config/quick-monitor/audit-YYYY-MM-DD.jsonl` (admin, target, reason, exit mode).
- **Disconnect-safe**: if the admin disconnects mid-monitor, the game mode is restored before the player data is saved.

## Building from source

Requires JDK 25 and an existing Fabric 26.2 server installation (used as the compile classpath).

```powershell
# from the repository root
.\build.ps1 -ServerDir 'C:\path\to\fabric-server' -Jdk 'C:\path\to\jdk-25'
```

If the machine blocks script execution, run it through a bypass:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -ServerDir 'C:\path\to\fabric-server'
```

The jar is written to `build/`.

## License

MIT — see [LICENSE](LICENSE).

---

# 中文说明

Fabric **服务端**管理员监管 mod：旁观镜头 + 只读容器镜像 + 物品栏快照。**客户端不用装**。

- `/monitor <玩家>` 开始监管，`Shift` 或 `/monitor stop` 退出时会弹出选择框：还原模式与位置，或留在旁观继续看；
- 目标容器是只读镜像，点不进去；管理员自己的背包只是临时替换成目标的副本，退出/断线/停服自动还原；
- 需要 OP（GAMEMASTERS）权限，普通玩家用不了；
- 零外联、零遥测，审计日志落在 `config/quick-monitor/`；
- 仅支持 Minecraft 26.2 + Fabric Loader 0.19.5+ + Java 25。

构建：`.\build.ps1 -ServerDir <Fabric服务端目录> -Jdk <JDK25目录>`，产物在 `build/`。
