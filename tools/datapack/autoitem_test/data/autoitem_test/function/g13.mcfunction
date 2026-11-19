# G13 refill sound
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:cobblestone 1
item replace entity @s inventory.0 with minecraft:cobblestone 64
tellraw @s {"text": "G13 refill sound", "color": "gold"}
tellraw @s {"text": "Place the cobblestone and listen: a quiet pickup sound. Is the volume right?", "color": "white"}
