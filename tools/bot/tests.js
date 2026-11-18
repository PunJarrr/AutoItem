// In-game AutoItem tests (SPEC §8 G and X IDs) driven by a Mineflayer bot.
// Usage: node tests.js <minecraft version> [port]   (run.sh starts the server and calls this)
//
// Each test resets the bot, builds its inventory, performs the action with raw packets (so "place, then
// F/Q/scroll" really reaches the server in one tick), waits, and compares the inventory with the expected
// slots and per-item totals. Output: one JSON line per test, then a summary line. Exit code 0 only if
// every test that ran passed.
//
// Two modes, picked automatically at startup:
//  - server mode (Paper): inventories are set with /item replace and read back with /execute if items, so
//    results come from the server and don't depend on Mineflayer decoding item data (it can't decode potion
//    components, and its inventory view goes stale after such an error).
//  - client mode (Folia, which disables those commands): slots are set with the creative-inventory packet and
//    read from the bot's own inventory view. Tests that need item components (potions) are skipped.

import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import mineflayer from 'mineflayer'
import prismarineChat from 'prismarine-chat'
import prismarineItem from 'prismarine-item'
import { Vec3 } from 'vec3'

const version = process.argv[2]
const port = Number(process.argv[3] ?? 25565)
const BOT_NAME = 'AutoItemBot'
// The test server's plugin config, edited by tests that need another setting (then restored).
const CONFIG_FILE = resolve(process.env.RUN_DIR ?? '.', 'plugins/AutoItem/config.yml')
let originalConfig = null

/** Item component syntax changed in 1.21.5 (enchantments map, text components as SNBT). */
const [, minor, patch] = version.split('.').map(Number)
const newComponents = version.split('.')[0] !== '1' || minor > 21 || (minor === 21 && (patch ?? 0) >= 5)
const efficiency5 = newComponents ? 'enchantments={"minecraft:efficiency":5}' : 'enchantments={levels:{"minecraft:efficiency":5}}'
const named = (name) => newComponents ? `custom_name="${name}"` : `custom_name='"${name}"'`
const lore = (line) => newComponents ? `lore=["${line}"]` : `lore=['"${line}"']`

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// ---- slot mapping ----
// Bukkit raw PlayerInventory index -> window 0 slot
const windowSlot = (bukkit) => (bukkit === 40 ? 45 : bukkit < 9 ? 36 + bukkit : bukkit)
// Bukkit index -> /item replace slot name
const itemSlot = (bukkit) => (bukkit === 40 ? 'weapon.offhand' : bukkit < 9 ? `hotbar.${bukkit}` : `inventory.${bukkit - 9}`)

const ORIGIN = new Vec3(0, 100, 0) // bot stands here; stone floor at y=99

const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: BOT_NAME, version, auth: 'offline' })
let sequence = 1000
let serverMode = true
let Item
let ChatMessage

function cmd (command) {
  bot.chat(command.startsWith('/') ? command : `/${command}`)
}

// ---- raw actions (written immediately, so consecutive calls land in the same server tick) ----

/**
 * In client mode, update the bot's own inventory view the way a real client predicts a place or a Q:
 * the server assumes the client already did this and doesn't resend the slot.
 */
function predictOneUsed (hand) {
  if (serverMode) return
  const slot = hand === 1 ? 45 : 36 + bot.quickBarSlot
  const item = bot.inventory.slots[slot]
  if (!item) return
  if (item.count > 1) {
    bot.inventory.updateSlot(slot, new Item(item.type, item.count - 1))
  } else {
    bot.inventory.updateSlot(slot, null)
  }
}

/** Right-click the top face of the block at `below` with a hand (0 = main, 1 = offhand). */
function placeOn (below, hand = 0) {
  const packet = {
    hand,
    location: below,
    direction: 1, // +Y face
    cursorX: 0.5,
    cursorY: 1.0,
    cursorZ: 0.5,
    insideBlock: false,
    worldBorderHit: false, // 1.21.3+; ignored by older protocols, as in Mineflayer's own placeBlock
    sequence: sequence++
  }
  bot._client.write('block_place', packet)
  predictOneUsed(hand)
}

/** Right-click with an item, looking in a direction (Minecraft yaw/pitch in degrees; pitch 90 = down). */
function useItem (hand = 0, yaw = 0, pitch = 90) {
  bot._client.write('use_item', { hand, sequence: sequence++, rotation: { x: yaw, y: pitch } })
}

/** Right-click with an item while looking at `target` (a point), as buckets and boats need. */
async function useItemAt (target, hand = 0) {
  await bot.lookAt(target, true)
  const eye = bot.entity.position.offset(0, bot.entity.eyeHeight, 0)
  const d = target.minus(eye)
  const yaw = -Math.atan2(d.x, d.z) * 180 / Math.PI
  const pitch = -Math.atan2(d.y, Math.hypot(d.x, d.z)) * 180 / Math.PI
  useItem(hand, yaw, pitch)
}

/** Hold right-click for `ms`, then release (drawing a bow, charging a crossbow or trident). */
async function holdUse (ms, hand = 0) {
  useItem(hand, -90, -10)
  await sleep(ms)
  dig(5) // release
}

