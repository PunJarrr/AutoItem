# Carrot on a stick used up: no refill
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:carrot_on_a_stick[minecraft:damage=24] 1
item replace entity @s inventory.0 with minecraft:carrot_on_a_stick 1
item replace entity @s hotbar.1 with minecraft:saddle 1
summon minecraft:pig ^ ^ ^3
tellraw @s {"text": "Carrot on a stick used up: no refill", "color": "gold"}
tellraw @s {"text": "Put the saddle on the pig, ride it, and boost with the carrot on a stick. It turns into a fishing rod.", "color": "white"}
function autoitem_test:_check_hint {id:"carrot"}
