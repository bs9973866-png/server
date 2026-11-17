# RP addons for Paper 26.2

NewYorkRP uses the standalone **NPCS** plugin for human-style mannequin NPCs and **Mercedex Car Plugin** for vehicles.

## Automatic NPC setup

The installer downloads NPCS (Modrinth project `nWsAIWES`). NewYorkRP talks to its public API and creates six named, player-style characters around the world spawn on server startup:

- Mayor — opens the city services menu
- Taxi dispatcher — opens the taxi job menu
- Hospital receptionist — opens the medic job menu
- Mechanic — opens the mechanic job menu
- Employment office — opens the full jobs menu
- Delivery manager — opens the courier job menu

Each character receives a Minecraft skin, is protected from damage and pushing, and looks toward nearby players. Existing NPCs are reused and moved back to their configured spawn positions, so restarting the server does not create duplicates. Right-click opens the matching NewYorkRP menu.

**No manual NPC commands, editor, or hand-written NPC configuration are required.** NPC positions are generated relative to the current world's spawn point. If you later import the city map, make sure its intended spawn is set as the world spawn before the first server start with these plugins.

## Install

1. Stop the server.
2. Run `INSTALL-RP-ADDONS.bat` from the extracted repository folder. It downloads NPCS and the vehicle addon into `plugins/`.
3. Download the latest successful `NewYorkRP-Paper-26.2` artifact from GitHub Actions and place `NewYorkRP.jar` in `plugins/`.
4. Start the server. NewYorkRP creates the NPCs automatically after the plugins load.

## Vehicles

The Mercedex plugin is installed separately by the same installer. It supplies its own vehicle models and resource pack. Accept the resource pack prompt when joining so the models display correctly.

## Important

- Back up the world and `plugins/` before changing plugin versions.
- Do not keep old duplicate JAR versions in `plugins/`.
- If a stable Paper 26.2 release is unavailable from Modrinth, the installer stops rather than downloading a mismatched build.
- The large city map is a separate world archive and must be imported using `INSTALL-CITY-WORLD.md`.
