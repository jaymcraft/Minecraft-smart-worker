package dev.smartworker;

import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

	@GameTest(maxTicks = 500)
	public void keepsMiningTouchingOreUntilGoalIsReached(GameTestHelper test) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) test.setBlock(x, 0, z, Blocks.STONE);
		}
		BlockPos[] ore = {
			new BlockPos(3, 1, 3), new BlockPos(4, 1, 3),
			new BlockPos(3, 1, 4), new BlockPos(4, 1, 4)
		};
		for (BlockPos pos : ore) test.setBlock(pos, Blocks.IRON_ORE);

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, WorkerState.DEFAULT_MINE_BLOCKS, "minecraft:raw_iron", 4, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> {
			manager.tick(test.getLevel().getServer());
			if (worker.miningGoalProgress() < 4) {
				test.assertTrue(worker.miningEnabled(), "Mining stopped before the four-ore goal was reached");
			}
		});
		test.succeedWhen(() -> {
			for (BlockPos pos : ore) test.assertBlockPresent(Blocks.AIR, pos);
			test.assertTrue(worker.miningGoalProgress() >= 4, "Worker did not collect four raw iron");
			test.assertTrue(!worker.miningEnabled(), "Worker did not stop at the completed goal");
		});
	}

	@GameTest(maxTicks = 500)
	public void keepsMiningOreChainBeyondInitialReach(GameTestHelper test) {
		makeFloor(test);
		BlockPos[] ore = {
			new BlockPos(3, 1, 3), new BlockPos(4, 1, 3),
			new BlockPos(5, 1, 3), new BlockPos(6, 1, 3)
		};
		for (BlockPos pos : ore) test.setBlock(pos, Blocks.IRON_ORE);

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:iron_ore"), "minecraft:raw_iron", 4, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> {
			for (BlockPos pos : ore) test.assertBlockPresent(Blocks.AIR, pos);
			test.assertTrue(worker.miningGoalProgress() >= 4, "Worker stopped before mining the ore chain");
		});
	}

	@GameTest(maxTicks = 300)
	public void minesNearbyGoalBeforeFartherGoal(GameTestHelper test) {
		makeFloor(test);
		BlockPos fartherOre = new BlockPos(7, 1, 1);
		BlockPos nearbyOre = new BlockPos(3, 1, 3);
		test.setBlock(fartherOre, Blocks.IRON_ORE);
		test.setBlock(nearbyOre, Blocks.IRON_ORE);

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:iron_ore"), "minecraft:raw_iron", 1, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> {
			test.assertBlockPresent(Blocks.AIR, nearbyOre);
			test.assertBlockPresent(Blocks.IRON_ORE, fartherOre);
		});
	}

	@GameTest(maxTicks = 300)
	public void unreachableDroppedItemDoesNotBlockMining(GameTestHelper test) {
		makeFloor(test);
		BlockPos ore = new BlockPos(3, 1, 3);
		BlockPos trappedItem = new BlockPos(7, 1, 7);
		test.setBlock(ore, Blocks.IRON_ORE);
		test.setBlock(trappedItem, Blocks.OBSIDIAN);
		BlockPos absoluteItem = test.absolutePos(trappedItem);
		test.getLevel().addFreshEntity(new ItemEntity(test.getLevel(), absoluteItem.getX() + 0.5,
				absoluteItem.getY() + 0.5, absoluteItem.getZ() + 0.5, new ItemStack(Items.DIAMOND)));

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:iron_ore"), "minecraft:raw_iron", 1, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> test.assertBlockPresent(Blocks.AIR, ore));
	}

	@GameTest(maxTicks = 300)
	public void defaultConfigurationMinesIronBlocks(GameTestHelper test) {
		makeFloor(test);
		BlockPos iron = new BlockPos(3, 1, 3);
		test.setBlock(iron, Blocks.IRON_BLOCK);

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, WorkerState.DEFAULT_MINE_BLOCKS,
				"minecraft:iron_block", 1, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> test.assertBlockPresent(Blocks.AIR, iron));
	}

	@GameTest(maxTicks = 160)
	public void minesNearbyIronBeforeStoneAndDroppedItems(GameTestHelper test) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) test.setBlock(x, 0, z, Blocks.STONE);
		}
		BlockPos ore = new BlockPos(3, 1, 3);
		test.setBlock(ore, Blocks.IRON_ORE);
		BlockPos absoluteItem = test.absolutePos(new BlockPos(2, 1, 3));
		test.getLevel().addFreshEntity(new ItemEntity(test.getLevel(), absoluteItem.getX() + 0.5,
				absoluteItem.getY() + 0.5, absoluteItem.getZ() + 0.5, new ItemStack(Items.DIAMOND)));

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:stone", "minecraft:iron_ore"),
				"minecraft:raw_iron", 1, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> {
			test.assertBlockPresent(Blocks.AIR, ore);
			test.assertBlockPresent(Blocks.STONE, new BlockPos(1, 0, 3));
			test.assertTrue(worker.miningGoalProgress() >= 1, "Worker did not collect raw iron first");
			test.assertTrue(worker.collected().getOrDefault("minecraft:cobblestone", 0L) == 0,
					"Worker mined ordinary stone before nearby iron");
			test.assertTrue(worker.collected().getOrDefault("minecraft:diamond", 0L) == 0,
					"Worker collected the dropped item before mining nearby iron");
		});
	}

	@GameTest(maxTicks = 200)
	public void skipsBuriedIronForReachableIron(GameTestHelper test) {
		makeFloor(test);
		BlockPos buried = new BlockPos(3, 1, 3);
		BlockPos reachable = new BlockPos(6, 1, 3);
		test.setBlock(buried, Blocks.IRON_ORE);
		test.setBlock(reachable, Blocks.IRON_ORE);
		test.setBlock(buried.above(), Blocks.OBSIDIAN);
		test.setBlock(buried.north(), Blocks.OBSIDIAN);
		test.setBlock(buried.south(), Blocks.OBSIDIAN);
		test.setBlock(buried.east(), Blocks.OBSIDIAN);
		test.setBlock(buried.west(), Blocks.OBSIDIAN);

		ServerPlayer owner = test.makeMockServerPlayerInLevel();
		BlockPos ownerPos = test.absolutePos(new BlockPos(0, 1, 3));
		owner.snapTo(ownerPos.getX() + 0.5, ownerPos.getY(), ownerPos.getZ() + 0.5);
		WorkerManager manager = new WorkerManager();
		WorkerState worker = manager.spawn(owner);
		manager.configureMining(worker, true, Set.of("minecraft:iron_ore"), "minecraft:raw_iron", 1, null);
		manager.setActive(worker, true);
		test.onEachTick(() -> manager.tick(test.getLevel().getServer()));
		test.succeedWhen(() -> {
			test.assertBlockPresent(Blocks.IRON_ORE, buried);
			test.assertBlockPresent(Blocks.AIR, reachable);
		});
	}

	private void makeFloor(GameTestHelper test) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) test.setBlock(x, 0, z, Blocks.BEDROCK);
		}
	}
}
