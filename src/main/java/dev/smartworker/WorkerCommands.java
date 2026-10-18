package dev.smartworker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Comparator;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class WorkerCommands {
	private WorkerCommands() {}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher, WorkerManager manager) {
		dispatcher.register(Commands.literal("worker")
				.requires(source -> source.getEntity() instanceof ServerPlayer)
				.then(Commands.literal("spawn").executes(context -> spawn(context.getSource(), manager)))
				.then(Commands.literal("start").executes(context -> setRunning(context.getSource(), manager, true)))
				.then(Commands.literal("stop").executes(context -> setRunning(context.getSource(), manager, false)))
				.then(Commands.literal("status").executes(context -> status(context.getSource(), manager)))
				.then(Commands.literal("stats").executes(context -> stats(context.getSource(), manager))));
	}

	private static int spawn(CommandSourceStack source, WorkerManager manager) {
		try {
			WorkerState state = manager.spawn(source.getPlayerOrException());
			source.sendSuccess(() -> Component.literal("Spawned Smart Worker " + shortId(state) + ". Use /worker start to begin."), false);
			return 1;
		} catch (Exception exception) {
			source.sendFailure(Component.literal("Could not spawn worker: " + exception.getMessage()));
			return 0;
		}
	}

	private static int setRunning(CommandSourceStack source, WorkerManager manager, boolean active) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		WorkerState state = manager.nearestOwned(player).orElse(null);
		if (state == null) {
			source.sendFailure(Component.literal("No owned Smart Worker is loaded within 32 blocks."));
			return 0;
		}
		manager.setActive(state, active);
		source.sendSuccess(() -> Component.literal("Smart Worker " + shortId(state) + (active ? " started collecting." : " stopped.")), false);
		return 1;
	}

	private static int status(CommandSourceStack source, WorkerManager manager) throws CommandSyntaxException {
		WorkerState state = manager.nearestOwned(source.getPlayerOrException()).orElse(null);
		if (state == null) {
			source.sendFailure(Component.literal("No owned Smart Worker is loaded within 32 blocks."));
			return 0;
		}
		int occupied = 0;
		int items = 0;
		for (ItemStack stack : state.inventory()) {
			if (!stack.isEmpty()) {
				occupied++;
				items += stack.getCount();
			}
		}
		int finalOccupied = occupied;
		int finalItems = items;
		source.sendSuccess(() -> Component.literal("Smart Worker " + shortId(state) + ": "
				+ (state.active() ? "running" : "stopped") + ", inventory " + finalOccupied + "/"
				+ WorkerState.INVENTORY_SIZE + " slots (" + finalItems + " items)."), false);
		return 1;
	}

	private static int stats(CommandSourceStack source, WorkerManager manager) throws CommandSyntaxException {
		WorkerState state = manager.nearestOwned(source.getPlayerOrException()).orElse(null);
		if (state == null) {
			source.sendFailure(Component.literal("No owned Smart Worker is loaded within 32 blocks."));
			return 0;
		}
		if (state.collected().isEmpty()) {
			source.sendSuccess(() -> Component.literal("Smart Worker " + shortId(state) + " has not collected anything yet."), false);
			return 1;
		}
		String summary = state.collected().entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
				.limit(8)
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.reduce((left, right) -> left + ", " + right)
				.orElse("none");
		source.sendSuccess(() -> Component.literal("Learned preferences (most collected first): " + summary), false);
		return 1;
	}

	private static String shortId(WorkerState state) {
		return state.entityId().toString().substring(0, 8);
	}
}
