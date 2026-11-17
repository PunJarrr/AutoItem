#!/bin/sh
# Runs tools/bot/tests.js against one local test server.
# Usage (from the project root): tools/bot/run.sh <gradle task> <run dir> <minecraft version>
#   tools/bot/run.sh runServer1_21_1  run/1.21.1        1.21.1
#   tools/bot/run.sh runServer1_21_11 run/1.21.11       1.21.11
#   tools/bot/run.sh runFolia1_21_11  run/folia-1.21.11 1.21.11
#
# Puts the test server in offline mode (so the bot can join), ops the bot, starts the server, runs the
# tests once 'Done (' appears, then stops the server. Results: build/bot/<run dir name>.jsonl (bot errors in .stderr);
# server log: build/bot/<run dir name>.log. Exit code: the test script's (0 = all passed).
set -u
task=$1; dir=$2; version=$3
name=$(basename "$dir")
here=$(cd "$(dirname "$0")" && pwd)
mkdir -p build/bot "$dir"
log=build/bot/$name.log; results=build/bot/$name.jsonl
: > "$log"; : > "$results"

if pgrep -f 'java.*com.mojang.eula.agree' >/dev/null; then
  echo "A test server is already running; stop it first." >&2; exit 3
fi
[ -d "$here/node_modules/mineflayer" ] || { echo "Run 'npm install --ignore-scripts' in tools/bot first." >&2; exit 3; }

# Offline mode, no hostile mobs, no spawn protection. Other keys are left as they are.
props=$dir/server.properties
touch "$props"
for kv in online-mode=false enforce-secure-profile=false spawn-monsters=false difficulty=easy \
          spawn-protection=0 allow-flight=true view-distance=4 simulation-distance=4 server-port=25565; do
  key=${kv%%=*}
  if grep -q "^$key=" "$props"; then sed -i '' "s/^$key=.*/$kv/" "$props"; else echo "$kv" >> "$props"; fi
done
# Op the bot (offline-mode UUID).
uuid=$(node -e "
const h=require('crypto').createHash('md5').update('OfflinePlayer:AutoItemBot').digest();
h[6]=(h[6]&0x0f)|0x30; h[8]=(h[8]&0x3f)|0x80; const x=h.toString('hex');
console.log(x.slice(0,8)+'-'+x.slice(8,12)+'-'+x.slice(12,16)+'-'+x.slice(16,20)+'-'+x.slice(20))")
printf '[{"uuid":"%s","name":"AutoItemBot","level":4,"bypassesPlayerLimit":false}]\n' "$uuid" > "$dir/ops.json"

( t=0; until grep -q 'Done (' "$log" 2>/dev/null || [ $t -ge 300 ]; do sleep 2; t=$((t+2)); done; sleep 3
  BOT_DEBUG=${BOT_DEBUG:-} node "$here/tests.js" "$version" 25565 > "$results" 2> "build/bot/$name.stderr"; echo $? > "build/bot/$name.exit"
  echo stop
  t=0; while pgrep -f 'java.*com.mojang.eula.agree' >/dev/null && [ $t -lt 90 ]; do sleep 2; t=$((t+2)); done
  pgrep -f 'java.*com.mojang.eula.agree' >/dev/null && { echo "SIGTERM fallback" >&2; pkill -TERM -f 'java.*com.mojang.eula.agree'; } ) | ./gradlew "$task" --console=plain >> "$log" 2>&1

grep "^{" "$results"  # Mineflayer prints item-decoding stack traces to stdout; they only affect its own view
exit "$(cat "build/bot/$name.exit" 2>/dev/null || echo 2)"
