#!/usr/bin/env bash
# Farm goblins by tool (round 7 point 9) over RCON, six farmers at once: shears shear sheep and the wool ends up in the
# chest (A); a bucket milks a cow, the milk goes into the Milk Churn of the home, no milk bucket stays with the goblin,
# the churn's bucket slot fills and empties buckets, its milk shows as a block state and a broken churn keeps it (B); a hoe harvests ripe cocoa and replants it on the same side
# of the log, and cocoa beans in the tool row are planted on a free side (C); a hoe picks sweet berries and glow
# berries (D); a hoe cuts sugar cane, cactus and bamboo down to the bottom block (E); a farmer without a hoe leaves the
# ripe wheat alone, is blocked and says so (F); a treetap taps a Tech Reborn rubber log (G, only when Tech Reborn is in
# libs/ - otherwise skipped with a note).
# Scenes at x 2090..2125, z 2090..2350 on the flat world. Prints PASS/FAIL and RESULT.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
check_not() { if [[ "$3" != *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: did not expect '$2' in: $3"; fail=$((fail+1)); fi; }
ok() { if [ "$2" = 1 ]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1${3:+: $3}"; fail=$((fail+1)); fi; }
status() { rcon "goblinlabour status $1" | grep -v '^>' ; }
wait_out() { # wait_out <seconds> <substring> <command...>: runs the command every 3 s until its output has the substring
  local end=$(( $(date +%s) + $1 )) want="$2"; shift 2
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(rcon "$@" | grep -v '^>')
    if [[ "$s" == *"$want"* ]]; then echo "$s"; return 0; fi
    sleep 3
  done
  rcon "$@" | grep -v '^>'; return 1
}

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

A="2100 -60 2100"; AC="2102 -60 2100"
B="2100 -60 2140"; BC="2102 -60 2140"; BK="2098 -60 2140"
C="2100 -60 2180"; CC="2102 -60 2180"
D="2100 -60 2220"; DC="2102 -60 2220"
E="2100 -60 2260"; EC="2102 -60 2260"
F="2100 -60 2300"
G="2100 -60 2340"; GC="2102 -60 2340"
rcon "forceload add 2090 2090 2125 2350" "time set day" "weather clear" > /dev/null
rcon "setblock $A air" "setblock $B air" "setblock $C air" "setblock $D air" "setblock $E air" "setblock $F air" "setblock $G air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=2090,y=-64,z=2090,dx=35,dy=20,dz=260]" "kill @e[type=item,x=2090,y=-64,z=2090,dx=35,dy=20,dz=260]" \
     "kill @e[type=sheep,x=2090,y=-64,z=2090,dx=35,dy=20,dz=260]" "kill @e[type=cow,x=2090,y=-64,z=2090,dx=35,dy=20,dz=260]" > /dev/null
# 36 x 11 x 44 in slabs below Minecraft's fill limit
for z in 2090 2134 2178 2222 2266 2310; do
  rcon "fill 2090 -60 $z 2125 -50 $((z + 43)) air" "fill 2090 -61 $z 2125 -61 $((z + 43)) grass_block" > /dev/null
done
sleep 2

# ---- A: shears and two sheep ----
rcon "setblock $AC goblinlabour:goblin_chest[facing=west]" "setblock $A goblinlabour:goblin_straw_bed[facing=east]" \
     "summon sheep 2110 -60 2097 {NoAI:1b}" "summon sheep 2111 -60 2103 {NoAI:1b,Color:14}" > /dev/null
# ---- B: bucket, a cow and a Milk Churn at home ----
rcon "setblock $BC goblinlabour:milk_churn" "setblock $BK goblinlabour:goblin_chest[facing=east]" "setblock $B goblinlabour:goblin_straw_bed[facing=east]" \
     "summon cow 2110 -60 2140 {NoAI:1b}" > /dev/null
# ---- C: hoe, a jungle log pillar with a ripe cocoa pod on its east side ----
rcon "setblock $CC goblinlabour:goblin_chest[facing=west]" "setblock $C goblinlabour:goblin_straw_bed[facing=east]" \
     "fill 2110 -60 2180 2110 -57 2180 jungle_log" "setblock 2111 -59 2180 cocoa[age=2,facing=west]" > /dev/null
# ---- D: hoe, a ripe sweet berry bush and glow berries hanging from a stone block ----
rcon "setblock $DC goblinlabour:goblin_chest[facing=west]" "setblock $D goblinlabour:goblin_straw_bed[facing=east]" \
     "setblock 2108 -60 2220 sweet_berry_bush[age=3]" "setblock 2110 -57 2223 stone" "setblock 2110 -58 2223 cave_vines[berries=true,age=25]" > /dev/null
# ---- E: hoe, sugar cane by the water, a cactus on sand, bamboo ----
rcon "setblock $EC goblinlabour:goblin_chest[facing=west]" "setblock $E goblinlabour:goblin_straw_bed[facing=east]" \
     "setblock 2109 -61 2260 water" "setblock 2108 -60 2260 sugar_cane" "setblock 2108 -59 2260 sugar_cane" "setblock 2108 -58 2260 sugar_cane" \
     "setblock 2112 -61 2265 sand" "setblock 2112 -60 2265 cactus" "setblock 2112 -59 2265 cactus" "setblock 2112 -58 2265 cactus" \
     "setblock 2107 -60 2268 bamboo" "setblock 2107 -59 2268 bamboo" "setblock 2107 -58 2268 bamboo" "setblock 2107 -57 2268 bamboo" > /dev/null
# ---- F: no hoe, ripe wheat ----
rcon "setblock $F goblinlabour:goblin_straw_bed[facing=east]" "setblock 2106 -61 2300 farmland" "setblock 2106 -60 2300 wheat[age=7]" > /dev/null
# ---- G: treetap and a rubber log with sap (Tech Reborn only) ----
tr=$(rcon "setblock 2108 -60 2340 techreborn:rubber_log[hassap=true,facing=west]" | grep -v '^>')
rcon "setblock $GC goblinlabour:goblin_chest[facing=west]" "setblock $G goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
sleep 1

rcon "goblinlabour spawn $A Shearer" "goblinlabour tool $A 0 minecraft:shears" "goblinlabour job $A farm 12" \
     "goblinlabour spawn $B Milker" "goblinlabour tool $B 0 minecraft:bucket" "goblinlabour job $B farm 12" \
     "goblinlabour spawn $C Cocoa" "goblinlabour tool $C 0 minecraft:iron_hoe" "goblinlabour job $C farm 12" \
     "goblinlabour spawn $D Berry" "goblinlabour tool $D 0 minecraft:iron_hoe" "goblinlabour job $D farm 12" \
     "goblinlabour spawn $E Cane" "goblinlabour tool $E 0 minecraft:iron_hoe" "goblinlabour job $E farm 12" \
     "goblinlabour spawn $F Handless" "goblinlabour job $F farm 12" > /dev/null
[[ "$tr" == *"Changed the block"* ]] && rcon "goblinlabour spawn $G Tapper" "goblinlabour tool $G 0 techreborn:treetap" "goblinlabour job $G farm 12" > /dev/null
t0=$(date +%s)

# ---- B: milk into the churn ----
out=$(wait_out 90 "milk=1000 " "goblinlabour churn $BC"); check "B the milk went into the Milk Churn" "milk=1000 " "$out"
echo "  B: churn has milk after $(( $(date +%s) - t0 )) s"
out=$(status "$B")
check_not "B no milk bucket stays with the goblin" "milk_bucket" "$(echo "$out" | grep -o 'inventory:[^|]*')"
check "B the bucket stays in the tool row" "0=1xminecraft:bucket" "$out"
rcon "data merge block $BC {Items:[{Slot:0b,id:\"minecraft:milk_bucket\",count:1}]}" > /dev/null
out=$(wait_out 10 "milk=2000 " "goblinlabour churn $BC")
check "B a milk bucket in the bucket slot is poured in" "milk=2000 " "$out"
check "B its empty bucket comes out below" "output=1xminecraft:bucket" "$out"
rcon "data merge block $BC {Items:[{Slot:0b,id:\"minecraft:bucket\",count:1}]}" > /dev/null
out=$(wait_out 10 "milk=1000 " "goblinlabour churn $BC")
check "B an empty bucket in the bucket slot is filled" "milk=1000 " "$out"
check "B the milk bucket comes out below" "output=1xminecraft:milk_bucket" "$out"
rcon "goblinlabour churn $BC 4000" > /dev/null
out=$(wait_out 10 "Test passed" "execute if block $BC goblinlabour:milk_churn[level=4]"); check "B the churn shows four buckets behind the glass" "Test passed" "$out"
# in one go: the Milker tidies loose items at home into its chest within seconds
out=$(rcon "setblock $BC air destroy" \
  "execute if entity @e[type=item,x=2099,y=-62,z=2137,dx=6,dy=6,dz=6,nbt={Item:{id:\"goblinlabour:milk_churn\",components:{\"goblinlabour:milk\":4000}}}]" \
  "goblinlabour churn $BC placedrop" | grep -v '^>')
check "B the broken churn drops with its 4000 mB" "Test passed" "$out"
check "B placed again, the churn has its milk back" "milk=4000 " "$out"
out=$(wait_out 10 "Test passed" "execute if block $BC goblinlabour:milk_churn[level=4]"); check "B and shows it again" "Test passed" "$out"
rcon "goblinlabour churn $BC 0" > /dev/null
out=$(wait_out 10 "Test passed" "execute if block $BC goblinlabour:milk_churn[level=0]"); check "B an empty churn shows no milk" "Test passed" "$out"

# ---- A: wool in the chest ----
out=$(wait_out 90 "wool" "data get block $AC Items"); check "A the wool of the sheared sheep is in the chest" "wool" "$out"
out=$(rcon "execute if entity @e[type=sheep,x=2100,y=-62,z=2090,dx=20,dy=5,dz=20,nbt={Sheared:1b}]" | grep -v '^>')
check "A both sheep were sheared" "Count: 2" "$out"

# ---- C: cocoa ----
out=$(wait_out 90 "Test passed" "execute if block 2111 -59 2180 cocoa[age=0,facing=west]"); check "C the ripe cocoa pod was harvested and replanted" "Test passed" "$out"
out=$(wait_out 60 "cocoa_beans" "data get block $CC Items"); check "C the cocoa beans are in the chest" "cocoa_beans" "$out"
rcon "goblinlabour tool $C 1 minecraft:cocoa_beans" > /dev/null
pods=0
for _ in $(seq 1 20); do
  sleep 3
  Q=(); for y in -60 -59 -58 -57; do for xz in "2111 2180" "2109 2180" "2110 2179" "2110 2181"; do set -- $xz; Q+=("execute if block $1 $y $2 cocoa"); done; done
  pods=$(rcon "${Q[@]}" | grep -c "Test passed")
  [ "$pods" -ge 2 ] && break
done
ok "C a cocoa bean from the tool row was planted on a free side of the log" "$(( pods >= 2 ? 1 : 0 ))" "$pods pods"
check_not "C the bean left the tool row" "1=1xminecraft:cocoa_beans" "$(status "$C")"

# ---- D: berries ----
out=$(wait_out 90 "Test passed" "execute if block 2108 -60 2220 sweet_berry_bush[age=1]"); check "D the sweet berry bush was picked (back to age 1)" "Test passed" "$out"
out=$(wait_out 60 "Test passed" "execute if block 2110 -58 2223 cave_vines[berries=false]"); check "D the glow berries were picked" "Test passed" "$out"
out=$(wait_out 60 "glow_berries" "data get block $DC Items"); check "D glow berries in the chest" "glow_berries" "$out"
check "D sweet berries in the chest" "sweet_berries" "$(rcon "data get block $DC Items")"

# ---- E: stalks ----
out=$(wait_out 90 "cactus" "data get block $EC Items")
for item in sugar_cane cactus bamboo; do check "E $item in the chest" "minecraft:$item" "$(rcon "data get block $EC Items")"; done
for b in "2108 -60 2260 sugar_cane" "2112 -60 2265 cactus" "2107 -60 2268 bamboo"; do
  set -- $b; check "E the bottom $4 block stays" "Test passed" "$(rcon "execute if block $1 $2 $3 $4")"
done
for b in "2108 -59 2260" "2112 -59 2265" "2107 -59 2268"; do check "E everything above the bottom block is cut ($b)" "Test passed" "$(rcon "execute if block $b air")"; done

# ---- F: no hoe ----
out=$(status "$F"); check "F the farmer without a hoe is blocked" "blocked" "$out"
check "F the ripe wheat is left standing" "Test passed" "$(rcon "execute if block 2106 -60 2300 wheat[age=7]")"
ok "F it asks for a hoe" "$(grep -q 'Handless> The crops are ripe, but I have no hoe' run/runServer.out && echo 1 || echo 0)"

# ---- G: treetap ----
if [[ "$tr" == *"Changed the block"* ]]; then
  out=$(wait_out 90 "Test passed" "execute if block 2108 -60 2340 techreborn:rubber_log[hassap=false]"); check "G the rubber log was tapped" "Test passed" "$out"
  out=$(wait_out 60 "techreborn:sap" "data get block $GC Items"); check "G the sap is in the chest" "techreborn:sap" "$out"
else
  echo "  G skipped: Tech Reborn is not loaded ($tr)"
fi

deaths=$(grep 'died:' run/runServer.out | grep -c 'GoblinEntity')
ok "no farmer died" "$(( deaths == 0 ? 1 : 0 ))" "$(grep 'died:' run/runServer.out | sed 's/.*died: //' | tr '\n' ';')"
echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
