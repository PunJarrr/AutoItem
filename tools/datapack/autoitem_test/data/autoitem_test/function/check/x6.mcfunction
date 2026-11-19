# Check for X6 die right after the last block
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute store result score #n0 autoitem_test if items entity @s container.* minecraft:cobblestone
execute unless score #n0 autoitem_test matches 64 run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: X6 die right after the last block", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: X6 die right after the last block (tell Claude what's in the slots)", "color": "red"}
execute if score #ok autoitem_test matches 0 run tellraw @s ["", {"text": "  cobblestone: ", "color": "gray"}, {"score": {"name": "#n0", "objective": "autoitem_test"}, "color": "white"}, {"text": " (expected 64)", "color": "gray"}]
