# Changelog

All notable changes to AutoItem are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Project setup: Gradle build, test servers (Paper 1.21.1, 1.21.11, 26.2 and Folia 26.2), design spec.
- When you place your last block, or eat or drink your last item, the slot is refilled with an identical stack from your inventory (main inventory first, smallest stack first, then the hotbar). Works for the main hand and the offhand.
- Stews, potions, honey bottles and milk leave their bowl, bottle or bucket in your inventory instead of in the refilled slot. If your inventory is full, it drops at your feet.
- `config.yml` with every option documented: triggers, `sources.hotbar`, `ignored-items` (material names or `#tags`), the `remainders` table and `disabled-worlds`. Invalid values, unknown items and typos in keys are reported in the console with the key path, and the default is used.
- Folia support.
