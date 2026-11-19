# In-game checks for Pun

The bot (`tools/bot`) plays the full scenario list on Paper 1.21, 1.21.1, 1.21.11 and 26.1.2 and Folia 1.21.11 and 26.1.2: placing, eating, potions, honey, buckets (empty, fill, cauldron), throwables, spawn eggs, bone meal, fireworks, end crystals, item frames, paintings, armor stands, boats, minecarts, leads, name tags, feeding, bottles, mooshrooms, bows, tipped arrows, crossbows, tool breaking (pickaxe, flint and steel, shears, fishing rod), toggle, disabled worlds, reload, sound, action bar, Q/F/click/scroll/totem cases, pick block, the armor-stand swap and full inventories.

So this list is only what the bot can't do: **Paper 26.3 and 26.2** (the bot can't join them), and a few things a bot can't easily reproduce. Test IDs refer to docs/SPEC.md §8.

## Setup

1. `./gradlew runServer` starts Paper 26.3 in `run/26.3`. Join `localhost` with a 26.3 client.
2. In the server console: `op <your name>`, then in-game `/gamemode survival`.
3. **Shortcut: the test data pack.** Every Paper test server gets it automatically (`tools/datapack`). Run `/function autoitem_test:menu` and click **[setup]** next to a test: it clears your inventory, puts the items in the right slots and summons any mob or block the test needs. Do the action, then click **[check]** for PASS or FAIL. Tests without a [check] need your eyes. Not available on Folia (it disables `/item` and `/execute`); use the manual commands below there.
4. Manual setup: `/clear` before each test. Put items in exact slots with `/item replace entity @s hotbar.0 with <item> <count>` (`hotbar.0`–`hotbar.8`, `inventory.0`–`inventory.26`, `weapon.offhand`).
5. "Refilled" means the hand gets the spare stack; a bowl, bottle or bucket left over takes the spare's old slot.

## 1. Quick pass on Paper 26.3

Everything here passed on 26.1.2 with the bot; this confirms 26.3, the version most servers run.

- [ ] **G1** `/item replace entity @s hotbar.0 with cobblestone 1` and `/item replace entity @s inventory.0 with cobblestone 64`. Place it: refilled.
- [ ] **G3** Last `mushroom_stew` (eat while hungry) with a spare: refilled, the bowl takes the spare's slot.
- [ ] **G5** Last `water_bucket` with a spare: empty it, refilled, the empty bucket takes the spare's slot.
- [ ] **G6** Last `snowball` with spares: throw it, refilled.
- [ ] **G8** Bow in hand, `arrow 1` in the offhand, arrows in the inventory: shoot, offhand refilled.
- [ ] **G9** Set `tool-break: true` in `run/26.3/plugins/AutoItem/config.yml`, `/autoitem reload`. `/give @s diamond_pickaxe[damage=1560]` in hand and a fresh `diamond_pickaxe` spare: mine a block, the spare takes its place.
- [ ] **G12** `/autoitem toggle`, use a last block: no refill. Toggle back.
- [ ] **G13** A refill makes a quiet pickup sound. Is the volume right for you?
- [ ] **X13** Empty main hand, `totem_of_undying` in the offhand, two spare totems. Hold right-click and `/damage @s 1000`: the totem saves you and the offhand stays **empty**.

## 2. Things the bot can't do (any server; 26.3 preferred)

- [ ] **Elytra boost:** fly with an elytra, use your last firework rocket while gliding (spares in the inventory): refilled.
- [ ] **Eye of ender:** throw your last one (spares in the inventory): refilled.
- [ ] **Milking:** last `bucket` on a cow: another empty bucket in hand, the milk bucket takes the spare's slot.
- [ ] **Catching a fish or axolotl** in your last water bucket: another water bucket in hand, the fish bucket takes the spare's slot.
- [ ] **G9 shield:** with `tool-break: true`, let a shield in the offhand break while blocking (e.g. `/give @s shield[damage=335]` and let a zombie or skeleton hit it): replaced by a spare shield.
- [ ] **Carrot on a stick** used up while riding a pig: no refill (it turns into a fishing rod).
- [ ] **Recipe book:** last item in hand, click a recipe in the inventory's recipe book that uses it: no refill.
- [ ] **Villager trade:** last emerald in hand, open a villager and pick an emerald trade at once: no refill (normally impossible to see; the trade pulls all your emeralds).
- [ ] **Pick block on 26.3** (middle-click a block you also have in your inventory) right after right-clicking with your last item: no refill.
- [ ] **X5:** eat the last stew with a spare in the inventory and, in the same moment, click the bowl onto your cursor (inventory open): no refill, the bowl is on the cursor.
- [ ] **X6:** use the last block and die at the same moment, with `/gamerule doImmediateRespawn true` and false, `keepInventory` on and off: no refill after respawn.
- [ ] **X11:** only if your server runs Multiverse-Inventories or PerWorldInventory: use the last item and go through a portal at once. Nothing moves in either world's inventory.
- [ ] **Folia 26.3:** `./gradlew runFolia`, then G1, G5 and G8 from section 1.
- [ ] **Paper 26.2** (`./gradlew runServer26_2`) and **Folia 26.2** (`./gradlew runFolia26_2`): G1, G3, G5, G8 and G9 from section 1, with a 26.2 client.
- [ ] **bStats:** after starting any test server, `plugins/bStats/config.yml` exists. With `metrics: false` in AutoItem's `config.yml` and a restart, the server still starts without errors. Real data only shows up on https://bstats.org/plugin/bukkit/AutoItem/35427 once the released plugin runs on servers (the first upload is about 30 minutes after a server starts). Add the `enabled_triggers` (Advanced Pie) and `tool_break` (Simple Pie) charts on that page so the custom data is displayed.
