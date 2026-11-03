package com.davixium.cableworks.content;

import com.davixium.cableworks.CableWorks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.RegisterGuiLayersEvent;

/**
 * Hint above the hotbar while the held spool has a selected node on a cable tray. The word "Alt" is 60% opaque, and fully
 * opaque while Alt is held.
 */
@EventBusSubscriber(modid = CableWorks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AutoConnectTrayHud {
    private static final int BLUE = 0xFF7FD4FF;
    private static final int BLUE_FAINT = 0x997FD4FF;

    private AutoConnectTrayHud() {
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CableWorks.MODID, "auto_connect_hint"), AutoConnectTrayHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui)
            return;

        ItemStack spool = mc.player.getMainHandItem();
        if (!(spool.getItem() instanceof WireSpoolItem))
            return;

        InWorldNode selected = spool.get(CEEDataComponents.SELECTED_NODE);
        if (selected == null || !(mc.level.getBlockState(selected.sourcePos()).getBlock() instanceof CableTrayBlock))
            return;

        Font font = mc.font;
        String before = "Hold ";
        String key = "Alt";
        String after = " to auto-connect";
        int total = font.width(before) + font.width(key) + font.width(after);
        int x = (graphics.guiWidth() - total) / 2;
        int y = graphics.guiHeight() - 59;

        graphics.drawString(font, before, x, y, BLUE, true);
        x += font.width(before);
        graphics.drawString(font, key, x, y, Screen.hasAltDown() ? BLUE : BLUE_FAINT, true);
        x += font.width(key);
        graphics.drawString(font, after, x, y, BLUE, true);
    }
}