/** Right-click the nearest entity of a type with the main hand. */
async function useOnEntity (type) {
  let entity = null
  for (let i = 0; i < 30 && !entity; i++) {
    entity = bot.nearestEntity((e) => e.name === type)
    if (!entity) await sleep(100)
  }
  if (!entity) throw new Error(`no ${type} near the bot`)
  bot.activateEntity(entity)
}

function dig (status) {
  bot._client.write('block_dig', { status, location: new Vec3(0, 0, 0), face: 0, sequence: 0 })
}
const dropOne = () => { dig(4); predictOneUsed(0) } // Q
const swapHands = () => dig(6) // F
const selectSlot = (slot) => bot._client.write('held_item_slot', { slotId: slot })

// ---- per-test reset ----

async function reset (heldSlot = 0) {
  cmd('/clear @s')
  cmd('/kill @e[type=!player]')
  cmd('/effect clear @s')
  cmd('/gamemode survival @s')
  cmd(`/fill -4 99 -4 8 99 4 stone`)
  cmd(`/fill -4 100 -4 8 103 4 air`)
  cmd(`/execute in minecraft:overworld run tp @s ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 0 90`)
  selectSlot(heldSlot)
  bot.quickBarSlot = heldSlot
  await sleep(700)
}

async function give (slots) {
  if (serverMode) {
    for (const [bukkit, spec] of Object.entries(slots)) {
      cmd(`/item replace entity @s ${itemSlot(Number(bukkit))} with ${spec}`)
    }
    await sleep(700)
    return
  }
  // Commands can run a tick or more after later packets, so wait until the server has really switched game
  // mode: creative-slot packets sent before that are rejected by the server.
  await setGameMode('creative')
  for (const [bukkit, spec] of Object.entries(slots)) {
    const [id, amount] = spec.split(' ')
    const type = bot.registry.itemsByName[id]
    if (!type || id.includes('[')) throw new Error(`can't set ${spec} in client mode`)
    // Raw packet: Mineflayer's creative helper rejects the offhand (window slot 45). The server doesn't
    // echo creative slot changes, so the bot's own view is updated here, as a real client would.
    const item = new Item(type.id, Number(amount))
    bot._client.write('set_creative_slot', { slot: windowSlot(Number(bukkit)), item: Item.toNotch(item) })
    bot.inventory.updateSlot(windowSlot(Number(bukkit)), item)
    await sleep(100)
  }
  await setGameMode('survival')
  await sleep(500)
}

async function setGameMode (mode) {
  cmd(`/gamemode ${mode} @s`)
  for (let i = 0; i < 50 && bot.game.gameMode !== mode; i++) await sleep(100)
  if (bot.game.gameMode !== mode) throw new Error(`game mode is still ${bot.game.gameMode}`)
  await sleep(200)
}

/** Waits for the next chat/system message matching `pattern`. */
function nextMessage (pattern, ms = 3000) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => { bot.off('messagestr', onMessage); resolve(null) }, ms)
    function onMessage (text) {
      if (pattern.test(text)) { clearTimeout(timer); bot.off('messagestr', onMessage); resolve(text) }
    }
    bot.on('messagestr', onMessage)
  })
}

/** Rewrites the test server's config.yml with replacements ([from, to] pairs) and runs /autoitem reload. */
async function configure (...pairs) {
  originalConfig ??= readFileSync(CONFIG_FILE, 'utf8')
  let yaml = originalConfig
  for (const [from, to] of pairs) {
    if (!yaml.includes(from)) throw new Error(`config.yml has no "${from}"`)
    yaml = yaml.replace(from, to)
  }
  writeFileSync(CONFIG_FILE, yaml)
  const reply = nextMessage(/Config (reloaded|has errors)/)
  cmd('/autoitem reload')
  const text = await reply
  if (!text || !/Config reloaded\./.test(text)) throw new Error(`reload failed: ${text}`)
}

async function restoreConfig () {
  if (originalConfig !== null && readFileSync(CONFIG_FILE, 'utf8') !== originalConfig) await configure()
}

/**
 * Mines the floor block at (x, 99, z) with the held item, with raw packets: Mineflayer's dig() reads the
 * tool's enchantments, which it can't decode on 1.21.1. Glass takes about 0.5 s.
 */
async function mine (x, z) {
  // Glass: it drops nothing without Silk Touch (so no pickup lands in the emptied slot) but still costs the
  // pickaxe durability.
  cmd(`/setblock ${x} 99 ${z} glass`)
  await sleep(300)
  const location = new Vec3(x, 99, z)
  await bot.lookAt(location.offset(0.5, 1, 0.5), true)
  bot._client.write('block_dig', { status: 0, location, face: 1, sequence: sequence++ })
  await sleep(1200)
  bot._client.write('block_dig', { status: 2, location, face: 1, sequence: sequence++ })
}

