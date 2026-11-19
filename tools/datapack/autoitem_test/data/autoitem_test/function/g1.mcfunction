# G1 place last block
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:cobblestone 1
item replace entity @s inventory.0 with minecraft:cobblestone 64
tellraw @s {"text": "G1 place last block", "color": "gold"}
tellraw @s {"text": "Place the cobblestone in slot 1.", "color": "white"}
function autoitem_test:_check_hint {id:"g1"}
