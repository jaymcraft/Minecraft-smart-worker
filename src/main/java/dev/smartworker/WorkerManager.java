package dev.smartworker;

import dev.smartworker.content.WorkerChestBlockEntity;
import dev.smartworker.learning.PreferenceModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class WorkerManager {
	public static final String ENTITY_TAG = "smart_worker";
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final double SEARCH_RADIUS = 16.0;
	private static final double CONTROL_RADIUS_SQUARED = 32.0 * 32.0;
	private static final int CHEST_SEARCH_INTERVAL_TICKS = 100;
	private static final int CHEST_HORIZONTAL_RADIUS = 16;
	private static final int CHEST_VERTICAL_RADIUS = 6;

	private final Map<UUID, WorkerState> workers = new LinkedHashMap<>();
	private final Map<UUID, BlockPos> chestTargets = new LinkedHashMap<>();
	private final Map<UUID, Integer> lastChestSearch = new LinkedHashMap<>();
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

	public void tick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter % SCAN_INTERVAL_TICKS == 0) {
			for (WorkerState state : new ArrayList<>(workers.values())) tickWorker(server, state);
		}
		if (tickCounter % 1200 == 0 && dirty) save(server);
	}

	private void tickWorker(MinecraftServer server, WorkerState state) {
		if (!state.active()) return;
		IronGolem worker = findLoadedWorker(server, state.entityId());
		if (worker == null || !worker.isAlive()) return;

		worker.setTarget(null);
		if (state.isInventoryFull() && tryUnload(worker, state)) return;

		List<ItemEntity> candidates = worker.level().getEntitiesOfClass(
				ItemEntity.class,
				worker.getBoundingBox().inflate(SEARCH_RADIUS),
				item -> item.isAlive() && !item.getItem().isEmpty() && state.canAccept(item.getItem()));

		ItemEntity target = candidates.stream().max(Comparator.comparingDouble(item -> {
			String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
			return PreferenceModel.targetScore(id, worker.distanceToSqr(item), state.collected());
		})).orElse(null);

		if (target == null) {
			worker.getNavigation().stop();
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
		} else {
			worker.getNavigation().moveTo(target, 1.0);
		}
	}

	private boolean tryUnload(IronGolem worker, WorkerState state) {
		BlockPos targetPos = chestTargets.get(state.entityId());
		WorkerChestBlockEntity chest = targetPos == null ? null : loadedChest(worker.level(), targetPos);
		if (chest == null || !chest.hasSpace()) {
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
				worker.getNavigation().stop();
			} else if (!chest.hasSpace()) {
				chestTargets.remove(state.entityId());
				lastChestSearch.remove(state.entityId());
			}
		} else {
			worker.getNavigation().moveTo(targetX, targetY, targetZ, 1.0);
		}
		return true;
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
