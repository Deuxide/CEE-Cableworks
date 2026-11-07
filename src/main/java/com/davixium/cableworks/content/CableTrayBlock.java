package com.davixium.cableworks.content;

import com.davixium.cableworks.CableWorks;
import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.content.connector.ConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Cable tray block. One block position holds two layers: a bottom tray and a top tray at 8/16 height.
 * Each layer has its own kind and facing, stored as blockstate properties (no block entity).
 * Uses CEE's connector device, so it behaves like a connector electrically,
 * and its nodes can be wired to each other (a short between nodes is allowed).
 */
public class CableTrayBlock extends SimpleElectricalDeviceBlock<ConnectorDevice> {

    /** What one layer holds: nothing, or a tray of a kind. */
    public enum TrayLayer implements StringRepresentable {
        NONE("none"), STRAIGHT("straight"), BEND("bend"), TEE("tee"), HEAVY_STRAIGHT("heavy_straight");

        private final String serializedName;

        TrayLayer(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public static TrayLayer of(TrayRules.Kind kind) {
            return switch (kind) {
                case STRAIGHT -> TrayLayer.STRAIGHT;
                case BEND -> TrayLayer.BEND;
                case TEE -> TrayLayer.TEE;
                case HEAVY_STRAIGHT -> TrayLayer.HEAVY_STRAIGHT;
            };
        }

        public TrayRules.Kind toKind() {
            return switch (this) {
                case NONE -> null;
                case STRAIGHT -> TrayRules.Kind.STRAIGHT;
                case BEND -> TrayRules.Kind.BEND;
                case TEE -> TrayRules.Kind.TEE;
                case HEAVY_STRAIGHT -> TrayRules.Kind.HEAVY_STRAIGHT;
            };
        }
    }

    /** Which layer of the block: the bottom tray, or the top tray at 8/16. */
    public enum Slot { BOTTOM, TOP }

    public static final EnumProperty<TrayLayer> BOTTOM_LAYER = EnumProperty.create("bottom", TrayLayer.class);
    public static final DirectionProperty BOTTOM_FACING = DirectionProperty.create("bottom_facing", Direction.Plane.HORIZONTAL);
    public static final EnumProperty<TrayLayer> TOP_LAYER = EnumProperty.create("top", TrayLayer.class);
    public static final DirectionProperty TOP_FACING = DirectionProperty.create("top_facing", Direction.Plane.HORIZONTAL);

    // All nodes are small, 1x1 pixels.
    private static final float NODE_SIZE = 1 / 16f;
    // The heavy tray's nodes are 2x2.
    private static final float HEAVY_NODE_SIZE = 3 / 16f;
    // The top layer sits this high in the block.
    private static final double TOP_HEIGHT = 8 / 16d;
    // Node ids on the top layer start here, so they never clash with the bottom layer's ids.
    private static final int TOP_NODE_OFFSET = 100;

    // Collision shape for each (bottom, top) pair, where each index is layer ordinal * 4 + facing.
    private static final int LAYER_COUNT = TrayLayer.values().length;
    private static final VoxelShape[][] SHAPES = new VoxelShape[LAYER_COUNT * 4][LAYER_COUNT * 4];

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    static {
        VoxelShape[] bottom = new VoxelShape[LAYER_COUNT * 4];
        VoxelShape[] top = new VoxelShape[LAYER_COUNT * 4];
        for (TrayLayer layer : TrayLayer.values()) {
            double[][] boxes = boxesOf(layer);
            if (boxes == null)
                continue;
            VoxelShape[] low = TrayShapes.build(boxes, 0);
            VoxelShape[] high = TrayShapes.build(boxes, 8);
            for (int facing = 0; facing < 4; facing++) {
                bottom[index(layer, facing)] = low[facing];
                top[index(layer, facing)] = high[facing];
            }
        }
        for (int b = 0; b < LAYER_COUNT * 4; b++) {
            for (int t = 0; t < LAYER_COUNT * 4; t++) {
                VoxelShape shape = Shapes.empty();
                if (bottom[b] != null)
                    shape = Shapes.or(shape, bottom[b]);
                if (top[t] != null)
                    shape = Shapes.or(shape, top[t]);
                SHAPES[b][t] = shape.optimize();
            }
        }
    }

