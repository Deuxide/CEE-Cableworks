package com.davixium.cableworks.content;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.events.datagen.CEEAdvancements;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Ctrl-click with a wire spool on a cable tray. The clicked node is the end, the spool's selected node is the start.
 * <p>
 * The route follows the trays' openings through straight, bend and tee trays, turning at bends and tees. Every tray on the
 * route gets one cable section: the cable runs from the start node, through the node closest to it on each tray, to the end
 * node. A tee gets two nodes, one on the side the cable comes in and one on the side it leaves, joined by a short cable across the tee.
 * It stops at a gap, when the spool runs out, or when a cable can't be placed. Each cable uses one spool unit.
 */
public final class AutoConnectTray {
    // Longest route, in tray blocks, that auto-connect will follow.
    private static final int MAX_ROUTE_TRAYS = 128;

    private AutoConnectTray() {
    }

    public static void connect(ServerPlayer player, ServerLevel level, ItemStack spool, InWorldNode end) {
        InWorldNode start = spool.get(CEEDataComponents.SELECTED_NODE);
        if (start == null)
            return;

        AutoConnectTrayGuard.mark(player);

        // The alt-click uses up the selection. If nothing gets connected, it is put back below.
        spool.remove(CEEDataComponents.SELECTED_NODE);
        int[] made = {0};
        String failure = null;
        try {
            failure = connectChain(player, level, spool, start, end, made);
        } finally {
            if (made[0] == 0)
                spool.set(CEEDataComponents.SELECTED_NODE, start);
            // Send the whole inventory so the client gets the selection state exactly as the server has it.
            player.containerMenu.sendAllDataToRemote();
        }
        if (made[0] == 0) {
            AllSoundEvents.DENY.playOnServer(level, end.sourcePos());
            if (failure != null)
                player.displayClientMessage(Component.translatable("message.cableworks.auto_connect_fail." + failure), true);
        }
    }

