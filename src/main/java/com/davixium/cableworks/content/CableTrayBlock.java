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

    /**
     * @param boxes      the collision boxes for this model, from {@link TrayShapes}
     * @param northNodes the node positions for this model, from {@link TrayNodes}
     */
    public CableTrayBlock(Properties properties, double[][] boxes, Vec3[] northNodes) {
        super(properties);
        this.shapes = TrayShapes.build(boxes);
        this.northNodes = northNodes;
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

    // One clockwise quarter turn seen from above, the same as TrayShapes.rotateQuarter but for points.
    private static Vec3 rotateNode(Vec3 north, Direction facing) {
        Vec3 p = north;
        for (int i = 0; i < facing.get2DDataValue(); i++)
            p = new Vec3(1 - p.z, p.y, p.x);
        return p;
    }

    /**
     * Holding a tray and aiming near one of its edges places the new tray right beside it.
     * Aiming at an end keeps the tray's facing, so the run continues straight. Aiming at a
     * side turns the new tray to face that side, like Create's sails do.
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

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            Direction facing = state.getValue(FACING);
            Vec3 local = ray.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

            // The closest edge along the facing axis (an end) and across it (a side), if the crosshair is near one.
            Direction end = null;
            double endDist = EDGE_RANGE;
            Direction side = null;
            double sideDist = EDGE_RANGE;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                double c = d.getStepX() != 0 ? local.x : local.z;
                double toEdge = d.getStepX() + d.getStepZ() > 0 ? 1 - c : c;
                if (d.getAxis() == facing.getAxis()) {
                    if (toEdge < endDist) {
                        endDist = toEdge;
                        end = d;
                    }
                } else if (toEdge < sideDist) {
                    sideDist = toEdge;
                    side = d;
                }
            }
            if (end == null && side == null)
                return PlacementOffset.fail();

            // Near an end: the new tray goes at that end. Near a side: it faces that side.
            // Near a corner (both): it goes at the end and faces the side, like a bend starting there.
            Direction offset = end != null ? end : side;
            Direction newFacing = side != null ? side : facing;
            BlockPos newPos = pos.relative(offset);
            if (world.getBlockState(newPos).canBeReplaced())
                return PlacementOffset.success(newPos, bs -> bs.setValue(FACING, newFacing));

            return PlacementOffset.fail();
        }
    }
}
