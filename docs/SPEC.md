# AutoItem — Design Spec

This is the source of truth for how the plugin behaves. If the code and this file disagree, one of them is a bug: fix the code, or update this file in the same change when the behavior is intentionally changing.

## 1. What it does

When a player uses up the last item in a hand slot (placing a block, eating, drinking, throwing, emptying or filling a bucket, using a right-click item, firing the last arrow, or a tool breaking), AutoItem refills that slot with a matching stack from the rest of their inventory. Everything is set in `config.yml`; there is no GUI.

The overriding requirement is **no duplication and no loss**. A refill only ever *moves* items that already exist in the player's inventory.

### In scope for v1

- Refill of the main hand (the hotbar slot that was used) and the offhand
- Triggers: block placing, eating/drinking, buckets, throwables, right-click items, bows and crossbows, tool breaking (off by default)
- A configurable remainder table (stew → bowl, potion → bottle, filled bucket → bucket)
- Per-player toggle, per-world disable list, ignored items, hotbar-as-source switch
- Feedback: a quiet sound (on) and an action-bar message (off)
- Paper and its forks, including **Folia**

### Not in v1

- Re-equipping broken armor or elytra (armor slots are never targets)
- Refilling after dropping with Q, or after any inventory click
- Pulling from shulker boxes, bundles, ender chests or containers
- Spigot, GUI config, bStats, update checker

## 2. Platform and compatibility

