package dev.smartworker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
				.then(Commands.literal("stats").executes(context -> stats(context.getSource(), manager)))
				.then(Commands.literal("filters").executes(context -> filters(context.getSource(), manager)))
				.then(filterCommand("whitelist", true, manager))
				.then(filterCommand("blacklist", false, manager)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> filterCommand(
			String name, boolean whitelist, WorkerManager manager) {
		return Commands.literal(name)
				.then(Commands.literal("add")
						.then(Commands.argument("item", IdentifierArgument.id())
								.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
										BuiltInRegistries.ITEM.keySet(), builder))
								.executes(context -> updateFilter(context.getSource(), manager, whitelist, true,
										IdentifierArgument.getId(context, "item")))))
				.then(Commands.literal("remove")
						.then(Commands.argument("item", IdentifierArgument.id())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										filterValues(context.getSource(), manager, whitelist), builder))
								.executes(context -> updateFilter(context.getSource(), manager, whitelist, false,
										IdentifierArgument.getId(context, "item")))))
				.then(Commands.literal("clear").executes(context -> clearFilter(context.getSource(), manager, whitelist)));
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

	private static int filters(CommandSourceStack source, WorkerManager manager) throws CommandSyntaxException {
		WorkerState state = ownedWorker(source, manager);
		if (state == null) return 0;
		source.sendSuccess(() -> Component.literal("Whitelist: " + describe(state.whitelist())
				+ "; blacklist: " + describe(state.blacklist()) + ". Blacklist takes priority."), false);
		return 1;
	}

	private static int updateFilter(CommandSourceStack source, WorkerManager manager, boolean whitelist,
			boolean add, Identifier itemId) throws CommandSyntaxException {
		WorkerState state = ownedWorker(source, manager);
		if (state == null) return 0;
		if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
			source.sendFailure(Component.literal("Unknown item ID: " + itemId));
			return 0;
		}

		String normalized = itemId.toString();
		Set<String> selected = whitelist ? state.whitelist() : state.blacklist();
		boolean changed = add ? selected.add(normalized) : selected.remove(normalized);
		if (add) {
			// Keep configuration unambiguous when moving an item from one list to the other.
			changed |= (whitelist ? state.blacklist() : state.whitelist()).remove(normalized);
		}
		if (changed) manager.filtersChanged();
		boolean wasChanged = changed;
		String operation = add ? "Added " : "Removed ";
		String preposition = add ? " to " : " from ";
		String listName = whitelist ? "whitelist" : "blacklist";
		source.sendSuccess(() -> Component.literal(wasChanged
				? operation + normalized + preposition + listName + "."
				: normalized + " was already " + (add ? "in" : "absent from") + " the " + listName + "."), false);
		return wasChanged ? 1 : 0;
	}

	private static int clearFilter(CommandSourceStack source, WorkerManager manager, boolean whitelist)
			throws CommandSyntaxException {
		WorkerState state = ownedWorker(source, manager);
		if (state == null) return 0;
		Set<String> selected = whitelist ? state.whitelist() : state.blacklist();
		int removed = selected.size();
		selected.clear();
		manager.filtersChanged();
		String listName = whitelist ? "whitelist" : "blacklist";
		source.sendSuccess(() -> Component.literal("Cleared " + removed + " item(s) from the " + listName + "."), false);
		return 1;
	}

	private static Iterable<String> filterValues(CommandSourceStack source, WorkerManager manager, boolean whitelist) {
		try {
			WorkerState state = manager.nearestOwned(source.getPlayerOrException()).orElse(null);
			return state == null ? List.of() : whitelist ? state.whitelist() : state.blacklist();
		} catch (CommandSyntaxException ignored) {
			return List.of();
		}
	}

	private static WorkerState ownedWorker(CommandSourceStack source, WorkerManager manager) throws CommandSyntaxException {
		WorkerState state = manager.nearestOwned(source.getPlayerOrException()).orElse(null);
		if (state == null) source.sendFailure(Component.literal("No owned Smart Worker is loaded within 32 blocks."));
		return state;
	}

	private static String describe(Set<String> values) {
		return values.isEmpty() ? "empty (no restriction)" : values.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
	}

	private static String shortId(WorkerState state) {
		return state.entityId().toString().substring(0, 8);
	}
}
