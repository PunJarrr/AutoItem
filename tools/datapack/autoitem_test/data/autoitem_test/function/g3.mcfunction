# G3 last stew
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:mushroom_stew 1
item replace entity @s inventory.0 with minecraft:mushroom_stew 1
effect give @s minecraft:hunger 3 255 true
tellraw @s {"text": "G3 last stew", "color": "gold"}
tellraw @s {"text": "Wait until you're hungry, then eat the stew.", "color": "white"}
function autoitem_test:_check_hint {id:"g3"}
