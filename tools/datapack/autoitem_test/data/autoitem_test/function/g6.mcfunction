# G6 last snowball
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:snowball 1
item replace entity @s inventory.0 with minecraft:snowball 16
tellraw @s {"text": "G6 last snowball", "color": "gold"}
tellraw @s {"text": "Throw the snowball.", "color": "white"}
function autoitem_test:_check_hint {id:"g6"}
