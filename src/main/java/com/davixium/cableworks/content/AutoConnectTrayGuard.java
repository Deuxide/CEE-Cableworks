package com.davixium.cableworks.content;

import com.davixium.cableworks.CableWorks;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The normal click that belongs to an alt-click can still reach the server right after the auto-connect packet. The
 * spool would then see no selection and select the clicked end node as a new start. This drops that click.
 */
@EventBusSubscriber(modid = CableWorks.MODID)
public final class AutoConnectTrayGuard {
    private static final Map<UUID, Long> LAST_AUTO_CONNECT = new ConcurrentHashMap<>();

    private AutoConnectTrayGuard() {
    }

    public static void mark(ServerPlayer player) {
        LAST_AUTO_CONNECT.put(player.getUUID(), player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItemStack().getItem() instanceof WireSpoolItem))
            return;

        Long last = LAST_AUTO_CONNECT.get(player.getUUID());
        if (last != null && player.level().getGameTime() - last <= 2) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
