package dev.smartworker;

import dev.smartworker.content.WorkerChestBlockEntity;
import dev.smartworker.content.ModContent;
import dev.smartworker.learning.PreferenceModel;
import dev.smartworker.network.UpdateWorkerConfigPayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

public final class WorkerManager {
	public static final String ENTITY_TAG = "smart_worker";
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final double SEARCH_RADIUS = 16.0;
	private static final double CONTROL_RADIUS_SQUARED = 32.0 * 32.0;
	private static final int CHEST_SEARCH_INTERVAL_TICKS = 100;
	private static final int CHEST_HORIZONTAL_RADIUS = 16;
	private static final int CHEST_VERTICAL_RADIUS = 6;
	private static final int MINING_SCAN_INTERVAL_TICKS = 20;
	private static final int MINING_HORIZONTAL_RADIUS = 32;
	private static final int MINING_VERTICAL_RADIUS = 24;
	private static final int MINING_SCAN_BUDGET = 8_192;
	private static final int MINING_PATH_BUDGET = 16;
	private static final double MINING_REACH_SQUARED = 3.75 * 3.75;

	private final Map<UUID, WorkerState> workers = new LinkedHashMap<>();
	private final Map<UUID, BlockPos> chestTargets = new LinkedHashMap<>();
	private final Map<UUID, Integer> lastChestSearch = new LinkedHashMap<>();
	private final Set<UUID> navigatingWorkers = new HashSet<>();
	private final Map<UUID, MiningTarget> miningTargets = new LinkedHashMap<>();
	private final Map<UUID, Integer> miningProgress = new LinkedHashMap<>();
	private final Map<UUID, MiningScanState> miningScans = new LinkedHashMap<>();
	private final Map<UUID, Float> lastHealth = new LinkedHashMap<>();
	private int tickCounter;
	private boolean dirty;

	public void load(MinecraftServer server) {
		workers.clear();
		try {
			WorkerPersistence.load(server, workers);
			SmartWorkerMod.LOGGER.info("Loaded {} smart worker(s)", workers.size());
		} catch (Exception exception) {
			SmartWorkerMod.LOGGER.error("Could not load smart worker data; preserving the unread file", exception);
		}
	}

	public void save(MinecraftServer server) {
		if (!dirty && tickCounter != 0) return;
		try {
			WorkerPersistence.save(server, workers.values());
			dirty = false;
		} catch (IOException | RuntimeException exception) {
			SmartWorkerMod.LOGGER.error("Could not save smart worker data", exception);
		}
	}

