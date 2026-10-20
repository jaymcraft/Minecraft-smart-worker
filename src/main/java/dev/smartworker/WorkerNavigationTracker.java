package dev.smartworker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/** Tracks navigation progress and temporarily avoids destinations a worker cannot reach. */
final class WorkerNavigationTracker {
	static final int STUCK_TICKS = 100;
	static final int AVOID_TICKS = 600;
	private static final double MIN_PROGRESS = 0.5;

	enum Kind { ITEM, BLOCK, MEMORY, CHEST }

	record Destination(Kind kind, String dimension, Object id) {
		static Destination item(String dimension, UUID id) {
			return new Destination(Kind.ITEM, dimension, id);
		}

		static Destination block(String dimension, BlockPos pos) {
			return new Destination(Kind.BLOCK, dimension, pos.immutable());
		}

		static Destination memory(String dimension, BlockPos pos) {
			return new Destination(Kind.MEMORY, dimension, pos.immutable());
		}

		static Destination chest(String dimension, BlockPos pos) {
			return new Destination(Kind.CHEST, dimension, pos.immutable());
		}
	}

	private record Progress(Destination destination, double distance, int lastProgressTick) {}

	private final Map<UUID, Progress> progress = new HashMap<>();
	private final Map<UUID, Map<Destination, Integer>> avoided = new HashMap<>();

	boolean isAvoided(UUID workerId, Destination destination, int tick) {
		Map<Destination, Integer> workerAvoided = avoided.get(workerId);
		if (workerAvoided == null) return false;
		Integer expiresAt = workerAvoided.get(destination);
		if (expiresAt != null && expiresAt <= tick) workerAvoided.remove(destination);
		if (workerAvoided.isEmpty()) {
			avoided.remove(workerId);
			return false;
		}
		return expiresAt != null && expiresAt > tick;
	}

	/** Returns false when the destination has timed out or is still cooling down. */
	boolean observe(UUID workerId, Destination destination, double distance, int tick) {
		if (isAvoided(workerId, destination, tick)) return false;
		Progress previous = progress.get(workerId);
		if (previous == null || !previous.destination().equals(destination)) {
			progress.put(workerId, new Progress(destination, distance, tick));
			return true;
		}
		if (previous.distance() - distance >= MIN_PROGRESS) {
			progress.put(workerId, new Progress(destination, distance, tick));
			return true;
		}
		if (tick - previous.lastProgressTick() >= STUCK_TICKS) {
			avoid(workerId, destination, tick);
			return false;
		}
		return true;
	}

	void avoid(UUID workerId, Destination destination, int tick) {
		avoided.computeIfAbsent(workerId, ignored -> new HashMap<>())
				.put(destination, tick + AVOID_TICKS);
		progress.remove(workerId);
	}

	void clearProgress(UUID workerId) {
		progress.remove(workerId);
	}

	void pruneExpired(int tick) {
		avoided.values().forEach(destinations ->
				destinations.entrySet().removeIf(entry -> entry.getValue() <= tick));
		avoided.values().removeIf(Map::isEmpty);
	}

	void clearAll() {
		progress.clear();
		avoided.clear();
	}
}
