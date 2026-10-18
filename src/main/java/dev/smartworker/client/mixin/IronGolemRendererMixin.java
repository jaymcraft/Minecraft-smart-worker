package dev.smartworker.client.mixin;

import dev.smartworker.SmartWorkerMod;
import dev.smartworker.WorkerManager;
import dev.smartworker.client.SmartWorkerRenderState;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolemRenderer.class)
public abstract class IronGolemRendererMixin {
	private static final Identifier SMART_WORKER_TEXTURE =
			Identifier.fromNamespaceAndPath(SmartWorkerMod.MOD_ID, "textures/entity/robot_worker.png");

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/golem/IronGolem;Lnet/minecraft/client/renderer/entity/state/IronGolemRenderState;F)V", at = @At("TAIL"))
	private void smartWorker$copyWorkerFlag(IronGolem entity, IronGolemRenderState state, float tickDelta, CallbackInfo ci) {
		boolean tagged = entity.entityTags().contains(WorkerManager.ENTITY_TAG);
		boolean named = entity.hasCustomName() && "Smart Worker".equals(entity.getCustomName().getString());
		((SmartWorkerRenderState) state).smartWorker$setWorker(tagged || named);
	}

	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/IronGolemRenderState;)Lnet/minecraft/resources/Identifier;", at = @At("HEAD"), cancellable = true)
	private void smartWorker$useRobotTexture(IronGolemRenderState state, CallbackInfoReturnable<Identifier> cir) {
		if (((SmartWorkerRenderState) state).smartWorker$isWorker()) {
			cir.setReturnValue(SMART_WORKER_TEXTURE);
		}
	}
}
