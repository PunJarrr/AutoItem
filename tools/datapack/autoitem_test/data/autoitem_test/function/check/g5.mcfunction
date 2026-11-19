# Check for G5 last water bucket
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:water_bucket run scoreboard players set #ok autoitem_test 0
execute unless items entity @s inventory.0 minecraft:bucket run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G5 last water bucket", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G5 last water bucket (look at the slots and tell Claude)", "color": "red"}
