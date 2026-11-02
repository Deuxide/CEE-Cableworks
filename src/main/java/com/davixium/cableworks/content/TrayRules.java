package com.davixium.cableworks.content;

import java.util.HashMap;
import java.util.Map;

/**
 * Placement preview table for the cable trays. Change the numbers here to change where a new tray goes
 * and how it is turned. Connections are not checked; the table is used as written.
 *
 * - Cursor zone: where the crosshair is on the placed tray, relative to that tray's own north (its facing).
 *   N is the front end, S the back end, E the right side, W the left side.
 * - Player side: the way the player faces, relative to the placed tray. Each row has four numbers in the order
 *   {north, east, south, west}: 0 = the placed tray's north, 1 = its east, 2 = its south, 3 = its west.
 * - Rotation: how the new tray is turned, relative to the placed tray. 0 = same way as the placed tray,
 *   1 = a quarter turn clockwise, 2 = a half turn, 3 = a quarter turn counterclockwise.
 * - Spot: which side of the placed tray the new tray goes on, for each player side, using the same
 *   numbering as the rotation: 0 = front, 1 = right, 2 = back, 3 = left.
 *
 * A placed kind, held kind and cursor zone with no entry gives no preview.
 */
public final class TrayRules {
    public enum Kind { STRAIGHT, BEND, TEE }

    // Cursor zones, clockwise from the placed tray's north (its facing).
    public enum Zone { N, NE, E, SE, S, SW, W, NW }

    // Key: placed, held and zone. Value: the rotation for each player side, {north, east, south, west}.
    private static final Map<String, int[]> PREVIEW = new HashMap<>();

    // Key: placed and zone. Value: the spot for each player side, {north, east, south, west}.
    private static final Map<String, int[]> SPOT = new HashMap<>();

    static {
        // Placed straight: the front end for north zones, the back end for south zones.
        spotAll(Kind.STRAIGHT, Zone.N, 0);
        spotAll(Kind.STRAIGHT, Zone.NE, 0);
        spotAll(Kind.STRAIGHT, Zone.NW, 0);
        spotAll(Kind.STRAIGHT, Zone.S, 2);
        spotAll(Kind.STRAIGHT, Zone.SE, 2);
        spotAll(Kind.STRAIGHT, Zone.SW, 2);

        // Placed straight, held straight.
        set(Kind.STRAIGHT, Kind.STRAIGHT, Zone.N, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.STRAIGHT, Zone.NE, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.STRAIGHT, Zone.NW, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.STRAIGHT, Zone.S, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.STRAIGHT, Zone.SE, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.STRAIGHT, Zone.SW, 0, 0, 0, 0);

        // Placed straight, held bend.
        set(Kind.STRAIGHT, Kind.BEND, Zone.NE, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.BEND, Zone.NW, 1, 1, 1, 1);
        set(Kind.STRAIGHT, Kind.BEND, Zone.SE, 3, 3, 3, 3);
        set(Kind.STRAIGHT, Kind.BEND, Zone.SW, 2, 2, 2, 2);

        // Placed straight, held tee.
        set(Kind.STRAIGHT, Kind.TEE, Zone.N, 1, 1, 1, 1);
        set(Kind.STRAIGHT, Kind.TEE, Zone.NE, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.TEE, Zone.NW, 2, 2, 2, 2);
        set(Kind.STRAIGHT, Kind.TEE, Zone.S, 3, 3, 3, 3);
        set(Kind.STRAIGHT, Kind.TEE, Zone.SE, 0, 0, 0, 0);
        set(Kind.STRAIGHT, Kind.TEE, Zone.SW, 2, 2, 2, 2);

        // Placed bend: the right side for NE and E. SE goes on the right side for E/W players and the back end
        // for S/N players. S and SW go on the back end.
        spotAll(Kind.BEND, Zone.NE, 1);
        spotAll(Kind.BEND, Zone.E, 1);
        spot(Kind.BEND, Zone.SE, 2, 1, 2, 1);
        spotAll(Kind.BEND, Zone.S, 2);
        spotAll(Kind.BEND, Zone.SW, 2);

        // Placed bend, held straight.
        set(Kind.BEND, Kind.STRAIGHT, Zone.E, 1, 1, 1, 1);
        set(Kind.BEND, Kind.STRAIGHT, Zone.S, 0, 0, 0, 0);

        // Placed bend, held bend.
        set(Kind.BEND, Kind.BEND, Zone.NE, 2, 2, 2, 2);
        set(Kind.BEND, Kind.BEND, Zone.SE, 3, 1, 3, 1);
        set(Kind.BEND, Kind.BEND, Zone.SW, 2, 2, 2, 2);

        // Placed bend, held tee.
        set(Kind.BEND, Kind.TEE, Zone.NE, 3, 3, 3, 3);
        set(Kind.BEND, Kind.TEE, Zone.E, 2, 2, 2, 2);
        set(Kind.BEND, Kind.TEE, Zone.SE, 0, 1, 0, 1);
        set(Kind.BEND, Kind.TEE, Zone.S, 3, 3, 3, 3);
        set(Kind.BEND, Kind.TEE, Zone.SW, 2, 2, 2, 2);

        // Placed tee: no entries yet, so no preview on a tee.
    }

    private static void set(Kind placed, Kind held, Zone zone, int north, int east, int south, int west) {
        PREVIEW.put(placed + "/" + held + "/" + zone, new int[] {north, east, south, west});
    }

    private static void spot(Kind placed, Zone zone, int north, int east, int south, int west) {
        SPOT.put(placed + "/" + zone, new int[] {north, east, south, west});
    }

    // The same spot for every player side.
    private static void spotAll(Kind placed, Zone zone, int side) {
        spot(placed, zone, side, side, side, side);
    }

    /** The rotations for each player side ({north, east, south, west}), or null for no preview. */
    public static int[] previewFor(Kind placed, Kind held, Zone zone) {
        return PREVIEW.get(placed + "/" + held + "/" + zone);
    }

    /** The spot for each player side ({north, east, south, west}, 0 front, 1 right, 2 back, 3 left), or null for none. */
    public static int[] spotFor(Kind placed, Zone zone) {
        return SPOT.get(placed + "/" + zone);
    }

    private TrayRules() {
    }
}
