package dev.smartworker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

final class WorkerPersistence {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "smart_workers.json";

	private WorkerPersistence() {}

	static void save(MinecraftServer server, Iterable<WorkerState> workers) throws IOException {
		DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		JsonArray workerArray = new JsonArray();

		for (WorkerState state : workers) {
			JsonObject object = new JsonObject();
			object.addProperty("entity", state.entityId().toString());
			object.addProperty("owner", state.ownerId().toString());
			object.addProperty("active", state.active());

			JsonArray inventory = new JsonArray();
			for (ItemStack stack : state.inventory()) {
				if (stack.isEmpty()) {
					inventory.add(new JsonObject());
				} else {
					inventory.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
				}
			}
			object.add("inventory", inventory);

			JsonObject collected = new JsonObject();
			state.collected().forEach(collected::addProperty);
			object.add("collected", collected);
			workerArray.add(object);
		}
		root.add("workers", workerArray);

		Path destination = file(server);
		Path temporary = destination.resolveSibling(FILE_NAME + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary)) {
			GSON.toJson(root, writer);
		}
		try {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	static void load(MinecraftServer server, Map<UUID, WorkerState> target) throws IOException {
		Path path = file(server);
		if (!Files.exists(path)) return;
		DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (JsonElement element : root.getAsJsonArray("workers")) {
				JsonObject object = element.getAsJsonObject();
				UUID entityId = UUID.fromString(object.get("entity").getAsString());
				UUID ownerId = UUID.fromString(object.get("owner").getAsString());
				WorkerState state = new WorkerState(entityId, ownerId);
				state.setActive(object.get("active").getAsBoolean());

				JsonArray inventory = object.getAsJsonArray("inventory");
				for (int i = 0; i < Math.min(inventory.size(), WorkerState.INVENTORY_SIZE); i++) {
					JsonElement encoded = inventory.get(i);
					if (encoded.isJsonObject() && !encoded.getAsJsonObject().isEmpty()) {
						state.inventory().set(i, ItemStack.CODEC.parse(ops, encoded).getOrThrow());
					}
				}

				JsonObject collected = object.getAsJsonObject("collected");
				for (Map.Entry<String, JsonElement> entry : collected.entrySet()) {
					state.collected().put(entry.getKey(), Math.max(0L, entry.getValue().getAsLong()));
				}
				target.put(entityId, state);
			}
		}
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
	}
}
