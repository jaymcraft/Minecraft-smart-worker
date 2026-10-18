package dev.smartworker.client.mixin;

import dev.smartworker.client.SmartWorkerRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IronGolemRenderState.class)
public abstract class IronGolemRenderStateMixin implements SmartWorkerRenderState {
	@Unique
	private boolean smartWorker$worker;

	@Override
	public boolean smartWorker$isWorker() {
		return smartWorker$worker;
	}

	@Override
	public void smartWorker$setWorker(boolean worker) {
		smartWorker$worker = worker;
	}
}
