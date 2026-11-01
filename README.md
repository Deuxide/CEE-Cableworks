# Cable Works

Cable Works is a support addon for **Create: Electro Energetics** (CEE), which is itself an addon for **Create**. It adds cable trays that carry the electrical wiring. The trays are support-only: they hold up wires and connect to CEE's connector logic, but they do not carry any electricity between each other.

The addon is aimed at building neat wire runs. Place the trays along a route, attach wires to their connector nodes, and the wires follow the tray runs.

---
## Current Features

- **Cable Tray (Straight)**, **Cable Tray (Bend)** and **Cable Tray (Tee)** blocks, with 3D models made in Blockbench.
- All trays use CEE's connector device, so wires can be attached to them like any other connector.
- **Support-only**: a tray's nodes cannot be wired to each other. Wires can only join a tray to a different block.
- **Tray-to-tray placement**: holding a tray and aiming near an edge of an existing tray places the new tray right beside it, the same way Create's sails attach. Aiming at an end continues the run in a straight line. Aiming at a side places the new tray facing that side, so you can branch or turn a run.

---
## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 or newer (1.21.1 line) |
| Create | 6.0.7 up to (not including) 6.1.0 |
| Create: Electro Energetics | 1.x |

Cable Works is loaded after Create: Electro Energetics.

---
## Planned features

Cable Works is meant to grow into a full cable-routing set. Planned additions:

- **Ducts** for routing cables through enclosed runs.
- **Conduits** for protected wire routing.
- **More tray types**, such as corner, tee, cross, and vertical trays.
- supports for other mods that adds cables
- Crafting recipes for survival

These are not implemented yet.

---
## Credits

- **Create** by the Create team (simibubi and contributors).
- **Create: Electro Energetics** by George_VI, which provides the connector logic these trays use.
- **Claude** :3 (im too lazy to code alr)

---
## License

All Rights Reserved, unless stated otherwise. See `gradle.properties` for the mod license setting.
