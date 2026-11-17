# AutoItem

A Paper plugin that refills a hand slot when the player uses up its last item (placing, eating, drinking, throwing, buckets, right-click items, arrows, and optionally broken tools), taking a matching stack from the rest of the inventory. All behavior is set in `config.yml`; there is no GUI. **Requirement #1 is zero duplication and zero item loss: a refill only moves items.**

Read `docs/SPEC.md` before any feature work. It defines the behavior, invariants I1–I9, the default config, the architecture, the milestones and the test matrix. When behavior intentionally changes, update the spec in the same change.

The developer is Pun (author `PunJarrr`). He runs a Paper server on Proxmox and publishes plugins on Modrinth and Hangar.

## Commands

- `./gradlew build` — compile, unit + MockBukkit tests, and jar (`build/libs/AutoItem-<version>.jar`)
- `./gradlew test` — all tests
- `./gradlew test --tests '*RefillPlanner*'` — one class (the pure planner)
- `./gradlew test --tests '*MockBukkit*'` — the MockBukkit integration tests only
- Test servers. The first run downloads the server jar, and Java 25 for 26.x (Gradle toolchains handle this).
  - `./gradlew runServer` → Paper 26.2 in `run/26.2`
  - `./gradlew runServer1_21_11` → `run/1.21.11`
  - `./gradlew runServer1_21_1` → `run/1.21.1` (the oldest supported version)
  - `./gradlew runFolia` → Folia 26.2 in `run/folia-26.2`
  - These are interactive servers. For automated boot checks use the `/test-server` skill, and never leave a server running.
- `tools/bot/run.sh <task> <run dir> <version>` — the SPEC §8 G/X tests with a Mineflayer bot as the player, e.g. `tools/bot/run.sh runServer1_21_1 run/1.21.1 1.21.1`. Works on Paper 1.21.1 and 1.21.11 and Folia 1.21.11 (`runFolia1_21_11`); the bot can't join 26.x. It switches that run dir to offline mode and ops the bot. First time: `cd tools/bot && npm install --ignore-scripts`. Results go to `build/bot/<run dir>.jsonl`. Needs the Mac awake and on power: a sleeping machine freezes the server mid-test.
- If `./gradlew` gives "permission denied", run `chmod +x gradlew`. If it can't find Java, set `JAVA_HOME` to a JDK 21.

## Stack and compatibility

- Java 21, Gradle Kotlin DSL, `compileOnly paper-api 1.21.1`. Adventure and MiniMessage come with Paper, so shade nothing.
- Supports Minecraft 1.21.1 through 26.x on Paper, its forks, and **Folia** (`folia-supported: true`).
- A compile error against the 1.21.1 API means that API doesn't exist on our oldest version. **Never bump paper-api to make something compile**; find a 1.21.1-safe way.
- Tests run against the paper-api version MockBukkit is built for (1.21.11, see `build.gradle.kts`). Something that compiles in tests but not in `main` doesn't exist on 1.21.1.
- Javadocs: https://jd.papermc.io/paper/1.21.11/ (1.21.1 is a subset of this; the compiler has the final say).
- `plugin.yml` stays on `api-version: '1.21'`.
- Config strings go through `Material.matchMaterial()` and item tags. Never `valueOf` or `switch` on Bukkit enums or registry types. No data component API (not in 1.21.1).

## Anti-dupe rules (summary of SPEC §3; the spec wins if they differ)

1. Listeners only record checks. They never touch inventories and never cancel or un-cancel events.
2. The decision is `RefillPlanner.plan(...)`, a pure function of the check and the inventory **read when the task runs**.
3. `RefillExecutor` is the only class that writes inventories. It clones, clears the source, takes the remainder, fills the target, and gives the remainder back with `addItem()`, dropping what doesn't fit. Never delete an item.
4. Clone every `ItemStack` before its slot is written.
5. Sources are storage slots 0–35 only, never the target.
6. Re-check every gate (online, dead, game mode, world, toggle, permission) at task time; a world or game-mode change aborts.
7. Thread: everything per player runs on the player's entity scheduler (`player.getScheduler()`), never `Bukkit.getScheduler()` or async.

After changing any code that records checks, plans or performs refills, run the `exploit-reviewer` agent before calling the work done.

## Code conventions

- Package `dev.punjarrr.autoItem`, with the sub-packages from SPEC §6.
- Pure logic (`refill` model and planner, remainders, config parsing) stays Bukkit-free where it can and gets JUnit tests in `src/test/java`. Bukkit adapters live in `bukkit`.
- `final` classes (except the plugin main class, which MockBukkit subclasses) and constructor injection. No static singletons or service locators; the main class only wires things together.
- Config is loaded once into an immutable `PluginConfig` record held by `ConfigService`; no other code calls `getConfig()`. For every new option, follow the `/add-config-option` skill.
- Every player-facing string lives in `config.yml` under `messages` (MiniMessage).
- Log with `getLogger()`. Config warnings include the key path and the fallback value used.
- Don't add dependencies without asking Pun first.

## Workflow

- Work through the SPEC §7 milestones one at a time: plan, implement, `./gradlew build`, `/test-server`, commit.
- A Stop hook runs `./gradlew build` whenever the source has changed since the last green build. If it blocks you, fix the build or tests; never disable or bypass the hook.
- Commits are small with imperative subjects ("Add bucket triggers"). Don't push without asking.
- Record user-visible changes in `CHANGELOG.md` under `Unreleased`.
- In-game checks need Pun with a Minecraft client. When a milestone is done, list the SPEC §8 G/X test IDs he should run, with the exact `/give` setup commands and which test server to use.
