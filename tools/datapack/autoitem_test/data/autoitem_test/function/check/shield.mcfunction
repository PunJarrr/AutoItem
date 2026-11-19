# Check for Shield breaks (needs tool-break: true)
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute unless items entity @s weapon.offhand minecraft:shield run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: Shield breaks (needs tool-break: true)", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: Shield breaks (needs tool-break: true) (tell Claude what's in the slots)", "color": "red"}
