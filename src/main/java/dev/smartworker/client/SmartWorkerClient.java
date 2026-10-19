package dev.smartworker.client;

import dev.smartworker.network.OpenWorkerConfigPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SmartWorkerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(OpenWorkerConfigPayload.TYPE,
				(payload, context) -> context.client().setScreenAndShow(new WorkerConfigScreen(payload)));
	}
}