	public WorkerState spawn(ServerPlayer owner) {
		ServerLevel level = owner.level();
		IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
		if (golem == null) throw new IllegalStateException("Minecraft did not create an iron golem");

		golem.snapTo(owner.getX() + 1.5, owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
		golem.setPlayerCreated(true);
		golem.setPersistenceRequired();
		golem.setNoAi(true);
		golem.addTag(ENTITY_TAG);
		golem.setCustomName(Component.literal("Smart Worker"));
		golem.setCustomNameVisible(true);
		if (!level.addFreshEntity(golem)) throw new IllegalStateException("Minecraft rejected the worker entity");

		WorkerState state = new WorkerState(golem.getUUID(), owner.getUUID());
		workers.put(golem.getUUID(), state);
		dirty = true;
		return state;
	}

	public Optional<WorkerState> nearestOwned(ServerPlayer player) {
		return workers.values().stream()
				.filter(state -> state.ownerId().equals(player.getUUID()))
				.filter(state -> {
					Entity entity = player.level().getEntity(state.entityId());
					return entity instanceof IronGolem && entity.distanceToSqr(player) <= CONTROL_RADIUS_SQUARED;
				})
				.min(Comparator.comparingDouble(state -> player.level().getEntity(state.entityId()).distanceToSqr(player)));
	}

	public Optional<WorkerState> stateForEntity(UUID entityId) {
		return Optional.ofNullable(workers.get(entityId));
	}

	public void setActive(WorkerState state, boolean active) {
		state.setActive(active);
		dirty = true;
	}

	public void filtersChanged() {
		dirty = true;
	}

	public void configureMining(WorkerState state, boolean enabled, Set<String> blocks, String goalItem,
			int goalCount, BlockPos chest) {
		state.setMiningEnabled(enabled);
		state.mineBlocks().clear();
		state.mineBlocks().addAll(blocks);
		state.setMiningGoalItem(goalItem);
		state.setMiningGoalCount(goalCount);
		state.setMiningGoalProgress(0);
		state.setAssignedChest(chest);
		miningTargets.remove(state.entityId());
		miningProgress.remove(state.entityId());
		miningScans.remove(state.entityId());
		dirty = true;
	}

	public void applyMiningConfig(ServerPlayer player, UpdateWorkerConfigPayload payload) {
		WorkerState state = workers.get(payload.workerId());
		Entity entity = player.level().getEntity(payload.workerId());
		if (state == null || entity == null || !state.ownerId().equals(player.getUUID())
				|| entity.distanceToSqr(player) > CONTROL_RADIUS_SQUARED) {
			player.sendSystemMessage(Component.literal("You must be within 32 blocks of your own Smart Worker."));
			return;
		}

		Set<String> blocks = new HashSet<>();
		for (String raw : payload.mineBlocks().split("[,\\s]+")) {
			if (raw.isBlank()) continue;
			Identifier id = Identifier.tryParse(raw.trim());
			if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)
					|| BuiltInRegistries.BLOCK.getValue(id) == Blocks.AIR
					|| BuiltInRegistries.BLOCK.getValue(id) == ModContent.WORKER_CHEST) {
				player.sendSystemMessage(Component.literal("Invalid or unsafe mining block: " + raw));
				return;
			}
			blocks.add(id.toString());
			if (blocks.size() > 64) {
				player.sendSystemMessage(Component.literal("Choose at most 64 mining blocks."));
				return;
			}
		}
		if (blocks.isEmpty()) {
			player.sendSystemMessage(Component.literal("Add at least one block to the mining list."));
			return;
		}

		Identifier goalId = Identifier.tryParse(payload.goalItem().trim());
		if (goalId == null || !BuiltInRegistries.ITEM.containsKey(goalId)) {
			player.sendSystemMessage(Component.literal("Unknown goal item: " + payload.goalItem()));
			return;
		}
		BlockPos chest = null;
		if (payload.hasChest()) {
			chest = new BlockPos(payload.chestX(), payload.chestY(), payload.chestZ());
			if (chest.distSqr(entity.blockPosition()) > 64.0 * 64.0
					|| !(player.level().getBlockEntity(chest) instanceof WorkerChestBlockEntity)) {
				player.sendSystemMessage(Component.literal("Chest coordinates must point to a Worker Chest within 64 blocks."));
				return;
			}
		}