/** Makes the bot hungry so food can be eaten. */
async function getHungry () {
  cmd('/effect give @s minecraft:hunger 10 255 true')
  for (let i = 0; i < 100 && bot.food >= 18; i++) await sleep(100)
  cmd('/effect clear @s minecraft:hunger')
  await sleep(300)
}

/**
 * Client mode (Folia): makes the server resend the whole inventory, so the bot's view is the server's truth.
 * A container click with an outdated state id makes the server send its full state; clicking outside the
 * window (-999) with an empty cursor changes nothing. Folia has no /execute if items, and its /clear count
 * doesn't report real amounts.
 */
async function resync () {
  bot._client.write('window_click', {
    windowId: 0,
    stateId: 0x7fffff,
    slot: -999,
    mouseButton: 0,
    mode: 0,
    changedSlots: [],
    cursorItem: newComponents ? undefined : Item.toNotch(null)
  })
  await sleep(500)
}

// ---- assertions (asked from the server, so they don't depend on the bot decoding item data) ----

/** Sends a command and resolves with the text of the next "Test passed/failed" reply. */
function ask (command) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => { bot.off('message', onMessage); resolve(null) }, 3000)
    function onMessage (message) {
      const text = message.toString()
      if (/commands\.execute\.conditional/.test(JSON.stringify(message.json ?? {})) || /Test (passed|failed)/.test(text)) {
        clearTimeout(timer)
        bot.off('message', onMessage)
        if (process.env.BOT_DEBUG && debugLeft-- > 0) {
          console.log(JSON.stringify({ debug: text, json: message.json }))
        }
        resolve(message)
      }
    }
    bot.on('message', onMessage)
    cmd(command)
  })
}
let debugLeft = 3

/** Bukkit slots for an /item slot selector. */
function bukkitSlots (selector) {
  if (selector === 'container.*') return [...Array(36).keys()]
  if (selector === 'weapon.offhand') return [40]
  const [kind, index] = selector.split('.')
  return [kind === 'hotbar' ? Number(index) : 9 + Number(index)]
}

/** How many items matching `predicate` are in `slots` (an /item slot selector). */
async function count (slots, predicate = '*') {
  if (!serverMode) {
    let total = 0
    for (const bukkit of bukkitSlots(slots)) {
      const item = bot.inventory.slots[windowSlot(bukkit)]
      if (item && (predicate === '*' || item.name === predicate)) total += item.count
    }
    return total
  }
  const reply = await ask(`/execute if items entity @s ${slots} ${predicate}`)
  if (reply === null) throw new Error(`no reply counting ${predicate} in ${slots}`)
  // Read the translation arguments, not the text: the wording differs between versions
  // ("Test passed, count: 5" on 1.21.1, "Test passed. Count: 5" on 1.21.11).
  const json = reply.json ?? {}
  if (json.translate === 'commands.execute.conditional.pass_count') {
    const arg = json.with?.[0]
    return Number(typeof arg === 'object' ? (arg.text ?? arg['']) : arg)
  }
  // A failed test is sent as a (red) error message wrapping the translation.
  if (JSON.stringify(json).includes('commands.execute.conditional.fail')) return 0
  throw new Error(`unexpected reply counting ${predicate} in ${slots}: ${reply.toString()}`)
}

