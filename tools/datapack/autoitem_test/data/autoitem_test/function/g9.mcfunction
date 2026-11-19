# G9 tool break (needs tool-break: true)
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:diamond_pickaxe[minecraft:damage=1560] 1
item replace entity @s inventory.0 with minecraft:diamond_pickaxe 1
setblock ^ ^ ^2 minecraft:stone
tellraw @s {"text": "G9 tool break (needs tool-break: true)", "color": "gold"}
tellraw @s {"text": "Set tool-break: true in plugins/AutoItem/config.yml and /autoitem reload first. Then mine the stone in front of you.", "color": "white"}
function autoitem_test:_check_hint {id:"g9"}
