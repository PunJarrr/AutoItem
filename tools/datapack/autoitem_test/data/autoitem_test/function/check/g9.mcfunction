# Check for G9 tool break (needs tool-break: true)
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:diamond_pickaxe run scoreboard players set #ok autoitem_test 0
execute if items entity @s inventory.0 * run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G9 tool break (needs tool-break: true)", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G9 tool break (needs tool-break: true) (look at the slots and tell Claude)", "color": "red"}
