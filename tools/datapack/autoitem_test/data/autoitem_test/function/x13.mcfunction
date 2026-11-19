# X13 totem: offhand stays empty
function autoitem_test:_reset
item replace entity @s weapon.offhand with minecraft:totem_of_undying 1
item replace entity @s inventory.0 with minecraft:totem_of_undying 1
item replace entity @s inventory.1 with minecraft:totem_of_undying 1
tellraw @s {"text": "X13 totem: offhand stays empty", "color": "gold"}
tellraw @s {"text": "Hold right-click with the empty main hand, and while holding it type /damage @s 1000 (or have someone else run it). The totem saves you.", "color": "white"}
function autoitem_test:_check_hint {id:"x13"}
