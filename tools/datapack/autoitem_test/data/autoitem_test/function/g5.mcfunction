# G5 last water bucket
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:water_bucket 1
item replace entity @s inventory.0 with minecraft:water_bucket 1
tellraw @s {"text": "G5 last water bucket", "color": "gold"}
tellraw @s {"text": "Empty the water bucket on the ground.", "color": "white"}
function autoitem_test:_check_hint {id:"g5"}
