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
    // Placement turns and connection openings for this kind, see TrayRules.
    private final TrayRules.Rules rules;

    /**
     * @param boxes      the collision boxes for this model, from {@link TrayShapes}
     * @param northNodes the node positions for this model, from {@link TrayNodes}
     * @param rules      the placement turns and connection openings for this kind, from {@link TrayRules}
     */
    public CableTrayBlock(Properties properties, double[][] boxes, Vec3[] northNodes, TrayRules.Rules rules) {
        super(properties);
        this.shapes = TrayShapes.build(boxes);
        this.northNodes = northNodes;
        this.rules = rules;
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

    private static boolean contains(int[] values, int value) {
        for (int v : values)
            if (v == value)
                return true;
        return false;
    }

    // Whether a tray facing this way opens toward the given direction. Openings are quarter turns from facing.
    private static boolean opensToward(Direction facing, int[] openings, Direction dir) {
        for (int opening : openings)
            if (turnClockwise(facing, opening) == dir)
                return true;
        return false;
    }

    // The aimed turn first if this kind allows it, then the rest of the turns this kind allows.
    private static int[] turnOrder(int[] turns, int first) {
        int[] order = new int[turns.length];
        int n = 0;
        if (contains(turns, first))
            order[n++] = first;
        for (int turn : turns)
            if (turn != first)
                order[n++] = turn;
        return order;
    }

    // The end of the tray (along its facing) that the local point is closer to.
    private static Direction nearestEnd(Direction facing, Vec3 local) {
        double along = (local.x - 0.5) * facing.getStepX() + (local.z - 0.5) * facing.getStepZ();
        return along >= 0 ? facing : facing.getOpposite();
    }

    // One clockwise quarter turn seen from above, the same as TrayShapes.rotateQuarter but for points.
    private static Vec3 rotateNode(Vec3 north, Direction facing) {
        Vec3 p = north;
        for (int i = 0; i < facing.get2DDataValue(); i++)
            p = new Vec3(1 - p.z, p.y, p.x);
        return p;
    }

    /**
     * Holding a tray and aiming near its edges places the new tray beside it, using the rules of the held kind
     * (see {@link TrayRules}). A spot is only used when the aimed-at tray opens toward it and the new tray
     * opens back toward the aimed-at tray.
     * - near an end: the new tray goes at that end, in line if this kind can go in line.
     * - near a side: the new tray goes beside that side and turns toward it.
     * - near a corner (an end and a side): tries the end first, then the side.
     * A kind that can't turn sideways (the straight) only continues in line, at the end nearest the aim.
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

        // The rules of the tray kind being placed, from the held item. The aimed-at tray's rules are only a fallback.
        private static TrayRules.Rules heldRules(Player player, TrayRules.Rules fallback) {
            if (player == null)
                return fallback;
            for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
                if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CableTrayBlock tray)
                    return tray.rules;
            }
            return fallback;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            CableTrayBlock target = (CableTrayBlock) state.getBlock();
            TrayRules.Rules held = heldRules(player, target.rules);
            Direction facing = state.getValue(FACING);
            Direction right = facing.getClockWise();
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

            boolean turnsSideways = contains(held.turns(), 1) || contains(held.turns(), 3);
            if (!turnsSideways) {
                side = null;
                if (end == null)
                    end = nearestEnd(facing, local);
            }

            // The turn the aim asks for: toward the side when aiming at one, otherwise in line if this kind can go in line.
            int aimTurn;
            if (side != null) {
                aimTurn = side == right ? 1 : 3;
            } else if (contains(held.turns(), 0)) {
                aimTurn = 0;
            } else {
                double across = (local.x - 0.5) * right.getStepX() + (local.z - 0.5) * right.getStepZ();
                aimTurn = across >= 0 ? 1 : 3;
            }

            // The spots to try, in order. A corner tries the end first, then the side.
            Direction[] spots = end != null && side != null ? new Direction[] {end, side} : new Direction[] {end != null ? end : side};
            for (Direction offset : spots) {
                // The aimed-at tray has to open toward this spot.
                if (!opensToward(facing, target.rules.openings(), offset))
                    continue;
                BlockPos newPos = pos.relative(offset);
                if (!world.getBlockState(newPos).canBeReplaced())
                    continue;

                // The first turn that leaves the new tray open back toward the aimed-at tray.
                for (int turn : turnOrder(held.turns(), aimTurn)) {
                    Direction newFacing = turnClockwise(facing, turn);
                    if (opensToward(newFacing, held.openings(), offset.getOpposite()))
                        return PlacementOffset.success(newPos, bs -> bs.setValue(FACING, newFacing));
                }
            }

            return PlacementOffset.fail();
        }
    }
}
