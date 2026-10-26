package main.java.testsmell.plugin.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the results of the most recently completed detection run.
 *
 * A run builds its results in a local list and publishes them here in a single
 * assignment once it has finished. Readers therefore always see either the
 * previous complete set of results or the new one, never a list that is still
 * being built.
 *
 * This replaces the earlier cursor-based collection, which was mutated while a
 * run was in progress and so could not be read safely from another thread.
 */
public final class TSmellResults {

	private static volatile List<TSmellDetection> current = Collections.emptyList();

	private TSmellResults() {
	}

	/**
	 * Publishes the results of a completed run.
	 *
	 * @param detections the run's detections; copied defensively
	 */
	public static void publish(List<TSmellDetection> detections) {
		if (detections == null) {
			current = Collections.emptyList();
			return;
		}
		current = Collections.unmodifiableList(new ArrayList<TSmellDetection>(detections));
	}

	/**
	 * The most recently published results.
	 *
	 * @return an unmodifiable list; empty if no run has completed
	 */
	public static List<TSmellDetection> get() {
		return current;
	}

	/**
	 * Discards any published results.
	 */
	public static void clear() {
		current = Collections.emptyList();
	}
}
