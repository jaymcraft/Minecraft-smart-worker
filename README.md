# Smart Worker

Smart Worker is a server-authoritative Fabric mod for Minecraft Java Edition 26.3. It creates owner-controlled workers with a dedicated industrial robot skin while retaining Minecraft's reliable golem navigation underneath.

## Features

- `/worker spawn` creates a persistent worker owned by the command sender.
- `/worker start` and `/worker stop` control the nearest owned worker within 32 blocks.
- Right-clicking a worker toggles it for its owner; other players are denied.
- Tagged Smart Workers use a graphite-and-steel robot skin with orange accents and cyan lights. Ordinary iron golems are unchanged.
- A running worker scans every 10 ticks for dropped items within 16 blocks and uses Minecraft's navigation to walk to them.
- Nine inventory slots enforce normal item stack limits. A partial pickup leaves the uncollected remainder in the world.
- Each successful pickup updates a per-item collection count. Target priority is `4 * log(1 + collected count) - distance`, so frequently useful items gradually win against similarly distant alternatives while nearby items remain attractive.
- `/worker status` reports running state and inventory use.
- `/worker stats` reports the learned preferences.
- Ownership, inventory (including item components), active state, and learning data are saved in `smart_workers.json` in the world directory using an atomic replacement.

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.163.0+26.3
- Java 25

Install the built JAR and Fabric API in the dedicated server's `mods` directory. Install them on clients as well to see the dedicated robot skin. Because the worker still uses a vanilla entity type and no custom network protocol, unmodded clients can still connect when the rest of the server's mod set permits it, but they will see an iron golem.

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

The production JAR is written to `build/libs/smart-worker-1.0.0.jar`.

## Usage

Stand near the worker you intend to control. Commands only select workers you own, in the same dimension, within 32 blocks.

1. Run `/worker spawn`.
2. Drop items within 16 blocks.
3. Run `/worker start`.
4. Inspect it with `/worker status` and `/worker stats`.

## Known limitations

- The first release only collects dropped item entities; it does not break blocks or unload its inventory.
- There is no inventory GUI. Inventory contents are currently visible as a count through `/worker status` and persist for future expansion.
- Unreachable items remain untouched. The worker relies on vanilla navigation and retries target selection at the next scan.
- Commands control the nearest owned loaded worker; there is no worker-name selector yet.
