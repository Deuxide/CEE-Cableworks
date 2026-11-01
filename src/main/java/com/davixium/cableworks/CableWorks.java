package com.davixium.cableworks;

import com.davixium.cableworks.content.CableTrayBlock;
import com.davixium.cableworks.content.TrayNodes;
import com.davixium.cableworks.content.TrayShapes;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CableWorks.MODID)
public class CableWorks {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "cableworks";
    // Deferred Registers for blocks, items and creative tabs under the "cableworks" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Straight cable tray, "cableworks:cable_tray_straight", using CEE's connector logic
    public static final DeferredBlock<CableTrayBlock> CABLE_TRAY_STRAIGHT = BLOCKS.registerBlock("cable_tray_straight",
            props -> new CableTrayBlock(props, TrayShapes.STRAIGHT, TrayNodes.STRAIGHT),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion());
    public static final DeferredItem<BlockItem> CABLE_TRAY_STRAIGHT_ITEM = ITEMS.registerSimpleBlockItem("cable_tray_straight", CABLE_TRAY_STRAIGHT);

    // Bent cable tray, "cableworks:cable_tray_bend", using CEE's connector logic
    public static final DeferredBlock<CableTrayBlock> CABLE_TRAY_BEND = BLOCKS.registerBlock("cable_tray_bend",
            props -> new CableTrayBlock(props, TrayShapes.BEND, TrayNodes.BEND),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).noOcclusion());
    public static final DeferredItem<BlockItem> CABLE_TRAY_BEND_ITEM = ITEMS.registerSimpleBlockItem("cable_tray_bend", CABLE_TRAY_BEND);

    // Own creative tab holding both trays. The icon is the straight tray.
    public static final Supplier<CreativeModeTab> CABLE_WORKS_TAB = CREATIVE_TABS.register("cable_works", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 8)
            .title(Component.translatable("itemGroup.cableworks"))
            .icon(() -> CABLE_TRAY_STRAIGHT_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(CABLE_TRAY_STRAIGHT_ITEM.get());
                output.accept(CABLE_TRAY_BEND_ITEM.get());
            })
            .build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus and pass them in automatically.
    public CableWorks(IEventBus modEventBus) {
        // Register the Deferred Registers to the mod event bus so blocks, items and tabs get registered
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
