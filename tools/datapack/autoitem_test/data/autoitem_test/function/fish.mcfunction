# Catch a fish in last water bucket
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:water_bucket 1
item replace entity @s inventory.0 with minecraft:water_bucket 1
fill ^-1 ^-1 ^2 ^1 ^-1 ^4 minecraft:glass
fill ^-1 ^ ^2 ^1 ^ ^4 minecraft:water
summon minecraft:cod ^ ^ ^3 {NoAI:1b}
tellraw @s {"text": "Catch a fish in last water bucket", "color": "gold"}
tellraw @s {"text": "Use the water bucket on the cod in the pool in front of you.", "color": "white"}
function autoitem_test:_check_hint {id:"fish"}
