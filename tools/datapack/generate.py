"""Generates tools/datapack/autoitem_test. Edit the tests here, then run: python3 tools/datapack/generate.py"""
import json, pathlib, shutil, sys
root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).parent / "autoitem_test"
if root.exists(): shutil.rmtree(root)
NS = "autoitem_test"
fn = root / "data" / NS / "function"
new_fn = root / "new_text" / "data" / NS / "function"
# 1.21.11 renamed game rules (keepInventory -> keep_inventory)
new_rules_fn = root / "new_gamerules" / "data" / NS / "function"

def w(path, lines):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n")

def say(text, color="gray"):
    return 'tellraw @s ' + json.dumps({"text": text, "color": color})

# id, title, setup lines, instructions, checks: a list of
#   ("if"/"unless", slot, item)  the slot holds (or doesn't hold) the item; "*" = anything
#   ("count", item, n)           the 36 storage slots hold exactly n of the item in total
T = []
def t(id, title, setup, how, checks=None):
    T.append((id, title, setup, how, checks))

t("g1", "G1 place last block", [
    "item replace entity @s hotbar.0 with minecraft:cobblestone 1",
    "item replace entity @s inventory.0 with minecraft:cobblestone 64"],
  "Place the cobblestone in slot 1.",
  [("if", "hotbar.0", "minecraft:cobblestone"), ("unless", "inventory.0", "*")])
t("g3", "G3 last stew", [
    "item replace entity @s hotbar.0 with minecraft:mushroom_stew 1",
    "item replace entity @s inventory.0 with minecraft:mushroom_stew 1",
    "effect give @s minecraft:hunger 5 255 true"],
  "Wait about 5 seconds until your food bar drops, then eat the stew.",
  [("if", "hotbar.0", "minecraft:mushroom_stew"), ("if", "inventory.0", "minecraft:bowl")])
t("g5", "G5 last water bucket", [
    "item replace entity @s hotbar.0 with minecraft:water_bucket 1",
    "item replace entity @s inventory.0 with minecraft:water_bucket 1"],
  "Empty the water bucket on the ground.",
  [("if", "hotbar.0", "minecraft:water_bucket"), ("if", "inventory.0", "minecraft:bucket")])
t("g6", "G6 last snowball", [
    "item replace entity @s hotbar.0 with minecraft:snowball 1",
    "item replace entity @s inventory.0 with minecraft:snowball 16"],
  "Throw the snowball.",
  [("if", "hotbar.0", "minecraft:snowball"), ("unless", "inventory.0", "*")])
t("g8", "G8 bow, last arrow in offhand", [
    "item replace entity @s hotbar.0 with minecraft:bow 1",
    "item replace entity @s weapon.offhand with minecraft:arrow 1",
    "item replace entity @s inventory.0 with minecraft:arrow 64"],
  "Shoot the bow once.",
  [("if", "weapon.offhand", "minecraft:arrow"), ("unless", "inventory.0", "*")])
t("g9", "G9 tool break (needs tool-break: true)", [
    "item replace entity @s hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=1560] 1",
    "item replace entity @s inventory.0 with minecraft:diamond_pickaxe 1",
    "setblock ^ ^ ^2 minecraft:stone"],
  "Set tool-break: true in plugins/AutoItem/config.yml and /autoitem reload first. Then mine the stone in front of you.",
  [("if", "hotbar.0", "minecraft:diamond_pickaxe"), ("unless", "inventory.0", "*")])
t("g12", "G12 toggled off: no refill", [
    "item replace entity @s hotbar.0 with minecraft:cobblestone 1",
    "item replace entity @s inventory.0 with minecraft:cobblestone 64"],
  "Run /autoitem toggle (off), place the cobblestone, check, then /autoitem toggle again.",
  [("unless", "hotbar.0", "*"), ("if", "inventory.0", "minecraft:cobblestone")])
t("g13", "G13 refill sound", [
    "item replace entity @s hotbar.0 with minecraft:cobblestone 1",
    "item replace entity @s inventory.0 with minecraft:cobblestone 64"],
  "Place the cobblestone and listen: a quiet pickup sound. Is the volume right?")
t("x13", "X13 totem: offhand stays empty", [
    "item replace entity @s weapon.offhand with minecraft:totem_of_undying 1",
    "item replace entity @s inventory.0 with minecraft:totem_of_undying 1",
    "item replace entity @s inventory.1 with minecraft:totem_of_undying 1"],
  "Hold right-click with the empty main hand, and while holding it type /damage @s 1000 (or have someone else run it). The totem saves you.",
  [("unless", "weapon.offhand", "*")])
t("elytra", "Elytra boost with last rocket", [
    "item replace entity @s armor.chest with minecraft:elytra 1",
    "item replace entity @s hotbar.0 with minecraft:firework_rocket 1",
    "item replace entity @s inventory.0 with minecraft:firework_rocket 16",
    "tp @s ~ ~40 ~"],
  "Start gliding (jump while falling), then use the rocket.",
  [("if", "hotbar.0", "minecraft:firework_rocket")])
