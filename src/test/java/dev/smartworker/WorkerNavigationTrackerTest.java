package dev.smartworker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class WorkerNavigationTrackerTest {
	@Test
	void timesOutAfterFiveSecondsAndRetriesAfterCooldown() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var target = WorkerNavigationTracker.Destination.item("minecraft:overworld", UUID.randomUUID());

		assertTrue(tracker.observe(worker, target, 10.0, 0));
		assertTrue(tracker.observe(worker, target, 9.8, 90));
		assertFalse(tracker.observe(worker, target, 9.8, 100));
		assertTrue(tracker.isAvoided(worker, target, 699));
		assertFalse(tracker.isAvoided(worker, target, 700));
	}

	@Test
	void failedPathIsAvoidedImmediately() {
		WorkerNavigationTracker tracker = new WorkerNavigationTracker();
		UUID worker = UUID.randomUUID();
		var target = WorkerNavigationTracker.Destination.item("minecraft:overworld", UUID.randomUUID());

		tracker.avoid(worker, target, 20);
		assertTrue(tracker.isAvoided(worker, target, 619));
		assertFalse(tracker.isAvoided(worker, target, 620));
	}
}
