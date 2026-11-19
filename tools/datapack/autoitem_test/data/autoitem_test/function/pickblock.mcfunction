# Pick block right after last item: nothing lost or duplicated
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:cobblestone 1
item replace entity @s inventory.0 with minecraft:cobblestone 64
item replace entity @s hotbar.1 with minecraft:dirt 1
item replace entity @s hotbar.2 with minecraft:dirt 1
item replace entity @s hotbar.3 with minecraft:dirt 1
item replace entity @s hotbar.4 with minecraft:dirt 1
item replace entity @s hotbar.5 with minecraft:dirt 1
item replace entity @s hotbar.6 with minecraft:dirt 1
item replace entity @s hotbar.7 with minecraft:dirt 1
item replace entity @s hotbar.8 with minecraft:dirt 1
item replace entity @s inventory.1 with minecraft:stone 1
setblock ^ ^ ^2 minecraft:stone
tellraw @s {"text": "Pick block right after last item: nothing lost or duplicated", "color": "gold"}
tellraw @s {"text": "Place the cobblestone, then as fast as you can middle-click the stone block in front of you. The stone moves into your hand. Whether AutoItem refilled first or not, nothing may be lost or duplicated.", "color": "white"}
function autoitem_test:_check_hint {id:"pickblock"}