    public CableTrayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BOTTOM_LAYER, TrayLayer.STRAIGHT)
                .setValue(BOTTOM_FACING, Direction.NORTH)
                .setValue(TOP_LAYER, TrayLayer.NONE)
                .setValue(TOP_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOTTOM_LAYER, BOTTOM_FACING, TOP_LAYER, TOP_FACING);
    }

    // Boxes for each kind of layer, for facing NORTH.
    private static double[][] boxesOf(TrayLayer layer) {
        return switch (layer) {
            case NONE -> null;
            case STRAIGHT -> TrayShapes.STRAIGHT;
            case BEND -> TrayShapes.BEND;
            case TEE -> TrayShapes.TEE;
            case HEAVY_STRAIGHT -> TrayShapes.HEAVY_STRAIGHT;
        };
    }

    // Connector nodes for each kind of layer, for facing NORTH.
    private static Vec3[] nodesOf(TrayLayer layer) {
        return switch (layer) {
            case NONE -> new Vec3[0];
            case STRAIGHT -> TrayNodes.STRAIGHT;
            case BEND -> TrayNodes.BEND;
            case TEE -> TrayNodes.TEE;
            case HEAVY_STRAIGHT -> TrayNodes.HEAVY_STRAIGHT;
        };
    }

    private static int index(TrayLayer layer, int facing) {
        return layer.ordinal() * 4 + facing;
    }

    private static EnumProperty<TrayLayer> layerProperty(Slot slot) {
        return slot == Slot.BOTTOM ? BOTTOM_LAYER : TOP_LAYER;
    }

    private static DirectionProperty facingProperty(Slot slot) {
        return slot == Slot.BOTTOM ? BOTTOM_FACING : TOP_FACING;
    }

    public static TrayLayer layerOf(BlockState state, Slot slot) {
        return state.getValue(layerProperty(slot));
    }

    public static Direction facingOf(BlockState state, Slot slot) {
        return state.getValue(facingProperty(slot));
    }

    private BlockState emptyState() {
        return defaultBlockState().setValue(BOTTOM_LAYER, TrayLayer.NONE).setValue(TOP_LAYER, TrayLayer.NONE);
    }

    private static BlockState withLayer(BlockState base, Slot slot, TrayLayer layer, Direction facing) {
        return base.setValue(layerProperty(slot), layer).setValue(facingProperty(slot), facing);
    }

    /** The layer a click on a block is in: the top layer when the click is in the upper half. */
    public static Slot slotForClick(BlockPlaceContext context) {
        return context.getClickLocation().y - context.getClickedPos().getY() > 0.5 ? Slot.TOP : Slot.BOTTOM;
    }

    /** The state after placing the target: the existing tray block at that spot, or a new one, with the layer filled. */
    public BlockState placedState(Level level, PlacementTarget target) {
        BlockState existing = level.getBlockState(target.pos());
        BlockState base = existing.is(this) ? existing : emptyState();
        return withLayer(base, target.slot(), target.layer(), target.facing());
    }

    /**
     * The state a tray placed from a click on a normal block gets: a new block, with the layer the click is in.
     * Null when that layer is taken.
     */
    public BlockState placementState(BlockPlaceContext context, TrayRules.Kind kind) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        Slot slot = slotForClick(context);
        BlockState base = existing.is(this) ? existing : emptyState();
        if (layerOf(base, slot) != TrayLayer.NONE)
            return null;
        return withLayer(base, slot, TrayLayer.of(kind), context.getHorizontalDirection());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withLayer(emptyState(), slotForClick(context), TrayLayer.STRAIGHT, context.getHorizontalDirection());
    }

    // A held tray can be placed into a free layer of a tray that is already in the spot next to a normal block.
    // The click's height picks the layer: the upper half is the top layer.
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (!(context.getItemInHand().getItem() instanceof TrayItem))
            return false;
        return layerOf(state, slotForClick(context)) == TrayLayer.NONE;
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState
                .setValue(BOTTOM_FACING, originalState.getValue(BOTTOM_FACING).getClockWise())
                .setValue(TOP_FACING, originalState.getValue(TOP_FACING).getClockWise());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BOTTOM_FACING, rotation.rotate(state.getValue(BOTTOM_FACING)))
                .setValue(TOP_FACING, rotation.rotate(state.getValue(TOP_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction bottom = state.getValue(BOTTOM_FACING);
        Direction top = state.getValue(TOP_FACING);
        return state.setValue(BOTTOM_FACING, mirror.getRotation(bottom).rotate(bottom))
                .setValue(TOP_FACING, mirror.getRotation(top).rotate(top));
    }

    private static VoxelShape shapeOf(BlockState state) {
        int bottom = index(state.getValue(BOTTOM_LAYER), state.getValue(BOTTOM_FACING).get2DDataValue());
        int top = index(state.getValue(TOP_LAYER), state.getValue(TOP_FACING).get2DDataValue());
        return SHAPES[bottom][top];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(state);
    }

    // The tray doesn't block light or hide the faces of neighboring blocks.
    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof TrayItem item))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        PlacementTarget target = PlacementHelper.resolve(player, level, state, pos, hitResult, item.kind());
        if (target == null)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;

        level.setBlock(target.pos(), placedState(level, target), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild)
            stack.shrink(1);
        level.playSound(null, target.pos(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, 0.8f);
        return ItemInteractionResult.SUCCESS;
    }

    // A creative break removes connections so they don't drop. The base class removes the connections of every node on the
    // block. With both layers here, only the layer being broken should lose its connections, so the other layer keeps them.
    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        boolean bothLayers = layerOf(state, Slot.BOTTOM) != TrayLayer.NONE && layerOf(state, Slot.TOP) != TrayLayer.NONE;
        if (level instanceof ServerLevel sl && player.isCreative() && bothLayers) {
            Slot broken = aimedSlot(player, pos);
            InfrastructureSavedData sd = InfrastructureSavedData.load(sl);
            for (int i = 0; i < nodesOf(layerOf(state, broken)).length; i++) {
                InWorldNode node = new InWorldNode(nodeId(broken, i), pos);
                for (InWorldNodeConnection connection : sd.getConnections(node))
                    sd.removeConnection(connection);
            }
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
            return state;
        }
        // A break of the last layer takes the whole block, so the base class removes all of its connections.
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Breaking one layer only removes that layer, if the other layer is still here.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (layerOf(state, Slot.BOTTOM) == TrayLayer.NONE || layerOf(state, Slot.TOP) == TrayLayer.NONE)
            return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);

        Slot aimed = aimedSlot(player, pos);
        if (!level.isClientSide) {
            TrayLayer layer = layerOf(state, aimed);
            level.setBlock(pos, state.setValue(layerProperty(aimed), TrayLayer.NONE), Block.UPDATE_ALL);
            if (willHarvest && !player.isCreative())
                Block.popResource(level, pos, stackFor(layer));
        }
        return false;
    }

    // The layer the player is looking at when breaking the block.
    private static Slot aimedSlot(Player player, BlockPos pos) {
        HitResult hit = player.pick(6.0, 0f, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos))
            return blockHit.getLocation().y - pos.getY() > 0.5 ? Slot.TOP : Slot.BOTTOM;
        return Slot.TOP;
    }

    // Drops one tray for each layer that is here (also used for explosions and other breaks).
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            ItemStack stack = stackFor(layerOf(state, slot));
            if (!stack.isEmpty())
                drops.add(stack);
        }
        return drops;
    }

    private static ItemStack stackFor(TrayLayer layer) {
        return switch (layer) {
            case NONE -> ItemStack.EMPTY;
            case STRAIGHT -> new ItemStack(CableWorks.CABLE_TRAY_STRAIGHT_ITEM.get());
            case BEND -> new ItemStack(CableWorks.CABLE_TRAY_BEND_ITEM.get());
            case TEE -> new ItemStack(CableWorks.CABLE_TRAY_TEE_ITEM.get());
            case HEAVY_STRAIGHT -> new ItemStack(CableWorks.CABLE_TRAY_HEAVY_STRAIGHT_ITEM.get());
        };
    }

    // Middle click: give the tray of the layer the player is looking at, not the block's default item.
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        Slot slot = Slot.TOP;
        if (target instanceof BlockHitResult hit)
            slot = hit.getLocation().y - pos.getY() > 0.5 ? Slot.TOP : Slot.BOTTOM;
        if (layerOf(state, slot) == TrayLayer.NONE)
            slot = slot == Slot.TOP ? Slot.BOTTOM : Slot.TOP;
        return stackFor(layerOf(state, slot));
    }

    @Override
    public SimulatedDeviceType<ConnectorDevice> getDevice() {
        return CEESimulatedDevices.CONNECTOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> positions = new LinkedHashMap<>();
        for (Slot slot : Slot.values()) {
            int count = nodesOf(layerOf(state, slot)).length;
            for (int i = 0; i < count; i++)
                positions.put(nodeId(slot, i), nodePosition(state, slot, i));
        }
        return positions;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        Slot slot = id >= TOP_NODE_OFFSET ? Slot.TOP : Slot.BOTTOM;
        int index = slot == Slot.TOP ? id - TOP_NODE_OFFSET : id;
        if (index < 0 || index >= nodesOf(layerOf(state, slot)).length)
            return null;
        return nodePosition(state, slot, index);
    }

    public static int nodeId(Slot slot, int index) {
        return slot == Slot.TOP ? TOP_NODE_OFFSET + index : index;
    }

    /** The slot a node id belongs to. */
    public static Slot slotOfNode(int nodeId) {
        return nodeId >= TOP_NODE_OFFSET ? Slot.TOP : Slot.BOTTOM;
    }

    /** The index of a node id within its slot. */
    public static int indexOfNode(int nodeId) {
        return nodeId >= TOP_NODE_OFFSET ? nodeId - TOP_NODE_OFFSET : nodeId;
    }


    private static Vec3 nodePosition(BlockState state, Slot slot, int index) {
        Vec3 p = rotateNode(nodesOf(layerOf(state, slot))[index], facingOf(state, slot));
        return slot == Slot.TOP ? p.add(0, TOP_HEIGHT, 0) : p;
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return layerOf(state, slotOfNode(id)) == TrayLayer.HEAVY_STRAIGHT ? HEAVY_NODE_SIZE : NODE_SIZE;
    }

    @Override
    public boolean canSelfConnect(Level level, BlockPos pos, BlockState state, int id1, int id2) {
        return id1 != id2;
    }

    // Turns a direction clockwise by the given number of quarter turns.
    private static Direction turnClockwise(Direction direction, int turns) {
        Direction result = direction;
        for (int i = 0; i < turns; i++)
            result = result.getClockWise();
        return result;
    }

    // One clockwise quarter turn seen from above, the same as TrayShapes.rotateQuarter but for points.
    private static Vec3 rotateNode(Vec3 north, Direction facing) {
        Vec3 p = north;
        for (int i = 0; i < facing.get2DDataValue(); i++)
            p = new Vec3(1 - p.z, p.y, p.x);
        return p;
    }

    /** A tray placement: the block position, the layer to fill, the kind, and the new tray's facing. */
    public record PlacementTarget(BlockPos pos, Slot slot, TrayLayer layer, Direction facing) {
    }

    /**
     * Holding a tray and aiming at a tray block places it, and shows the preview.
     * - Top face of a tray with a free top layer: nothing, so the click is a normal placement (the block above).
     * - A click on a normal block next to a tray fills the tray's free layer (upper half = top layer); see canBeReplaced.
     * - Anywhere else on the tray: the layer under the crosshair is the aimed layer, and the new tray goes
     *   beside it, using the TrayRules preview table for the aimed layer's kind and facing.
     */
    public static class PlacementHelper implements IPlacementHelper {
        // How close (in blocks) the crosshair must be to an edge of the tray to attach to it.
        private static final double EDGE_RANGE = 6 / 16d;

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof TrayItem;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof CableTrayBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray) {
            TrayRules.Kind held = heldKind(player);
            if (held == null)
                return PlacementOffset.fail();
            PlacementTarget target = resolve(player, level, state, pos, ray, held);
            if (target == null)
                return PlacementOffset.fail();
            CableTrayBlock block = (CableTrayBlock) state.getBlock();
            return PlacementOffset.success(target.pos(), bs -> block.placedState(level, target));
        }

        // The kind of tray held in either hand, or null if neither hand holds one.
        static TrayRules.Kind heldKind(Player player) {
            for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
                if (stack.getItem() instanceof TrayItem item)
                    return item.kind();
            }
            return null;
        }

        static PlacementTarget resolve(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit, TrayRules.Kind held) {
            boolean hasBottom = layerOf(state, Slot.BOTTOM) != TrayLayer.NONE;
            boolean hasTop = layerOf(state, Slot.TOP) != TrayLayer.NONE;

            // The top face of a bottom-only tray uses the side helper too: its horizontal position picks the side.
            Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            // The aimed layer: the top one when the crosshair is in the upper half, or when it's the only one.
            Slot aimed = hasTop && (local.y > 0.5 || !hasBottom) ? Slot.TOP : Slot.BOTTOM;
            Direction facing = facingOf(state, aimed);
            TrayRules.Kind placed = layerOf(state, aimed).toKind();

            // The cursor's offset from the centre of the aimed layer, along its facing and across it.
            Direction right = facing.getClockWise();
            double dx = local.x - 0.5;
            double dz = local.z - 0.5;
            double along = dx * facing.getStepX() + dz * facing.getStepZ();
            double across = dx * right.getStepX() + dz * right.getStepZ();
            // Ignore the middle of the tray, so the preview only shows near its edges.
            if (Math.max(Math.abs(along), Math.abs(across)) < 0.5 - EDGE_RANGE)
                return null;

            TrayRules.Zone zone = zoneOf(along, across);
            int[] rotations = TrayRules.previewFor(placed, held, zone);
            int[] spots = TrayRules.spotFor(placed, zone);
            if (rotations == null || spots == null)
                return null;

            // The player's facing, relative to the aimed layer: 0 = its north, 1 = east, 2 = south, 3 = west.
            int side = Math.floorMod(player.getDirection().get2DDataValue() - facing.get2DDataValue(), 4);
            BlockPos newPos = pos.relative(turnClockwise(facing, spots[side]));

            BlockState target = level.getBlockState(newPos);
            if (target.getBlock() instanceof CableTrayBlock) {
                if (layerOf(target, aimed) != TrayLayer.NONE)
                    return null;
            } else if (!target.canBeReplaced()) {
                return null;
            }
            return new PlacementTarget(newPos, aimed, TrayLayer.of(held), turnClockwise(facing, rotations[side]));
        }

        // The cursor zone from its offset: 'along' is toward the facing, 'across' toward the right.
        // Eight 45-degree sectors, starting at the placed tray's north and going clockwise.
        private static TrayRules.Zone zoneOf(double along, double across) {
            double degrees = Math.toDegrees(Math.atan2(across, along));
            int sector = Math.floorMod((int) Math.round(degrees / 45.0), 8);
            return TrayRules.Zone.values()[sector];
        }
    }
}
