# 发布清单（Modrinth / CurseForge）

产物：`build/quick-monitor-0.1.0+26.2.jar`
图标：`src/main/resources/assets/quick-monitor/icon.png`（400×400）

## 一、Modrinth

| 字段 | 值 |
|---|---|
| Project type | **Mod** |
| Name | Quick Monitor |
| Slug | `quick-monitor` |
| Summary | Admin spectate + read-only inventory inspection for Fabric servers. Server-side only, vanilla clients work. |
| Environment | **Server**（Client 不勾） |
| Loaders | **Fabric** |
| Game versions | **26.2** |
| License | **MIT** |
| Source / Issues | GitHub 仓库地址 |
| Categories | `management`、`utility` |

上传：Version 0.1.0+26.2，Channel **Release**，文件 `quick-monitor-0.1.0+26.2.jar`，Changelog 用 `CHANGELOG.md` 的 0.1.0 段。

正文：`docs/publish/modrinth.md`（去掉首行标题即可）。

## 二、CurseForge

| 字段 | 值 |
|---|---|
| Game | Minecraft |
| Project class | **Mods** |
| Name | Quick Monitor |
| Summary | Admin spectate and read-only inventory inspection. Server-side only. |
| License | **MIT** |
| Categories | Server Utility / Admin Tools（最多 4 个） |
| Environment | **Server** |
| Game version | **26.2** |
| Mod loader | **Fabric** |
| Java version | **Java 25** |
| Release type | **Release** |

正文：`docs/publish/curseforge.md` 的「Quick Monitor」起。

> CurseForge 要求标题和描述为英文；中文说明可以放在正文末尾的 `---` 之后，但不是必需。

## 三、两个平台共同注意

- 文件只传 release jar，不传 `-sources`、不传 `build/` 目录；
- `fabric.mod.json` 里 `contact.sources` / `contact.issues` 必须是真实可访问的地址；
- 描述里明确写 **server-side only**，否则会有玩家装到客户端然后报 bug；
- 首次发布后，把项目链接补回 README。

## 四、截图（待补，放 `docs/screenshots/`）

1. 旁观镜头下的监管画面（能看到目标视角 + actionbar 血量/护甲/手持）；
2. 5 行背包镜像窗口（快捷栏独占最后一行）；
3. 退出选择框（还原模式与位置 / 留在旁观）。

建议 1920×1080，裁剪掉窗口边框。
