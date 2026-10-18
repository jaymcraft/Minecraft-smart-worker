package dev.smartworker.learning;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PreferenceModelTest {
	@Test
	void repeatedCollectionsIncreasePriority() {
		double unknown = PreferenceModel.targetScore("minecraft:oak_log", 16.0, Map.of());
		double learned = PreferenceModel.targetScore("minecraft:oak_log", 16.0, Map.of("minecraft:oak_log", 20L));
		assertTrue(learned > unknown);
	}

	@Test
	void distanceStillMatters() {
		Map<String, Long> counts = Map.of("minecraft:stone", 4L);
		assertTrue(PreferenceModel.targetScore("minecraft:stone", 4.0, counts)
				> PreferenceModel.targetScore("minecraft:stone", 100.0, counts));
	}
}