t("eye", "Eye of ender, last one", [
    "item replace entity @s hotbar.0 with minecraft:ender_eye 1",
    "item replace entity @s inventory.0 with minecraft:ender_eye 16"],
  "Throw the eye of ender (needs a stronghold in this world).",
  [("if", "hotbar.0", "minecraft:ender_eye")])
t("milk", "Milking with last bucket", [
    "item replace entity @s hotbar.0 with minecraft:bucket 1",
    "item replace entity @s inventory.0 with minecraft:bucket 1",
    "summon minecraft:cow ^ ^ ^3 {NoAI:1b}"],
  "Milk the cow in front of you.",
  [("if", "hotbar.0", "minecraft:bucket"), ("if", "inventory.0", "minecraft:milk_bucket")])
t("fish", "Catch a fish in last water bucket", [
    "item replace entity @s hotbar.0 with minecraft:water_bucket 1",
    "item replace entity @s inventory.0 with minecraft:water_bucket 1",
    "fill ^-1 ^-1 ^2 ^1 ^-1 ^4 minecraft:glass",
    "fill ^-1 ^ ^2 ^1 ^ ^4 minecraft:water",
    "summon minecraft:cod ^ ^ ^3 {NoAI:1b}"],
  "Use the water bucket on the cod in the pool in front of you.",
  [("if", "hotbar.0", "minecraft:water_bucket"), ("if", "inventory.0", "minecraft:cod_bucket")])
t("shield", "Shield breaks (needs tool-break: true)", [
    "item replace entity @s hotbar.0 with minecraft:stone_sword 1",
    "item replace entity @s weapon.offhand with minecraft:shield[minecraft:damage=335] 1",
    "item replace entity @s inventory.0 with minecraft:shield 1",
    "difficulty normal",
    "summon minecraft:zombie ^ ^ ^4"],
  "Set tool-break: true and /autoitem reload first. Block (hold right-click) while the zombie hits the shield until it breaks.",
  [("if", "weapon.offhand", "minecraft:shield")])
t("carrot", "Carrot on a stick used up: no refill", [
    "item replace entity @s hotbar.0 with minecraft:carrot_on_a_stick[minecraft:damage=24] 1",
    "item replace entity @s inventory.0 with minecraft:carrot_on_a_stick 1",
    "item replace entity @s hotbar.1 with minecraft:saddle 1",
    "summon minecraft:pig ^ ^ ^3"],
  "Put the saddle on the pig, ride it, and boost with the carrot on a stick. It turns into a fishing rod.",
  [("if", "hotbar.0", "minecraft:fishing_rod"), ("if", "inventory.0", "minecraft:carrot_on_a_stick")])
t("recipe", "Recipe book: no refill", [
    "item replace entity @s hotbar.0 with minecraft:oak_planks 1",
    "item replace entity @s inventory.0 with minecraft:oak_planks 1",
    "item replace entity @s inventory.1 with minecraft:oak_planks 4",
    "recipe give @s minecraft:stick"],
  "Hold the planks, open your inventory, and click the stick recipe in the recipe book. Your hand must not be refilled by AutoItem (the recipe book may move planks itself).")
t("trade", "Villager trade with last emerald: no refill", [
    "item replace entity @s hotbar.0 with minecraft:emerald 1",
    "item replace entity @s inventory.0 with minecraft:emerald 10",
    'summon minecraft:villager ^ ^ ^2 {NoAI:1b,VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"},Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:bread",count:1},maxUses:999}]}}'],
  "Open the villager and pick the emerald trade at once.")
t("pickblock", "Pick block right after last item: nothing lost or duplicated", [
    "item replace entity @s hotbar.0 with minecraft:cobblestone 1",
    "item replace entity @s inventory.0 with minecraft:cobblestone 64",
    "item replace entity @s hotbar.1 with minecraft:dirt 1",
    "item replace entity @s hotbar.2 with minecraft:dirt 1",
    "item replace entity @s hotbar.3 with minecraft:dirt 1",
    "item replace entity @s hotbar.4 with minecraft:dirt 1",
    "item replace entity @s hotbar.5 with minecraft:dirt 1",
    "item replace entity @s hotbar.6 with minecraft:dirt 1",
    "item replace entity @s hotbar.7 with minecraft:dirt 1",
    "item replace entity @s hotbar.8 with minecraft:dirt 1",
    "item replace entity @s inventory.1 with minecraft:stone 1",
    "setblock ^ ^ ^2 minecraft:stone"],
  "Place the cobblestone, then as fast as you can middle-click the stone block in front of you. The stone moves into your hand. Whether AutoItem refilled first or not, nothing may be lost or duplicated.",
  [("count", "minecraft:cobblestone", 64), ("count", "minecraft:stone", 1), ("count", "minecraft:dirt", 8)])
