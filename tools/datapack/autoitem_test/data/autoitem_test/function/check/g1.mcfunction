# Check for G1 place last block
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:cobblestone run scoreboard players set #ok autoitem_test 0
execute if items entity @s inventory.0 * run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G1 place last block", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G1 place last block (look at the slots and tell Claude)", "color": "red"}
