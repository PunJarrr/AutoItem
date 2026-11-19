# X5 stew bowl onto cursor: no refill
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:mushroom_stew 1
item replace entity @s inventory.0 with minecraft:mushroom_stew 1
effect give @s minecraft:hunger 3 255 true
tellraw @s {"text": "X5 stew bowl onto cursor: no refill", "color": "gold"}
tellraw @s {"text": "Open your inventory and, when hungry, eat the stew; in the same moment click the bowl onto your cursor. Expected: no refill, the bowl stays on the cursor.", "color": "white"}
