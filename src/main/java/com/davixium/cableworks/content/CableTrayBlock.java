package com.davixium.cableworks.content;

import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.content.connector.ConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Cable tray, used for the straight, bend and tee models.
 * The model is rotated by the FACING property through the blockstate, and the
 * collision/outline shape is rotated the same way.
 * Uses CEE's connector device, so it behaves like a connector electrically,
 * and its nodes can be wired to each other (a short between nodes is allowed).
 */
public class CableTrayBlock extends SimpleElectricalDeviceBlock<ConnectorDevice> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    // All nodes are small, 1x1 pixels.
    private static final float NODE_SIZE = 1 / 16f;

    private final VoxelShape[] shapes;
    // Node positions for facing NORTH, block-local (0..1). The index is the node id.
    // Rotated by facing with the same quarter turn as the collision shapes (see rotateNode).
    private final Vec3[] northNodes;
    // Which kind of tray this is, used by the placement preview table in TrayRules.
    private final TrayRules.Kind kind;

    /**
     * @param boxes      the collision boxes for this model, from {@link TrayShapes}
     * @param northNodes the node positions for this model, from {@link TrayNodes}
     * @param kind       the kind of tray this is, from {@link TrayRules.Kind}
     */
    public CableTrayBlock(Properties properties, double[][] boxes, Vec3[] northNodes, TrayRules.Kind kind) {
        super(properties);
        this.shapes = TrayShapes.build(boxes);
        this.northNodes = northNodes;
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes[state.getValue(FACING).get2DDataValue()];
    }

    // The tray doesn't block light or hide the faces of neighboring blocks.
    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
        if (placementHelper.matchesItem(stack) && !player.isShiftKeyDown())
            return placementHelper.getOffset(player, level, state, pos, hitResult).placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult);

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public SimulatedDeviceType<ConnectorDevice> getDevice() {
        return CEESimulatedDevices.CONNECTOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> positions = new LinkedHashMap<>();
        for (int id = 0; id < northNodes.length; id++)
            positions.put(id, getNodePosition(level, pos, state, id));
        return positions;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (id < 0 || id >= northNodes.length)
            return null;
        return rotateNode(northNodes[id], state.getValue(FACING));
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return NODE_SIZE;
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

    /**
     * Holding a tray and aiming at it places the new tray using the TrayRules preview table.
     * The cursor's zone (relative to the placed tray's facing) picks the spot and the table row,
     * the player's facing picks the rotation. The player's facing is read relative to the placed tray,
     * so the table works for any facing.
     */
    @MethodsReturnNonnullByDefault
    public static class PlacementHelper implements IPlacementHelper {
        // How close (in blocks) the crosshair must be to an edge of the tray to attach to it.
        private static final double EDGE_RANGE = 6 / 16d;

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CableTrayBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof CableTrayBlock;
        }

        // The kind of tray being placed, from the held item. The aimed-at tray's kind is only a fallback.
        private static TrayRules.Kind heldKind(Player player, TrayRules.Kind fallback) {
            if (player == null)
                return fallback;
            for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
                if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CableTrayBlock tray)
                    return tray.kind;
            }
            return fallback;
        }

        // The cursor zone from its offset from the tray's centre: 'along' is toward the facing, 'across' toward the right.
        // Eight 45-degree sectors, starting at the placed tray's north.
        private static TrayRules.Zone zoneOf(double along, double across) {
            double degrees = Math.toDegrees(Math.atan2(across, along));
            int sector = Math.floorMod((int) Math.round(degrees / 45.0), 8);
            return TrayRules.Zone.values()[sector];
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            CableTrayBlock target = (CableTrayBlock) state.getBlock();
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();
            Vec3 local = ray.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

            // The cursor's offset from the centre of the tray, along its facing and across it.
            double dx = local.x - 0.5;
            double dz = local.z - 0.5;
            double along = dx * facing.getStepX() + dz * facing.getStepZ();
            double across = dx * right.getStepX() + dz * right.getStepZ();
            // Ignore the middle of the tray, so the preview only shows near its edges.
            if (Math.max(Math.abs(along), Math.abs(across)) < 0.5 - EDGE_RANGE)
                return PlacementOffset.fail();

            TrayRules.Zone zone = zoneOf(along, across);
            TrayRules.Kind held = heldKind(player, target.kind);
            int[] rotations = TrayRules.previewFor(target.kind, held, zone);
            int[] spots = TrayRules.spotFor(target.kind, zone);
            if (rotations == null || spots == null)
                return PlacementOffset.fail();

            // The player's facing, relative to the placed tray: 0 = its north, 1 = its east, 2 = south, 3 = west.
            int playerSide = player == null ? 0 : Math.floorMod(player.getDirection().get2DDataValue() - facing.get2DDataValue(), 4);
            int rotation = rotations[playerSide];
            int spot = spots[playerSide];

            BlockPos newPos = pos.relative(turnClockwise(facing, spot));
            if (!world.getBlockState(newPos).canBeReplaced())
                return PlacementOffset.fail();

            Direction newFacing = turnClockwise(facing, rotation);
            return PlacementOffset.success(newPos, bs -> bs.setValue(FACING, newFacing));
        }
    }
}
