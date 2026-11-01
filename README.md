# Cable Works

Cable Works is a support addon for **Create: Electro Energetics** (CEE), which is itself an addon for **Create**. It adds cable trays that carry the electrical wiring. The trays are support-only: they hold up wires and connect to CEE's connector logic, but they do not carry any electricity between each other.

The addon is aimed at building neat wire runs. Place the trays along a route, attach wires to their connector nodes, and the wires follow the tray runs.

## Features

- **Cable Tray (Straight)** and **Cable Tray (Bend)** blocks, with 3D models made in Blockbench.
- Both trays use CEE's connector device, so wires can be attached to them like any other connector.
- **Support-only**: a tray's nodes cannot be wired to each other. Wires can only join a tray to a different block.
- **Six small connector nodes** (1×1 pixels) per tray, spread along the top of the tray.
- **Rotation**: both trays face one of the four horizontal directions and rotate with a wrench.
- **Tray-to-tray placement**: holding a tray and aiming near one end of an existing tray places the new tray right beside it, in line with its facing, the same way Create's sails attach.
- **Accurate collision**: the collision box is 1 pixel tall, so you can walk over and place things on the trays without them blocking movement.
- **Light and visibility**: the trays do not block light or hide the faces of neighboring blocks.
- A dedicated **Cable Works** creative tab.

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 or newer (1.21.1 line) |
| Create | 6.0.7 up to (not including) 6.1.0 |
| Create: Electro Energetics | 1.x |

Cable Works is loaded after Create: Electro Energetics.

## Using the trays

1. Craft or find the trays. They are in the **Cable Works** creative tab.
2. Place a tray. It faces the direction you were looking when you placed it.
3. Rotate a tray with a wrench to turn it.
4. To extend a run, hold a tray and aim near the end of an existing tray. The new tray is placed beside it in line with the run. Hold Shift to place a tray without this help.
5. Attach wires to the small connector nodes on the trays using CEE's wire spools, the same way you would connect any other CEE connector.

## Planned features

Cable Works is meant to grow into a full cable-routing set. Planned additions:

- **Ducts** for routing cables through enclosed runs.
- **Conduits** for protected wire routing.
- **More tray types**, such as corner, tee, cross, and vertical trays.

These are not implemented yet.

## Project layout

```
src/main/java/com/davixium/cableworks/
  CableWorks.java                 Mod entry point; registers blocks, items and the creative tab
  content/
    CableTrayBlock.java           Shared tray block: connector logic, rotation, collision, placement helper
    TrayShapes.java               Collision boxes for each tray model (1/16 block tall)
    TrayNodes.java                Connector node positions for each tray model
src/main/resources/
  META-INF/neoforge.mods.toml     Mod metadata and dependencies
  assets/cableworks/
    blockstates/                  Facing variants for each tray
    models/block/                 Blockbench models for the trays
    models/item/                  Item models (inventory view)
    textures/block/               Tray textures
    lang/en_us.json               English names
```

## Building and running from source

Cable Works compiles against the published CEE artifact, so CEE has to be in your local Maven repository first.

1. Clone the CEE source (`Create-Electro-Energetics`) and publish it to your local Maven repository:

   ```
   ./gradlew publishMavenJavaPublicationToMavenLocal
   ```

   This produces `com.george_vi.electroenergetics:Create-Electro-Energetics:1.21.1-1.2.0`, which Cable Works depends on.

2. Clone this repository and open it in IntelliJ IDEA (JDK 21).

3. Run the Gradle tasks:

   ```
   ./gradlew build        # builds the mod jar
   ./gradlew runClient    # starts Minecraft with the mod loaded
   ```

If Gradle reports missing libraries, run `./gradlew --refresh-dependencies`. Use `./gradlew clean` to reset the build.

## Credits

- **Create** by the Create team (simibubi and contributors).
- **Create: Electro Energetics** by George_VI, which provides the connector logic these trays use.
- Tray models were made in Blockbench.

## License

All Rights Reserved, unless stated otherwise. See `gradle.properties` for the mod license setting.
