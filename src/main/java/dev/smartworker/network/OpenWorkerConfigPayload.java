package dev.smartworker.network;

import dev.smartworker.SmartWorkerMod;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenWorkerConfigPayload(UUID workerId, boolean miningEnabled, String mineBlocks, String goalItem,
		int goalCount, int goalProgress, boolean hasChest, int chestX, int chestY, int chestZ,
		int rememberedAreas, int dangerAreas) implements CustomPacketPayload {
	public static final Type<OpenWorkerConfigPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(SmartWorkerMod.MOD_ID, "open_worker_config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenWorkerConfigPayload> CODEC =
			CustomPacketPayload.codec(OpenWorkerConfigPayload::write, OpenWorkerConfigPayload::new);

	private OpenWorkerConfigPayload(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUUID(), buffer.readBoolean(), buffer.readUtf(4096), buffer.readUtf(128),
				buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(),
				buffer.readInt(), buffer.readVarInt(), buffer.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(workerId);
		buffer.writeBoolean(miningEnabled);
		buffer.writeUtf(mineBlocks, 4096);
		buffer.writeUtf(goalItem, 128);
		buffer.writeVarInt(goalCount);
		buffer.writeVarInt(goalProgress);
		buffer.writeBoolean(hasChest);
		buffer.writeInt(chestX);
		buffer.writeInt(chestY);
		buffer.writeInt(chestZ);
		buffer.writeVarInt(rememberedAreas);
		buffer.writeVarInt(dangerAreas);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
