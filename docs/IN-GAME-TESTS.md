# In-game checks for Pun

Everything the automated tests can't cover. The bot already passes the full scenario list on Paper 1.21.1, Paper 1.21.11 and Folia 1.21.11 (`tools/bot`), so this list is mostly about **Paper 26.2** (the bot can't join 26.x), items the bot doesn't use, and things only a real client shows.

Test IDs refer to docs/SPEC.md §8.

## Setup

1. `./gradlew runServer` starts Paper 26.2 in `run/26.2`. Join `localhost` with a 26.2 client.
2. In the server console: `op <your name>`, then in-game `/gamemode survival`.
3. Before each test, `/clear` your inventory. Put items in exact slots with `/item replace entity @s hotbar.0 with <item> <count>` (`hotbar.0`–`hotbar.8`, `inventory.0`–`inventory.26`, `weapon.offhand`), or use `/give`.
4. "Refilled" means the hand slot gets the spare stack and the spare's old slot is empty. "Moves out" means the bowl, bottle or bucket goes to the first free inventory slot.

## 1. Basics (milestone 1)

- [ ] **G1** `/item replace entity @s hotbar.3 with cobblestone 1`, `/item replace entity @s inventory.11 with cobblestone 10`, `/item replace entity @s inventory.2 with cobblestone 64`. Hold slot 3, place it. Expected: slot 3 has 10 (smallest main stack used first).
- [ ] **G2** Same with `redstone 1` (+ `redstone 5` spare) and `wheat_seeds 1` (+ spare) on farmland. Expected: refilled with dust or seeds, not with wire or crops.
- [ ] **G3** `mushroom_stew 1` in hand + 1 spare (eat while hungry). Expected: refilled, bowl moves out. Then `/give @s potion[potion_contents={potion:"minecraft:swiftness"}]` ×2 (one in hand) and `/give @s potion[potion_contents={potion:"minecraft:slowness"}]`. Drink. Expected: only a Swiftness potion refills, glass bottle moves out.
- [ ] **G4** `honey_bottle 3` in hand + `honey_bottle 2` spare. Drink three. Expected: no refill until the last one; three glass bottles in the end.
- [ ] **G11** Last item in hand, press **Q**. Expected: no refill. Also: right-click the air with the last diamond and press Q at the same moment: no refill.
- [ ] **X1** Last cobblestone in hand, shield in offhand, place and press **F** immediately. Expected: shield in hand, nothing else moves.
- [ ] **X3** Place the last block and scroll away at the same moment. Expected: the slot you used is refilled, not the new one.
- [ ] **X10** One torch in each hand + a spare stack: place from main hand, then offhand. Expected: main hand refilled; the offhand doesn't empty the main hand.

## 2. Triggers (milestone 2)

- [ ] **G5** Last `water_bucket` + a spare: empty it. Expected: refilled, empty bucket moves out. Last `bucket` + spare empty buckets: fill it from water. Expected: another empty bucket in hand, filled one moves out. Repeat by **milking a cow** and **catching a fish/axolotl** with the last water bucket.
- [ ] **G6** Throw the last snowball, egg, ender pearl, `splash_potion[potion_contents={potion:"minecraft:swiftness"}]` (with a Slowness splash and a lingering Swiftness as decoys), experience bottle, wind charge. Expected: refilled; potions only from the identical potion.
- [ ] **G7** Last of each, with a spare: spawn egg, bone meal on grass, firework on the ground, end crystal on obsidian, item frame, boat, wheat to a cow. *Not bot-tested, so the important ones:* **minecart** on rail, **painting**, **armor stand**, **lead** on a cow, **name tag** (rename it in an anvil first), **eye of ender**, **firework while flying with elytra**, **water bucket into a cauldron**, **glass bottle filled from water**, **bowl on a mooshroom**. Expected: refilled each time.
- [ ] **G8** Bow in hand, `arrow 1` in offhand + 32 spare: shoot. Expected: offhand refilled. Same with `tipped_arrow[potion_contents={potion:"minecraft:poison"}]` (decoys: harming tipped arrows, plain arrows): only poison refills. Crossbow + `firework_rocket 1` in offhand + spares: load it. Expected: offhand refilled.
- [ ] **G10** Throw your only trident (with a spare in the inventory). Expected: no refill. Right-click an `iron_helmet` (spare in the inventory). Expected: it's worn, the hand is not refilled.
- [ ] **X13** Empty main hand, `totem_of_undying` in offhand, two spare totems. Hold right-click and take lethal damage (`/damage @s 1000`). Expected: the totem saves you, the offhand stays **empty** (no auto-totem).

## 3. Tool breaking, commands, feedback (milestone 3)

Set `tool-break: true` in `run/26.2/plugins/AutoItem/config.yml`, then `/autoitem reload`.

- [ ] **G9** `/give @s diamond_pickaxe[damage=1560]` in hand + a fresh `diamond_pickaxe` spare: mine one block. Expected: the spare takes its place.
- [ ] **G9** Broken `diamond_pickaxe[damage=1560,enchantments={"minecraft:efficiency":5}]`, spares: a plain pickaxe and an efficiency-5 one. Expected: the efficiency one is chosen.
- [ ] **G9** Spare only `diamond_pickaxe[custom_name="Excavator"]` (or with `lore=["Ruby"]`). Expected: **no** replacement.
- [ ] **G9** Not bot-tested: a **shield** breaking in the offhand while blocking, a **fishing rod**, **flint and steel**, **shears**. Expected: replaced by the same item. A **carrot on a stick** used up: no refill.
- [ ] **G12** `/autoitem toggle`, then use a last block: no refill; toggle back. Add your world to `disabled-worlds`, `/autoitem reload`: no refill there. In creative: nothing happens.
- [ ] **G13** A refill makes a quiet pickup sound. Set `action-bar: true`, reload: "Refilled …" appears above the hotbar. Break the YAML (`enabled: [true`), `/autoitem reload`: "Config has errors, kept the previous one", and the old settings still work.

## 4. Edge cases only a real setup shows (milestone 4)

- [ ] **X5** Eat the last stew with a spare stew in the inventory, and in the same moment click the bowl onto your cursor (inventory open). Expected: no refill, the bowl is on the cursor, nothing lost.
- [ ] **X6** Use the last block and die in the same moment, with `/gamerule doImmediateRespawn true` and false, and with `keepInventory` on and off. Expected: no refill after respawn; with keepInventory off, everything is in the death drop.
- [ ] **X11** Only if the server uses Multiverse-Inventories or PerWorldInventory: use the last item and go through a portal at once. Expected: nothing moves in either world's inventory.
- [ ] **Folia 26.2** `./gradlew runFolia`: a quick pass of G1, G5 (water bucket) and G8 (bow) to see it works on 26.x Folia too.

## 5. Milestone 4 audit fixes

- [ ] Right-click the air with the last diamond, then middle-click (pick block) another item: no refill.
- [ ] Last emerald in hand, right-click a villager and pick an emerald trade at once: no refill.
- [ ] Last item in hand, use the recipe book to fill the crafting grid with it: no refill.
- [ ] Armor stand with arms (`/summon armor_stand ~ ~ ~ {ShowArms:1b}`) holding cobblestone: swap your last diamond onto it, then place the cobblestone you got back. Expected: refilled with cobblestone, not diamond.
- [ ] Eat the last stew from the **offhand** while the held hotbar slot is empty. Expected: the bowl goes to the slot the spare stew came from, not into your hand.
