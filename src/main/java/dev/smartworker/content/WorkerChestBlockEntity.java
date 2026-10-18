package dev.smartworker.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class WorkerChestBlockEntity extends BaseContainerBlockEntity {
	public static final int SIZE = 27;
	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public WorkerChestBlockEntity(BlockPos pos, BlockState state) {
		super(ModContent.WORKER_CHEST_BLOCK_ENTITY, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.smart_worker.worker_chest");
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return ChestMenu.threeRows(containerId, inventory, this);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items);
		super.saveAdditional(output);
	}

	/** Moves items directly and returns the number transferred. Source stacks are shrunk in place. */
	public int insertAll(NonNullList<ItemStack> source) {
		int movedTotal = 0;
		for (ItemStack sourceStack : source) {
			if (sourceStack.isEmpty()) continue;

			for (ItemStack slot : items) {
				if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, sourceStack)) {
					int moved = Math.min(sourceStack.getCount(), slot.getMaxStackSize() - slot.getCount());
					if (moved > 0) {
						slot.grow(moved);
						sourceStack.shrink(moved);
						movedTotal += moved;
					}
					if (sourceStack.isEmpty()) break;
				}
			}

			for (int i = 0; i < items.size() && !sourceStack.isEmpty(); i++) {
				if (items.get(i).isEmpty()) {
					int moved = Math.min(sourceStack.getCount(), sourceStack.getMaxStackSize());
					ItemStack inserted = sourceStack.copy();
					inserted.setCount(moved);
					items.set(i, inserted);
					sourceStack.shrink(moved);
					movedTotal += moved;
				}
			}
		}
		if (movedTotal > 0) setChanged();
		return movedTotal;
	}

	public boolean hasSpace() {
		for (ItemStack stack : items) {
			if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return true;
		}
		return false;
	}
}
