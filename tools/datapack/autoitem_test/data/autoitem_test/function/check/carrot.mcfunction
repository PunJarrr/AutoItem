# Check for Carrot on a stick used up: no refill
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:fishing_rod run scoreboard players set #ok autoitem_test 0
execute unless items entity @s inventory.0 minecraft:carrot_on_a_stick run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: Carrot on a stick used up: no refill", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: Carrot on a stick used up: no refill (look at the slots and tell Claude)", "color": "red"}
