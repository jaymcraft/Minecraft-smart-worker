package dev.smartworker.client;

import dev.smartworker.network.OpenWorkerConfigPayload;
import dev.smartworker.network.UpdateWorkerConfigPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

final class WorkerConfigScreen extends Screen {
	private final OpenWorkerConfigPayload config;
	private boolean miningEnabled;
	private boolean hasChest;
	private EditBox blocks;
	private EditBox goalItem;
	private EditBox goalCount;
	private EditBox chestX;
	private EditBox chestY;
	private EditBox chestZ;
	private Button miningToggle;
	private Button chestToggle;
	private String error = "";

	WorkerConfigScreen(OpenWorkerConfigPayload config) {
		super(Component.literal("Smart Worker Mining Configuration"));
		this.config = config;
		this.miningEnabled = config.miningEnabled();
		this.hasChest = config.hasChest();
	}

	@Override
	protected void init() {
		int left = width / 2 - 180;
		miningToggle = addRenderableWidget(Button.builder(miningLabel(), button -> {
			miningEnabled = !miningEnabled;
			button.setMessage(miningLabel());
		}).bounds(left, 32, 175, 20).build());

		blocks = input(left, 72, 360, config.mineBlocks(), 4096, "Comma-separated block IDs");
		goalItem = input(left, 111, 225, config.goalItem(), 128, "minecraft:raw_iron");
		goalCount = input(left + 235, 111, 125, Integer.toString(config.goalCount()), 6, "0 = unlimited");

		chestX = input(left, 150, 70, config.hasChest() ? Integer.toString(config.chestX()) : "", 12, "X");
		chestY = input(left + 75, 150, 70, config.hasChest() ? Integer.toString(config.chestY()) : "", 12, "Y");
		chestZ = input(left + 150, 150, 70, config.hasChest() ? Integer.toString(config.chestZ()) : "", 12, "Z");
		chestToggle = addRenderableWidget(Button.builder(chestLabel(), button -> {
			hasChest = !hasChest;
			button.setMessage(chestLabel());
		}).bounds(left + 230, 150, 130, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Use targeted block"), button -> useTargetedBlock())
				.bounds(left, 178, 175, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save configuration"), button -> save())
				.bounds(left, 214, 175, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(left + 185, 214, 175, 20).build());
	}

	private EditBox input(int x, int y, int width, String value, int maxLength, String hint) {
		EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
		box.setMaxLength(maxLength);
		box.setValue(value);
		box.setHint(Component.literal(hint));
		return addRenderableWidget(box);
	}

	private Component miningLabel() {
		return Component.literal("Automatic mining: " + (miningEnabled ? "ON" : "OFF"));
	}

	private Component chestLabel() {
		return Component.literal(hasChest ? "Chest: configured" : "Chest: auto-find");
	}

	private void useTargetedBlock() {
		if (!(minecraft.hitResult instanceof BlockHitResult hit)) {
			error = "Look at a Worker Chest first.";
			return;
		}
		BlockPos pos = hit.getBlockPos();
		chestX.setValue(Integer.toString(pos.getX()));
		chestY.setValue(Integer.toString(pos.getY()));
		chestZ.setValue(Integer.toString(pos.getZ()));
		hasChest = true;
		chestToggle.setMessage(chestLabel());
		error = "Chest target copied; the server will verify it when saved.";
	}

	private void save() {
		try {
			int count = Integer.parseInt(goalCount.getValue().trim());
			if (count < 0 || count > 100_000) throw new NumberFormatException();
			int x = hasChest ? Integer.parseInt(chestX.getValue().trim()) : 0;
			int y = hasChest ? Integer.parseInt(chestY.getValue().trim()) : 0;
			int z = hasChest ? Integer.parseInt(chestZ.getValue().trim()) : 0;
			ClientPlayNetworking.send(new UpdateWorkerConfigPayload(config.workerId(), miningEnabled,
					blocks.getValue(), goalItem.getValue(), count, hasChest, x, y, z));
			onClose();
		} catch (NumberFormatException exception) {
			error = "Goal count and chest coordinates must be whole numbers.";
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = width / 2 - 180;
		graphics.text(font, title, left, 15, 0xFFFFFF, true);
		graphics.text(font, "Blocks to mine", left, 60, 0xA0A0A0, false);
		graphics.text(font, "Goal item", left, 99, 0xA0A0A0, false);
		graphics.text(font, "Stop after (0 = unlimited)", left + 235, 99, 0xA0A0A0, false);
		graphics.text(font, "Worker Chest coordinates", left, 138, 0xA0A0A0, false);
		graphics.text(font, "Progress: " + config.goalProgress() + "/"
				+ (config.goalCount() == 0 ? "unlimited" : config.goalCount())
				+ " | remembered ore areas: " + config.rememberedAreas()
				+ " | danger areas: " + config.dangerAreas(), left, 190, 0xB0B0B0, false);
		if (!error.isEmpty()) graphics.text(font, error, left, 202, 0xFF6060, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
