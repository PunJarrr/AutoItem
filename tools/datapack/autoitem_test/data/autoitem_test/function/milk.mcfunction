# Milking with last bucket
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:bucket 1
item replace entity @s inventory.0 with minecraft:bucket 1
summon minecraft:cow ^ ^ ^3 {NoAI:1b}
tellraw @s {"text": "Milking with last bucket", "color": "gold"}
tellraw @s {"text": "Milk the cow in front of you.", "color": "white"}
function autoitem_test:_check_hint {id:"milk"}
