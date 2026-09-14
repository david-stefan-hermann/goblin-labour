#!/usr/bin/env bash
# Phase 2 functional test over RCON: mine-down job by hand on a flat world (grass + dirt), water sealed with
# cobblestone, stone blocks the goblin until it gets a pickaxe, drops land in its inventory. Prints PASS/FAIL.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
status() { rcon "goblinlabour status 20 -60 20" | grep -v '^>' ; }
wait_for() { # wait_for <seconds> <substring> ; polls status
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(status)
    if [[ "$s" == *"$2"* ]]; then echo "$s"; return 0; fi
    sleep 5
  done
  status; return 1
}

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

BED="20 -60 20"
# shaft for a south-facing bed, width 3: x 19..21, z 26..28, from y -60 down. Flat world: -61 grass, -62/-63 dirt, -64 bedrock.
rcon "forceload add -16 -16 48 48" "time set day" "weather clear" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" "setblock 30 -60 30 air" "setblock 0 -60 0 air" "setblock 3 -60 -2 air" \
     "setblock $BED air" "fill 18 -63 25 22 -60 29 air" "fill 18 -63 25 22 -61 29 dirt" "fill 18 -61 25 22 -61 29 grass_block" \
     "setblock 22 -61 27 water" "setblock $BED goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 2
out=$(rcon "goblinlabour spawn $BED Digger"); check "spawn" "Spawned Digger" "$out"

# 1. dig grass and dirt by hand until bedrock
out=$(rcon "goblinlabour dig $BED down 20 -60 27 3 -64 false"); check "dig order" "Order for Digger" "$out"
out=$(wait_for 20 "working"); check "starts working" "working" "$out"
out=$(wait_for 240 "Rest"); check "job finished (back to Rest)" "Rest" "$out"
out=$(status); check "dirt collected" "dirt" "$out"
out=$(rcon "execute if block 20 -63 27 air"); check "shaft bottom layer dug" "Test passed" "$out"
out=$(rcon "execute if block 22 -61 27 cobblestone"); check "water source sealed with cobblestone" "Test passed" "$out"
out=$(rcon "execute if block 20 -61 21 grass_block"); check "home untouched" "Test passed" "$out"

# 2. stone needs a pickaxe
rcon "fill 19 -61 26 21 -61 28 stone" > /dev/null
out=$(rcon "goblinlabour dig $BED down 20 -60 27 3 -64 false"); check "dig order again" "Order for Digger" "$out"
out=$(wait_for 30 "blocked"); check "blocked without pickaxe" "blocked" "$out"
out=$(rcon "goblinlabour tool $BED 0 minecraft:iron_pickaxe"); check "give pickaxe" "iron_pickaxe" "$out"
out=$(wait_for 90 "Rest"); check "stone layer done with pickaxe" "Rest" "$out"
out=$(status); check "cobblestone collected" "cobblestone" "$out"
out=$(status); check "pickaxe still in slot 0" "0=1xminecraft:iron_pickaxe" "$out"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