		configureMining(state, payload.miningEnabled(), blocks, goalId.toString(),
				Math.clamp(payload.goalCount(), 0, 100_000), chest);
		player.sendSystemMessage(Component.literal("Smart Worker mining configuration saved."));
	}

	public void tick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter % SCAN_INTERVAL_TICKS == 0) {
			navigatingWorkers.clear();
			for (WorkerState state : new ArrayList<>(workers.values())) tickWorker(server, state);
		}
		for (WorkerState state : workers.values()) {
			if (!state.active() || !navigatingWorkers.contains(state.entityId())) {
				IronGolem worker = findLoadedWorker(server, state.entityId());
				if (worker != null) freeze(worker);
			}
		}
		if (tickCounter % 1200 == 0 && dirty) save(server);
	}

	private void tickWorker(MinecraftServer server, WorkerState state) {
		if (!state.active()) return;
		IronGolem worker = findLoadedWorker(server, state.entityId());
		if (worker == null || !worker.isAlive()) return;
		String dimension = worker.level().dimension().identifier().toString();
		float previousHealth = lastHealth.getOrDefault(state.entityId(), worker.getHealth());
		if (worker.getHealth() < previousHealth) {
			state.rememberDanger(dimension, worker.blockPosition());
			dirty = true;
		}
		lastHealth.put(state.entityId(), worker.getHealth());

		worker.setTarget(null);
		if (state.isInventoryFull()) {
			if (!tryUnload(worker, state)) freeze(worker);
			return;
		}

		List<ItemEntity> candidates = worker.level().getEntitiesOfClass(
				ItemEntity.class,
				worker.getBoundingBox().inflate(SEARCH_RADIUS),
				item -> item.isAlive() && !item.getItem().isEmpty() && state.canAccept(item.getItem()));

		ItemEntity target = candidates.stream().max(Comparator.comparingDouble(item -> {
			String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
			return PreferenceModel.targetScore(id, worker.distanceToSqr(item), state.collected());
		})).orElse(null);

		if (target == null) {
			if (state.miningEnabled() && !state.miningGoalReached() && tickMining(server, worker, state, dimension)) return;
			freeze(worker);
			return;
		}

		if (worker.distanceToSqr(target) <= 2.25) {
			ItemStack dropped = target.getItem();
			String id = BuiltInRegistries.ITEM.getKey(dropped.getItem()).toString();
			int inserted = state.insert(dropped);
			if (inserted > 0) {
				dropped.shrink(inserted);
				state.record(id, inserted);
				dirty = true;
				if (dropped.isEmpty()) target.discard();
				else target.setItem(dropped);
			}
			freeze(worker);
		} else {
			moveTo(worker, state, target.getX(), target.getY(), target.getZ());
		}
	}

	private boolean tickMining(MinecraftServer server, IronGolem worker, WorkerState state, String dimension) {
		ServerLevel level = (ServerLevel) worker.level();
		MiningTarget target = miningTargets.get(state.entityId());
		if (target != null && !validMiningTarget(level, state, dimension, target.block())) {
			clearMiningTarget(level, worker, state);
			target = null;
		}
		if (target == null) {
			if (tickCounter % MINING_SCAN_INTERVAL_TICKS == 0) {
				target = continueMiningScan(level, worker, state, dimension);
				if (target != null) miningTargets.put(state.entityId(), target);
			}
		}

		if (target == null) {
			// Finish the bounded incremental scan from one stable origin before choosing a remembered area.
			if (miningScans.containsKey(state.entityId())) return false;
			return navigateToRememberedArea(worker, state, dimension);
		}
		BlockPos block = target.block();
		if (worker.distanceToSqr(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5) > MINING_REACH_SQUARED) {
			BlockPos stand = target.stand();
			if (!moveTo(worker, state, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5)) {
				clearMiningTarget(level, worker, state);
				miningScans.remove(state.entityId());
				return false;
			}
			return true;
		}

		freeze(worker);
		BlockState blockState = level.getBlockState(block);
		float hardness = blockState.getDestroySpeed(level, block);
		if (hardness < 0.0F) {
			clearMiningTarget(level, worker, state);
			return false;
		}
		int requiredSteps = Math.max(2, Math.round(Math.max(0.5F, hardness) * 6.0F));
		int progress = miningProgress.merge(state.entityId(), 1, Integer::sum);
		level.destroyBlockProgress(worker.getId(), block, Math.min(9, progress * 10 / requiredSteps));
		if (progress < requiredSteps) return true;

		mineBlock(server, level, worker, state, block, blockState, dimension);
		clearMiningTarget(level, worker, state);
		return true;
	}

	private MiningTarget continueMiningScan(ServerLevel level, IronGolem worker, WorkerState state, String dimension) {
		BlockPos center = worker.blockPosition();
		MiningScanState scan = miningScans.get(state.entityId());
		if (scan == null || scan.origin.distSqr(center) > 16.0) {
			scan = new MiningScanState(center.immutable());
			miningScans.put(state.entityId(), scan);
		}

		int width = MINING_HORIZONTAL_RADIUS * 2 + 1;
		int height = MINING_VERTICAL_RADIUS * 2 + 1;
		int total = width * width * height;
		int end = Math.min(total, scan.cursor + MINING_SCAN_BUDGET);
		int pathChecks = 0;
		for (; scan.cursor < end; scan.cursor++) {
			int index = scan.cursor;
			int x = index % width - MINING_HORIZONTAL_RADIUS;
			int z = index / width % width - MINING_HORIZONTAL_RADIUS;
			int layer = index / (width * width);
			int y = layer == 0 ? 0 : (layer + 1) / 2 * (layer % 2 == 1 ? -1 : 1);
			BlockPos candidate = scan.origin.offset(x, y, z);
			if (!validMiningTarget(level, state, dimension, candidate)) continue;
			if (hazardNearby(level, candidate)) {
				state.rememberDanger(dimension, candidate);
				dirty = true;
				continue;
			}
			String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(candidate).getBlock()).toString();
			String resource = resourceForBlock(blockId);
			double distance = candidate.distSqr(center);
			double goalBonus = resource.equals(state.miningGoalItem()) ? 2_000.0 : 0.0;
			double memoryBonus = state.rememberedFinds(dimension, resource, candidate) * 40.0;
			double score = goalBonus + memoryBonus - distance;
			if (score <= scan.bestScore || !hasOpenMiningFace(level, candidate)) continue;
			if (pathChecks >= MINING_PATH_BUDGET) break;
			pathChecks++;
			MiningTarget approach = findMiningApproach(level, worker, candidate);
			if (approach == null) continue;
			scan.bestScore = score;
			scan.best = approach;
			if (resource.equals(state.miningGoalItem())) {
				miningScans.remove(state.entityId());
				return approach;
			}
		}
		if (scan.cursor < total) {
			MiningTarget next = scan.best;
			scan.best = null;
			scan.bestScore = -Double.MAX_VALUE;
			return next;
		}
		miningScans.remove(state.entityId());
		return scan.best;
	}

	private boolean hasOpenMiningFace(ServerLevel level, BlockPos block) {
		if (isOpen(level, block.above())) return true;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (isOpen(level, block.relative(direction))) return true;
		}
		return false;
	}

	private MiningTarget findMiningApproach(ServerLevel level, IronGolem worker, BlockPos block) {
		BlockPos target = block.immutable();
		if (worker.distanceToSqr(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5)
				<= MINING_REACH_SQUARED) return new MiningTarget(target, worker.blockPosition().immutable());

		Set<BlockPos> stands = new LinkedHashSet<>();
		if (isOpen(level, block.above())) stands.add(block.above());
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (isOpen(level, block.relative(direction))) stands.add(block.relative(direction, 2));
		}
		stands.removeIf(pos -> !level.hasChunkAt(pos));
		if (stands.isEmpty()) return null;
		prepareNavigation(worker);
		Path path = worker.getNavigation().createPath(stands, 0);
		return path != null && path.canReach() ? new MiningTarget(target, path.getTarget().immutable()) : null;
	}

	private boolean isOpen(ServerLevel level, BlockPos pos) {
		return level.hasChunkAt(pos)
				&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private boolean validMiningTarget(ServerLevel level, WorkerState state, String dimension, BlockPos pos) {
		if (pos == null || !level.hasChunkAt(pos) || state.isDangerous(dimension, pos)) return false;
		BlockState blockState = level.getBlockState(pos);
		String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
		return state.mineBlocks().contains(blockId) && !blockState.isAir()
				&& blockState.getDestroySpeed(level, pos) >= 0.0F && level.getBlockEntity(pos) == null;
	}

	private boolean hazardNearby(ServerLevel level, BlockPos pos) {
		for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(nearby).getBlock()).toString();
			if (id.contains("lava") || id.contains("fire") || id.contains("cactus")
					|| id.contains("magma") || id.contains("sweet_berry_bush")) return true;
		}
		return false;
	}

	private void mineBlock(MinecraftServer server, ServerLevel level, IronGolem worker, WorkerState workerState,
			BlockPos pos, BlockState blockState, String dimension) {
		String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
		List<ItemStack> drops = Block.getDrops(blockState, level, pos, level.getBlockEntity(pos), worker,
				new ItemStack(Items.IRON_PICKAXE));
		if (!level.destroyBlock(pos, false, worker, 512)) return;
		level.levelEvent(null, 2001, pos, Block.getId(blockState));

		String resource = resourceForBlock(blockId);
		if (isOre(blockId)) workerState.rememberResource(dimension, resource, pos);
		for (ItemStack drop : drops) {
			String itemId = BuiltInRegistries.ITEM.getKey(drop.getItem()).toString();
			int inserted = workerState.insert(drop);
			if (inserted > 0) {
				workerState.record(itemId, inserted);
				workerState.recordMinedGoal(itemId, inserted);
			}
			if (inserted < drop.getCount()) {
				ItemStack remainder = drop.copy();
				remainder.setCount(drop.getCount() - inserted);
				Block.popResource(level, pos, remainder);
			}
		}
		dirty = true;
		if (workerState.miningGoalReached()) {
			workerState.setMiningEnabled(false);
			ServerPlayer owner = server.getPlayerList().getPlayer(workerState.ownerId());
			if (owner != null) owner.sendSystemMessage(Component.literal("Smart Worker reached its mining goal of "
					+ workerState.miningGoalCount() + " " + workerState.miningGoalItem() + "."));
		}
	}

	private boolean navigateToRememberedArea(IronGolem worker, WorkerState state, String dimension) {
		MiningAreaMemory best = state.miningAreas().stream()
				.filter(memory -> memory.dimension().equals(dimension))
				.filter(memory -> memory.resource().equals(state.miningGoalItem()))
				.filter(memory -> !state.isDangerous(dimension, memory.center()))
				.filter(memory -> memory.center().distSqr(worker.blockPosition()) <= 64.0 * 64.0)
				.max(Comparator.comparingDouble(memory -> memory.finds() * 100.0
						- memory.center().distSqr(worker.blockPosition()))).orElse(null);
		if (best == null || best.center().distSqr(worker.blockPosition()) < 6.0 * 6.0) return false;
		BlockPos center = best.center();
		moveTo(worker, state, center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		return true;
	}

	private void clearMiningTarget(ServerLevel level, IronGolem worker, WorkerState state) {
		MiningTarget previous = miningTargets.remove(state.entityId());
		if (previous != null) level.destroyBlockProgress(worker.getId(), previous.block(), -1);
		miningProgress.remove(state.entityId());
	}

	private boolean isOre(String blockId) {
		return blockId.endsWith("_ore") || blockId.equals("minecraft:ancient_debris");
	}

	private String resourceForBlock(String blockId) {
		String path = Identifier.parse(blockId).getPath();
		if (path.contains("iron_ore")) return "minecraft:raw_iron";
		if (path.contains("copper_ore")) return "minecraft:raw_copper";
		if (path.contains("nether_gold_ore")) return "minecraft:gold_nugget";
		if (path.contains("gold_ore")) return "minecraft:raw_gold";
		if (path.contains("quartz_ore")) return "minecraft:quartz";
		if (path.contains("coal_ore")) return "minecraft:coal";
		if (path.contains("diamond_ore")) return "minecraft:diamond";
		if (path.contains("emerald_ore")) return "minecraft:emerald";
		if (path.contains("lapis_ore")) return "minecraft:lapis_lazuli";
		if (path.contains("redstone_ore")) return "minecraft:redstone";
		if (path.equals("stone")) return "minecraft:cobblestone";
		if (path.equals("deepslate")) return "minecraft:cobbled_deepslate";
		return blockId;
	}

	private static final class MiningScanState {
		private final BlockPos origin;
		private int cursor;
		private MiningTarget best;
		private double bestScore = -Double.MAX_VALUE;

		private MiningScanState(BlockPos origin) {
			this.origin = origin;
		}
	}

	private record MiningTarget(BlockPos block, BlockPos stand) {}

	private boolean tryUnload(IronGolem worker, WorkerState state) {
		BlockPos targetPos = state.assignedChest() != null ? state.assignedChest() : chestTargets.get(state.entityId());
		WorkerChestBlockEntity chest = targetPos == null ? null : loadedChest(worker.level(), targetPos);
		if (chest == null || !chest.hasSpace()) {
			if (state.assignedChest() != null) return false;
			chestTargets.remove(state.entityId());
			targetPos = null;
			int lastSearch = lastChestSearch.getOrDefault(state.entityId(), Integer.MIN_VALUE / 2);
			if (tickCounter - lastSearch >= CHEST_SEARCH_INTERVAL_TICKS) {
				lastChestSearch.put(state.entityId(), tickCounter);
				targetPos = findNearestChest(worker);
				if (targetPos != null) chestTargets.put(state.entityId(), targetPos);
			}
		}

		if (targetPos == null) return false;
		chest = loadedChest(worker.level(), targetPos);
		if (chest == null) {
			chestTargets.remove(state.entityId());
			return false;
		}

		double targetX = targetPos.getX() + 0.5;
		double targetY = targetPos.getY() + 0.5;
		double targetZ = targetPos.getZ() + 0.5;
		if (worker.distanceToSqr(targetX, targetY, targetZ) <= 6.25) {
			int moved = chest.insertAll(state.inventory());
			if (moved > 0) dirty = true;
			if (state.isInventoryEmpty()) {
				chestTargets.remove(state.entityId());
				freeze(worker);
			} else if (!chest.hasSpace()) {
				chestTargets.remove(state.entityId());
				lastChestSearch.remove(state.entityId());
			}
		} else {
			moveTo(worker, state, targetX, targetY, targetZ);
		}
		return true;
	}

	private boolean moveTo(IronGolem worker, WorkerState state, double x, double y, double z) {
		prepareNavigation(worker);
		worker.setTarget(null);
		boolean started = worker.getNavigation().moveTo(x, y, z, 1.0);
		if (started) navigatingWorkers.add(state.entityId());
		return started;
	}

	private void prepareNavigation(IronGolem worker) {
		worker.setNoAi(false);
		if (!worker.onGround() && worker.level().findSupportingBlock(worker,
				worker.getBoundingBox().move(0.0, -0.05, 0.0)).isPresent()) {
			worker.setOnGround(true);
		}
	}

	private void freeze(IronGolem worker) {
		worker.getNavigation().stop();
		worker.setTarget(null);
		worker.setNoAi(true);
		worker.setDeltaMovement(0.0, worker.getDeltaMovement().y, 0.0);
	}

	private BlockPos findNearestChest(IronGolem worker) {
		BlockPos center = worker.blockPosition();
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos candidate : BlockPos.betweenClosed(
				center.offset(-CHEST_HORIZONTAL_RADIUS, -CHEST_VERTICAL_RADIUS, -CHEST_HORIZONTAL_RADIUS),
				center.offset(CHEST_HORIZONTAL_RADIUS, CHEST_VERTICAL_RADIUS, CHEST_HORIZONTAL_RADIUS))) {
			WorkerChestBlockEntity chest = loadedChest(worker.level(), candidate);
			if (chest == null || !chest.hasSpace()) continue;
			double distance = candidate.distSqr(center);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate.immutable();
			}
		}
		return nearest;
	}

	private WorkerChestBlockEntity loadedChest(net.minecraft.world.level.Level level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) return null;
		return level.getBlockEntity(pos) instanceof WorkerChestBlockEntity chest ? chest : null;
	}

	private IronGolem findLoadedWorker(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof IronGolem golem && golem.entityTags().contains(ENTITY_TAG)) return golem;
		}
		return null;
	}
}
