# Common setup before every test
clear @s
gamemode survival @s
effect clear @s
kill @e[type=!minecraft:player,distance=..16]
effect give @s minecraft:resistance 3 4 true
