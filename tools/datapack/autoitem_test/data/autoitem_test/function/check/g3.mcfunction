# Check for G3 last stew
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:mushroom_stew run scoreboard players set #ok autoitem_test 0
execute unless items entity @s inventory.0 minecraft:bowl run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G3 last stew", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G3 last stew (look at the slots and tell Claude)", "color": "red"}
