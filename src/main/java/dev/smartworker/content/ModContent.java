package dev.smartworker.content;

import dev.smartworker.SmartWorkerMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.Registry;

public final class ModContent {
	private static final Identifier WORKER_CHEST_ID = Identifier.fromNamespaceAndPath(SmartWorkerMod.MOD_ID, "worker_chest");
	private static final BlockItemId WORKER_CHEST_KEYS = BlockItemId.create(WORKER_CHEST_ID, WORKER_CHEST_ID);

	public static final WorkerChestBlock WORKER_CHEST = Registry.register(
			BuiltInRegistries.BLOCK,
			WORKER_CHEST_KEYS.block(),
			new WorkerChestBlock(BlockBehaviour.Properties.of()
					.setId(WORKER_CHEST_KEYS.block())
					.strength(3.5F)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops()));

	public static final BlockItem WORKER_CHEST_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			WORKER_CHEST_KEYS.item(),
			new BlockItem(WORKER_CHEST, new Item.Properties()
					.setId(WORKER_CHEST_KEYS.item())
					.useBlockDescriptionPrefix()));

	public static final BlockEntityType<WorkerChestBlockEntity> WORKER_CHEST_BLOCK_ENTITY = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			WORKER_CHEST_ID,
			FabricBlockEntityTypeBuilder.create(WorkerChestBlockEntity::new, WORKER_CHEST).build());

	private ModContent() {}

	public static void initialize() {
		ResourceKey<CreativeModeTab> functionalBlocks = ResourceKey.create(
				Registries.CREATIVE_MODE_TAB,
				Identifier.withDefaultNamespace("functional_blocks"));
		CreativeModeTabEvents.modifyOutputEvent(functionalBlocks).register(entries -> entries.accept(WORKER_CHEST_ITEM));
	}
}
