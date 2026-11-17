# RP addons for Paper 26.2

This setup intentionally uses maintained external plugins for the two features that should not be faked with villagers or pig entities.

## Selected plugins

- **NPC Plugin** (Modrinth project `npc-plugin`): player-like NPCs, editable skins, click actions, paths, and GUI editing. Paper 26.2 is listed as supported.
- **Mercedex Car Plugin** (Modrinth project `2CrfFJyk`): two detailed four-seat cars, fuel, storage, and an automatically delivered resource pack. The project lists 26.2 compatibility.

The installer queries Modrinth's API for stable Paper 26.2 files and downloads them into `plugins/`. It does not install unrelated addons or weapon systems.

## Install

1. Stop the server.
2. Run `INSTALL-RP-ADDONS.bat` from the extracted repository folder.
3. Download the latest successful `NewYorkRP-Paper-26.2` artifact from GitHub Actions and place `NewYorkRP.jar` in `plugins/`.
4. Start the server. Check the console for enabled plugins.

## Make human NPCs and connect the city menu

1. In game, stand at the spot where the first character should stand.
2. Run `/npc create`.
3. Run `/npc edit` and right-click the NPC to open its editor.
4. Set its name and skin in the editor. The plugin accepts a player name, UUID, local PNG skin, URL, or YAML skin file.
5. In the NPC editor's Click Action section, add a **Command** action with:
   `ny menu {player}`
6. Repeat for the Mayor, Taxi Dispatcher, Hospital Receptionist, Mechanic, and other city characters. Use distinct skins and names.

The command opens the NEW YORK RP city GUI for the player who clicked the NPC. The GUI contains profession selection and wallet access.

## Cars

Use the Mercedex plugin's own `/mercedex` commands or its `/car` alias. It supplies actual car models and seats; it is separate from NewYorkRP. Accept the plugin's resource pack when joining so the models display correctly.

## Important

- Back up the world and `plugins/` before changing plugin versions.
- Do not keep old duplicate JAR versions in `plugins/`.
- If the selected 26.2 stable release is unavailable from Modrinth, the installer stops rather than downloading a mismatched build.
- The downloaded city map is a separate world archive and must be imported using `INSTALL-CITY-WORLD.md`.
