package com.davixium.cableworks.content;

import com.davixium.cableworks.CableWorksPackets;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.simibubi.create.AllSoundEvents;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sent when the player alt-clicks a cable tray while holding a wire spool with a selected node. The server can't see the
 * Alt key, so the client sends this instead of the normal click.
 */
public record AutoConnectTrayPacket(BlockPos pos, Vec3 clickLocation, boolean offHand) implements ServerboundPacketPayload {
    // Vec3 has no stream codec here, so the click position goes as three doubles.
    public static final StreamCodec<ByteBuf, AutoConnectTrayPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AutoConnectTrayPacket::pos,
            ByteBufCodecs.DOUBLE, AutoConnectTrayPacket::clickX,
            ByteBufCodecs.DOUBLE, AutoConnectTrayPacket::clickY,
            ByteBufCodecs.DOUBLE, AutoConnectTrayPacket::clickZ,
            ByteBufCodecs.BOOL, AutoConnectTrayPacket::offHand,
            AutoConnectTrayPacket::fromParts
    );

    private static double clickX(AutoConnectTrayPacket p) {
        return p.clickLocation().x;
    }

    private static double clickY(AutoConnectTrayPacket p) {
        return p.clickLocation().y;
    }

    private static double clickZ(AutoConnectTrayPacket p) {
        return p.clickLocation().z;
    }

    private static AutoConnectTrayPacket fromParts(BlockPos pos, double x, double y, double z, boolean offHand) {
        return new AutoConnectTrayPacket(pos, new Vec3(x, y, z), offHand);
    }

    @Override
    public void handle(ServerPlayer player) {
        ItemStack spool = offHand ? player.getOffhandItem() : player.getMainHandItem();
        if (!(spool.getItem() instanceof WireSpoolItem) || !spool.has(CEEDataComponents.SELECTED_NODE))
            return;
        if (!(player.level() instanceof ServerLevel level))
            return;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CableTrayBlock))
            return;

        InWorldNode end = InWorldNode.closestNode(level, clickLocation, 1.5f, pos);
        if (end == null)
            end = InWorldNode.closestNode(level, pos, state, 1.5f, clickLocation);
        if (end == null) {
            AllSoundEvents.DENY.playOnServer(level, pos);
            return;
        }

        AutoConnectTray.connect(player, level, spool, end);
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return CableWorksPackets.AUTO_CONNECT_TRAY;
    }
}
