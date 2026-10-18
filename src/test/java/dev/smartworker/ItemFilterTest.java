package dev.smartworker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;

import java.util.Set;

import net.minecraft.commands.arguments.IdentifierArgument;
import org.junit.jupiter.api.Test;

class ItemFilterTest {
	@Test
	void itemArgumentAcceptsNamespacedIds() throws Exception {
		String input = "minecraft:cobblestone";
		StringReader reader = new StringReader(input);
		assertEquals(input, IdentifierArgument.id().parse(reader).toString());
		assertEquals(input.length(), reader.getCursor());
	}

	@Test
	void emptyWhitelistAcceptsAnythingNotBlacklisted() {
		assertTrue(ItemFilter.allows("minecraft:oak_log", Set.of(), Set.of()));
		assertFalse(ItemFilter.allows("minecraft:cobblestone", Set.of(), Set.of("minecraft:cobblestone")));
	}

	@Test
	void populatedWhitelistRejectsItemsNotListed() {
		Set<String> whitelist = Set.of("minecraft:oak_log");
		assertTrue(ItemFilter.allows("minecraft:oak_log", whitelist, Set.of()));
		assertFalse(ItemFilter.allows("minecraft:stone", whitelist, Set.of()));
	}

	@Test
	void blacklistWinsIfAnItemAppearsInBothLists() {
		assertFalse(ItemFilter.allows("minecraft:oak_log", Set.of("minecraft:oak_log"), Set.of("minecraft:oak_log")));
	}
}
