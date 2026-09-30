package io.umce.platform.fabric.optimization;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class InsideWallBlockParityTest {
    @BeforeAll static void initializeVanillaRegistries() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void floorIsNotSuffocationButAFullCubeAtEyeHeightIs() {
        Box eye = InsideWallScanGeometry.eyeBox(new Vec3d(0.5, 1.62, 0.5), 0.6f);
        Fixture floor = new Fixture(position -> position.getY() == 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
        assertFalse(InsideWallScanGeometry.intersectsSuffocatingBlock(floor, eye));
        assertFalse(reference(floor, eye));
        Fixture eyeBlock = new Fixture(position -> Blocks.STONE.getDefaultState());
        assertTrue(InsideWallScanGeometry.intersectsSuffocatingBlock(eyeBlock, eye));
        assertTrue(reference(eyeBlock, eye));
    }

    @Test
    void matchesVanillaResultAndReadOrderAcrossRealBlockStatesAndBoundaryBoxes() {
        BlockState[] states = {Blocks.AIR.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.GLASS.getDefaultState(), Blocks.OAK_SLAB.getDefaultState(),
                Blocks.OAK_STAIRS.getDefaultState(), Blocks.OAK_TRAPDOOR.getDefaultState(),
                Blocks.WATER.getDefaultState(), Blocks.OAK_LEAVES.getDefaultState()};
        Vec3d[] eyes = {new Vec3d(0.5, 1.62, 0.5), new Vec3d(-0.1, 1, -0.1),
                new Vec3d(1, 0.5, 1), new Vec3d(0.5, 0.25, 0.5)};
        for (BlockState state : states) for (Vec3d eye : eyes) for (float width : new float[]{0.6f, 1.4f}) {
            Box box = InsideWallScanGeometry.eyeBox(eye, width);
            Fixture vanilla = new Fixture(position -> position.getX() == 0 && position.getZ() == 0 ? state : Blocks.AIR.getDefaultState());
            Fixture optimized = new Fixture(vanilla.states);
            assertEquals(reference(vanilla, box), InsideWallScanGeometry.intersectsSuffocatingBlock(optimized, box));
            assertEquals(vanilla.reads, optimized.reads, "Must preserve short-circuit block reads");
        }
    }

    private static boolean reference(BlockView world, Box box) {
        // Literal predicate from the inspected vanilla Entity.method_30022 bytecode.
        return BlockPos.stream(box).anyMatch(position -> {
            BlockState state = world.getBlockState(position);
            return !state.isAir() && state.shouldSuffocate(world, position)
                    && VoxelShapes.matchesAnywhere(state.getCollisionShape(world, position)
                    .offset(position.getX(), position.getY(), position.getZ()), VoxelShapes.cuboid(box), BooleanBiFunction.AND);
        });
    }

    private static final class Fixture implements BlockView {
        private final Function<BlockPos, BlockState> states;
        private final List<Long> reads = new ArrayList<>();
        private Fixture(Function<BlockPos, BlockState> states) { this.states = states; }
        @Override public BlockState getBlockState(BlockPos position) { reads.add(position.asLong()); return states.apply(position); }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
        @Override public FluidState getFluidState(BlockPos position) { return states.apply(position).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getBottomY() { return -64; }
    }
}
