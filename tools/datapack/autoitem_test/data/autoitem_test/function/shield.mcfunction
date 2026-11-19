# Shield breaks (needs tool-break: true)
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:stone_sword 1
item replace entity @s weapon.offhand with minecraft:shield[minecraft:damage=335] 1
item replace entity @s inventory.0 with minecraft:shield 1
difficulty normal
summon minecraft:zombie ^ ^ ^4
tellraw @s {"text": "Shield breaks (needs tool-break: true)", "color": "gold"}
tellraw @s {"text": "Set tool-break: true and /autoitem reload first. Block (hold right-click) while the zombie hits the shield until it breaks.", "color": "white"}
function autoitem_test:_check_hint {id:"shield"}
