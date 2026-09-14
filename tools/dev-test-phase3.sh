#!/usr/bin/env bash
# Phase 3 functional test over RCON: stairs in the shaft, chop + replant, farm + replant, unload into a goblin
# chest and wait when it is full, "no exit" detection. Five beds far apart on the flat world. Prints PASS/FAIL.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
status() { rcon "goblinlabour status $1" | grep -v '^>' ; }
wait_status() { # wait_status <bed> <seconds> <substring>
  local end=$(( $(date +%s) + $2 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(status "$1")
    if [[ "$s" == *"$3"* ]]; then echo "$s"; return 0; fi
    sleep 5
  done
  status "$1"; return 1
}
wait_block() { # wait_block <seconds> <x y z> <block>
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(rcon "execute if block $2 $3")
    if [[ "$s" == *"Test passed"* ]]; then echo "$s"; return 0; fi
    sleep 5
  done
  rcon "execute if block $2 $3"; return 1
}

wait_notblock() { # wait_notblock <seconds> <x y z> <block>
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(rcon "execute unless block $2 $3")
    if [[ "$s" == *"Test passed"* ]]; then echo "$s"; return 0; fi
    sleep 5
  done
  rcon "execute unless block $2 $3"; return 1
}

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add 0 0 270 270" "time set day" "weather clear" > /dev/null
# display beds of the screenshot scenes (x/z 40..49) would merge their homes with A's and protect the shaft
rcon "fill 36 -60 36 52 -60 52 air replace goblinlabour:goblin_straw_bed" > /dev/null
# beds first (a bed respawns its goblin 5 s after a kill), then goblins and items, then a clean slate per area
rcon "setblock 20 -60 20 air" "setblock 10 -60 10 air" "setblock 30 -60 30 air" "setblock 60 -60 60 air" "setblock 90 -60 90 air" \
     "setblock 120 -60 120 air" "setblock 150 -60 150 air" "setblock 180 -60 180 air" "setblock 210 -60 210 air" "setblock 240 -60 240 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" \
     "fill 25 -63 25 40 -50 45 air" "fill 55 -61 55 70 -45 75 air" "fill 85 -62 85 100 -55 105 air" "fill 115 -61 115 130 -55 135 air" \
     "fill 145 -62 145 160 -50 165 air" "fill 175 -61 175 190 -55 195 air" "fill 205 -61 205 220 -45 225 air" "fill 235 -61 235 255 -45 265 air" > /dev/null
rcon "fill 25 -63 25 40 -62 45 dirt" "fill 25 -61 25 40 -61 45 grass_block" "fill 55 -63 55 70 -62 75 dirt" "fill 55 -61 55 70 -61 75 grass_block" \
     "fill 85 -63 85 100 -62 105 dirt" "fill 85 -61 85 100 -61 105 grass_block" "fill 115 -63 115 130 -62 135 dirt" "fill 115 -61 115 130 -61 135 grass_block" \
     "fill 145 -63 145 160 -62 165 dirt" "fill 145 -61 145 160 -61 165 grass_block" "fill 175 -63 175 190 -62 195 dirt" "fill 175 -61 175 190 -61 195 grass_block" \
     "fill 205 -63 205 220 -62 225 dirt" "fill 205 -61 205 220 -61 225 grass_block" "fill 235 -63 235 255 -62 265 dirt" "fill 235 -61 235 255 -61 265 grass_block" > /dev/null
sleep 3

# ---- A: stairs in a 3x3 shaft (bed 30,-60,30 facing south -> shaft x 29..31, z 36..38) ----
A="30 -60 30"
rcon "setblock $A air" "fill 28 -63 35 32 -60 39 air" "fill 28 -63 35 32 -61 39 dirt" "fill 28 -61 35 32 -61 39 grass_block" \
     "setblock $A goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $A Stairs" "goblinlabour tool $A 0 minecraft:iron_shovel" > /dev/null
out=$(rcon "goblinlabour dig $A down 30 -60 37 3 -64 true"); check "A dig order" "Order for Stairs" "$out"

# ---- B: chop a log column (bed 60,-60,60 facing south, logs at 60,-60..-57,67) ----
B="60 -60 60"
rcon "setblock $B air" "fill 60 -60 67 60 -53 67 oak_log" "setblock $B goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $B Chopper" "goblinlabour tool $B 0 minecraft:stone_axe" "goblinlabour tool $B 9 minecraft:oak_sapling" > /dev/null
out=$(rcon "goblinlabour job $B chop 16"); check "B set job" "CHOP" "$out"

# ---- C: farm one ripe wheat (bed 90,-60,90 facing south, wheat at 90,-60,97) ----
C="90 -60 90"
rcon "setblock $C air" "setblock 90 -61 97 farmland" "setblock 90 -60 97 wheat[age=7]" "setblock $C goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $C Farmer" "goblinlabour tool $C 9 minecraft:wheat_seeds" > /dev/null
out=$(rcon "goblinlabour job $C farm 16"); check "C set job" "FARM" "$out"

# ---- D: unload into a goblin chest, then wait when it is full (bed 120,-60,120, chest 122,-60,120) ----
D="120 -60 120"
rcon "setblock $D air" "setblock 122 -60 120 air" "setblock 122 -60 120 goblinlabour:goblin_chest[facing=north]" "setblock $D goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
# 18 of the 27 chest slots pre-filled: the goblin's 9 stacks fill it up, the next load has to wait
PRE=(); for n in $(seq 0 17); do PRE+=("item replace block 122 -60 120 container.$n with minecraft:cobblestone 64"); done
rcon "${PRE[@]}" > /dev/null
sleep 1
rcon "goblinlabour spawn $D Porter" "goblinlabour fillstorage $D minecraft:cobblestone" > /dev/null
out=$(rcon "goblinlabour job $D chop 8"); check "D set job" "CHOP" "$out"

# ---- E: sealed in (bed 150,-60,150 inside a hollow stone box) ----
E="150 -60 150"
rcon "fill 148 -62 148 152 -56 152 air" "fill 149 -61 149 151 -57 151 stone hollow" "setblock $E goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $E Prisoner" > /dev/null
out=$(rcon "goblinlabour job $E chop 8"); check "E set job" "CHOP" "$out"

# ---- G: death -> respawn -> fetch dropped items (bed 180,-60,180) ----
G="180 -60 180"
rcon "setblock $G air" "setblock $G goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $G Phoenix" "goblinlabour fillstorage $G minecraft:coal" > /dev/null
sleep 1
rcon "kill @e[type=goblinlabour:goblin,name=Phoenix]" > /dev/null

# ---- H: dig up through a stone block with scaffold (bed 210,-60,210, ceiling at y -59, target -50) ----
H="210 -60 210"
rcon "setblock $H air" "fill 208 -59 216 212 -50 220 stone" "setblock $H goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $H Climber" "goblinlabour tool $H 0 minecraft:iron_pickaxe" > /dev/null
out=$(rcon "goblinlabour dig $H up 210 -59 218 3 -50 false"); check "H dig-up order" "Order for Climber" "$out"

# ---- I: tunnel into a stone wall (bed 240,-60,240, wall z 246..260) ----
I="240 -60 240"
rcon "setblock $I air" "fill 240 -60 246 250 -56 260 stone" "setblock $I goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $I Tunneler" "goblinlabour tool $I 0 minecraft:iron_pickaxe" > /dev/null
out=$(rcon "goblinlabour tunnel $I 245 -60 246 south 3 3 8"); check "I tunnel order" "Order for Tunneler" "$out"

# ---- results ----
for k in 1 2 3 4 5 6 7 8; do sleep 10; echo "  t=$((k*10)) H: $(status "$H" | sed "s/ | order=.*//; s/inventory:.*health=//")"; echo "  t=$((k*10)) I: $(status "$I" | sed "s/ | order=.*//; s/inventory:.*health=//")"; done
out=$(wait_status "$E" 40 "no way out"); check "E reports no exit" "no way out" "$out"

out=$(wait_block 60 "90 -60 97" "wheat[age=0]"); check "C wheat harvested and replanted" "Test passed" "$out"
out=$(status "$C"); check "C wheat in inventory" "minecraft:wheat" "$out"

out=$(wait_block 150 "60 -53 67" "air"); check "B top log (8 high) felled" "Test passed" "$out"
out=$(wait_status "$B" 60 "up=false"); check "B came down the scaffold (it stays for two minutes)" "up=false" "$out"
out=$(rcon "execute if block 60 -60 67 oak_sapling"); check "B sapling replanted" "Test passed" "$out"
out=$(status "$B"); check "B logs in inventory" "oak_log" "$out"

out=$(wait_status "$D" 60 "idle"); echo "  D: $out"
out=$(rcon "data get block 122 -60 120 Items"); check "D unloaded into the goblin chest" "cobblestone" "$out"
rcon "goblinlabour fillstorage $D minecraft:cobblestone" > /dev/null
out=$(wait_status "$D" 60 "waiting"); check "D waits when the chest is full" "waiting" "$out"

out=$(wait_status "$A" 240 "Rest"); check "A shaft finished" "Rest" "$out"
out=$(rcon "execute if block 31 -60 38 cobblestone"); check "A stair corner at the top" "Test passed" "$out"
out=$(rcon "execute if block 30 -60 38 cobblestone_stairs"); check "A stair step at the top" "Test passed" "$out"
out=$(rcon "execute if block 29 -61 38 cobblestone"); check "A stair corner one layer down" "Test passed" "$out"

out=$(wait_status "$G" 60 "coal"); check "G picked its coal up again after respawn" "coal" "$out"

echo "  H: $(status "$H" | cut -c1-160)"; echo "  I: $(status "$I" | cut -c1-160)"
out=$(wait_notblock 180 "245 -59 253" "stone"); check "I tunnel reached slice 8" "Test passed" "$out"
echo "  I: $(status "$I" | cut -c1-160)"
out=$(wait_notblock 240 "210 -50 218" "stone"); check "H dug up to the target" "Test passed" "$out"
out=$(wait_status "$H" 60 "up=false"); check "H came down the scaffold" "up=false" "$out"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
