package com.davixium.cableworks.content;

/**
 * Placement rules for each cable tray kind. Change these to change how trays connect and where
 * the placement helper will put them.
 *
 * Quarter turns are clockwise from the tray's facing: 0 = front, 1 = right, 2 = back, 3 = left.
 * - turns: the turns the placement helper may give a new tray of this kind (0 = in line, 1 = right, 3 = left).
 * - openings: the sides this kind connects on. A tray only attaches to another tray on a side
 *   it opens to, and the new tray must open back toward it.
 */
public final class TrayRules {
    public record Rules(int[] turns, int[] openings) {
    }

    // Straight: in line only, opens to its front and back.
    public static final Rules STRAIGHT = new Rules(new int[] {0}, new int[] {0, 2});

    // Bend: turns right or left, opens to its back and right (the two sides are 90 degrees apart).
    public static final Rules BEND = new Rules(new int[] {1, 3}, new int[] {2, 1});

    // Tee: in line or turns right or left, opens to its front, right and back (three sides).
    public static final Rules TEE = new Rules(new int[] {0, 1, 3}, new int[] {0, 1, 2});

    private TrayRules() {
    }
}
