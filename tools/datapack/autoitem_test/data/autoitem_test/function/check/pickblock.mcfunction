# Check for Pick block right after last item: nothing lost or duplicated
scoreboard objectives add autoitem_test dummy
scoreboard players set #ok autoitem_test 1
execute store result score #n0 autoitem_test if items entity @s container.* minecraft:cobblestone
execute unless score #n0 autoitem_test matches 64 run scoreboard players set #ok autoitem_test 0
execute store result score #n1 autoitem_test if items entity @s container.* minecraft:stone
execute unless score #n1 autoitem_test matches 1 run scoreboard players set #ok autoitem_test 0
execute store result score #n2 autoitem_test if items entity @s container.* minecraft:dirt
execute unless score #n2 autoitem_test matches 8 run scoreboard players set #ok autoitem_test 0
execute if score #ok autoitem_test matches 1 run tellraw @s {"text": "PASS: Pick block right after last item: nothing lost or duplicated", "color": "green"}
execute if score #ok autoitem_test matches 0 run tellraw @s {"text": "FAIL: Pick block right after last item: nothing lost or duplicated (tell Claude what's in the slots)", "color": "red"}
execute if score #ok autoitem_test matches 0 run tellraw @s ["", {"text": "  cobblestone: ", "color": "gray"}, {"score": {"name": "#n0", "objective": "autoitem_test"}, "color": "white"}, {"text": " (expected 64)", "color": "gray"}]
execute if score #ok autoitem_test matches 0 run tellraw @s ["", {"text": "  stone: ", "color": "gray"}, {"score": {"name": "#n1", "objective": "autoitem_test"}, "color": "white"}, {"text": " (expected 1)", "color": "gray"}]
execute if score #ok autoitem_test matches 0 run tellraw @s ["", {"text": "  dirt: ", "color": "gray"}, {"score": {"name": "#n2", "objective": "autoitem_test"}, "color": "white"}, {"text": " (expected 8)", "color": "gray"}]
