# SmpCore v2

Everything you need to run an SMP in one Paper plugin for **Minecraft 1.21.11**, configured from
**native in-game dialog menus**. `/settings` opens a real Minecraft dialog (the menu system added in
1.21.6) with toggles, sliders, dropdowns and text boxes instead of a chest full of items.

## Highlights

- **`/settings`**: the full control panel, built with native dialogs. Every option changes live with no
  restart. Includes search. Normal players can open it read-only to see what's allowed.
- **Plugin Installer** (`/smpcore plugins`): search, browse and install **any** server plugin on
  Modrinth. It resolves required dependencies, verifies SHA-512 checksums, tracks installed plugins,
  checks for updates and can uninstall.
- **Every command can be switched off** so another plugin can take its place. For example, turn off
  `/rtp` and RTP+ handles `/rtp` instead.
- **Anti-cheat**, **alt detector**, **mod detector** and **command ban**.
- **Inspector**: CoreProtect-style block and container logging with lookup and rollback.

## Features

| Area | Features |
| --- | --- |
| Protection | Naked protection, AFK protection, void protection, immortal death drops, totem keeps effects, anti stasis, death ban |
| Combat | Global PvP, combat tag (boss bar, kill on logout, no elytra/pearls/riptide/ender chest/commands), damage modifier, attribute-swap fix, ban breach swapping, mace stuns shield, shield 5-tick delay fix, axe stun toggle, invisibility death-message QoL, custom kill sound, lightning on death |
| Cooldowns | Ender pearl, wind charge, golden/enchanted apple, chorus fruit, riptide, mace smash, elytra firework, totem, shield, spear |
| Damage limiters | Mace, end crystal, respawn anchor, bed, TNT, TNT minecart, arrows, trident, spear, any PvP hit |
| Bans & limits | Ban mace / one-time mace craft, crystal/anchor/bed/TNT/TNT-minecart bans, lava near players, tipped arrows, nether/end bans, netherite pieces, item ban, enchant ban/caps, potion-effect ban, server-wide craft limits, per-player carry limits, mob disabler, block placing/breaking switches, elytra ban, inventory purge |
| World | One-player sleep, locator bar, fire spread, no falling blocks, render/simulation distance, Paper anti-xray modes 1/2/3, anti seed cracking, tree feller, veinminer, sit on stairs/slabs, apple drop boost, block decay, armor-stand arms, happy-ghast speed, glowing dropped items, instant brewing, book size limit, tick rate |
| Villagers & vaults | Infinite restock, trade cycling, disable trades (e.g. mending), disable vaults, reopenable vaults, heavy-core drop control |
| Items | Stackable potions, easy recipes, vanilla recipe remover, unbreakable anvils, "too expensive" fix, rituals (public countdown when crafting a mace, etc.) |
| Chat | No chat reports, chat formats, word filter, spam limiter, mutes, anti-minimap |
| Security | Maintenance mode (+ MOTD), anti-VPN, anti-alt (max accounts per IP), mod detector, command ban |
| Anti-cheat | Fly, speed, reach, killaura (angle and multi-aura), autoclicker, fastplace/scaffold, nuker. Alert, setback or kick modes. Alt detector with ban-evasion blocking |
| Inspector | Logs breaks, places, buckets, explosions, fire and container transactions to SQLite. Inspect mode, lookups and rollback/restore |

## Commands

| Command | What it does |
| --- | --- |
| `/settings [search]` | Open the settings menu |
| `/smpcore [plugins\|reload\|version]` | Admin hub and plugin installer |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` | Teleport requests |
| `/rtp`, `/back`, `/spawn`, `/setspawn`, `/worldtp` | Teleports (with a warmup that cancels if you move) |
| `/home`, `/sethome`, `/delhome`, `/homes` | Homes, with a dialog menu |
| `/msg`, `/reply`, `/discord`, `/store`, `/sit`, `/recipes`, `/ritual` | Player utilities |
| `/vanish`, `/invsee`, `/endersee` | Staff tools. invsee/endersee also work on **offline** players |
| `/freeze`, `/heal`, `/feed`, `/fly`, `/broadcast` | Staff tools |
| `/ban <player> [7d\|perm] [reason]`, `/unban`, `/mute`, `/unmute` | Better bans and mutes with durations |
| `/banitem`, `/itemlimit`, `/bancommand`, `/unbreakable`, `/enchant` | Item and command rules |
| `/rollback <player>` | Browse a player's recent deaths and restore inventories |
| `/inspect`, `/inspector lookup\|rollback\|restore u: t: r: a: b:` | Block logging (CoreProtect style) |
| `/anticheat alerts\|vl\|reset`, `/alts <player>`, `/modcheck <player>` | Anti-cheat tools |
| `/kit save\|load\|give\|view\|delete\|list` | Kits (also a first-join kit) |
| `/start [countdown] [grace minutes] [kit]` | Start the SMP with a countdown and PvP grace period |
| `/npc ...` | NPCs with real skins (vanilla Mannequin entity) and click actions |
| `/arenarepair pos1\|pos2\|create\|repair\|auto` | Save and auto-repair arenas, no FAWE needed |
| `/nick`, `/skin`, `/setticksrate` | Cosmetics and tick rate |

## Permissions

- `smpcore.admin`: everything (default: op)
- `smpcore.settings.edit`: change settings
- `smpcore.staff`: staff commands and alerts
- `smpcore.bypass`: bypass restrictions (bans, limits, combat rules, anti-cheat)

## Dependencies

None are required. Paper 1.21.11 provides the dialog API, the Mannequin entity, data components and
SQLite. SmpCore does not need PacketEvents, FAWE or WorldGuard.

## Building

```bash
mvn package
```

The jar is written to `target/SmpCore-<version>.jar`. GitHub Actions builds every push, runs the unit
tests, and boots a real Paper 1.21.11 server with the plugin as a smoke test. You can download the jar
from the workflow run's artifacts.

## Notes

- The anti-cheat uses heuristics and is tuned to avoid false positives. For competitive servers,
  pair it with a dedicated anti-cheat such as GrimAC (installable from the plugin installer).
- Anti-xray and anti-seed-cracking write Paper's `config/paper-world-defaults.yml` and need a restart.
- "No chat reports" sends chat as system messages, so chat plugins that listen at `MONITOR` with
  `ignoreCancelled` will not see messages. Turn it off if you use one.
