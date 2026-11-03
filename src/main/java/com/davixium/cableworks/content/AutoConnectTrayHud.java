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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Hint above the hotbar while the held spool has a selected node on a cable tray. It fades in and out. The word "Alt" is
 * white at 60% opacity, and fully opaque while Alt is held.
 */
@EventBusSubscriber(modid = CableWorks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AutoConnectTrayHud {
    private static final int BLUE = 0xFF7FD4FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int WHITE_FAINT = 0x99FFFFFF;
    // Ticks for a full fade in or out.
    private static final float FADE_TICKS = 10f;

    private static float fade = 0f;

    private AutoConnectTrayHud() {
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(CableWorks.MODID, "auto_connect_hint"), AutoConnectTrayHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        boolean show = shouldShow(mc);

        // Move the fade toward the target, so the hint fades out instead of cutting off.
        float step = Math.min(deltaTracker.getRealtimeDeltaTicks(), 5f) / FADE_TICKS;
        fade = Math.max(0f, Math.min(1f, fade + (show ? step : -step)));
        if (fade <= 0f)
            return;

        Font font = mc.font;
        String before = "Hold ";
        String key = "Alt";
        String after = " to auto-connect";
        int total = font.width(before) + font.width(key) + font.width(after);
        int x = (graphics.guiWidth() - total) / 2;
        int y = graphics.guiHeight() - 59;

        graphics.drawString(font, before, x, y, withFade(BLUE), true);
        x += font.width(before);
        graphics.drawString(font, key, x, y, withFade(Screen.hasAltDown() ? WHITE : WHITE_FAINT), true);
        x += font.width(key);
        graphics.drawString(font, after, x, y, withFade(BLUE), true);
    }

    private static boolean shouldShow(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options.hideGui)
            return false;

        ItemStack spool = mc.player.getMainHandItem();
        if (!(spool.getItem() instanceof WireSpoolItem))
            return false;

        InWorldNode selected = spool.get(CEEDataComponents.SELECTED_NODE);
        return selected != null && mc.level.getBlockState(selected.sourcePos()).getBlock() instanceof CableTrayBlock;
    }

    // Scales the colour's alpha by the current fade.
    private static int withFade(int argb) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * fade);
        return (alpha << 24) | (argb & 0xFFFFFF);
    }
}
