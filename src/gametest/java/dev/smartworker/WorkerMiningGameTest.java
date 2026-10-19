package dev.smartworker;

import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("removal")
public final class WorkerMiningGameTest {
	@GameTest(maxTicks = 300)
	public void minesReachableBlockInsteadOfBuriedGoal(GameTestHelper test) {
		makeFloor(test);
		BlockPos exposed = new BlockPos(5, 1, 1);
		test.setBlock(exposed, Blocks.STONE);
		BlockPos buried = new BlockPos(6, 1, 5);
		test.setBlock(buried, Blocks.IRON_ORE);
		test.setBlock(buried.above(), Blocks.OBSIDIAN);
		test.setBlock(buried.below(), Blocks.OBSIDIAN);
		test.setBlock(buried.north(), Blocks.OBSIDIAN);
		test.setBlock(buried.south(), Blocks.OBSIDIAN);
		test.setBlock(buried.east(), Blocks.OBSIDIAN);
		test.setBlock(buried.west(), Blocks.OBSIDIAN);

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:stone", "minecraft:iron_ore"),
				"minecraft:raw_iron", 0, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> {
			test.assertBlockPresent(Blocks.AIR, exposed);
			test.assertBlockPresent(Blocks.IRON_ORE, buried);
		});
	}

	@GameTest(maxTicks = 300)
	public void minesReachableOreAndCollectsItsDrop(GameTestHelper test) {
		makeFloor(test);
		BlockPos ore = new BlockPos(6, 1, 3);
		test.setBlock(ore, Blocks.IRON_ORE);
		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:iron_ore"), "minecraft:raw_iron", 1, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> {
			test.assertBlockPresent(Blocks.AIR, ore);
			test.assertTrue(worker.miningGoalProgress() >= 1, "Worker did not collect raw iron");
		});
	}

	private void makeFloor(GameTestHelper test) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) test.setBlock(x, 0, z, Blocks.BEDROCK);
		}
	}
}
