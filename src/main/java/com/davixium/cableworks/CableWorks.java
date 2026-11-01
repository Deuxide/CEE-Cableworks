package com.davixium.cableworks;

import com.davixium.cableworks.content.CableTrayBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CableWorks.MODID)
public class CableWorks {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "cableworks";
    // Deferred Registers for blocks and items under the "cableworks" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // Creates a new support-only cable tray with the id "cableworks:cable_tray", using CEE's connector logic
    public static final DeferredBlock<CableTrayBlock> CABLE_TRAY = BLOCKS.registerBlock("cable_tray", CableTrayBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f));
    // Creates a new BlockItem with the id "cableworks:cable_tray"
    public static final DeferredItem<BlockItem> CABLE_TRAY_ITEM = ITEMS.registerSimpleBlockItem("cable_tray", CABLE_TRAY);

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus and pass them in automatically.
    public CableWorks(IEventBus modEventBus) {
        // Register the Deferred Registers to the mod event bus so blocks and items get registered
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        // Add the cable tray item to the building blocks tab
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(CABLE_TRAY_ITEM);
        }
    }
}
