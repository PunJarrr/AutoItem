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

import mineflayer from 'mineflayer'
import prismarineItem from 'prismarine-item'
import { Vec3 } from 'vec3'

const version = process.argv[2]
const port = Number(process.argv[3] ?? 25565)
const BOT_NAME = 'AutoItemBot'

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

function useItem (hand = 0) {
  bot._client.write('use_item', { hand, sequence: sequence++, rotation: { x: 0, y: 90 } })
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
  cmd('/kill @e[type=item]')
  cmd('/effect clear @s')
  cmd('/gamemode survival @s')
  cmd(`/fill -4 99 -4 8 99 4 stone`)
  cmd(`/fill -4 100 -4 8 103 4 air`)
  cmd(`/tp @s ${ORIGIN.x + 0.5} ${ORIGIN.y} ${ORIGIN.z + 0.5} 0 90`)
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
  cmd('/gamemode creative @s')
  await sleep(500)
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
  cmd('/gamemode survival @s')
  await sleep(700)
}

/** Makes the bot hungry so food can be eaten. */
async function getHungry () {
  cmd('/effect give @s minecraft:hunger 10 255 true')
  for (let i = 0; i < 100 && bot.food >= 18; i++) await sleep(100)
  cmd('/effect clear @s minecraft:hunger')
  await sleep(300)
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
  const sum = {}
  for (const name of names) {
    sum[name] = (await count('container.*', name)) + (await count('weapon.offhand', name))
  }
  return sum
}

async function check (expected, beforeTotals, delta, names) {
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
  return problems
}

// ---- the tests ----

const P = (x, z) => new Vec3(x, 99, z) // a floor block to place on

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
    expect: { 0: ['mushroom_stew', 1], 15: null },
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
      14: null
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
    expect: { 0: ['honey_bottle', 2], 9: null },
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
  cmd('/time set day')
  await reset(0)
  cmd('/item replace entity @s weapon.offhand with stick 3')
  await sleep(700)
  serverMode = (await count('weapon.offhand', 'stick')) === 3
  console.log(JSON.stringify({ version, mode: serverMode ? 'server' : 'client' }))
  let passed = 0
  let skipped = 0
  for (const test of tests) {
    if (test.needsComponents && !serverMode) {
      skipped++
      console.log(JSON.stringify({ version, id: test.id, skipped: true, name: test.name,
        reason: 'needs item components, which the bot client cannot decode' }))
      continue
    }
    let problems
    try {
      await reset(test.held ?? 0)
      await give(test.slots)
      if (test.setup) await test.setup()
      const names = [...new Set([...Object.values(test.slots).map(itemId), ...Object.keys(test.delta)])]
      const before = await serverTotals(names)
      await test.act()
      problems = await check(test.expect, before, test.delta, names)
    } catch (error) {
      problems = [`error: ${error.message}`]
    }
    const pass = problems.length === 0
    if (pass) passed++
    console.log(JSON.stringify({ version, id: test.id, pass, name: test.name, problems }))
  }
  const ran = tests.length - skipped
  console.log(JSON.stringify({ version, summary: `${passed}/${ran} passed, ${skipped} skipped` }))
  bot.quit()
  await sleep(500)
  process.exit(passed === ran ? 0 : 1)
}

run().catch((error) => {
  console.log(JSON.stringify({ version, fatal: error.message }))
  process.exit(2)
})
