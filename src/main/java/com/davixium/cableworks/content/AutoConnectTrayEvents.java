package com.davixium.cableworks.content;

import com.davixium.cableworks.CableWorks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client side: holding Alt while clicking a cable tray with a wire spool that has a selected node sends the auto-connect
 * packet instead of the normal click.
 */
@EventBusSubscriber(modid = CableWorks.MODID, value = Dist.CLIENT)
public final class AutoConnectTrayEvents {

    private AutoConnectTrayEvents() {
    }

    // Shows a hint while the held spool has a selected node on a cable tray.
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui == null)
            return;

        ItemStack spool = mc.player.getMainHandItem();
        if (!(spool.getItem() instanceof WireSpoolItem))
            return;

        InWorldNode selected = spool.get(CEEDataComponents.SELECTED_NODE);
        if (selected == null || !(mc.level.getBlockState(selected.sourcePos()).getBlock() instanceof CableTrayBlock))
            return;

        mc.gui.setOverlayMessage(Component.translatable("message.cableworks.auto_connect_hint"), false);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !Screen.hasAltDown())
            return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CableTrayBlock))
            return;

        ItemStack spool = event.getItemStack();
        if (!(spool.getItem() instanceof WireSpoolItem) || !spool.has(CEEDataComponents.SELECTED_NODE))
            return;

        // The selection is used up by this click, so drop it here too. The server pushes it back if nothing connects.
        spool.remove(CEEDataComponents.SELECTED_NODE);
        CatnipServices.NETWORK.sendToServer(new AutoConnectTrayPacket(
                event.getPos(),
                event.getHitVec().getLocation(),
                event.getHand() == InteractionHand.OFF_HAND));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
