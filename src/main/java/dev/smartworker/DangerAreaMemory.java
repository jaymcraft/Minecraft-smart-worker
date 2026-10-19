package dev.smartworker;

import net.minecraft.core.BlockPos;

public record DangerAreaMemory(String dimension, int areaX, int areaY, int areaZ) {
	public static DangerAreaMemory at(String dimension, BlockPos pos) {
		return new DangerAreaMemory(dimension, Math.floorDiv(pos.getX(), 8),
				Math.floorDiv(pos.getY(), 8), Math.floorDiv(pos.getZ(), 8));
	}

	public boolean contains(String candidateDimension, BlockPos pos) {
		return dimension.equals(candidateDimension) && areaX == Math.floorDiv(pos.getX(), 8)
				&& areaY == Math.floorDiv(pos.getY(), 8) && areaZ == Math.floorDiv(pos.getZ(), 8);
	}
}
