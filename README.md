# Smart Worker

Smart Worker is a server-authoritative Fabric mod for Minecraft Java Edition 26.3. It creates owner-controlled workers with a dedicated industrial robot skin while retaining Minecraft's reliable golem navigation underneath.

## Features

- `/worker spawn` creates a persistent worker owned by the command sender.
- `/worker start` and `/worker stop` control the nearest owned worker within 32 blocks.
- Workers remain stationary after spawning and while stopped. When running, movement is enabled only while pursuing a detected dropped item or returning a full inventory to a Worker Chest.
- Right-clicking a worker toggles it for its owner; other players are denied.
- Tagged Smart Workers use a graphite-and-steel robot skin with orange accents and cyan lights. Ordinary iron golems are unchanged.
- A running worker scans every 10 ticks for dropped items within 16 blocks and uses Minecraft's navigation to walk to them.
- Optional automatic mining incrementally scans up to 32 blocks horizontally and 24 blocks vertically, chooses exposed blocks with a reachable place to stand, mines configured blocks with iron-pickaxe loot rules, and inserts the real drops into its nine-slot inventory. Each once-per-second scan batch is capped to avoid a large operation every tick.
- Mining goals prioritize blocks that produce the requested item and can stop automatically after a configured number of that item has been collected.
- Workers remember ore finds in 16×16×16 areas. A matching gathering goal favors productive nearby areas and can guide the worker back toward remembered areas within 64 blocks.
- Workers remember 8×8×8 danger areas after taking damage or detecting hazards such as lava, fire, cactus, magma, or sweet berry bushes, and avoid future mining targets there.
- Nine inventory slots enforce normal item stack limits. A partial pickup leaves the uncollected remainder in the world.
- The craftable 27-slot Worker Chest has a custom industrial texture and a normal container screen. Once all nine worker inventory slots are occupied, workers automatically return to the nearest available Worker Chest within 16 horizontal and 6 vertical blocks and transfer everything they carry.
- Each successful pickup updates a per-item collection count. Target priority is `4 * log(1 + collected count) - distance`, so frequently useful items gradually win against similarly distant alternatives while nearby items remain attractive.
- `/worker status` reports running state and inventory use.
- `/worker stats` reports the learned preferences.
- `/worker config` opens the owner-only mining configuration screen.
- Each worker has its own item whitelist and blacklist. An empty whitelist permits every item; a nonempty whitelist permits only listed items, and the blacklist always denies an item. Filters apply before the worker chooses a dropped-item target.
- Ownership, inventory (including item components), active state, learning data, and item filters are saved in `smart_workers.json` in the world directory using an atomic replacement.

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.163.0+26.3
- Java 25

Install the built JAR and Fabric API in the dedicated server's `mods` directory and on every connecting client. Version 1.1 adds the custom `smart_worker:worker_chest` block, so clients now need the mod to synchronize the block registry and render its model and texture.

## Build

macOS/Linux:

```sh
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

Windows:

```bat
set JAVA_HOME=C:\path\to\jdk-25
gradlew.bat build
```

The production JAR is written to `build/libs/smart-worker-1.3.0.jar`.

## Usage

Stand near the worker you intend to control. Commands only select workers you own, in the same dimension, within 32 blocks.

1. Run `/worker spawn`.
2. Drop items within 16 blocks.
3. Craft and place a Worker Chest using one chest surrounded by eight iron ingots, or obtain it with `/give @s smart_worker:worker_chest`.
4. Run `/worker start`.
5. Inspect it with `/worker status` and `/worker stats`.

### Automatic mining

1. Stand within 32 blocks of your worker and run `/worker config`.
2. Enable automatic mining and enter comma-separated block IDs. Stone, deepslate, coal, iron, copper, gold, redstone, lapis, diamond, emerald, Nether quartz, and Nether gold ores are included by default.
3. Enter the goal drop item, such as `minecraft:raw_iron`, and a target count. Use `0` for unlimited mining.
4. Look at a placed Worker Chest, reopen the screen if necessary, select **Use targeted block**, and save. You may also type its coordinates or leave chest mode on auto-find.
5. Run `/worker start`. A full worker stops mining and returns to the configured Worker Chest before doing more work.

The server validates ownership, block and item IDs, target count, and the configured chest. A configured chest must be an actual `smart_worker:worker_chest` within 64 blocks when the settings are saved.

### Item filters

Commands affect your nearest owned worker within 32 blocks and accept registry IDs such as `minecraft:oak_log`:

- `/worker filters` displays both lists.
- `/worker whitelist add <item>` and `/worker whitelist remove <item>` edit the allow list.
- `/worker blacklist add <item>` and `/worker blacklist remove <item>` edit the deny list.
- `/worker whitelist clear` and `/worker blacklist clear` empty a list.

Adding an item to one list automatically removes it from the other. Item IDs are tab-completed and validated against Minecraft's item registry.

## Known limitations

- The robot's carried inventory has no direct GUI. It can be checked through `/worker status` and is automatically unloaded into Worker Chests.
- Unreachable items remain untouched. The worker relies on vanilla navigation and retries target selection at the next scan.
- Mining uses vanilla navigation and only targets blocks it can approach from an open face. It does not tunnel through solid terrain to reach buried ores.
- Commands control the nearest owned loaded worker; there is no worker-name selector yet.