    // Returns why nothing could be connected, or null when at least one cable was placed.
    private static String connectChain(ServerPlayer player, ServerLevel level, ItemStack spool, InWorldNode start, InWorldNode end, int[] made) {
        BlockPos startPos = start.sourcePos();
        BlockPos endPos = end.sourcePos();
        BlockState startState = level.getBlockState(startPos);
        BlockState endState = level.getBlockState(endPos);
        if (!(startState.getBlock() instanceof CableTrayBlock startTray) || !(endState.getBlock() instanceof CableTrayBlock))
            return "not_tray";

        boolean sameTray = startPos.equals(endPos);
        CableTrayBlock.Slot slot = CableTrayBlock.slotOfNode(start.id());
        if (!sameTray && CableTrayBlock.slotOfNode(end.id()) != slot)
            return "layer";

        List<InWorldNode> chain = new ArrayList<>();
        chain.add(start);

        if (sameTray) {
            chain.add(end);
        } else {
            List<BlockPos> route = findRoute(level, startPos, endPos, slot);
            if (route == null)
                return "no_route";

            // Each tray on the route gets the node closest to the previous node, so the cable follows the bends.
            Vec3 previous = start.getPosition(level);
            if (previous == null)
                return "no_node";
            for (int i = 1; i < route.size() - 1; i++) {
                BlockPos pos = route.get(i);
                BlockPos before = route.get(i - 1);
                BlockPos after = route.get(i + 1);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof CableTrayBlock tray))
                    return "no_route";
                // The directions the cable travels into and out of this tray.
                Direction travelIn = Direction.fromDelta(pos.getX() - before.getX(), 0, pos.getZ() - before.getZ());
                Direction travelOut = Direction.fromDelta(after.getX() - pos.getX(), 0, after.getZ() - pos.getZ());
                if (travelIn == null || travelOut == null)
                    return "no_route";

                if (CableTrayBlock.layerOf(state, slot) == CableTrayBlock.TrayLayer.TEE) {
                    // A tee has a node on the side the cable comes in and one on the side it leaves.
                    List<InWorldNode> pair = continueTee(level, tray, slot, pos, state, travelIn, travelOut, previous);
                    if (pair == null)
                        return "no_node";
                    chain.addAll(pair);
                    previous = pair.get(1).getPosition(level);
                } else {
                    InWorldNode node = continueLane(level, tray, slot, pos, state, travelIn, previous);
                    if (node == null)
                        return "no_node";
                    chain.add(node);
                    previous = node.getPosition(level);
                }
                if (previous == null)
                    return "no_node";
            }
            chain.add(end);
        }

        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        WireType wire = wireTypeOf(spool);
        if (wire == null)
            return "no_wire";
        // A dye in the offhand colours the wire, as it does for the normal click.
        if (wire instanceof WireType.Dyeable dyeable && player.getOffhandItem().getItem() instanceof DyeItem dye)
            wire = dyeable.getDyed(dye.getDyeColor());

        // Make sure every tray on the route has its nodes registered before connecting.
        startTray.ensureNodesExist(level, startPos, startState);
        for (int i = 1; i < chain.size(); i++) {
            BlockPos pos = chain.get(i).sourcePos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CableTrayBlock tray)
                tray.ensureNodesExist(level, pos, state);
        }

        String stop = null;
        for (int i = 1; i < chain.size(); i++) {
            if (!player.isCreative() && spool.isEmpty()) {
                stop = "no_wire_left";
                break;
            }

            InWorldNode a = chain.get(i - 1);
            InWorldNode b = chain.get(i);
            if (a.equals(b) || sd.isConnected(a, b)) {
                stop = "already";
                break;
            }
            if (!sd.getNodes().contains(a) || !sd.getNodes().contains(b)) {
                stop = "unregistered";
                break;
            }

            double distance = Math.sqrt(a.sableSourcePos(level).distSqr(b.sableSourcePos(level)));
            if (distance > wire.getMaxLength()) {
                stop = "far";
                break;
            }

            sd.connect(a, b, wire);
            made[0]++;

            if (!player.isCreative()) {
                spool.shrink(1);
                if (!CEEConfigs.server().alternateWirePlacement.get())
                    player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
            }
        }

        if (made[0] == 0)
            return stop != null ? stop : "unknown";

        CEEAdvancements.CONNECT_WIRES.awardTo(player);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, endPos);

        int total = chain.size() - 1;
        if (made[0] < total)
            player.displayClientMessage(Component.translatable("message.cableworks.auto_connect", made[0], total), true);
        return null;
    }

    // The shortest route of straight, bend and tee trays from one tray to another, following their openings.
    // Returns the positions from start to end, or null when there is no route.
    private static List<BlockPos> findRoute(ServerLevel level, BlockPos from, BlockPos to, CableTrayBlock.Slot slot) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        while (!queue.isEmpty() && !parent.containsKey(to)) {
            BlockPos pos = queue.poll();
            if (parent.size() > MAX_ROUTE_TRAYS)
                break;
            for (Direction side : openings(level.getBlockState(pos), slot)) {
                BlockPos next = pos.relative(side);
                if (parent.containsKey(next))
                    continue;
                if (!openings(level.getBlockState(next), slot).contains(side.getOpposite()))
                    continue;
                parent.put(next, pos);
                queue.add(next);
            }
        }
        if (!parent.containsKey(to))
            return null;

        LinkedList<BlockPos> route = new LinkedList<>();
        for (BlockPos pos = to; !pos.equals(from); pos = parent.get(pos))
            route.addFirst(pos);
        route.addFirst(from);
        return route;
    }

    // The sides a tray opens to, in the world. Straight trays open along their facing. A north-facing bend opens north and
    // west, turned by the facing. A north-facing tee opens north, south and west. Empty blocks open to nothing.
    private static List<Direction> openings(BlockState state, CableTrayBlock.Slot slot) {
        if (!(state.getBlock() instanceof CableTrayBlock))
            return List.of();
        Direction facing = CableTrayBlock.facingOf(state, slot);
        return switch (CableTrayBlock.layerOf(state, slot)) {
            case STRAIGHT, HEAVY_STRAIGHT -> List.of(facing, facing.getOpposite());
            case BEND -> List.of(turn(Direction.NORTH, facing), turn(Direction.WEST, facing));
            case TEE -> List.of(turn(Direction.NORTH, facing), turn(Direction.SOUTH, facing), turn(Direction.WEST, facing));
            default -> List.of();
        };
    }

    // Turns a direction clockwise as many quarter turns as the facing is from north.
    private static Direction turn(Direction direction, Direction facing) {
        Direction result = direction;
        for (int i = 0; i < facing.get2DDataValue(); i++)
            result = result.getClockWise();
        return result;
    }

    // The node on this tray that the cable meets. Going north or south, lanes are told apart by their X; going east or west,
    // by their Z. Bend nodes step in both X and Z, so the lane keeps its position across the corner.
    private static InWorldNode continueLane(ServerLevel level, CableTrayBlock tray, CableTrayBlock.Slot slot, BlockPos pos, BlockState state, Direction entry, Vec3 previous) {
        boolean compareX = entry.getAxis() == Direction.Axis.Z;

        InWorldNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int id : tray.getNodePositions(level, pos, state).keySet()) {
            if (CableTrayBlock.slotOfNode(id) != slot)
                continue;

            InWorldNode candidate = new InWorldNode(id, pos);
            Vec3 at = candidate.getPosition(level);
            if (at == null)
                continue;

            double distance = compareX ? Math.abs(at.x - previous.x) : Math.abs(at.z - previous.z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    // A tee node, with its position and the side of the tee it sits on.
    private record PortNode(InWorldNode node, Vec3 at, Direction side) {
    }

    // The tee's nodes on this layer, each with the side it belongs to. Nodes at the centre of the tee belong to no side.
    private static List<PortNode> portNodes(ServerLevel level, CableTrayBlock tray, CableTrayBlock.Slot slot, BlockPos pos, BlockState state) {
        List<Direction> sides = openings(state, slot);
        List<PortNode> result = new ArrayList<>();
        for (int id : tray.getNodePositions(level, pos, state).keySet()) {
            if (CableTrayBlock.slotOfNode(id) != slot)
                continue;
            InWorldNode node = new InWorldNode(id, pos);
            Vec3 at = node.getPosition(level);
            if (at == null)
                continue;
            Direction side = sideOf(at, pos, sides);
            if (side != null)
                result.add(new PortNode(node, at, side));
        }
        return result;
    }

    // The opening a node sits furthest toward, measured from the centre of the block. Null when it is not toward any opening.
    private static Direction sideOf(Vec3 at, BlockPos pos, List<Direction> sides) {
        double x = at.x - (pos.getX() + 0.5);
        double z = at.z - (pos.getZ() + 0.5);
        Direction best = null;
        double bestReach = 0;
        for (Direction side : sides) {
            double reach = x * side.getStepX() + z * side.getStepZ();
            if (reach > bestReach) {
                bestReach = reach;
                best = side;
            }
        }
        return best;
    }

    // How far a point sits to the right of a cable travelling in this direction, measured across the block.
    private static double rightOf(Vec3 at, BlockPos pos, Direction travel) {
        Direction right = travel.getClockWise();
        return (at.x - (pos.getX() + 0.5)) * right.getStepX() + (at.z - (pos.getZ() + 0.5)) * right.getStepZ();
    }

    // The two nodes a tee gives the cable: one on the side it comes in, and one on the side it leaves. The entry node is the lane
    // closest to the previous node. The exit node is the one on the leaving side that keeps the same offset to the right of the
    // travel, so the lane stays on the same side through the turn. Returns null when either side has no node.
    private static List<InWorldNode> continueTee(ServerLevel level, CableTrayBlock tray, CableTrayBlock.Slot slot, BlockPos pos, BlockState state, Direction travelIn, Direction travelOut, Vec3 previous) {
        List<PortNode> nodes = portNodes(level, tray, slot, pos, state);
        boolean compareX = travelIn.getAxis() == Direction.Axis.Z;

        PortNode entry = null;
        double entryDistance = Double.MAX_VALUE;
        for (PortNode n : nodes) {
            if (n.side() != travelIn.getOpposite())
                continue;
            double distance = compareX ? Math.abs(n.at().x - previous.x) : Math.abs(n.at().z - previous.z);
            if (distance < entryDistance) {
                entryDistance = distance;
                entry = n;
            }
        }
        if (entry == null)
            return null;

        double lane = rightOf(entry.at(), pos, travelIn);
        PortNode exit = null;
        double exitDistance = Double.MAX_VALUE;
        for (PortNode n : nodes) {
            if (n.side() != travelOut)
                continue;
            double distance = Math.abs(rightOf(n.at(), pos, travelOut) - lane);
            if (distance < exitDistance) {
                exitDistance = distance;
                exit = n;
            }
        }
        if (exit == null)
            return null;

        return List.of(entry.node(), exit.node());
    }

    // The wire type whose spool is the held item, or null when it is not a known spool.
    private static WireType wireTypeOf(ItemStack spool) {
        for (WireType type : CEERegistries.WIRE_TYPE)
            if (type.getSpooledItem() == spool.getItem())
                return type;
        return null;
    }

}
