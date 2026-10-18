package dev.smartworker.learning;

import java.util.Map;

/** A small, explainable frequency model. Collection counts are the learned state. */
public final class PreferenceModel {
	private PreferenceModel() {}

	public static double targetScore(String itemId, double distanceSquared, Map<String, Long> counts) {
		long observations = Math.max(0L, counts.getOrDefault(itemId, 0L));
		double learnedBonus = Math.log1p(observations) * 4.0;
		return learnedBonus - Math.sqrt(Math.max(0.0, distanceSquared));
	}
}
