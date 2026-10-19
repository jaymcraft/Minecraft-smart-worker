package dev.smartworker;

import dev.smartworker.content.ModContent;
import dev.smartworker.network.WorkerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartWorkerMod implements ModInitializer {
	public static final String MOD_ID = "smart_worker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final WorkerManager WORKERS = new WorkerManager();

	@Override
	public void onInitialize() {
		ModContent.initialize();
		WorkerNetworking.initialize(WORKERS);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				WorkerCommands.register(dispatcher, WORKERS));
		ServerLifecycleEvents.SERVER_STARTED.register(WORKERS::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(WORKERS::save);
		ServerTickEvents.END_SERVER_TICK.register(WORKERS::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			WorkerState state = WORKERS.stateForEntity(entity.getUUID()).orElse(null);
			if (state == null) return InteractionResult.PASS;
			if (!state.ownerId().equals(serverPlayer.getUUID())) {
				serverPlayer.sendOverlayMessage(Component.literal("Only this Smart Worker's owner can control it."));
				return InteractionResult.FAIL;
			}
			WORKERS.setActive(state, !state.active());
			serverPlayer.sendOverlayMessage(Component.literal("Smart Worker " + (state.active() ? "started." : "stopped.")));
			return InteractionResult.SUCCESS_SERVER;
		});
		LOGGER.info("Smart Worker initialized");
	}
}
