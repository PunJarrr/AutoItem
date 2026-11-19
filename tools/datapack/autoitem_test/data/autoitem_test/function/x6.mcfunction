# X6 die right after the last block
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:cobblestone 1
item replace entity @s inventory.0 with minecraft:cobblestone 64
function autoitem_test:_keep_inventory
tag @s add autoitem_x6
schedule function autoitem_test:_x6_kill 100t
tellraw @s {"text": "X6 die right after the last block", "color": "gold"}
tellraw @s {"text": "keepInventory is now on. You die in 5 seconds: place the cobblestone right before that. Respawn, then check that you still have all 64.", "color": "white"}
function autoitem_test:_check_hint {id:"x6"}
