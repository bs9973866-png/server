# NEW YORK RP — Paper 26.2

A server-side roleplay plugin. Players do not install client mods.

## Build
- Install 64-bit Java 25 and Apache Maven.
- Run `BUILD-PLUGIN.bat`.
- It compiles the plugin and copies `NewYorkRP.jar` into the server's `plugins` folder.
- Restart Paper.

## First test
- `/ny` — help.
- `/job taxi` — choose a job.
- `/work` — complete a work shift (cooldown applies).
- `/wallet` — check money.
- `/pay <player> <amount>` — transfer money.
- `/nycity generate` — admin command for the starter district.

## Resource pack delivery
On first startup the plugin creates `plugins/NewYorkRP/resource-pack/new-york-rp.zip`, starts a small HTTP server on port 8765, and sends the pack to players on join. Minecraft may show its normal accept prompt.

For players outside the host PC:
1. Set `resource-pack.public-host` in `plugins/NewYorkRP/config.yml` to the server's reachable public IP or domain.
2. Forward TCP port 8765 to the server PC and allow it in the firewall.
3. Keep the game server's own port 25565 forwarded separately.

Do not publish a private address or share your public IP in public chat. The first pack is a valid starter pack; custom city models, textures and sounds are planned for later iterations.

## Current prototype scope
Includes a basic economy, jobs, starter NPC interactions, pack delivery and an experimental district command. It is not yet a complete GTA-scale city: vehicle systems, interiors, missions, phone UI, advanced NPC schedules and a polished custom resource pack remain future development.
