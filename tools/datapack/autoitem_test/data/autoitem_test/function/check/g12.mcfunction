# Check for G12 toggled off: no refill
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute if items entity @s hotbar.0 * run scoreboard players set #ok autoitem_test 0
execute unless items entity @s inventory.0 minecraft:cobblestone run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G12 toggled off: no refill", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G12 toggled off: no refill (look at the slots and tell Claude)", "color": "red"}
