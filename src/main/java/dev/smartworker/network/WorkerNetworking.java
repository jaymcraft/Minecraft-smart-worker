package dev.smartworker.network;

import dev.smartworker.WorkerManager;
import dev.smartworker.WorkerState;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class WorkerNetworking {
	private WorkerNetworking() {}

	public static void initialize(WorkerManager manager) {
		PayloadTypeRegistry.clientboundPlay().register(OpenWorkerConfigPayload.TYPE, OpenWorkerConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UpdateWorkerConfigPayload.TYPE, UpdateWorkerConfigPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UpdateWorkerConfigPayload.TYPE,
				(payload, context) -> manager.applyMiningConfig(context.player(), payload));
	}

	public static boolean openConfig(ServerPlayer player, WorkerState state) {
		if (!ServerPlayNetworking.canSend(player, OpenWorkerConfigPayload.TYPE)) return false;
		BlockPos chest = state.assignedChest();
		ServerPlayNetworking.send(player, new OpenWorkerConfigPayload(state.entityId(), state.miningEnabled(),
				String.join(",", state.mineBlocks().stream().sorted().toList()), state.miningGoalItem(),
				state.miningGoalCount(), state.miningGoalProgress(), chest != null,
				chest == null ? 0 : chest.getX(), chest == null ? 0 : chest.getY(), chest == null ? 0 : chest.getZ(),
				state.miningAreas().size(), state.dangerAreas().size()));
		return true;
	}
}
