# G3 last stew
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:mushroom_stew 1
item replace entity @s inventory.0 with minecraft:mushroom_stew 1
effect give @s minecraft:hunger 5 255 true
tellraw @s {"text": "G3 last stew", "color": "gold"}
tellraw @s {"text": "Wait about 5 seconds until your food bar drops, then eat the stew.", "color": "white"}
function autoitem_test:_check_hint {id:"g3"}
