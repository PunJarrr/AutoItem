# AutoItem

A Paper plugin that refills your hand when you use up the last item in it. Place your last block, eat your last steak, throw your last snowball or empty your last bucket, and an identical stack from your inventory moves into that slot.

**A refill only moves items. It never duplicates or deletes anything.**

## Supported servers

- Paper and its forks (Purpur and others): Minecraft 1.21 – 26.3
- Folia: 1.21.4 – 26.3
- Java 21 or newer
- Spigot and CraftBukkit are not supported yet (planned).

## What refills

| Trigger (`config.yml`) | Examples |
|---|---|
| `block-place` | any block, redstone dust, seeds |
| `consume` | food, potions, honey, milk, stews (the bowl or bottle goes into your inventory) |
| `buckets` | emptying a filled bucket, filling your last empty one |
| `throwables` | snowballs, eggs, ender pearls, splash and lingering potions, XP bottles, wind charges |
| `right-click` | spawn eggs, boats, minecarts, bone meal, fireworks, end crystals, item frames, leads, name tags, feeding animals |
| `bows` | the last arrow or rocket in the hand opposite a bow or crossbow |
| `tool-break` (off by default) | a broken tool is replaced by the same tool |

Works for the main hand and the offhand. Refills come from the main inventory first, then the hotbar (`sources.hotbar`). Dropping with Q, swapping hands, inventory clicks and totems never trigger a refill.

## Commands and permissions

| Command | Permission | Default | |
|---|---|---|---|
| `/autoitem toggle` | `autoitem.toggle` | everyone | Turn refills on or off for yourself |
| `/autoitem reload` | `autoitem.reload` | op | Reload `config.yml` |
| — | `autoitem.use` | everyone | Needed for refills to happen |

## Configuration

Everything is in `plugins/AutoItem/config.yml`, with every option explained in the file: triggers, ignored items (materials or `#tags`), the remainder table, disabled worlds, the refill sound, an optional action-bar message, and all messages (MiniMessage). Run `/autoitem reload` after editing.

## Metrics

AutoItem uses [bStats](https://bstats.org/plugin/bukkit/AutoItem/35427) to collect anonymous usage statistics: server count, Minecraft version, server software, and which triggers are turned on. Turn it off with `metrics: false` in `config.yml`, or for every plugin in `plugins/bStats/config.yml`.

## Building

```bash
./gradlew build
```

The plugin jar is `build/libs/AutoItem-<version>.jar`. Run a test server with `./gradlew runServer` (Paper 26.2); see `CLAUDE.md` for the other versions and the bot tests. The design and its anti-duplication invariants are in [docs/SPEC.md](docs/SPEC.md).

## License

[MIT](LICENSE)
