# Recipe book: no refill
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:oak_planks 1
item replace entity @s inventory.0 with minecraft:oak_planks 1
item replace entity @s inventory.1 with minecraft:oak_planks 4
recipe give @s minecraft:stick
tellraw @s {"text": "Recipe book: no refill", "color": "gold"}
tellraw @s {"text": "Hold the planks, open your inventory, and click the stick recipe in the recipe book. Your hand must not be refilled by AutoItem (the recipe book may move planks itself).", "color": "white"}
