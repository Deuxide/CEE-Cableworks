package com.davixium.cableworks.content;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision and outline shapes for the cable tray models: one box per model that fits inside it.
 */
public final class TrayShapes {
    // Boxes are {x1, y1, z1, x2, y2, z2} in 1/16-block units, for facing NORTH (blockstate y=0).
    public static final double[][] STRAIGHT = {
        {2, 0, 0, 14, 1, 16},
    };

    public static final double[][] BEND = {
        {0, 0, 0, 14, 1, 14},
    };

    // Rotated 180 degrees from the model's own footprint.
    public static final double[][] TEE = {
        {0, 0, 0, 14, 1, 16},
    };

    /** Builds one VoxelShape per horizontal facing, indexed like Direction#get2DDataValue (N, E, S, W). */
    public static VoxelShape[] build(double[][] boxes) {
        return build(boxes, 0);
    }

    /** Builds one VoxelShape per facing, with every box raised by yOffset (in 1/16-block units). */
    public static VoxelShape[] build(double[][] boxes, double yOffset) {
        VoxelShape[] shapes = new VoxelShape[4];
        for (int facing = 0; facing < 4; facing++) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxes) {
                double[] r = box;
                for (int i = 0; i < facing; i++)
                    r = rotateQuarter(r);
                shape = Shapes.or(shape, Shapes.box(r[0] / 16, (r[1] + yOffset) / 16, r[2] / 16, r[3] / 16, (r[4] + yOffset) / 16, r[5] / 16));
            }
            shapes[facing] = shape.optimize();
        }
        return shapes;
    }

    // One clockwise quarter turn seen from above, matching the blockstate y=90 rotation.
    private static double[] rotateQuarter(double[] b) {
        return new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
    }
}
