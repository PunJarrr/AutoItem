# Check for Milking with last bucket
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:bucket run scoreboard players set #ok autoitem_test 0
execute unless items entity @s inventory.0 minecraft:milk_bucket run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: Milking with last bucket", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: Milking with last bucket (look at the slots and tell Claude)", "color": "red"}
