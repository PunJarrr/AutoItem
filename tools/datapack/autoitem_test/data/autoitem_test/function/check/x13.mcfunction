# Check for X13 totem: offhand stays empty
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute if items entity @s weapon.offhand * run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: X13 totem: offhand stays empty", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: X13 totem: offhand stays empty (look at the slots and tell Claude)", "color": "red"}
