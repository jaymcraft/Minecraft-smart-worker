package dev.smartworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class MiningMemoryTest {
	@Test
	void resourceMemoryGroupsFindsIntoSixteenBlockAreas() {
		MiningAreaMemory first = MiningAreaMemory.at("minecraft:overworld", "minecraft:raw_iron",
				new BlockPos(17, -20, 31));
		MiningAreaMemory nearby = MiningAreaMemory.at("minecraft:overworld", "minecraft:raw_iron",
				new BlockPos(30, -17, 16));
		assertTrue(first.sameArea(nearby));
		assertEquals(2, first.incremented().finds());
		assertEquals(new BlockPos(24, -24, 24), first.center());
	}

	@Test
	void dangerMemoryMatchesOnlyItsEightBlockAreaAndDimension() {
		DangerAreaMemory danger = DangerAreaMemory.at("minecraft:overworld", new BlockPos(-1, 10, 15));
		assertTrue(danger.contains("minecraft:overworld", new BlockPos(-8, 15, 8)));
		assertFalse(danger.contains("minecraft:overworld", new BlockPos(0, 15, 8)));
		assertFalse(danger.contains("minecraft:the_nether", new BlockPos(-1, 10, 15)));
	}
}
