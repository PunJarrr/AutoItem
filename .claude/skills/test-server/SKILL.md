---
name: test-server
description: Smoke-test AutoItem on real servers (Paper 26.2, 1.21.11, 1.21.1 and Folia 26.2). It builds the jar, boots each server headless, checks the plugin enabled cleanly, and shuts the server down. Use after finishing a milestone, before a release, or when asked to test on a server.
---

# Smoke-test on real servers

Versions to test: $ARGUMENTS (default: all four).

| Server | Gradle task | Run dir |
|---|---|---|
| Paper 26.2 (Java 25) | `runServer` | `run/26.2` |
| Paper 1.21.11 (newest 1.21.x) | `runServer1_21_11` | `run/1.21.11` |
| Paper 1.21.1 (oldest supported) | `runServer1_21_1` | `run/1.21.1` |
| Folia 26.2 (Java 25) | `runFolia` | `run/folia-26.2` |

## Steps

1. `./gradlew build` must pass first. If it doesn't, stop and fix it.
2. `mkdir -p build/smoke`
3. For each server, start it and pipe `stop` into its console only after `Done (` appears in the log. Don't send it earlier: Paper queues a command sent before startup finishes and runs it on the first tick, where `stop` throws a NullPointerException and the server never shuts down. The fallback sends SIGTERM if the server is still up 90 s after `stop`:

   ```sh
   log=build/smoke/<name>.log; : > $log
   ( t=0; until grep -q 'Done (' $log 2>/dev/null || [ $t -ge 300 ]; do sleep 2; t=$((t+2)); done; sleep 5; echo stop
     t=0; while pgrep -f '[j]ava.*com[.]mojang[.]eula[.]agree' >/dev/null && [ $t -lt 90 ]; do sleep 2; t=$((t+2)); done
     pgrep -f '[j]ava.*com[.]mojang[.]eula[.]agree' >/dev/null && { echo "SIGTERM fallback" >&2; pkill -TERM -f '[j]ava.*com[.]mojang[.]eula[.]agree'; } ) | ./gradlew <task> --console=plain >> $log 2>&1
   ```

   The `[j]ava` pattern keeps `pgrep`/`pkill` from matching the shell running this snippet (its own command line contains the pattern). Run this with a Bash timeout of 600000 ms, one server per call. The first run per server downloads the jar (and Java 25 for 26.2), so it's slower. If the log contains `SIGTERM fallback`, the clean `stop` failed: report it.
4. Check `build/smoke/<name>.log` (the full server log is also at `<run dir>/logs/latest.log`):
   - `Enabling AutoItem` is present
   - `Done (` is present (startup finished)
   - no stack trace mentions `dev.punjarrr`
   - no ERROR lines from AutoItem, and no WARN lines except config warnings you expected
   - the server stopped (`Stopping server`, and the Gradle task finished)
5. Report a table: server | enabled | errors | unexpected warnings | startup time (from the `Done (…s)` line).

## If something goes wrong

- **The task hangs until the timeout:** a server is probably still running. Find it with `pgrep -fl java`, stop the one whose working directory is the run dir, and report it. Never leave a server running.
- **"Unsupported Java" or a toolchain error:** report the exact message. Don't change the Java versions in `build.gradle.kts` without asking.
- **Testing a specific config:** the plugin's config for each server lives at `<run dir>/plugins/AutoItem/config.yml`. Edit it there before the run, and mention that you did.

## Afterwards

This only proves the plugin starts cleanly. The gameplay checks in docs/SPEC.md §8 (G and X tests) need Pun with a Minecraft client. List the test IDs relevant to the change, the server to use, and the exact setup commands (for example `/give @s cobblestone 1`).
