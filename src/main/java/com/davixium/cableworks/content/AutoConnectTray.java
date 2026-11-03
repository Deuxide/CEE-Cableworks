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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Alt-click with a wire spool on a straight cable tray. The clicked node is the end, the spool's selected node is the start.
 * <p>
 * Every straight tray between them along the line gets one cable: the cable runs from the start node, through the same
 * node lane on each tray, to the end node. It stops at the first block that is not a straight tray along the line, at a
 * gap, or when the spool runs out. Each cable uses one spool unit.
 */
public final class AutoConnectTray {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AutoConnectTray() {
    }

    public static void connect(ServerPlayer player, ServerLevel level, ItemStack spool, InWorldNode end) {
        InWorldNode start = spool.get(CEEDataComponents.SELECTED_NODE);
        if (start == null)
            return;

        // The alt-click uses up the selection. If nothing gets connected, it is put back below.
        spool.remove(CEEDataComponents.SELECTED_NODE);
        int[] made = {0};
        try {
            connectChain(player, level, spool, start, end, made);
        } finally {
            if (made[0] == 0)
                spool.set(CEEDataComponents.SELECTED_NODE, start);
            LOGGER.info("[AutoConnect] server: made={} selectedAfter={} count={}", made[0], spool.has(CEEDataComponents.SELECTED_NODE), spool.getCount());
            // Send the whole inventory so the client gets the selection state exactly as the server has it.
            player.containerMenu.sendAllDataToRemote();
        }
    }

    private static void connectChain(ServerPlayer player, ServerLevel level, ItemStack spool, InWorldNode start, InWorldNode end, int[] made) {
        BlockPos startPos = start.sourcePos();
        BlockPos endPos = end.sourcePos();
        BlockState startState = level.getBlockState(startPos);
        BlockState endState = level.getBlockState(endPos);
        if (!(startState.getBlock() instanceof CableTrayBlock startTray) || !(endState.getBlock() instanceof CableTrayBlock))
            { deny(level, endPos); return; }

        int dx = endPos.getX() - startPos.getX();
        int dz = endPos.getZ() - startPos.getZ();
        boolean sameTray = dx == 0 && dz == 0;
        boolean onLine = endPos.getY() == startPos.getY() && (dx == 0) != (dz == 0);
        CableTrayBlock.Slot slot = CableTrayBlock.slotOfNode(start.id());
        Direction.Axis axis = dx != 0 ? Direction.Axis.X : Direction.Axis.Z;

        List<InWorldNode> chain = new ArrayList<>();
        chain.add(start);

        if (sameTray) {
            chain.add(end);
        } else {
            if (!onLine || CableTrayBlock.slotOfNode(end.id()) != slot || !CableTrayBlock.isStraightAlong(startState, slot, axis))
                { deny(level, endPos); return; }

            int along = axis == Direction.Axis.X ? dx : dz;
            Direction step = Direction.fromAxisAndDirection(axis, along > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            int steps = Math.abs(along);
            Vec3 startWorld = start.getPosition(level);
            if (startWorld == null)
                return;
            Vec3 stepVec = new Vec3(step.getStepX(), 0, step.getStepZ());

            BlockPos cursor = startPos;
            for (int i = 1; i <= steps; i++) {
                BlockPos next = cursor.relative(step);
                BlockState nextState = level.getBlockState(next);
                if (!(nextState.getBlock() instanceof CableTrayBlock nextTray) || !CableTrayBlock.isStraightAlong(nextState, slot, axis))
                    break;

                // The last section lands on the clicked node. The ones before it are the node on this tray closest to the
                // start node's lane, so the cable stays on one lane even when a tray faces the other way.
                if (i == steps) {
                    chain.add(end);
                } else {
                    InWorldNode lane = nearestNodeOnLine(level, nextTray, slot, next, nextState, startWorld.add(stepVec.scale(i)));
                    if (lane == null)
                        break;
                    chain.add(lane);
                }
                cursor = next;
            }
        }

        InfrastructureSavedData sd = InfrastructureSavedData.load(level);
        WireType wire = wireTypeOf(spool);
        if (wire == null)
            { deny(level, endPos); return; }

        // Make sure every tray on the route has its nodes registered before connecting.
        startTray.ensureNodesExist(level, startPos, startState);
        for (int i = 1; i < chain.size(); i++) {
            BlockPos pos = chain.get(i).sourcePos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CableTrayBlock tray)
                tray.ensureNodesExist(level, pos, state);
        }

        int total = chain.size() - 1;
        for (int i = 1; i < chain.size(); i++) {
            if (!player.isCreative() && spool.isEmpty())
                break;

            InWorldNode a = chain.get(i - 1);
            InWorldNode b = chain.get(i);
            if (a.equals(b) || sd.isConnected(a, b) || !sd.getNodes().contains(a) || !sd.getNodes().contains(b))
                break;

            double distance = Math.sqrt(a.sableSourcePos(level).distSqr(b.sableSourcePos(level)));
            if (distance > wire.getMaxLength())
                break;

            sd.connect(a, b, wire);
            made[0]++;

            if (!player.isCreative()) {
                spool.shrink(1);
                if (!CEEConfigs.server().alternateWirePlacement.get())
                    player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
            }
        }

        if (made[0] == 0)
            { deny(level, endPos); return; }

        CEEAdvancements.CONNECT_WIRES.awardTo(player);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, endPos);

        if (made[0] < total)
            player.displayClientMessage(Component.translatable("message.cableworks.auto_connect", made[0], total), true);
    }

    // The node of this tray in the slot closest to the target point in the world.
    private static InWorldNode nearestNodeOnLine(ServerLevel level, CableTrayBlock tray, CableTrayBlock.Slot slot, BlockPos pos, BlockState state, Vec3 target) {
        InWorldNode best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int id : tray.getNodePositions(level, pos, state).keySet()) {
            if (CableTrayBlock.slotOfNode(id) != slot)
                continue;
            InWorldNode node = new InWorldNode(id, pos);
            Vec3 at = node.getPosition(level);
            if (at == null)
                continue;
            double distance = at.distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = node;
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

    private static void deny(ServerLevel level, BlockPos pos) {
        AllSoundEvents.DENY.playOnServer(level, pos);
    }
}
