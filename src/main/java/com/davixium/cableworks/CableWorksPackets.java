package com.davixium.cableworks;

import com.davixium.cableworks.content.AutoConnectTrayPacket;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

public enum CableWorksPackets implements BasePacketPayload.PacketTypeProvider {
    AUTO_CONNECT_TRAY(AutoConnectTrayPacket.class, AutoConnectTrayPacket.STREAM_CODEC),
    ;

    private final CatnipPacketRegistry.PacketType<?> type;

    <T extends BasePacketPayload> CableWorksPackets(Class<T> clazz, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        String name = this.name().toLowerCase(Locale.ROOT);
        this.type = new CatnipPacketRegistry.PacketType<>(
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CableWorks.MODID, name)),
                clazz, codec
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
        return (CustomPacketPayload.Type<T>) this.type.type();
    }

    public static void register() {
        CatnipPacketRegistry packetRegistry = new CatnipPacketRegistry(CableWorks.MODID, 1);
        for (CableWorksPackets packet : CableWorksPackets.values())
            packetRegistry.registerPacket(packet.type);
        packetRegistry.registerAllPackets();
    }
}
