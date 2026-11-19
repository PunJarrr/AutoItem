# G8 bow, last arrow in offhand
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:bow 1
item replace entity @s weapon.offhand with minecraft:arrow 1
item replace entity @s inventory.0 with minecraft:arrow 64
tellraw @s {"text": "G8 bow, last arrow in offhand", "color": "gold"}
tellraw @s {"text": "Shoot the bow once.", "color": "white"}
function autoitem_test:_check_hint {id:"g8"}
