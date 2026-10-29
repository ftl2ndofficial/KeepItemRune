# KeepItemRune

KeepItemRune is an open-source Paper and Folia plugin for Minecraft **1.21 – 26.3** that allows players to protect specific items from dropping upon death by applying a customizable rune item.

This project is a complete, ground-up rewrite of **KeepItemVoucher** (originally created in 2025), rebuilt from scratch for modern Paper/Folia servers with client-side packet lore, PersistentDataContainer (PDC) tracking, and multi-version NMS support.

- **Author**: FTL2nd
- **Community & Support (Discord)**: [https://discord.knualabs.icu](https://discord.knualabs.icu)

## Features

- **Customizable Rune Item**: Configure the base material, display name, lore, and enchantment glint in `config.yml`.
- **PDC-Based Identification**: Uses `PersistentDataContainer` (`keepitemrune:rune` and `keepitemrune:protected`) to track runes and protected items reliably.
- **Client-Side Lore Injection (NMS Netty Pipeline)**: Protection lore is injected into outgoing inventory packets (`SetSlot`, `SetContent`, `SetCursorItem`, `SetPlayerInventory`, `SetEquipment`) when sent to the client. Server-side item lore is never modified, ensuring items (including items with existing custom lore) return to their exact original state after death.
- **Customizable Sounds & Visual Effects**: Configure sounds and particle/totem animations for applying runes, failing validation, dying with protected items, and restoring items on respawn.
- **Third-Party & Instant Respawn Compatibility**: Three-phase death handling (`LOW` → `HIGHEST` → `MONITOR`) and `PlayerPostRespawnEvent` support ensure full compatibility with combat-management plugins (such as PvPManager), grave plugins, and instant respawn (`doImmediateRespawn`).
- **Multi-Version NMS Adapter (`1.21` – `26.3`)**: Automatically adapts to internal NMS changes across 1.21.x and 26.x releases.
- **Dumb-Proof Auto-Updating YAML Files**: `config.yml` and `messages.yml` automatically merge newly added settings on plugin updates while recovering gracefully from syntax or type errors.
- **Paper & Folia Support**: Uses entity-scoped schedulers for Folia compatibility.

## Commands & Permissions

| Command | Alias | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/keepitemrune giverune <player> [amount]` | `/kir giverune` | `keepitemrune.giverune` | Give Keep Item Rune(s) to a player |
| `/keepitemrune additem` | `/kir additem` | `keepitemrune.additem` | Protect the item held in your main hand |
| `/keepitemrune removeitem` | `/kir removeitem` | `keepitemrune.removeitem` | Remove protection from the item in your main hand |
| `/keepitemrune reload` | `/kir reload` | `keepitemrune.reload` | Reload `config.yml` and `messages.yml` |

Grant `keepitemrune.admin` to give access to all commands above.

## Requirements & Building

- **Server Software**: Paper or Folia `1.21` – `26.3`
- **Java Runtime**: Java 25+

To build the plugin from source:

```bash
./gradlew clean build
```

The compiled JAR is generated at `build/libs/KeepItemRune-1.0.1.jar`.

## License

This project is released under a custom source-available license (see [LICENSE](LICENSE) for full details):

- **Allowed**: You are free to use, modify, and adapt this plugin for your servers, as well as study and reuse parts of the code for learning or in your own projects.
- **Prohibited**: You may **not** rename/rebrand this plugin to claim it as your own original creation, and you may **not** package or sell it (or any modified/rebranded version of it) as a commercial product. Original credit to **FTL2nd** (`https://discord.knualabs.icu`) must be retained.
