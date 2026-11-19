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
  - `./gradlew runServer` → Paper 26.3 in `run/26.3` (the newest, and the main server for in-game checks)
  - `./gradlew runServer1_21_11` → `run/1.21.11`
  - `./gradlew runServer1_21_1` → `run/1.21.1`
  - `./gradlew runServer1_21` → `run/1.21` (the oldest supported version; Folia starts at 1.21.4)
  - `./gradlew runFolia` → Folia 26.3 in `run/folia-26.3`
  - `./gradlew runServer26_2` / `runFolia26_2` → Paper / Folia 26.2
  - `runServer26_1`, `runFolia26_1`, `runFolia1_21_11` → the other servers the bot plays on
  - These are interactive servers. For automated boot checks use the `/test-server` skill, and never leave a server running.
- `tools/bot/run.sh <task> <run dir> <version>` — the SPEC §8 G/X tests with a Mineflayer bot as the player, e.g. `tools/bot/run.sh runServer1_21_1 run/1.21.1 1.21.1`. Works on Paper 1.21 (`runServer1_21`), 1.21.1, 1.21.11 and 26.1.2 (`runServer26_1`, version `26.1`) and Folia 1.21.11 and 26.1.2 (`runFolia1_21_11`, `runFolia26_1`); the bot can't join 26.2 or 26.3. It switches that run dir to offline mode and ops the bot. First time: `cd tools/bot && npm install --ignore-scripts`. Results go to `build/bot/<run dir>.jsonl`. Needs the Mac awake and on power: a sleeping machine freezes the server mid-test.
- `tools/datapack/autoitem_test` — an in-game test data pack, installed into every Paper test server's world when it starts: `/function autoitem_test:menu` gives clickable setup and PASS/FAIL check buttons for the docs/IN-GAME-TESTS.md cases. Edit the tests in `tools/datapack/generate.py` and rerun it (`python3 tools/datapack/generate.py`).
- If `./gradlew` gives "permission denied", run `chmod +x gradlew`. If it can't find Java, set `JAVA_HOME` to a JDK 21.

## Stack and compatibility

- Java 21, Gradle Kotlin DSL, `compileOnly paper-api 1.21`. Adventure and MiniMessage come with Paper, so don't shade them. bStats is the only shaded library (relocated by the Shadow plugin); `build/libs/AutoItem-<version>.jar` is the shaded jar, the `-plain` jar lacks bStats.
- Supports Minecraft 1.21 through 26.x on Paper and its forks, and **Folia** from 1.21.4 (`folia-supported: true`). Spigot/CraftBukkit is the next milestone (SPEC §7.5), not supported yet.
- A compile error against the 1.21 API means that API doesn't exist on our oldest version. **Never bump paper-api to make something compile**; find a 1.21-safe way.
- Tests run against the paper-api version MockBukkit is built for (1.21.11, see `build.gradle.kts`). Something that compiles in tests but not in `main` doesn't exist on 1.21.
- Javadocs: https://jd.papermc.io/paper/1.21.11/ (1.21 is a subset of this; the compiler has the final say).
- `plugin.yml` stays on `api-version: '1.21'`.
- Config strings go through `Material.matchMaterial()` and item tags. Never `valueOf` or `switch` on Bukkit enums or registry types. No data component API (not in 1.21).

## Anti-dupe rules (summary of SPEC §3; the spec wins if they differ)

1. Listeners only record checks. They never touch inventories and never cancel or un-cancel events.
2. The decision is `RefillPlanner.plan(...)`, a pure function of the check and the inventory **read when the task runs**.
3. `RefillExecutor` is the only class that writes inventories. It verifies both slots, clones, and swaps: the stack goes into the target, the remainder (if any) into the source slot. Nothing is ever dropped or deleted.
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