/** Item id (without components or count) of a give spec like 'potion[...] 1'. */
const itemId = (spec) => spec.split(/[[ ]/)[0]

async function serverTotals (names) {
  if (!serverMode) await resync()
  const sum = {}
  for (const name of names) {
    sum[name] = (await count('container.*', name)) + (await count('weapon.offhand', name))
  }
  return sum
}

/** Where the test's items are now, for failure messages: 'slot:count name, ...'. */
async function describeInventory (names) {
  const parts = []
  for (const bukkit of [...Array(36).keys(), 40]) {
    for (const name of names) {
      const n = await count(itemSlot(bukkit), name)
      if (n > 0) parts.push(`${bukkit}:${n} ${name}`)
    }
  }
  return parts.join(', ') || 'none of them'
}

async function check (expected, beforeTotals, delta, names) {
  if (!serverMode) await resync()
  const problems = []
  for (const [bukkit, want] of Object.entries(expected)) {
    const slot = itemSlot(Number(bukkit))
    const any = await count(slot)
    if (!want) {
      if (any !== 0) problems.push(`slot ${bukkit}: want empty, has ${any} item(s)`)
      continue
    }
    const [predicate, amount] = want
    const matching = await count(slot, predicate)
    if (matching !== amount || any !== amount) {
      problems.push(`slot ${bukkit}: want ${amount} ${predicate}, has ${matching} matching of ${any} item(s)`)
    }
  }
  const after = await serverTotals(names)
  for (const name of names) {
    const want = beforeTotals[name] + (delta[name] ?? 0)
    if (after[name] !== want) problems.push(`total ${name}: want ${want}, got ${after[name]}`)
  }
  if (problems.length > 0) problems.push(`inventory now: ${await describeInventory(names)}`)
  return problems
}

// ---- the tests ----

const P = (x, z) => new Vec3(x, 99, z) // a floor block to place on
const TOP = (x, z) => new Vec3(x + 0.5, 100, z + 0.5) // the top face of that floor block

const tests = [
  {
    id: 'G1',
    name: 'last block refills from the smallest main-inventory stack',
    held: 3,
    slots: { 3: 'cobblestone 1', 11: 'cobblestone 64', 20: 'cobblestone 10', 5: 'cobblestone 2' },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 3: ['cobblestone', 10], 20: null, 11: ['cobblestone', 64], 5: ['cobblestone', 2] },
    delta: { cobblestone: -1 }
  },
  {
    id: 'G2a',
    name: 'last redstone dust refills with dust (placed block is redstone wire)',
    slots: { 0: 'redstone 1', 9: 'redstone 5' },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['redstone', 5], 9: null },
    delta: { redstone: -1 }
  },
  {
    id: 'G2b',
    name: 'last wheat seeds refill with seeds (placed block is wheat)',
    slots: { 0: 'wheat_seeds 1', 9: 'wheat_seeds 7' },
    setup: async () => { cmd('/setblock 2 99 0 farmland'); await sleep(300) },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['wheat_seeds', 7], 9: null },
    delta: { wheat_seeds: -1 }
  },
  {
    id: 'G3a',
    name: 'last mushroom stew refills, bowl moves out',
    slots: { 0: 'mushroom_stew 1', 15: 'mushroom_stew 1' },
    setup: getHungry,
    act: async () => { useItem(0); await sleep(2600) },
    expect: { 0: ['mushroom_stew', 1], 15: ['bowl', 1] }, // the bowl takes the source's slot
    delta: { mushroom_stew: -1, bowl: 1 }
  },
  {
    id: 'G3b',
    name: 'last Swiftness potion refills only from Swiftness, bottle moves out',
    needsComponents: true,
    slots: {
      0: 'potion[potion_contents={potion:"minecraft:swiftness"}] 1',
      10: 'potion[potion_contents={potion:"minecraft:slowness"}] 1',
      14: 'potion[potion_contents={potion:"minecraft:swiftness"}] 1'
    },
    act: async () => { useItem(0); await sleep(2600) },
    expect: {
      0: ['potion[potion_contents={potion:"minecraft:swiftness"}]', 1],
      10: ['potion[potion_contents={potion:"minecraft:slowness"}]', 1],
      14: ['glass_bottle', 1]
    },
    delta: { potion: -1, glass_bottle: 1 }
  },
  {
    id: 'G4',
    name: 'honey bottles: no refill until the last one, then bottle moves out',
    slots: { 0: 'honey_bottle 3', 9: 'honey_bottle 2' },
    act: async () => {
      for (let i = 0; i < 3; i++) { useItem(0); await sleep(2900) }
    },
    expect: { 0: ['honey_bottle', 2], 9: ['glass_bottle', 1] }, // the last bottle takes the source's slot
    delta: { honey_bottle: -3, glass_bottle: 3 }
  },
  {
    id: 'G11',
    name: 'Q on the last item never refills',
    slots: { 0: 'cobblestone 1', 9: 'cobblestone 64' },
    act: async () => { dropOne(); await sleep(800) },
    expect: { 0: null, 9: ['cobblestone', 64] },
    delta: { cobblestone: -1 }
  },
  {
    id: 'X2',
    name: 'place the second-to-last, then Q the last in the same tick: no refill',
    slots: { 0: 'cobblestone 2', 9: 'cobblestone 64' },
    act: async () => { placeOn(P(2, 0)); dropOne(); await sleep(800) },
    expect: { 0: null, 9: ['cobblestone', 64] },
    delta: { cobblestone: -2 }
  },
  {
    id: 'X1',
    name: 'place the last block, then F in the same tick: abort, nothing moved',
    slots: { 0: 'cobblestone 1', 40: 'shield 1', 9: 'cobblestone 64' },
    act: async () => { placeOn(P(2, 0)); swapHands(); await sleep(800) },
    expect: { 0: ['shield', 1], 40: null, 9: ['cobblestone', 64] },
    delta: { cobblestone: -1 }
  },
  {
    id: 'X3',
    name: 'place the last block, then scroll in the same tick: the used slot is refilled',
    held: 2,
    slots: { 2: 'cobblestone 1', 9: 'cobblestone 64' },
    act: async () => { placeOn(P(2, 0)); selectSlot(6); bot.quickBarSlot = 6; await sleep(800) },
    expect: { 2: ['cobblestone', 64], 6: null, 9: null },
    delta: { cobblestone: -1 }
  },
  {
    id: 'X10a',
    name: 'both hands run out in the same tick: both refilled',
    slots: { 0: 'cobblestone 1', 40: 'torch 1', 9: 'cobblestone 64', 10: 'torch 5' },
    act: async () => { placeOn(P(2, 0), 0); placeOn(P(2, 2), 1); await sleep(800) },
    expect: { 0: ['cobblestone', 64], 40: ['torch', 5], 9: null, 10: null },
    delta: { cobblestone: -1, torch: -1 }
  },
  {
    id: 'X10b',
    name: 'both hands run out, one source: main hand refilled, offhand never empties it',
    slots: { 0: 'torch 1', 40: 'torch 1', 9: 'torch 10' },
    act: async () => { placeOn(P(2, 0), 0); placeOn(P(2, 2), 1); await sleep(800) },
    expect: { 0: ['torch', 10], 40: null, 9: null },
    delta: { torch: -2 }
  },
  // ---- milestone 2: buckets ----
  {
    id: 'G5a',
    name: 'last water bucket emptied: refilled, empty bucket moves out',
    slots: { 0: 'water_bucket 1', 12: 'water_bucket 1' },
    act: async () => { await useItemAt(TOP(2, 0)); await sleep(800) },
    expect: { 0: ['water_bucket', 1], 12: ['bucket', 1] },
    delta: { water_bucket: -1, bucket: 1 }
  },
  {
    id: 'G5b',
    name: 'last empty bucket filled: another empty bucket, filled one moves out',
    slots: { 0: 'bucket 1', 9: 'bucket 3' },
    setup: async () => { cmd('/setblock 2 99 0 water'); await sleep(300) },
    act: async () => { await useItemAt(new Vec3(2.5, 99.8, 0.5)); await sleep(800) },
    expect: { 0: ['bucket', 3], 9: ['water_bucket', 1] },
    delta: { bucket: -1, water_bucket: 1 }
  },
  // ---- milestone 2: throwables ----
  ...['snowball', 'egg', 'ender_pearl', 'experience_bottle', 'wind_charge'].map((item) => ({
    id: `G6-${item}`,
    name: `last ${item} thrown: refilled`,
    slots: { 0: `${item} 1`, 9: `${item} 8` },
    act: async () => { useItem(0, -90, -60); await sleep(800) },
    expect: { 0: [item, 8], 9: null },
    delta: { [item]: -1 }
  })),
  {
    id: 'G6-splash',
    name: 'last Swiftness splash potion thrown: refilled only from Swiftness splash',
    needsComponents: true,
    slots: {
      0: 'splash_potion[potion_contents={potion:"minecraft:swiftness"}] 1',
      10: 'splash_potion[potion_contents={potion:"minecraft:slowness"}] 1',
      11: 'lingering_potion[potion_contents={potion:"minecraft:swiftness"}] 1',
      14: 'splash_potion[potion_contents={potion:"minecraft:swiftness"}] 1'
    },
    act: async () => { useItem(0, -90, -60); await sleep(800) },
    expect: {
      0: ['splash_potion[potion_contents={potion:"minecraft:swiftness"}]', 1],
      10: ['splash_potion[potion_contents={potion:"minecraft:slowness"}]', 1],
      11: ['lingering_potion', 1],
      14: null
    },
    delta: { splash_potion: -1 }
  },
  // ---- milestone 2: right-click items ----
  {
    id: 'G7-spawn-egg',
    name: 'last spawn egg: refilled',
    slots: { 0: 'cow_spawn_egg 1', 9: 'cow_spawn_egg 2' },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['cow_spawn_egg', 2], 9: null },
    delta: { cow_spawn_egg: -1 }
  },
  {
    id: 'G7-bone-meal',
    name: 'last bone meal on grass: refilled',
    slots: { 0: 'bone_meal 1', 9: 'bone_meal 5' },
    setup: async () => { cmd('/setblock 2 99 0 grass_block'); await sleep(300) },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['bone_meal', 5], 9: null },
    delta: { bone_meal: -1 }
  },
  {
    id: 'G7-firework',
    name: 'last firework rocket used on the ground: refilled',
    slots: { 0: 'firework_rocket 1', 9: 'firework_rocket 4' },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['firework_rocket', 4], 9: null },
    delta: { firework_rocket: -1 }
  },
  {
    id: 'G7-end-crystal',
    name: 'last end crystal on obsidian: refilled',
    slots: { 0: 'end_crystal 1', 9: 'end_crystal 2' },
    setup: async () => { cmd('/setblock 2 99 0 obsidian'); await sleep(300) },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['end_crystal', 2], 9: null },
    delta: { end_crystal: -1 }
  },
  {
    id: 'G7-item-frame',
    name: 'last item frame: refilled',
    slots: { 0: 'item_frame 1', 9: 'item_frame 3' },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: ['item_frame', 3], 9: null },
    delta: { item_frame: -1 }
  },
  {
    id: 'G7-boat',
    name: 'last boat: refilled',
    slots: { 0: 'oak_boat 1', 9: 'oak_boat 1' },
    act: async () => { await useItemAt(TOP(2, 0)); await sleep(800) },
    expect: { 0: ['oak_boat', 1], 9: null },
    delta: { oak_boat: -1 }
  },
  {
    id: 'G7-feed-cow',
    name: 'last wheat fed to a cow: refilled',
    slots: { 0: 'wheat 1', 9: 'wheat 6' },
    setup: async () => { cmd('/summon cow 2 100 0 {NoAI:1b}'); await sleep(800) },
    act: async () => { await useOnEntity('cow'); await sleep(800) },
    expect: { 0: ['wheat', 6], 9: null },
    delta: { wheat: -1 }
  },
  // ---- milestone 2: bows ----
  {
    id: 'G8-bow',
    name: 'last arrow in the offhand shot from a bow: refilled',
    slots: { 0: 'bow 1', 40: 'arrow 1', 9: 'arrow 32' },
    act: async () => { await holdUse(1200); await sleep(800) },
    expect: { 0: ['bow', 1], 40: ['arrow', 32], 9: null },
    delta: { arrow: -1 }
  },
  {
    id: 'G8-tipped',
    name: 'last poison tipped arrow: refilled only from poison arrows',
    needsComponents: true,
    slots: {
      0: 'bow 1',
      40: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}] 1',
      9: 'tipped_arrow[potion_contents={potion:"minecraft:harming"}] 5',
      10: 'arrow 5',
      11: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}] 7'
    },
    act: async () => { await holdUse(1200); await sleep(800) },
    expect: {
      40: ['tipped_arrow[potion_contents={potion:"minecraft:poison"}]', 7],
      9: ['tipped_arrow[potion_contents={potion:"minecraft:harming"}]', 5],
      10: ['arrow', 5],
      11: null
    },
    delta: { tipped_arrow: -1 }
  },
  {
    id: 'G8-crossbow',
    name: 'last rocket in the offhand loaded into a crossbow: refilled',
    slots: { 0: 'crossbow 1', 40: 'firework_rocket 1', 9: 'firework_rocket 3' },
    act: async () => { await holdUse(1700); await sleep(800) },
    expect: { 0: ['crossbow', 1], 40: ['firework_rocket', 3], 9: null },
    delta: { firework_rocket: -1 }
  },
  // ---- milestone 2: things that must not refill ----
  {
    id: 'G10-trident',
    name: 'thrown trident: no refill (damageable, comes back with Loyalty)',
    slots: { 0: 'trident 1', 9: 'trident 1' },
    act: async () => { await holdUse(800); await sleep(800) },
    expect: { 0: null, 9: ['trident', 1] },
    delta: { trident: -1 }
  },
  {
    id: 'G10-helmet',
    name: 'helmet equipped by right-click: no refill',
    slots: { 0: 'iron_helmet 1', 9: 'iron_helmet 1' },
    act: async () => { useItem(0); await sleep(800) },
    expect: { 0: null, 9: ['iron_helmet', 1] },
    delta: { iron_helmet: -1 }
  },
  // ---- found by the exploit review: refills after something other than a trigger emptied the slot ----
  {
    id: 'X13-totem',
    name: 'holding right-click with a totem in the offhand, then a totem pop: no refill',
    slots: { 40: 'totem_of_undying 1', 9: 'totem_of_undying 1', 10: 'totem_of_undying 1' }, // totems don't stack
    act: async () => {
      // Right-click every tick for a second, so a check is pending in whichever tick the damage lands.
      for (let i = 0; i < 20; i++) {
        useItem(1)
        if (i === 10) cmd('/damage @s 1000 minecraft:generic')
        await sleep(50)
      }
      await sleep(800)
    },
    expect: { 40: null, 9: ['totem_of_undying', 1], 10: ['totem_of_undying', 1] },
    delta: { totem_of_undying: -1 }
  },
  {
    id: 'G11b',
    name: 'right-click the air with the last diamond, then Q in the same tick: no refill',
    slots: { 0: 'diamond 1', 9: 'diamond 5' },
    act: async () => { useItem(0); dropOne(); await sleep(800) },
    expect: { 0: null, 9: ['diamond', 5] },
    delta: { diamond: -1 }
  },
  {
    id: 'X1b',
    name: 'right-click the air with the last diamond, then F in the same tick: no refill',
    slots: { 0: 'diamond 1', 9: 'diamond 5' },
    act: async () => { useItem(0); swapHands(); await sleep(800) },
    expect: { 0: null, 40: ['diamond', 1], 9: ['diamond', 5] },
    delta: {}
  },
  {
    id: 'X4',
    name: 'right-click the air with the last diamond, then click it onto the cursor (no inventory open): no refill',
    slots: { 0: 'diamond 1', 9: 'diamond 5' },
    act: async () => {
      useItem(0)
      bot.clickWindow(36, 0, 0).catch(() => {}) // hotbar 0 -> cursor, written at once
      await sleep(800)
      bot.clickWindow(36, 0, 0).catch(() => {}) // put it back so the totals can be counted
      await sleep(500)
    },
    expect: { 0: ['diamond', 1], 9: ['diamond', 5] },
    delta: {}
  },
  // ---- milestone 3: tool breaking (off by default; switched on for these tests) ----
  {
    id: 'G9a',
    name: 'tool-break on: a pickaxe that breaks is replaced by the same pickaxe',
    needsComponents: true,
    config: [['tool-break: false', 'tool-break: true']],
    slots: { 0: 'diamond_pickaxe[damage=1560] 1', 9: 'diamond_pickaxe 1' },
    act: async () => { await mine(1, 0); await sleep(800) },
    expect: { 0: ['diamond_pickaxe', 1], 9: null },
    delta: { diamond_pickaxe: -1 }
  },
  {
    id: 'G9b',
    name: 'tool-break: identical enchantments win over a fresher plain pickaxe',
    needsComponents: true,
    config: [['tool-break: false', 'tool-break: true']],
    slots: {
      0: `diamond_pickaxe[damage=1560,${efficiency5}] 1`,
      9: 'diamond_pickaxe 1',
      5: `diamond_pickaxe[damage=900,${efficiency5}] 1`
    },
    act: async () => { await mine(1, 0); await sleep(800) },
    expect: { 0: ['diamond_pickaxe', 1], 9: ['diamond_pickaxe', 1], 5: null },
    delta: { diamond_pickaxe: -1 }
  },
  {
    id: 'G9c',
    name: 'tool-break: a named pickaxe never stands in for an unnamed one',
    needsComponents: true,
    config: [['tool-break: false', 'tool-break: true']],
    slots: { 0: 'diamond_pickaxe[damage=1560] 1', 9: `diamond_pickaxe[${named('Excavator')}] 1` },
    act: async () => { await mine(1, 0); await sleep(800) },
    expect: { 0: null, 9: ['diamond_pickaxe', 1] },
    delta: { diamond_pickaxe: -1 }
  },
  {
    id: 'G9e',
    name: 'tool-break: a pickaxe that differs only by lore (a custom plugin item) never stands in',
    needsComponents: true,
    config: [['tool-break: false', 'tool-break: true']],
    slots: { 0: 'diamond_pickaxe[damage=1560] 1', 9: `diamond_pickaxe[${lore('Ruby Pickaxe')}] 1` },
    act: async () => { await mine(1, 0); await sleep(800) },
    expect: { 0: null, 9: ['diamond_pickaxe', 1] },
    delta: { diamond_pickaxe: -1 }
  },
  {
    id: 'G9d',
    name: 'tool-break off (default): a broken pickaxe is not replaced',
    needsComponents: true,
    slots: { 0: 'diamond_pickaxe[damage=1560] 1', 9: 'diamond_pickaxe 1' },
    act: async () => { await mine(1, 0); await sleep(800) },
    expect: { 0: null, 9: ['diamond_pickaxe', 1] },
    delta: { diamond_pickaxe: -1 }
  },
  // ---- milestone 3: toggle, worlds, feedback ----
  {
    id: 'G12a',
    name: '/autoitem toggle off: no refill (and back on afterwards)',
    slots: { 0: 'cobblestone 1', 9: 'cobblestone 64' },
    setup: async () => {
      const reply = nextMessage(/Auto refill (enabled|disabled)/)
      cmd('/autoitem toggle')
      if (!/disabled/.test(await reply ?? '')) throw new Error('toggle did not report disabled')
    },
    act: async () => {
      placeOn(P(2, 0))
      await sleep(800)
    },
    after: async () => {
      const reply = nextMessage(/Auto refill (enabled|disabled)/)
      cmd('/autoitem toggle')
      if (!/enabled/.test(await reply ?? '')) throw new Error('toggle did not report enabled')
    },
    expect: { 0: null, 9: ['cobblestone', 64] },
    delta: { cobblestone: -1 }
  },
  {
    id: 'G12b',
    name: 'world in disabled-worlds: no refill',
    config: [['disabled-worlds: []', 'disabled-worlds: [world]']],
    slots: { 0: 'cobblestone 1', 9: 'cobblestone 64' },
    act: async () => { placeOn(P(2, 0)); await sleep(800) },
    expect: { 0: null, 9: ['cobblestone', 64] },
    delta: { cobblestone: -1 }
  },
  {
    id: 'G13a',
    name: 'refill plays the item pickup sound',
    slots: { 0: 'cobblestone 1', 9: 'cobblestone 64' },
    act: async (state) => {
      state.sounds = []
      // Recognised by its volume and pitch (config: 0.3 / 1.4): Mineflayer's sound id -> name table is off
      // by one on some versions, so the name it reports isn't reliable.
      const onSound = (name, position, volume, pitch) => state.sounds.push({ name: String(name), volume, pitch })
      bot.on('soundEffectHeard', onSound)
      placeOn(P(2, 0))
      await sleep(800)
      bot.off('soundEffectHeard', onSound)
      const ours = state.sounds.some((s) => Math.abs(s.volume - 0.3) < 0.01 && Math.abs(s.pitch - 1.4) < 0.01)
      if (!ours) {
        throw new Error(`no 0.3/1.4 sound; heard: ${state.sounds.map((s) => `${s.name} ${s.volume}/${s.pitch}`).join(', ') || 'nothing'}`)
      }
    },
    expect: { 0: ['cobblestone', 64], 9: null },
    delta: { cobblestone: -1 }
  },
  {
    id: 'G13b',
    name: 'action-bar on: "Refilled <item>" on the action bar',
    config: [['action-bar: false', 'action-bar: true']],
    slots: { 0: 'cobblestone 1', 9: 'cobblestone 64' },
    act: async () => {
      // Raw packets: Paper's sendActionBar uses the set-action-bar-text packet ('action_bar'); a system chat
      // with isActionBar is the other way to show one. Mineflayer surfaces neither as 'actionBar' here.
      const bar = new Promise((resolve) => {
        const done = (text) => {
          clearTimeout(timer)
          bot._client.off('action_bar', onActionBar)
          bot._client.off('system_chat', onChat)
          resolve(text)
        }
        const decode = (content) => {
          try {
            return ChatMessage.fromNotch(content).toString() // JSON or NBT, depending on the version
          } catch (error) {
            return `(undecodable: ${error.message})`
          }
        }
        const onActionBar = (packet) => done(decode(packet.text))
        const onChat = (packet) => { if (packet.isActionBar) done(decode(packet.content)) }
        const timer = setTimeout(() => done(null), 2000)
        bot._client.on('action_bar', onActionBar)
        bot._client.on('system_chat', onChat)
      })
      placeOn(P(2, 0))
      const text = await bar
      if (!text || !text.startsWith('Refilled')) throw new Error(`action bar: ${text}`)
    },
    expect: { 0: ['cobblestone', 64], 9: null },
    delta: { cobblestone: -1 }
  },
  // ---- milestone 4 ----
  // Same-tick context changes (X6 death/game mode/world, X8 another plugin writing the slot) are covered by
  // MockBukkit: a command the bot sends doesn't reliably run in the same tick as its previous packet (Paper
  // runs chat commands through a task queue), so as bot tests they would be flaky.
  {
    id: 'X12',
    name: 'last block used 20,000 blocks from spawn (another Folia region): refilled',
    slots: { 0: 'cobblestone 1', 9: 'cobblestone 64' },
    setup: async () => {
      cmd('/effect give @s minecraft:resistance 20 255 true')
      cmd('/tp @s 20000.5 100 20000.5 0 90')
      await sleep(2500) // the chunks there load with the player; /fill needs them loaded
      cmd('/fill 19998 99 19998 20002 99 20002 stone')
      cmd('/fill 19998 100 19998 20002 103 20002 air')
      cmd('/tp @s 20000.5 100 20000.5 0 90')
      await sleep(800)
    },
    act: async () => { placeOn(new Vec3(20001, 99, 20000)); await sleep(800) },
    expect: { 0: ['cobblestone', 64], 9: null },
    delta: { cobblestone: -1 }
  },
  {
    id: 'K-offhand',
    name: 'offhand last block refills from storage',
    slots: { 40: 'cobblestone 1', 30: 'cobblestone 16' },
    act: async () => { placeOn(P(2, 0), 1); await sleep(800) },
    expect: { 40: ['cobblestone', 16], 30: null },
    delta: { cobblestone: -1 }
  }
]

