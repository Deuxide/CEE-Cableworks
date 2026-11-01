package com.davixium.cableworks.content;

import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.content.connector.ConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Support-only cable tray, used for both the straight and the bend models.
 * The model is rotated by the FACING property through the blockstate, and the
 * collision/outline shape is rotated the same way.
 * Uses CEE's connector device, so it behaves like a connector electrically,
 * but canSelfConnect is false, so its nodes can't be wired to each other.
 */
public class CableTrayBlock extends SimpleElectricalDeviceBlock<ConnectorDevice> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Node positions for facing NORTH, block-local (0..1). The index is the node id.
    // Rotated by facing with the same quarter turn as the collision shapes (see rotateNode).
    // Node 0 is the original centered node; nodes 1-6 are placeholders to set by hand.
    private static final Vec3[] NORTH_NODE_POSITIONS = {
            new Vec3(0.5, 3 / 16d, 0.5),    // 0 (centered, normal size)
            new Vec3(0.15, 3 / 16d, 0.5),   // 1 (placeholder)
            new Vec3(0.85, 3 / 16d, 0.5),   // 2 (placeholder)
            new Vec3(0.5, 3 / 16d, 0.15),   // 3 (placeholder)
            new Vec3(0.5, 3 / 16d, 0.85),   // 4 (placeholder)
            new Vec3(0.25, 3 / 16d, 0.25),  // 5 (placeholder)
            new Vec3(0.75, 3 / 16d, 0.75),  // 6 (placeholder)
    };

    private static final float NODE_SIZE = 4 / 16f;
    // The extra nodes are small, 1x1 pixels.
    private static final float SMALL_NODE_SIZE = 1 / 16f;

    private final VoxelShape[] shapes;

    /**
     * @param boxes the collision boxes for this model, from {@link TrayShapes}
     */
    public CableTrayBlock(Properties properties, double[][] boxes) {
        super(properties);
        this.shapes = TrayShapes.build(boxes);
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
    public SimulatedDeviceType<ConnectorDevice> getDevice() {
        return CEESimulatedDevices.CONNECTOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        Map<Integer, Vec3> positions = new LinkedHashMap<>();
        for (int id = 0; id < NORTH_NODE_POSITIONS.length; id++)
            positions.put(id, getNodePosition(level, pos, state, id));
        return positions;
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        if (id < 0 || id >= NORTH_NODE_POSITIONS.length)
            return null;
        return rotateNode(NORTH_NODE_POSITIONS[id], state.getValue(FACING));
    }

    @Override
    public float getNodeSize(Level level, BlockPos pos, BlockState state, int id) {
        return id == 0 ? NODE_SIZE : SMALL_NODE_SIZE;
    }

    @Override
    public boolean canSelfConnect(Level level, BlockPos pos, BlockState state, int id1, int id2) {
        return false;
    }

    // One clockwise quarter turn seen from above, the same as TrayShapes.rotateQuarter but for points.
    private static Vec3 rotateNode(Vec3 north, Direction facing) {
        Vec3 p = north;
        for (int i = 0; i < facing.get2DDataValue(); i++)
            p = new Vec3(1 - p.z, p.y, p.x);
        return p;
    }
}
