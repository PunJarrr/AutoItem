# Villager trade with last emerald: no refill
function autoitem_test:_reset
item replace entity @s hotbar.0 with minecraft:emerald 1
item replace entity @s inventory.0 with minecraft:emerald 10
summon minecraft:villager ^ ^ ^2 {NoAI:1b,VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"},Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:bread",count:1},maxUses:999}]}}
tellraw @s {"text": "Villager trade with last emerald: no refill", "color": "gold"}
tellraw @s {"text": "Open the villager and pick the emerald trade at once.", "color": "white"}
