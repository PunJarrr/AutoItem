# Check for G6 last snowball
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s hotbar.0 minecraft:snowball run scoreboard players set #ok autoitem_test 0
execute if items entity @s inventory.0 * run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: G6 last snowball", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: G6 last snowball (look at the slots and tell Claude)", "color": "red"}