t("x6", "X6 die right after the last block", [
    "item replace entity @s hotbar.0 with minecraft:cobblestone 1",
    "item replace entity @s inventory.0 with minecraft:cobblestone 64",
    "function autoitem_test:_keep_inventory",
    "tag @s add autoitem_x6",
    "schedule function autoitem_test:_x6_kill 100t"],
  "keepInventory is now on. You die in 5 seconds: place the cobblestone right before that. Respawn, then check that you still have all 64.",
  [("count", "minecraft:cobblestone", 64)])

for id, title, setup, how, checks in T:
    lines = ["# " + title, "function autoitem_test:_reset"] + setup + [
        say(title, "gold"), say(how, "white")]
    if checks:
        lines.append("function autoitem_test:_check_hint {id:\"" + id + "\"}")
    w(fn / (id + ".mcfunction"), lines)
    if checks:
        c = ["# Check for " + title, "scoreboard objectives add autoitem_test dummy",
             "scoreboard players set #ok autoitem_test 1"]
        detail = []
        for i, (kind, a, b) in enumerate(checks):
            if kind == "count":
                holder = f"#n{i}"
                c.append(f"execute store result score {holder} autoitem_test if items entity @s container.* {a}")
                c.append(f"execute unless score {holder} autoitem_test matches {b} run scoreboard players set #ok autoitem_test 0")
                detail.append(["", {"text": f"  {a.split(':')[1]}: ", "color": "gray"},
                               {"score": {"name": holder, "objective": "autoitem_test"}, "color": "white"},
                               {"text": f" (expected {b})", "color": "gray"}])
            else:
                neg = "unless" if kind == "if" else "if"
                c.append(f"execute {neg} items entity @s {a} {b} run scoreboard players set #ok autoitem_test 0")
                if a.startswith(("hotbar.", "inventory.")):
                    n = int(a.split(".")[1]) + (9 if a.startswith("inventory.") else 0)
                    detail.append(["", {"text": f"  {a}: ", "color": "gray"},
                                   {"nbt": f"Inventory[{{Slot:{n}b}}]", "entity": "@s", "color": "white"}])
        c.append("execute if score #ok autoitem_test matches 1 run " + say("PASS: " + title, "green"))
        c.append("execute if score #ok autoitem_test matches 0 run " + say("FAIL: " + title + " (tell Claude what's in the slots)", "red"))
        for d in detail:
            c.append("execute if score #ok autoitem_test matches 0 run tellraw @s " + json.dumps(d))
        w(fn / "check" / (id + ".mcfunction"), c)

w(fn / "_reset.mcfunction", [
    "# Common setup before every test",
    "clear @s",
    "gamemode survival @s",
    "effect clear @s",
    "kill @e[type=!minecraft:player,distance=..16]",
    "effect give @s minecraft:resistance 3 4 true"])

def menu_line(id, title, checks, new):
    ce = "click_event" if new else "clickEvent"
    def btn(label, cmd, color):
        if new:
            return {"text": label, "color": color, ce: {"action": "run_command", "command": cmd}}
        return {"text": label, "color": color, ce: {"action": "run_command", "value": cmd}}
    parts = ["", btn("[setup] ", "/function autoitem_test:" + id, "aqua")]
    if checks:
        parts.append(btn("[check] ", "/function autoitem_test:check/" + id, "green"))
    parts.append({"text": title, "color": "white"})
    return "tellraw @s " + json.dumps(parts)

def hint(new):
    ce = "click_event" if new else "clickEvent"
    action = {"action": "run_command", "command": "/function autoitem_test:check/$(id)"} if new else {"action": "run_command", "value": "/function autoitem_test:check/$(id)"}
    return ["$tellraw @s " + json.dumps(["", {"text": "Then run /function autoitem_test:check/$(id) or click ", "color": "gray"},
                                          {"text": "[check]", "color": "green", ce: action}])]

w(fn / "_x6_kill.mcfunction", ["kill @a[tag=autoitem_x6]", "tag @a remove autoitem_x6"])
w(fn / "_keep_inventory.mcfunction", ["gamerule keepInventory true"])
w(new_rules_fn / "_keep_inventory.mcfunction", ["gamerule minecraft:keep_inventory true"])

for new, base in ((False, fn), (True, new_fn)):
    m = ["# Lists every test with clickable setup and check buttons",
         say("AutoItem in-game tests: click [setup], do the action, click [check].", "gold")]
    for id, title, setup, how, checks in T:
        m.append(menu_line(id, title, checks, new))
    w(base / "menu.mcfunction", m)
    w(base / "_check_hint.mcfunction", hint(new))

(root / "pack.mcmeta").write_text(json.dumps({
    "pack": {"description": "AutoItem in-game test setups: /function autoitem_test:menu",
             "pack_format": 48, "supported_formats": [48, 121], "min_format": 48, "max_format": 121},
    "overlays": {"entries": [
        {"directory": "new_text", "formats": [71, 121], "min_format": 71, "max_format": 121},
        {"directory": "new_gamerules", "formats": [94, 121], "min_format": 94, "max_format": 121}]}
}, indent=2) + "\n")
print(len(T), "tests")
