# Cable Works

Cable Works is a support addon for **Create: Electro Energetics** (CEE), which is itself an addon for **Create**. It adds cable trays that carry the electrical wiring. The trays hold up wires and connect to CEE's connector logic. Wires can join any two nodes, including two nodes on the same tray.

The addon is aimed at building neat wire runs. Place the trays along a route, attach wires to their connector nodes, and the wires follow the tray runs.

---
## Current Features

- **Auto-connect**: automatically places wires across a long chain of trays with pathfinding algs
- All trays use CEE's connector device, so wires can be attached to them like any other connector.
- **Wiring across a tray**: a wire can join two nodes on the same tray. Short circuits between nodes are allowed.
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
- Supports for other mods that adds cables
- Crafting recipes for survival

These are not implemented yet.

---
## Credits

- **Create** by the Create team (simibubi and contributors).
- **Create: Electro Energetics** by George_VI, which provides the connector logic these trays use.
- **Claude** :3 I give claude pseudocode snippet for it to work with. So claude only applies my code snippet and ideas

models, texture, and arts all come from me (claude only make my bad code works)

---
## License

This project is licensed under the Apache License 2.0.
See [LICENSE](LICENSE.txt) for the full license text.