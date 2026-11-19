# Check for Elytra boost with last rocket
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:firework_rocket run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: Elytra boost with last rocket", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: Elytra boost with last rocket (look at the slots and tell Claude)", "color": "red"}
