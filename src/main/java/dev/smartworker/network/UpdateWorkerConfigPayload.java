package dev.smartworker.network;

import dev.smartworker.SmartWorkerMod;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateWorkerConfigPayload(UUID workerId, boolean miningEnabled, String mineBlocks, String goalItem,
		int goalCount, boolean hasChest, int chestX, int chestY, int chestZ) implements CustomPacketPayload {
	public static final Type<UpdateWorkerConfigPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath(SmartWorkerMod.MOD_ID, "update_worker_config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWorkerConfigPayload> CODEC =
			CustomPacketPayload.codec(UpdateWorkerConfigPayload::write, UpdateWorkerConfigPayload::new);

	private UpdateWorkerConfigPayload(RegistryFriendlyByteBuf buffer) {
		this(buffer.readUUID(), buffer.readBoolean(), buffer.readUtf(4096), buffer.readUtf(128),
				buffer.readVarInt(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(workerId);
		buffer.writeBoolean(miningEnabled);
		buffer.writeUtf(mineBlocks, 4096);
		buffer.writeUtf(goalItem, 128);
		buffer.writeVarInt(goalCount);
		buffer.writeBoolean(hasChest);
		buffer.writeInt(chestX);
		buffer.writeInt(chestY);
		buffer.writeInt(chestZ);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