- **Paper and its forks, Minecraft 1.21 through 26.x, and Folia (1.21.4 through 26.x: Folia has no earlier 1.21 builds).** The bot plays the full suite on Paper 1.21, 1.21.1, 1.21.11 and 26.1.2 and Folia 1.21.11 and 26.1.2. Paper and Folia 26.2 and 26.3 are boot-tested, with the order of every event used checked in their server code (the bot can't join them yet). Why not lower: bStats (Nov 2026) shows 1.20.6 at 0.3% of servers and 1.20.4 at 1.0%, while 1.20.4 and older would need Java 17 bytecode and fallbacks for missing API.
- Compile against `paper-api:1.21-R0.1-SNAPSHOT`, Java 21 bytecode. One jar for every version. A compile error means the API doesn't exist on 1.21: never bump the API to fix it.
- `plugin.yml`: `api-version: '1.21'`, `folia-supported: true`.
- Config strings are resolved with `Material.matchMaterial()` and item tags (`#minecraft:...` via `Bukkit.getTag(Tag.REGISTRY_ITEMS, …)`). Never `switch` on or `valueOf` Bukkit enums or registry types. An unknown name is a config warning and is skipped, so newer items work where they exist and nothing breaks where they don't.
- Don't use the data component API (`use_remainder` etc.); it isn't in 1.21.
- Sounds are played as Adventure `Sound`s built from a key string, so unknown keys on older versions play nothing instead of throwing.
- Adventure and MiniMessage come with Paper; nothing is shaded. Tests add MockBukkit (test scope only).

### Threading (Paper and Folia)

- Every per-player action (listener bookkeeping, the refill task, feedback, PDC toggle) runs on the thread that owns the player: the main thread on Paper, the player's region thread on Folia.
- The refill task is scheduled with `player.getScheduler().execute(plugin, task, retired, 1L)`. On Paper this is the next main-thread tick. If the player is removed before it runs, the `retired` callback just discards the pending checks.
- Pending checks live in a `ConcurrentHashMap<UUID, Batch>`. A batch is only touched by the player's owning thread; the map is concurrent because Folia can hand a player to another region thread between the event and the task.
- The config snapshot is an immutable record behind a `volatile` field; `/autoitem reload` swaps it.
- No `Bukkit.getScheduler()`, no async tasks, no global state mutated from region threads.
- On disable (`/reload`, plugin managers) pending checks are cleared, and a task queued before the disable doesn't run.

## 3. Core model

### Terms

- **Target slot**: the raw `PlayerInventory` index that was used: `0–8` for the main hand (the held slot *at event time*, never "the main hand" at task time), `40` for the offhand.
- **Snapshot**: a clone of the target slot's item, read at event time, before vanilla decrements it.
- **Allowed remainders**: the materials that may legitimately sit in the target slot after the last item was used (§4.3).
- **Check**: `(player UUID, target slot, snapshot, allowed remainders, kind, world UUID, game mode)` where kind is `NORMAL` or `TOOL_BREAK`.
- **Source slot**: a storage slot `0–35`, other than the target, holding a matching stack.
- **Remainder**: whatever is in the target slot at task time when it isn't empty but is allowed.

### Observe, then verify next tick

1. **Listeners never change inventories and never cancel or un-cancel events.** They only record checks.
2. A listener records a check only if the target slot, read at event time, holds an item similar to the event's item **with amount 1**. Every event used fires before vanilla decrements the stack (checked in the Paper 1.21.1 and 26.2 server code and by the bot tests), so amount 1 means "this use takes the last one". Larger stacks are never recorded. This is also why "use the second-to-last item, then press Q" never refills. Bows are the exception that proves the rule: a bow draws its arrow out of the inventory *before* `EntityShootBowEvent`, so the bow trigger uses `PlayerReadyArrowEvent`, which fires first.
   Listeners record even when their trigger is switched off; the switch is applied at task time to the merged check (step 4), so a disabled specific trigger also silences the right-click catch-all for the same use.
3. **Over-recording is safe, under-recording only misses a refill.** Where an event doesn't say which hand was used, record both hands; the task decides.
4. **Dedup:** at most one check per player per slot per tick. If several events record the same slot in one tick (`BlockPlaceEvent` + `PlayerInteractEvent`), keep the first snapshot, union the allowed remainders and triggers (only when both records saw the same item, where a damageable item counts as the same across damage: Paper records one flint-and-steel use at three damage values in one tick, the last with the pre-use copy restored for the fire's BlockPlaceEvent. If a different item is recorded later, the slot changed in between, e.g. an armor-stand swap, and the later check replaces the earlier one), and the kind becomes `TOOL_BREAK` if any record was. `right-click` is the catch-all (`PlayerInteractEvent` also fires for placing, buckets and throws): next to any more specific trigger it is dropped, so with `block-place: false` placing the last block never refills through right-click.
5. **Discards:** if something other than a trigger empties or swaps a hand in the same tick, the pending check for that slot is dropped: a drop (`PlayerDropItemEvent`, the held slot), a hand swap (`PlayerSwapHandItemsEvent`, both hands), an inventory click or drag (`InventoryClickEvent`/`InventoryDragEvent`, every check for that player, including clicks a hacked client sends with no inventory open) a totem pop (`EntityResurrectEvent`, the totem's hand), pick block (`PlayerPickItemEvent`), choosing a villager trade (`TradeSelectEvent`) and the recipe book (`PlayerRecipeBookClickEvent`); the last three move items between slots without a click event and discard every check for that player. All at `MONITOR`, `ignoreCancelled = true`, and they only remove recorder state. Without this, a check from a use that consumed nothing (the right-click catch-all, a bow starting to draw) refills a slot the player emptied another way: right-clicking a totem every tick and then popping it would be an auto-totem.
6. One tick later, one task per player drains the batch. Checks run main hand first, then offhand. **Each check re-reads the live inventory**, after the previous check's refill.

### The decision (pure function)

`RefillPlanner.plan(check, inventoryNow, settings, gateNow) → Refill(source, target) | Skip(reason)`, in this order:

| # | Condition at task time | Result |
|---|---|---|
| 1 | Player offline, dead, creative/spectator, world disabled, toggle off, or missing `autoitem.use` | `Skip(GATE)` |
| 2 | World or game mode differs from the check's (per-world/per-gamemode inventory plugins swap the whole inventory) | `Skip(CONTEXT_CHANGED)` |
| 3 | Trigger now disabled in config, or the snapshot's item is ignored | `Skip(DISABLED)` / `Skip(IGNORED)` |
| 4 | Target holds a stack similar to the snapshot | `Skip(NOT_USED_UP)` |
| 5 | Target is neither empty nor exactly one allowed remainder (amount 1) | `Skip(SLOT_CHANGED)` |
| 6 | Kind `NORMAL` and the snapshot is damageable (max durability > 0) | `Skip(DAMAGEABLE)`: damageable items only refill through tool break |
| 7 | Kind `TOOL_BREAK` and `triggers.tool-break` is off | `Skip(DISABLED)` |
| 8 | No source slot matches (§4.2) | `Skip(NO_SOURCE)` |
| 9 | Otherwise | `Refill(source, target)` |

The planner runs on a small model: `ItemView` (material key, amount, max durability, damage, `isSimilar(other)`, custom name, enchantments, "same item ignoring wear") and an array of 41 slots. At runtime `ItemView` wraps cloned `ItemStack`s; tests use a fake. It needs no server.

### The refill (one synchronous step, no yield)

Given `Refill(source, target)`, on the owning thread, in one method:

1. Re-verify that the source slot still holds exactly what was planned and the target still holds what was planned (empty or the remainder). Same task, so this is defensive; on any mismatch, change nothing.
2. `sourceClone = inv.getItem(source).clone()`
3. `inv.setItem(source, null)` (in code, steps 3 and 6 are one write: the source slot gets the remainder or nothing)
4. `remainderClone = inv.getItem(target)` cloned if not empty, else none
5. `inv.setItem(target, sourceClone)`
6. If there is a remainder, it goes into the source slot (step 3 just emptied it): a swap. It always fits, nothing is ever dropped, and it never lands in a hand or in another pending target. (Until milestone 4 this used `addItem` with a drop fallback; the audit showed the drop could be deleted by plugins that cancel item spawns, and `addItem` could put a bowl into an empty held slot.)
7. Feedback (sound, action bar).

Every `ItemStack` read from the inventory is cloned before its slot is written; `getItem()` can return a live mirror that turns to air when the slot is cleared.

### Storage

| Key | Type | Meaning |
|---|---|---|
| `autoitem:enabled` | BYTE on the player PDC | `0` = the player turned AutoItem off. Missing or `1` = on. |

No files, no database. Pending checks are in memory for at most one tick.

### Invariants

Every change must preserve all of these. The `exploit-reviewer` agent checks against this list.

- **I1 No duplication:** a refill only moves items (a swap of two inventory slots). For every material, the count across the player's inventory (all 41 slots and the cursor) is identical before and after.
- **I2 No loss:** the target slot is overwritten only when it is empty or holds exactly one allowed remainder. The remainder always ends up in the slot the refill stack came from. The source slot is cleared only after its clone exists.
- **I3 Task-time state only:** the decision uses the inventory and player state read when the task runs. Event data only says *which slot*, *what was there* and *what may be left*.
- **I4 One task, no yield:** each check's read, decision and mutation happen in one synchronous run on the player's owning thread.
- **I5 Listeners are observers:** no inventory writes, no cancelling or un-cancelling, `MONITOR` priority.
- **I6 Sources are storage slots 0–35 only**, never the target or the held slot, the armor slots, the offhand, the crafting grid, the cursor, or the contents of bundles or shulker boxes.
- **I7 Clone before clear** for every `ItemStack` read from a slot that is then written.
- **I8 Gates are re-checked at task time**, and any change of world or game mode between event and task aborts the check.
- **I9 Matching:** `ItemStack#isSimilar` against the item that was used (never the placed block). Tool break uses the rules in §4.2 only.

## 4. Behavior

### 4.1 Triggers

All listeners are `@EventHandler(priority = MONITOR, ignoreCancelled = true)`, except `PlayerInteractEvent`, which uses `ignoreCancelled = false` and requires `useItemInHand() != Result.DENY` (Paper reports right-click-air as cancelled from the start). Only `Player`s are considered. Each trigger group has a config switch.

| Group (config key) | Events | Hand | Notes |
|---|---|---|---|
| `block-place` | `BlockPlaceEvent` (covers `BlockMultiPlaceEvent`) | `getHand()` | Snapshot is `getItemInHand()`, so redstone dust matches dust, seeds match seeds. Powder snow bucket placement leaves a bucket via the remainder table. |
| `consume` | `PlayerItemConsumeEvent` | `getHand()` | Food, potions, honey, milk, stews, ominous bottles. |
| `buckets` | `PlayerBucketEmptyEvent`, `PlayerBucketFillEvent`, `PlayerBucketEntityEvent` | `getHand()` | Emptying: remainder is a bucket. Filling the last empty bucket: the filled bucket is the remainder and gets moved out, and the slot gets another empty bucket. The filled item the event reports (`getItemStack()`, `getEntityBucket()`) is also allowed as the remainder, so catching a fish in the last water bucket moves the fish bucket out and refills the water bucket. |
| `throwables` | `PlayerLaunchProjectileEvent` | both hands | Only when `shouldConsume()`. Snowballs, eggs, ender pearls, splash/lingering potions, experience bottles, wind charges. Tridents are damageable, so rule 6 skips them (they come back with Loyalty). |
| `right-click` | `PlayerInteractEvent`, `PlayerInteractEntityEvent`, `EntityPlaceEvent`, `HangingPlaceEvent`, `PlayerArmorStandManipulateEvent`, `PlayerElytraBoostEvent` | `getHand()` | Spawn eggs, boats, minecarts, bone meal, firework rockets, end crystals, ender eyes, item frames, paintings, armor stands, feeding/breeding, name tags, leads, cauldrons, filling glass bottles and bowls. Items whose `Material#getEquipmentSlot()` isn't a hand are not recorded (right-click equips them). `PlayerInteractEvent` uses `useItemInHand() != DENY` and right-click actions only. |
| `bows` | `PlayerReadyArrowEvent` | both hands (the weapon's hand never matches its ammo) | Fires while a bow or crossbow picks its ammo (bow: drawing starts and on release; crossbow: on load), before anything is taken from the inventory. `EntityShootBowEvent` comes after a bow has drawn its arrow, too late to see the last one. It names the exact ammo, so tipped arrows match exactly and an unrelated item in the other hand is never recorded. Infinity leaves the arrow, so the check finds it still there. Ammo pulled from storage slots isn't in a hand, so nothing to refill. |
| `tool-break` (default **off**) | `PlayerItemBreakEvent` | both hands | Fires while the broken tool is still in its slot (checked in the 1.21.1 and 26.2 server code). Record a `TOOL_BREAK` check for each hand holding exactly that item (isSimilar). Broken armor never matches a hand, so it is never re-equipped (not in v1). |

- Dropping with Q, inventory clicks, hand swaps (F) and hotbar scrolling never record anything, and the first three discard what was recorded (§3 step 5).
- Known gap: on 26.x a plugin or datapack item with an `equippable` component on a non-armor material isn't recognised as "equips on right-click" (1.21.1 has no API to see components), so equipping the last one refills the hand. Conserved; accepted.
- Each listener is a thin adapter: find the target slot(s), build the snapshot, call `CheckRecorder.record(...)`.

### 4.2 Matching and source slots

- **Normal checks:** a source matches when `source.isSimilar(snapshot)`. So a Speed potion only refills from Speed potions, a named or enchanted item only from an identical one.
- **Candidates:** storage slots `9–35`, then `0–8` if `sources.hotbar` is on (default on). Never the target slot, and never the hotbar slot the player is holding at task time: taking it would just empty the main hand (when both hands run out together, the offhand must not take the stack that was just put in the main hand). The targets of every check in the same batch are never sources either, so one check can't take another's slot: the offhand can't take back what the main-hand check just put in (even after a scroll), and a remainder never lands in another target.
- **Order:** main inventory (`9–35`) before hotbar; within each, the **smallest** matching stack first (tidies partial stacks); ties go to the lowest slot index.
- **Ignored items** (`ignored-items`) are never refilled: if the snapshot matches an entry, skip.
- **Tool break** (isSimilar can't work because durability differs):
  - Candidate must have the same material.
  - Custom name: a candidate with a custom name is only allowed if the broken item had the exact same name; an unnamed broken tool only takes unnamed candidates.
  - Candidate must be the same item once wear is ignored: `isSimilar` after resetting damage, enchantments, repair cost and custom name on copies of both. Item name, lore, model, attributes, unbreakable and all custom data (PDC, and other plugins' NBT such as ItemsAdder or MMOItems) must match, so a custom tool never stands in for a plain one or the other way round.
  - Tool-break matching only applies when the used item can break (max durability > 0); a tool-break record merged into a check for another item keeps that item's exact matching.
  - Preference: identical enchantments first, then main inventory before hotbar, then the least-damaged, then the lowest slot.

### 4.3 Remainders

The `remainders` table maps a used item to what vanilla leaves in its slot. Allowed remainders for a check are:

- `remainders[snapshot]` (stew → bowl, filled bucket → bucket, potion → glass bottle), **and**
- every key whose value is the snapshot (the inverse: a used empty bucket allows any filled bucket; a glass bottle allows `POTION` and `HONEY_BOTTLE`; a bowl allows the stews, for mooshrooms).

A remainder is accepted only as exactly one item of an allowed material. Being lenient here is safe because a remainder is always moved, never deleted.

`NONE` means the item leaves nothing (ominous bottle): the slot must be empty.

Stacked items: with a stack larger than one (honey bottles), vanilla puts the remainder elsewhere in the inventory and the slot keeps the rest. The listener doesn't record those (amount > 1), so only the last one refills.

Dragon's breath and other fills not in the table leave a material that isn't allowed, so the check skips (`SLOT_CHANGED`): no refill, nothing moved.

### 4.4 Gates

Checked at event time (to avoid recording) **and again at task time** (rule 1):

- Player online and not dead
- Game mode survival or adventure
- World not in `disabled-worlds`
- Player toggle on (`autoitem:enabled`)
- Permission `autoitem.use`
- Plugin master switch `enabled`

### 4.5 Commands and permissions

| Command | Permission | Default | Effect |
|---|---|---|---|
| `/autoitem toggle` | `autoitem.toggle` | true | Flips the player's own `autoitem:enabled`. Player only. |
| `/autoitem reload` | `autoitem.reload` | op | Reloads `config.yml`. A YAML syntax error keeps the previous config. |
| (none) | `autoitem.use` | true | Required for refills to happen at all. |

`/autoitem` with no or an unknown argument shows usage. Tab completion lists only subcommands the sender may use.

### 4.6 Feedback

On every successful refill (only when `RefillExecutor` really moved a stack): the configured sound (on by default, quiet), and the action-bar message (off by default). The sound is an Adventure sound built from `feedback.sound.key`, so a key an older version doesn't know plays nothing. `<item>` in the message is the item's custom name, or its name translated on the player's client.

## 5. Default `config.yml`

```yaml
# AutoItem configuration
# After editing, run /autoitem reload
#
# Invalid values are reported in the console and replaced by their default.
# Missing keys use their default silently. This file is never rewritten by the plugin.

# Version of this file's layout. Don't change it.
config-version: 1

# Master switch. false = AutoItem does nothing.
enabled: true

# Which actions refill the slot when they use its last item.
triggers:
  block-place: true      # placing blocks
  consume: true          # eating and drinking
  buckets: true          # emptying and filling buckets
  throwables: true       # snowballs, eggs, ender pearls, splash potions, XP bottles, wind charges
  right-click: true      # spawn eggs, boats, bone meal, fireworks, item frames, feeding animals...
  bows: true             # last arrow or rocket in the hand opposite a bow or crossbow
  tool-break: false      # replace a broken tool with the same tool

sources:
  # Also take refills from the hotbar (the main inventory is always used first).
  hotbar: true

# Items that are never refilled. Material names or item tags, e.g.
# [TOTEM_OF_UNDYING, "#minecraft:beds"]
ignored-items: []

# What an item leaves in the slot when it is used up. NONE = nothing.
# Filling works the other way round: using the last empty bucket allows any
# item that maps to BUCKET here. Unknown item names are skipped with a warning.
remainders:
  MUSHROOM_STEW: BOWL
  RABBIT_STEW: BOWL
  BEETROOT_SOUP: BOWL
  SUSPICIOUS_STEW: BOWL
  POTION: GLASS_BOTTLE
  HONEY_BOTTLE: GLASS_BOTTLE
  WATER_BUCKET: BUCKET
  LAVA_BUCKET: BUCKET
  MILK_BUCKET: BUCKET
  POWDER_SNOW_BUCKET: BUCKET
  COD_BUCKET: BUCKET
  SALMON_BUCKET: BUCKET
  PUFFERFISH_BUCKET: BUCKET
  TROPICAL_FISH_BUCKET: BUCKET
  AXOLOTL_BUCKET: BUCKET
  TADPOLE_BUCKET: BUCKET
  OMINOUS_BOTTLE: NONE

# Worlds (exact names) where AutoItem does nothing.
disabled-worlds: []

feedback:
  sound:
    enabled: true
    # Any sound key; unknown keys play nothing.
    key: "minecraft:entity.item.pickup"
    volume: 0.3
    pitch: 1.4
  # Show messages.refilled on the action bar after a refill.
  action-bar: false

# Every message the plugin sends. MiniMessage format; <prefix> inserts the prefix.
messages:
  prefix: "<gray>[<gold>AutoItem</gold>]</gray> "
  # <item> = the item's name
  refilled: "<gray>Refilled <white><item></white></gray>"
  toggled-on: "<prefix><green>Auto refill enabled.</green>"
  toggled-off: "<prefix><yellow>Auto refill disabled.</yellow>"
  reloaded: "<prefix><green>Config reloaded.</green>"
  # <count> = number of warnings
  reloaded-with-warnings: "<prefix><yellow>Config reloaded with <count> warning(s). See console.</yellow>"
  reload-failed: "<prefix><red>Config has errors, kept the previous one. See console.</red>"
  no-permission: "<prefix><red>You don't have permission.</red>"
  player-only: "<prefix><red>Only players can use this.</red>"
  usage: "<prefix><gray>/autoitem <toggle|reload></gray>"
```

## 6. Architecture

Package `dev.punjarrr.autoItem`. Pure logic is Bukkit-free and unit tested; config parsing may use `YamlConfiguration`, `Material.matchMaterial` and MiniMessage, but never registries or anything needing a running server (tags are kept as strings and resolved at runtime).

| Class | Responsibility |
|---|---|
| `AutoItem` | Plugin main: load config, register listeners and the command. Wiring only. |
| `config.PluginConfig` | Immutable record snapshot of config.yml. |
| `config.ConfigLoader` | YAML → `PluginConfig`, validation, fallbacks, warnings. Unit tested. |
| `config.ConfigService` | Holds the current snapshot (`volatile`); reload swaps it. |
| `refill.model.ItemView`, `InventoryView`, `Check`, `Gate`, `Decision` | The pure model. |
| `refill.RefillPlanner` | **Pure** decision (§3 table, §4.2, §4.3). Unit tested. |
| `refill.Remainders` | **Pure** allowed-remainder sets from the table, incl. the inverse. Unit tested. |
| `refill.CheckRecorder` | Per-player batches, dedup, schedules the one-tick task via the entity scheduler. |
| `refill.RefillTask` | Drains a batch: builds the model from live clones, calls the planner, calls the executor. |
| `refill.RefillExecutor` | The only code that writes inventories (§3 refill steps). |
| `bukkit.BukkitItemView` | `ItemView` over a cloned `ItemStack`. |
| `listener.*Listener` | One per trigger group; observers only. |
| `toggle.ToggleService` | `autoitem:enabled` PDC read/write. |
| `feedback.FeedbackService` | Sound and action bar. |
| `command.AutoItemCommand` | `toggle`, `reload`, tab completion. |

## 7. Milestones

Each ends with `./gradlew build` green, `/test-server` on all versions, a commit, and a list of G/X test IDs for Pun to run in-game.

1. **Mechanism + placing + eating/drinking.** Project scaffold (Gradle, run-paper servers incl. Folia, `CLAUDE.md`/`AGENTS.md`, Stop hook, skills, `.gitignore`, `CHANGELOG.md`, git init). `PluginConfig`/`ConfigLoader` for the keys M1 needs, model, `RefillPlanner`, `Remainders`, `CheckRecorder`, `RefillTask`, `RefillExecutor`, `block-place` and `consume` listeners, remainders. Unit tests U1–U30, K1–K4.
2. **Buckets, throwables, right-click items, bows/crossbows.** Listeners and their K tests.
3. **Tool breaking, full config, commands, toggle, worlds, feedback.** Remaining options, `/autoitem toggle|reload`, permissions, feedback service.
4. **Adversarial pass.** `/exploit-audit` + `exploit-reviewer` over I1–I9 and every X test; fix findings; rerun the matrix on all servers.
5. **Next update: Spigot and CraftBukkit** (about 7% of servers by bStats, Nov 2026). Not started. What it involves:
   - Paper-only events need Spigot paths: `PlayerLaunchProjectileEvent` → `ProjectileLaunchEvent` (shooter is a player; hand unknown, both hands as now), `PlayerElytraBoostEvent` → the right-click record covers it, `EntityLoadCrossbowEvent` and `PlayerReadyArrowEvent` have **no** Spigot equivalent. A bow draws its arrow before `EntityShootBowEvent`, so on Spigot the last arrow in a hand needs another way to be seen (e.g. record the arrow hand when drawing starts via `PlayerInteractEvent` with a bow, verified next tick as usual). `PlayerPickItemEvent` and `PlayerRecipeBookClickEvent` have no Spigot equivalent either (their discards are lost; refills stay conserved).
   - Scheduling: Folia's entity scheduler doesn't exist on Spigot; use the Bukkit scheduler there (one jar choosing at runtime).
   - Text: Adventure isn't on Spigot; MiniMessage/Adventure would have to be shaded (or bungee-chat used) for messages, the action bar and the sound.
   - Testing: Spigot test servers via BuildTools (run-paper doesn't download Spigot), and the bot suite on them.

## 8. Test matrix

Automated coverage: the unit and MockBukkit tests below, plus `tools/bot` (a Mineflayer client playing on real servers: Paper 1.21.1, 1.21.11 and 26.1.2, Folia 1.21.11 and 26.1.2; the bot can't join 26.2). The G and X rows the bot runs are listed in `tools/bot/tests.js`; what still needs a person is in `docs/IN-GAME-TESTS.md`.

### Unit tests (pure, `RefillPlanner` / `Remainders` / `ConfigLoader`)

| ID | Case | Expected |
|---|---|---|
| U1 | Target empty, one similar source in 9–35 | Refill from it |
| U2 | Target still holds a similar stack | `NOT_USED_UP` |
| U3 | Target holds something else | `SLOT_CHANGED` |
| U4 | Target holds exactly one allowed remainder (bowl after stew) | Refill |
| U5 | Target holds 2 bowls; or 1 renamed bowl | 2 bowls → `SLOT_CHANGED`; 1 renamed bowl → refill, bowl moved out intact |
| U6 | Ominous bottle (`NONE`), target holds a glass bottle | `SLOT_CHANGED` |
| U7 | Sources in 9–35 and 0–8 | 9–35 wins |
| U8 | Several sources of different sizes | Smallest first; tie → lowest index |
| U9 | Only hotbar source, `sources.hotbar: false` | `NO_SOURCE` |
| U10 | Only hotbar source, `sources.hotbar: true`; source is the held slot | Refill from hotbar, never the target itself; the held slot is never a source |
| U11 | Candidates only in armor, offhand (40) or crafting | `NO_SOURCE` |
| U12 | Speed vs Swiftness II vs Slowness potions | Only the identical potion |
| U13 | Splash vs lingering vs drinkable of the same effect | Never cross-match |
| U14 | Tipped arrows of different effects | Only the identical one |
| U15 | Enchanted / named block or food | Only identical |
| U16 | Ignored by material; by tag | Skip `IGNORED` |
| U17 | Each trigger disabled at task time; a merged check runs if any trigger is on; right-click merged with a disabled `block-place` | `DISABLED`; refill; `DISABLED` |
| U18 | Each gate (offline, dead, creative, spectator, disabled world, toggle off, no permission, master off) | `GATE` |
| U19 | World or game mode changed since the event | `CONTEXT_CHANGED` |
| U20 | `NORMAL` check with damageable snapshot (trident, shears); a tool break merged into a Speed potion check | `DAMAGEABLE`; still only a Speed potion |
| U21 | Tool break, same material, identical enchants available in hotbar and different enchants in main | Identical enchants win |
| U22 | Tool break, candidate custom-named, broken unnamed | Not picked |
| U23 | Tool break, both named identically | Picked |
| U24 | Tool break, candidate with different plugin data, custom data, lore or model; a broken custom tool with a plain and a custom spare | Not picked; only the custom spare |
| U25 | Tool break, several candidates, same enchants | Main before hotbar, then least damaged |
| U26 | Tool break with `tool-break: false` | `DISABLED` |
| U27 | Remainders inverse: BUCKET → all filled buckets; GLASS_BOTTLE → POTION, HONEY_BOTTLE | As listed |
| U28 | Dedup: two records same slot same tick; discard one slot / all | Same item: one check, first snapshot, union of remainders, TOOL_BREAK wins, right-click dropped next to a specific trigger. Different item: the later check replaces the earlier one. Discarded checks are gone |
| U29 | Two checks (main + offhand) needing the same source material, only one source; same after scrolling away from the refilled slot | Main hand gets it, offhand `NO_SOURCE` (the refilled slot is held, or reserved for this batch) |
| U30 | Config: invalid values, unknown materials, unknown tags, `NONE`, missing keys, broken MiniMessage | Warnings with key path; defaults |
| U31 | Conservation property: random inventories × random checks, apply plan to model | Per-material totals unchanged; target only overwritten when empty/allowed |

### MockBukkit integration (where MockBukkit supports the API)

| ID | Case |
|---|---|
| K1 | Fire `BlockPlaceEvent` with the last block, tick → slot refilled from 9–35, source cleared |
| K2 | `PlayerItemConsumeEvent` on last stew, set slot to bowl, tick → stew in slot, bowl elsewhere |
| K3 | Remainder with an otherwise full inventory → bowl lands in the source slot; offhand stew with an empty held slot → the bowl never lands in the hand; per-material totals equal |
| K4 | Event cancelled → nothing recorded |
| K5 | Per-event: snapshot amount read at event time is pre-decrement (amount-1 gate) |
| K6 | Each listener records the right slot and hand (incl. both-hands events): bucket fill/empty, thrown snowball from the offhand, non-consuming throw, right-click air (reported cancelled), spawn egg, denied use, left click, right-click equip, bow arrow in either hand, infinity, crossbow rocket, crossbow loading from storage with an unrelated offhand item |
| K8 | Discards: totem pop, drop, hand swap and recipe book after a right-click don't refill; a cancelled resurrect discards nothing; a real use after a discard still refills; a different item used later in the same slot is the one refilled; pending checks are dropped on plugin disable (inventory clicks, pick block and trades can't be built in MockBukkit; X4 is a bot test, the other two in-game) |
| K7 | `/autoitem toggle` flips and persists the PDC key and stops refills; needs `autoitem.toggle`; refused from the console. `/autoitem reload` applies a new config, reports warnings, keeps the old config on broken YAML, needs `autoitem.reload`. Usage on no/unknown argument; tab completion only lists allowed subcommands. No refill without `autoitem.use` or in a disabled world. Tool break off by default; on, identical enchantments win and a named tool never stands in. Sound by default, action bar when configured, nothing when no refill happened |

### In-game (Pun, any test server unless noted)

| ID | Scenario | Expected |
|---|---|---|
| G1 | Place the last cobblestone from hotbar slot 3, more in the main inventory | Slot 3 refilled, smallest main stack used |
| G2 | Last redstone dust, last wheat seeds | Refilled with dust/seeds |
| G3 | Eat the last steak; drink the last Speed potion with other potions in the inventory | Steak refilled; only a Speed potion refills, bottle moved out |
| G4 | Last honey bottle of a stack; then a stack of 3 | Bottle moves out only on the last one |
| G5 | Empty the last water bucket; fill the last empty bucket; milk a cow with the last bucket; catch a fish | Correct refill each time, filled bucket moved out |
| G6 | Throw last snowball, egg, pearl, splash potion, XP bottle, wind charge | Refilled |
| G7 | Last spawn egg, boat, bone meal, rocket (incl. elytra boost), end crystal, eye of ender, item frame, name tag, lead, wheat to a cow | Refilled |
| G8 | Bow with last arrow in offhand; last tipped arrow; crossbow loading last rocket | Offhand refilled, tipped only with identical |
| G9 | `tool-break: true`, break an enchanted pickaxe with a plain and an identical one available; a named sword | Identical enchants picked; named never substitutes |
| G10 | Throw a trident, equip armor by right-click | No refill |
| G11 | Q the last item; right-click the air with the last diamond and Q in the same tick (G11b) | No refill |
| G12 | `/autoitem toggle`, disabled world (via config + `/autoitem reload`), creative mode | No refill |
| G13 | Sound on by default; action bar after enabling it | As configured |
| V1 | Boot on Paper 1.21, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 and Folia 1.21.11, 26.1.2, 26.2, 26.3 | Enables, no errors or warnings |

### Exploit cases (G-style, run on Paper 26.2 and Folia)

"Conserved" = per-material counts in inventory + cursor + nearby drops equal before and after.

| ID | Scenario (same tick as using the last item) | Expected |
|---|---|---|
| X1 | Swap hands (F) | Target holds a different item → abort, conserved |
| X2 | Drop (Q) after using the second-to-last | Not recorded (amount 2), no refill |
| X3 | Change hotbar slot | Original slot refilled, not the new one |
| X4 | Inventory click with no inventory open (hacked client) moving an item into the slot | Abort; conserved |
| X5 | Click that takes the remainder onto the cursor | No refill (the click discards the check); cursor bowl untouched; conserved |
| X6 | Die, log out, change world, change game mode before the task | No refill; conserved |
| X7 | Another plugin cancels the event | Nothing recorded |
| X8 | Another plugin writes the target slot in the same tick | `NOT_USED_UP` or `SLOT_CHANGED`; nothing overwritten |
| X9 | Inventory full when the remainder comes back | The remainder goes into the source slot (a swap), so it always fits; nothing drops. Conserved |
| X10 | Both hands run out in the same tick (place with main, eat with offhand) | Both refilled if sources exist, main first; conserved |
| X11 | Per-world / per-gamemode inventory plugin swapping inventories on change | `CONTEXT_CHANGED`, nothing touched |
| X12 | Folia: player crosses a region border between event and task | Task runs on the new owner; conserved |
| X13 | Offhand totem, empty main hand, hold right-click, take lethal damage | Totem pops, offhand stays empty (no auto-totem); spares untouched |
| X1b / X4 | Right-click the air with the last diamond, then F, or a click that picks it up, in the same tick | No refill; conserved |

## 9. Open questions

Answered before M1:

- **Folia:** yes, via entity schedulers (§2 Threading).
- **Armor and elytra re-equip:** not in v1.
- **Published predecessor:** none; `~/IdeaProjects/AutoItem_old` was never released. Names follow this spec.

Still open:

- Final listing name for Modrinth/Hangar if "AutoItem" is taken (decide before release; it's a `plugin.yml` + `settings.gradle.kts` change).
- The tool-break tie-breakers (identical enchantments, then main before hotbar, then least damaged) go beyond the brief; keep or change? (The plugin-data rule became "same item ignoring wear" after the milestone 3 review: custom tools from other plugins kept their identity in data the PDC check couldn't see.)
