package dev.smartworker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class WorkerState {
	public static final int INVENTORY_SIZE = 9;
	public static final Set<String> DEFAULT_MINE_BLOCKS = Set.of(
			"minecraft:stone", "minecraft:deepslate",
			"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
			"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
			"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
			"minecraft:gold_ore", "minecraft:deepslate_gold_ore",
			"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
			"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
			"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
			"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
			"minecraft:nether_quartz_ore", "minecraft:nether_gold_ore");

	private final UUID entityId;
	private final UUID ownerId;
	private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private final Map<String, Long> collected = new HashMap<>();
	private final Set<String> whitelist = new HashSet<>();
	private final Set<String> blacklist = new HashSet<>();
	private final Set<String> mineBlocks = new HashSet<>(DEFAULT_MINE_BLOCKS);
	private final List<MiningAreaMemory> miningAreas = new ArrayList<>();
	private final List<DangerAreaMemory> dangerAreas = new ArrayList<>();
	private boolean miningEnabled;
	private String miningGoalItem = "minecraft:raw_iron";
	private int miningGoalCount;
	private int miningGoalProgress;
	private BlockPos assignedChest;
	private boolean active;

	public WorkerState(UUID entityId, UUID ownerId) {
		this.entityId = entityId;
		this.ownerId = ownerId;
	}

	public UUID entityId() { return entityId; }
	public UUID ownerId() { return ownerId; }
	public NonNullList<ItemStack> inventory() { return inventory; }
	public Map<String, Long> collected() { return collected; }
	public Set<String> whitelist() { return whitelist; }
	public Set<String> blacklist() { return blacklist; }
	public Set<String> mineBlocks() { return mineBlocks; }
	public List<MiningAreaMemory> miningAreas() { return miningAreas; }
	public List<DangerAreaMemory> dangerAreas() { return dangerAreas; }
	public boolean miningEnabled() { return miningEnabled; }
	public void setMiningEnabled(boolean miningEnabled) { this.miningEnabled = miningEnabled; }
	public String miningGoalItem() { return miningGoalItem; }
	public void setMiningGoalItem(String miningGoalItem) { this.miningGoalItem = miningGoalItem; }
	public int miningGoalCount() { return miningGoalCount; }
	public void setMiningGoalCount(int miningGoalCount) { this.miningGoalCount = Math.max(0, miningGoalCount); }
	public int miningGoalProgress() { return miningGoalProgress; }
	public void setMiningGoalProgress(int miningGoalProgress) { this.miningGoalProgress = Math.max(0, miningGoalProgress); }
	public BlockPos assignedChest() { return assignedChest; }
	public void setAssignedChest(BlockPos assignedChest) { this.assignedChest = assignedChest == null ? null : assignedChest.immutable(); }
	public boolean active() { return active; }
	public void setActive(boolean active) { this.active = active; }
	public boolean isInventoryEmpty() {
		for (ItemStack stack : inventory) if (!stack.isEmpty()) return false;
		return true;
	}
	public boolean isInventoryFull() {
		for (ItemStack stack : inventory) if (stack.isEmpty()) return false;
		return true;
	}

	public void record(String itemId, int amount) {
		if (amount > 0) collected.merge(itemId, (long) amount, Long::sum);
	}

	public void recordMinedGoal(String itemId, int amount) {
		if (amount > 0 && miningGoalItem.equals(itemId)) miningGoalProgress += amount;
	}

	public boolean miningGoalReached() {
		return miningGoalCount > 0 && miningGoalProgress >= miningGoalCount;
	}

	public void rememberResource(String dimension, String resource, BlockPos pos) {
		MiningAreaMemory found = MiningAreaMemory.at(dimension, resource, pos);
		for (int i = 0; i < miningAreas.size(); i++) {
			if (miningAreas.get(i).sameArea(found)) {
				miningAreas.set(i, miningAreas.get(i).incremented());
				return;
			}
		}
		if (miningAreas.size() >= 128) miningAreas.remove(0);
		miningAreas.add(found);
	}

	public int rememberedFinds(String dimension, String resource, BlockPos pos) {
		MiningAreaMemory wanted = MiningAreaMemory.at(dimension, resource, pos);
		return miningAreas.stream().filter(memory -> memory.sameArea(wanted)).mapToInt(MiningAreaMemory::finds).sum();
	}

	public void rememberDanger(String dimension, BlockPos pos) {
		DangerAreaMemory danger = DangerAreaMemory.at(dimension, pos);
		if (dangerAreas.contains(danger)) return;
		if (dangerAreas.size() >= 64) dangerAreas.remove(0);
		dangerAreas.add(danger);
	}

	public boolean isDangerous(String dimension, BlockPos pos) {
		return dangerAreas.stream().anyMatch(danger -> danger.contains(dimension, pos));
	}

	/** Inserts as much as possible and returns the number inserted. */
	public int insert(ItemStack offered) {
		if (offered.isEmpty()) return 0;
		int remaining = offered.getCount();

		for (ItemStack slot : inventory) {
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, offered)) {
				int moved = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
				if (moved > 0) {
					slot.grow(moved);
					remaining -= moved;
				}
				if (remaining == 0) return offered.getCount();
			}
		}

		for (int i = 0; i < inventory.size() && remaining > 0; i++) {
			if (inventory.get(i).isEmpty()) {
				int moved = Math.min(remaining, offered.getMaxStackSize());
				ItemStack placed = offered.copy();
				placed.setCount(moved);
				inventory.set(i, placed);
				remaining -= moved;
			}
		}
		return offered.getCount() - remaining;
	}

	public boolean canAccept(ItemStack stack) {
		if (stack.isEmpty()) return false;
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (!ItemFilter.allows(itemId, whitelist, blacklist)) return false;
		for (ItemStack slot : inventory) {
			if (slot.isEmpty()) return true;
			if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) return true;
		}
		return false;
	}
}
