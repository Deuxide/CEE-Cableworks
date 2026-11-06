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
 * The route follows the trays' openings through straight and bend trays, turning at bends. Tees stop it. Every tray on the
 * route gets one cable section: the cable runs from the start node, through the node closest to it on each tray, to the end
 * node. It stops at a gap, when the spool runs out, or when a cable can't be placed. Each cable uses one spool unit.
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
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof CableTrayBlock tray))
                    return "no_route";
                // The direction the cable travels into this tray.
                Direction entry = Direction.fromDelta(pos.getX() - before.getX(), 0, pos.getZ() - before.getZ());
                if (entry == null)
                    return "no_route";
                InWorldNode node = continueLane(level, tray, slot, pos, state, entry, previous);
                if (node == null)
                    return "no_node";
                chain.add(node);
                previous = node.getPosition(level);
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

    // The shortest route of straight and bend trays from one tray to another, following their openings. Tees stop it.
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
    // west, turned by the facing. Tees and empty blocks open to nothing here.
    private static List<Direction> openings(BlockState state, CableTrayBlock.Slot slot) {
        if (!(state.getBlock() instanceof CableTrayBlock))
            return List.of();
        Direction facing = CableTrayBlock.facingOf(state, slot);
        return switch (CableTrayBlock.layerOf(state, slot)) {
            case STRAIGHT, HEAVY_STRAIGHT -> List.of(facing, facing.getOpposite());
            case BEND -> List.of(turn(Direction.NORTH, facing), turn(Direction.WEST, facing));
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

    // The wire type whose spool is the held item, or null when it is not a known spool.
    private static WireType wireTypeOf(ItemStack spool) {
        for (WireType type : CEERegistries.WIRE_TYPE)
            if (type.getSpooledItem() == spool.getItem())
                return type;
        return null;
    }

}
