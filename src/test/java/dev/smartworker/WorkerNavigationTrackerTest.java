package dev.smartworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WorkerNavigationTrackerTest {
	private static final String DIMENSION = "minecraft:overworld";

	@Test
	void timesOutAfterFiveSecondsWithoutProgressAndRetriesAfterCooldown() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var target = WorkerNavigationTracker.Destination.item(DIMENSION, UUID.randomUUID());

		assertTrue(tracker.observe(worker, target, 10.0, 0));
		assertTrue(tracker.observe(worker, target, 9.8, 90));
		assertFalse(tracker.observe(worker, target, 9.8, 100));
		assertTrue(tracker.isAvoided(worker, target, 699));
		assertFalse(tracker.isAvoided(worker, target, 700));
		assertTrue(tracker.observe(worker, target, 9.8, 700));
	}

	@Test
	void progressAndTargetChangesRestartTheTimer() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var first = WorkerNavigationTracker.Destination.block(DIMENSION, new BlockPos(5, 64, 5));
		var second = WorkerNavigationTracker.Destination.block(DIMENSION, new BlockPos(6, 64, 5));

		assertTrue(tracker.observe(worker, first, 10.0, 0));
		assertTrue(tracker.observe(worker, first, 9.4, 90));
		assertTrue(tracker.observe(worker, first, 9.2, 150));
		assertTrue(tracker.observe(worker, second, 8.0, 180));
		assertTrue(tracker.observe(worker, second, 8.0, 279));
		assertFalse(tracker.observe(worker, second, 8.0, 280));
		assertFalse(tracker.isAvoided(worker, first, 280));
	}

	@Test
	void avoidedTargetIsSkippedWhileAnotherCanBeSelected() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var blocked = WorkerNavigationTracker.Destination.chest(DIMENSION, new BlockPos(1, 64, 1));
		var available = WorkerNavigationTracker.Destination.chest(DIMENSION, new BlockPos(2, 64, 1));

		assertTrue(tracker.observe(worker, blocked, 12.0, 0));
		assertFalse(tracker.observe(worker, blocked, 12.0, 100));
		var next = List.of(blocked, available).stream()
				.filter(destination -> !tracker.isAvoided(worker, destination, 110))
				.findFirst().orElseThrow();
		assertEquals(available, next);
		assertTrue(tracker.observe(worker, available, 11.0, 110));
	}

	@Test
	void clearingProgressDoesNotClearCooldown() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var target = WorkerNavigationTracker.Destination.memory(DIMENSION, new BlockPos(8, 64, 8));
		assertTrue(tracker.observe(worker, target, 20.0, 0));
		assertFalse(tracker.observe(worker, target, 20.0, 100));
		tracker.clearProgress(worker);
		assertTrue(tracker.isAvoided(worker, target, 101));
	}

	@Test
	void failedPathIsAvoidedImmediately() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var target = WorkerNavigationTracker.Destination.item(DIMENSION, UUID.randomUUID());

		tracker.avoid(worker, target, 20);
		assertTrue(tracker.isAvoided(worker, target, 619));
		assertFalse(tracker.isAvoided(worker, target, 620));
	}
}
