---
name: add-config-option
description: Checklist for adding or changing an AutoItem config option so config.yml, PluginConfig, validation, reload, tests and the spec stay in sync. Use whenever a behavior should be configurable or an existing option changes.
---

# Add or change a config option

Option: $ARGUMENTS

Everything in AutoItem is configured from files, and server owners edit `config.yml` by hand. Every option has to be documented, validated and reloadable.

1. **`src/main/resources/config.yml`:** add the key in the right section (same order as docs/SPEC.md §5), with a comment directly above it covering what it does, allowed values and the default.
2. **`PluginConfig` record:** add a typed field, and its default in `PluginConfig.defaults()`.
3. **`ConfigLoader`:** add the path to `KNOWN_PATHS`, then parse and validate.
   - A missing key uses the default silently (old configs must keep working, and the user's file is never rewritten).
   - An invalid value logs `Invalid value for '<path>': <value> (using default <default>)` and falls back to the default.
   - An unknown material or tag logs a warning and skips that entry.
   - Broken MiniMessage logs a warning and falls back to the default text.
4. **Runtime:** if the option affects refills, carry it into `RefillSettings` (built in `SettingsFactory`) so the planner reads it at task time. `/autoitem reload` must apply it without a restart.
5. **Tests:** add unit tests covering valid, invalid and missing values; `bundledConfigMatchesDefaultsWithoutWarnings` must keep passing.
6. **Spec:** update docs/SPEC.md §5 (default config) and the behavior text in §4.
7. **Changelog:** add a line under `Unreleased` in `CHANGELOG.md`.
