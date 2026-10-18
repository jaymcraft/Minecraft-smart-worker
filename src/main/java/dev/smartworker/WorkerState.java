package dev.smartworker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public final class WorkerState {
	public static final int INVENTORY_SIZE = 9;

	private final UUID entityId;
	private final UUID ownerId;
	private final NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
	private final Map<String, Long> collected = new HashMap<>();
	private boolean active;

	public WorkerState(UUID entityId, UUID ownerId) {
		this.entityId = entityId;
		this.ownerId = ownerId;
	}

	public UUID entityId() { return entityId; }
	public UUID ownerId() { return ownerId; }
	public NonNullList<ItemStack> inventory() { return inventory; }
	public Map<String, Long> collected() { return collected; }
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
		for (ItemStack slot : inventory) {
			if (slot.isEmpty()) return true;
			if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) return true;
		}
		return false;
	}
}
