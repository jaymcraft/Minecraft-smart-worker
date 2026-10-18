package dev.smartworker;

import java.util.Set;

final class ItemFilter {
	private ItemFilter() {}

	static boolean allows(String itemId, Set<String> whitelist, Set<String> blacklist) {
		return !blacklist.contains(itemId) && (whitelist.isEmpty() || whitelist.contains(itemId));
	}
}
