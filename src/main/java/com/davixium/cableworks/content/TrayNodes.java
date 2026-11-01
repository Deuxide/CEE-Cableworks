package com.davixium.cableworks.content;

import net.minecraft.world.phys.Vec3;

/**
 * Connector node positions for each cable tray model, for facing NORTH (blockstate y=0).
 * Each array's index is the node id. Positions are block-local (0..1).
 * The straight and bend trays have their own lists so each can be set separately.
 */
public final class TrayNodes {
    public static final Vec3[] STRAIGHT = {
            new Vec3(13/16d, 1.5 / 16d, 0.5),
            new Vec3(11/16d, 1.5 / 16d, 0.5),
            new Vec3(9/16d, 1.5 / 16d, 0.5),
            new Vec3(7/16d, 1.5 / 16d, 0.5),
            new Vec3(5/16d, 1.5 / 16d, 0.5),
            new Vec3(3/16d, 1.5 / 16d, 0.5),
    };

    public static final Vec3[] BEND = {
            new Vec3(13/16d, 1.5 / 16d, 13/16d),
            new Vec3(11/16d, 1.5 / 16d, 11/16d),
            new Vec3(9/16d, 1.5 / 16d, 9/16d),
            new Vec3(7/16d, 1.5 / 16d, 7/16d),
            new Vec3(5/16d, 1.5 / 16d, 5/16d),
            new Vec3(3/16d, 1.5 / 16d, 3/16d),
    };

    // Placeholder positions for the tee tray, to be set by hand.
    public static final Vec3[] TEE = {
            new Vec3(13/16d, 1.5 / 16d, 0.5),
            new Vec3(11/16d, 1.5 / 16d, 0.5),
            new Vec3(9/16d, 1.5 / 16d, 0.5),
            new Vec3(7/16d, 1.5 / 16d, 0.5),
            new Vec3(5/16d, 1.5 / 16d, 0.5),
            new Vec3(3/16d, 1.5 / 16d, 0.5),
    };

    private TrayNodes() {
    }
}
