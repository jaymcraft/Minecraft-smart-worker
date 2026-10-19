package dev.smartworker;

import net.minecraft.core.BlockPos;

public record MiningAreaMemory(String dimension, String resource, int areaX, int areaY, int areaZ, int finds) {
	public static MiningAreaMemory at(String dimension, String resource, BlockPos pos) {
		return new MiningAreaMemory(dimension, resource, Math.floorDiv(pos.getX(), 16),
				Math.floorDiv(pos.getY(), 16), Math.floorDiv(pos.getZ(), 16), 1);
	}

	public boolean sameArea(MiningAreaMemory other) {
		return dimension.equals(other.dimension) && resource.equals(other.resource)
				&& areaX == other.areaX && areaY == other.areaY && areaZ == other.areaZ;
	}

	public BlockPos center() {
		return new BlockPos(areaX * 16 + 8, areaY * 16 + 8, areaZ * 16 + 8);
	}

	public MiningAreaMemory incremented() {
		return new MiningAreaMemory(dimension, resource, areaX, areaY, areaZ, Math.min(10_000, finds + 1));
	}
}
