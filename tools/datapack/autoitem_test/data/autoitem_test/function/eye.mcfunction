# Eye of ender, last one
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:ender_eye 1
item replace entity @s inventory.0 with minecraft:ender_eye 16
tellraw @s {"text": "Eye of ender, last one", "color": "gold"}
tellraw @s {"text": "Throw the eye of ender (needs a stronghold in this world).", "color": "white"}
function autoitem_test:_check_hint {id:"eye"}
