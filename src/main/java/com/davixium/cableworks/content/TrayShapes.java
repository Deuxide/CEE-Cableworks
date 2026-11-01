package com.davixium.cableworks.content;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Collision and outline shapes for the cable tray models. The boxes were measured from the
 * Blockbench elements (which are zero-thickness planes, so each one is given 1/16 of thickness).
 */
public final class TrayShapes {
    // Boxes are {x1, y1, z1, x2, y2, z2} in 1/16-block units, for facing NORTH (blockstate y=0).
    public static final double[][] STRAIGHT = {
        {1.5, 0, 0, 2.5, 1, 16},
        {1.5, 1, 0, 2.5, 2, 1},
        {1.5, 1, 7, 2.5, 2, 9},
        {1.5, 1, 15, 2.5, 2, 16},
        {1.5, 2, 0, 2.5, 3, 16},
        {2, 0, 0, 4, 0.5, 16},
        {2, 2.5, 0, 3, 3.5, 16},
        {2.5, 0, 7.5, 3.5, 1.5, 8.5},
        {4, 0, 0, 12, 0.5, 1},
        {4, 0, 7, 12, 0.5, 9},
        {4, 0, 15, 12, 0.5, 16},
        {4.5, 0, 7.5, 5.5, 1.5, 8.5},
        {6.5, 0, 7.5, 7.5, 1.5, 8.5},
        {8.5, 0, 7.5, 9.5, 1.5, 8.5},
        {10.5, 0, 7.5, 11.5, 1.5, 8.5},
        {12, 0, 0, 14, 0.5, 16},
        {12.5, 0, 7.5, 13.5, 1.5, 8.5},
        {13, 2.5, 0, 14, 3.5, 16},
        {13.5, 0, 0, 14.5, 1, 16},
        {13.5, 1, 0, 14.5, 2, 1},
        {13.5, 1, 7, 14.5, 2, 9},
        {13.5, 1, 15, 14.5, 2, 16},
        {13.5, 2, 0, 14.5, 3, 16},
    };

    public static final double[][] BEND = {
        {0, 0, 1.5, 2, 1, 2.5},
        {0, 0, 2, 2, 0.5, 4},
        {0, 0, 4, 1, 0.5, 12},
        {0, 0, 12, 12, 0.5, 14},
        {0, 0, 13.5, 14, 1, 14.5},
        {0, 1, 1.5, 1, 2, 2.5},
        {0, 1, 13.5, 1, 2, 14.5},
        {0, 2, 1.5, 2, 3, 2.5},
        {0, 2, 13.5, 14, 3, 14.5},
        {0, 2.5, 2, 2, 3.5, 3},
        {0, 2.5, 13, 13, 3.5, 14},
        {1.5, 0, 0, 2.5, 1, 2},
        {1.5, 1, 0, 2.5, 2, 1},
        {1.5, 2, 0, 2.5, 3, 2},
        {1.879, 0, 1.893, 13.192, 0.501, 13.207},
        {2, 0, 0, 4, 0.5, 4},
        {2, 2.5, 0, 3, 3.5, 3},
        {2.5, 0, 2.5, 3.5, 1.5, 3.5},
        {4, 0, 0, 12, 0.5, 1},
        {4.5, 0, 4.5, 5.5, 1.5, 5.5},
        {6, 1, 13.5, 8, 2, 14.5},
        {6.5, 0, 6.5, 7.5, 1.5, 7.5},
        {8.5, 0, 8.5, 9.5, 1.5, 9.5},
        {10.5, 0, 10.5, 11.5, 1.5, 11.5},
        {12, 0, 0, 14, 0.5, 14},
        {12.5, 0, 12.5, 13.5, 1.5, 13.5},
        {13, 1, 13.5, 14, 2, 14.5},
        {13, 2.5, 0, 14, 3.5, 14},
        {13.5, 0, 0, 14.5, 1, 14},
        {13.5, 1, 0, 14.5, 2, 1},
        {13.5, 1, 6, 14.5, 2, 8},
        {13.5, 1, 13, 14.5, 2, 14},
        {13.5, 2, 0, 14.5, 3, 14},
    };

    /** Builds one VoxelShape per horizontal facing, indexed like Direction#get2DDataValue (N, E, S, W). */
    public static VoxelShape[] build(double[][] boxes) {
        VoxelShape[] shapes = new VoxelShape[4];
        for (int facing = 0; facing < 4; facing++) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxes) {
                double[] r = box;
                for (int i = 0; i < facing; i++)
                    r = rotateQuarter(r);
                shape = Shapes.or(shape, Shapes.box(r[0] / 16, r[1] / 16, r[2] / 16, r[3] / 16, r[4] / 16, r[5] / 16));
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
