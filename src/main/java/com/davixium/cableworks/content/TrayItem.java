package com.davixium.cableworks.content;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A cable tray item. There is one item per kind (straight, bend, tee), and they all place the shared CableTrayBlock.
 */
public class TrayItem extends BlockItem {
    private final TrayRules.Kind kind;

    public TrayItem(Block block, TrayRules.Kind kind, Properties properties) {
        super(block, properties);
        this.kind = kind;
    }

    public TrayRules.Kind kind() {
        return kind;
    }

    // Each kind has its own name, since they share one block.
    @Override
    public String getDescriptionId() {
        return "item.cableworks.cable_tray_" + kind.name().toLowerCase(Locale.ROOT);
    }

    // Placing on a normal block: the tray goes in the top layer if the click is in the upper half, else the bottom.
    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        return ((CableTrayBlock) getBlock()).placementState(context, kind);
    }
}
