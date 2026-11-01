package com.example.examplemod.content;

import com.george_vi.electroenergetics.CEESimulatedDevices;
import com.george_vi.electroenergetics.content.connector.ConnectorDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Support-only cable tray: a full cube with a single connector node on top.
 * Uses CEE's connector device, so it behaves like a connector electrically,
 * but canSelfConnect is false, so its nodes can't be wired to each other.
 */
public class CableTrayBlock extends SimpleElectricalDeviceBlock<ConnectorDevice> {
    private static final int NODE_ID = 0;
    // Same top-middle position CEE uses for its long connectors.
    private static final Vec3 NODE_POS = new Vec3(0.5, 14 / 16d, 0.5);

    public CableTrayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public SimulatedDeviceType<ConnectorDevice> getDevice() {
        return CEESimulatedDevices.CONNECTOR.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(NODE_ID, NODE_POS);
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int id) {
        return id == NODE_ID ? NODE_POS : null;
    }

    @Override
    public boolean canSelfConnect(Level level, BlockPos pos, BlockState state, int id1, int id2) {
        return false;
    }
}
