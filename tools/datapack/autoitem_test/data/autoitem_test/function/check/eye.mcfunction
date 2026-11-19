# Check for Eye of ender, last one
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:ender_eye run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: Eye of ender, last one", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: Eye of ender, last one (look at the slots and tell Claude)", "color": "red"}
