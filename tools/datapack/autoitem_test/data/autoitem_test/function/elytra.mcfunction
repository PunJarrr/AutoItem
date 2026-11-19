# Elytra boost with last rocket
function autoitem_test:_reset
item replace entity @s armor.chest with minecraft:elytra 1
item replace entity @s hotbar.0 with minecraft:firework_rocket 1
item replace entity @s inventory.0 with minecraft:firework_rocket 16
tp @s ~ ~40 ~
tellraw @s {"text": "Elytra boost with last rocket", "color": "gold"}
tellraw @s {"text": "Start gliding (jump while falling), then use the rocket.", "color": "white"}
function autoitem_test:_check_hint {id:"elytra"}