async function run () {
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve)
    bot.once('kicked', (reason) => reject(new Error(`kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
  await sleep(1500)
  Item = prismarineItem(bot.registry)
  ChatMessage = prismarineChat(bot.registry)
  cmd('/time set day')
  await reset(0)
  cmd('/item replace entity @s weapon.offhand with stick 3')
  await sleep(700)
  serverMode = (await count('weapon.offhand', 'stick')) === 3
  console.log(JSON.stringify({ version, mode: serverMode ? 'server' : 'client' }))
  let passed = 0
  let skipped = 0
  // BOT_ONLY=G1,X13-totem runs just those tests.
  const only = process.env.BOT_ONLY ? new Set(process.env.BOT_ONLY.split(',')) : null
  for (const test of tests) {
    if (only && !only.has(test.id)) continue
    if ((test.needsComponents || test.serverOnly) && !serverMode) {
      skipped++
      console.log(JSON.stringify({ version, id: test.id, skipped: true, name: test.name,
        reason: test.needsComponents ? 'needs item components, which the bot client cannot decode'
          : 'needs /item, which Folia disables' }))
      continue
    }
    let problems
    try {
      await reset(test.held ?? 0)
      if (test.config) await configure(...test.config)
      await give(test.slots)
      if (test.setup) await test.setup()
      const names = [...new Set([...Object.values(test.slots).map(itemId), ...Object.keys(test.delta)])]
      const before = await serverTotals(names)
      await test.act({})
      problems = await check(test.expect, before, test.delta, names)
    } catch (error) {
      problems = [`error: ${error.message}`]
    }
    try {
      if (test.after) await test.after()
      if (test.config) await restoreConfig()
    } catch (error) {
      problems.push(`cleanup: ${error.message}`)
    }
    const pass = problems.length === 0
    if (pass) passed++
    console.log(JSON.stringify({ version, id: test.id, pass, name: test.name, problems }))
  }
  await restoreConfig().catch(() => {})
  const ran = (only ? tests.filter((t) => only.has(t.id)).length : tests.length) - skipped
  console.log(JSON.stringify({ version, summary: `${passed}/${ran} passed, ${skipped} skipped` }))
  bot.quit()
  await sleep(500)
  process.exit(passed === ran ? 0 : 1)
}

run().catch((error) => {
  console.log(JSON.stringify({ version, fatal: error.message }))
  process.exit(2)
})
