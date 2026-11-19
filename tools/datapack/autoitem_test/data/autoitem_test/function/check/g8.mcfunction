# Check for G8 bow, last arrow in offhand
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s weapon.offhand minecraft:arrow run scoreboard players set #ok autoitem_test 0
execute if items entity @s inventory.0 * run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G8 bow, last arrow in offhand", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G8 bow, last arrow in offhand (look at the slots and tell Claude)", "color": "red"}
