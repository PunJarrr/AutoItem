# Changelog

All notable changes to AutoItem are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Project setup: Gradle build, test servers (Paper 1.21.1, 1.21.11, 26.2 and Folia 26.2), design spec.
- When you place your last block, or eat or drink your last item, the slot is refilled with an identical stack from your inventory (main inventory first, smallest stack first, then the hotbar). Works for the main hand and the offhand.
- Stews, potions, honey bottles and milk leave their bowl, bottle or bucket in your inventory instead of in the refilled slot. If your inventory is full, it drops at your feet.
- `config.yml` with every option documented: triggers, `sources.hotbar`, `ignored-items` (material names or `#tags`), the `remainders` table and `disabled-worlds`. Invalid values, unknown items and typos in keys are reported in the console with the key path, and the default is used.
- Buckets: emptying your last water, lava or other filled bucket refills it; filling your last empty bucket (water, lava, milking, catching a fish or axolotl) gives you another empty bucket and moves the filled one into your inventory.
- Throwables: snowballs, eggs, ender pearls, splash and lingering potions, experience bottles and wind charges refill when you throw the last one. Tridents never do (they come back with Loyalty).
- Right-click items: spawn eggs, boats, minecarts, bone meal, firework rockets (also during elytra flight), end crystals, eyes of ender, item frames, paintings, armor stands, feeding and breeding animals, name tags, leads, and filling bottles and bowls. Armor and elytra equipped by right-click are never refilled.
- Bows and crossbows: the last arrow or rocket in the hand opposite the bow or crossbow is refilled. Tipped arrows only refill from identical tipped arrows.
- Turning off a trigger (for example `triggers.block-place: false`) also stops the right-click trigger from refilling that same action.
- A slot is never refilled when you emptied it some other way in the same tick: dropping with Q, swapping hands, an inventory click or a totem popping. Holding right-click with a totem can't be used as an auto-totem.
- Folia support.
