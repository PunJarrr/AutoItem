# G12 toggled off: no refill
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:cobblestone 1
item replace entity @s inventory.0 with minecraft:cobblestone 64
tellraw @s {"text": "G12 toggled off: no refill", "color": "gold"}
tellraw @s {"text": "Run /autoitem toggle (off), place the cobblestone, check, then /autoitem toggle again.", "color": "white"}
function autoitem_test:_check_hint {id:"g12"}
