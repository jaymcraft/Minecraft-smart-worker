# Smart Worker

Smart Worker is a server-authoritative Fabric mod for Minecraft Java Edition 26.3. It creates owner-controlled workers with a dedicated industrial robot skin while retaining Minecraft's reliable golem navigation underneath.

## Features

- `/worker spawn` creates a persistent worker owned by the command sender.
- `/worker start` and `/worker stop` control the nearest owned worker within 32 blocks.
- Right-clicking a worker toggles it for its owner; other players are denied.
- Tagged Smart Workers use a graphite-and-steel robot skin with orange accents and cyan lights. Ordinary iron golems are unchanged.
- A running worker scans every 10 ticks for dropped items within 16 blocks and uses Minecraft's navigation to walk to them.
- Nine inventory slots enforce normal item stack limits. A partial pickup leaves the uncollected remainder in the world.
- The craftable 27-slot Worker Chest has a custom industrial texture and a normal container screen. Once all nine worker inventory slots are occupied, workers automatically return to the nearest available Worker Chest within 16 horizontal and 6 vertical blocks and transfer everything they carry.
- Each successful pickup updates a per-item collection count. Target priority is `4 * log(1 + collected count) - distance`, so frequently useful items gradually win against similarly distant alternatives while nearby items remain attractive.
- `/worker status` reports running state and inventory use.
- `/worker stats` reports the learned preferences.
- Ownership, inventory (including item components), active state, and learning data are saved in `smart_workers.json` in the world directory using an atomic replacement.

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

The production JAR is written to `build/libs/smart-worker-1.1.0.jar`.

## Usage

Stand near the worker you intend to control. Commands only select workers you own, in the same dimension, within 32 blocks.

1. Run `/worker spawn`.
2. Drop items within 16 blocks.
3. Craft and place a Worker Chest using one chest surrounded by eight iron ingots, or obtain it with `/give @s smart_worker:worker_chest`.
4. Run `/worker start`.
5. Inspect it with `/worker status` and `/worker stats`.

## Known limitations

- The first release only collects dropped item entities; it does not break blocks.
- The robot's carried inventory has no direct GUI. It can be checked through `/worker status` and is automatically unloaded into Worker Chests.
- Unreachable items remain untouched. The worker relies on vanilla navigation and retries target selection at the next scan.
- Commands control the nearest owned loaded worker; there is no worker-name selector yet.
