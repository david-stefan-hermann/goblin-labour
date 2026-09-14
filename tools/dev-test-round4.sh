#!/usr/bin/env bash
# Feedback round 4 over RCON: climbing out of a pit on scaffold, a second goblin reusing that column, the two-minute
# scaffold lifetime, a real shaft without stairs, the collect job and the rest stroll (pauses between walks).
# Scenes live at x/z 300..460 on the flat world. Prints PASS/FAIL and RESULT.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
check_eq() { if [ "$2" = "$3" ]; then echo "PASS $1 ($3)"; pass=$((pass+1)); else echo "FAIL $1: expected $2, got $3"; fail=$((fail+1)); fi; }
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
wait_cmd() { # wait_cmd <seconds> <command>: until the command answers "Test passed"
  local end=$(( $(date +%s) + $1 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(rcon "$2")
    if [[ "$s" == *"Test passed"* ]]; then echo "$s"; return 0; fi
    sleep 3
  done
  rcon "$2"; return 1
}
count_scaffold() { # count_scaffold x1 x2 y1 y2 z1 z2
  local cmds=()
  for x in $(seq "$1" "$2"); do for y in $(seq "$3" "$4"); do for z in $(seq "$5" "$6"); do
    cmds+=("execute if block $x $y $z goblinlabour:goblin_scaffold")
  done; done; done
  rcon "${cmds[@]}" | grep -c "Test passed"
}
in_box() { # in_box <name> x y z dx dy dz
  echo "execute if entity @e[type=goblinlabour:goblin,name=$1,x=$2,y=$3,z=$4,dx=$5,dy=$6,dz=$7]"
}

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add 290 290 460 460" "time set day" "weather clear" > /dev/null
rcon "setblock 300 -60 300 air" "setblock 300 -60 320 air" "setblock 345 -51 350 air" "setblock 400 -60 400 air" "setblock 440 -60 440 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=290,y=-64,z=290,dx=170,dy=40,dz=170]" "kill @e[type=item,x=290,y=-64,z=290,dx=170,dy=40,dz=170]" \
     "fill 292 -60 292 330 -45 330 air" "fill 336 -60 336 376 -45 364 air" "fill 390 -60 390 425 -50 415 air" "fill 430 -60 430 452 -50 452 air" > /dev/null
rcon "fill 292 -61 292 330 -61 330 grass_block" "fill 336 -61 336 376 -61 364 grass_block" "fill 390 -61 390 425 -61 415 grass_block" "fill 430 -61 430 452 -61 452 grass_block" > /dev/null
sleep 2

# ---- P: resting goblin stuck in a 3x3 pit, 5 deep, dug into raised ground (surface y -56, pit x 313..315 z 299..301) ----
P="300 -55 300"
rcon "fill 292 -60 292 330 -56 330 stone" "fill 313 -60 299 315 -56 301 air" "setblock $P goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $P Pitty" > /dev/null
sleep 1
rcon "tp @e[type=goblinlabour:goblin,name=Pitty] 314.5 -60 300.5" > /dev/null

# ---- S: shaft without stairs from a raised platform (bed on top at 345,-51,350, shaft x 359..361 z 349..351) ----
S="345 -51 350"
rcon "fill 338 -60 340 372 -52 360 stone" "setblock $S goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $S Digger" "goblinlabour tool $S 0 minecraft:iron_pickaxe" > /dev/null
out=$(rcon "goblinlabour dig $S down 360 -52 350 3 -60 false"); check "S dig order without stairs" "Order for Digger" "$out"

# ---- C: collect (bed 400,-60,400, copper chest in the flat, two items in the radius, one outside) ----
C="400 -60 400"
rcon "setblock 402 -60 400 copper_chest" "setblock $C goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $C Magpie" > /dev/null
rcon "summon item 404 -60 406 {Item:{id:\"minecraft:diamond\",count:3}}" "summon item 397 -60 407 {Item:{id:\"minecraft:apple\",count:5}}" \
     "summon item 420 -60 400 {Item:{id:\"minecraft:emerald\",count:1}}" > /dev/null
out=$(rcon "goblinlabour job $C collect 8"); check "C set job" "COLLECT" "$out"

# ---- W: stroll (bed 440,-60,440, resting) ----
W="440 -60 440"
rcon "setblock $W goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $W Stroller" > /dev/null

# ---- results ----
out=$(wait_cmd 120 "$(in_box Pitty 295 -56 295 10 4 10)"); check "P climbed out of the pit and went home" "Test passed" "$out"
n1=$(count_scaffold 313 315 -60 -56 299 301); echo "  P scaffold blocks left in the pit: $n1"
if [ "$n1" -ge 5 ]; then echo "PASS P left its scaffold column standing"; pass=$((pass+1)); else echo "FAIL P scaffold column: $n1 blocks"; fail=$((fail+1)); fi

# ---- Q: a second goblin in the same pit uses the existing column ----
Q="300 -55 320"
rcon "setblock $Q goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $Q Second" > /dev/null
sleep 1
rcon "tp @e[type=goblinlabour:goblin,name=Second] 313.5 -60 301.5" > /dev/null
out=$(wait_cmd 120 "$(in_box Second 295 -56 315 10 4 10)"); check "Q climbed out as well" "Test passed" "$out"
n2=$(count_scaffold 313 315 -60 -56 299 301)
check_eq "Q reused the column (no new scaffold)" "$n1" "$n2"
t_left=$(date +%s)

# ---- W: pauses between walks (sampled once a second) ----
still=0; moving=0; last=""
for _ in $(seq 1 60); do
  p=$(rcon "data get entity @e[type=goblinlabour:goblin,name=Stroller,limit=1] Pos" | grep -o '\[[-0-9.d, ]*\]' | tail -1 | tr -d '[]d ')
  if [ -n "$last" ] && [ -n "$p" ]; then
    if awk -F, -v a="$last" -v b="$p" 'BEGIN{split(a,x,",");split(b,y,","); d=(x[1]-y[1])^2+(x[3]-y[3])^2; exit !(d<0.0025)}'; then still=$((still+1)); else moving=$((moving+1)); fi
  fi
  last="$p"; sleep 1
done
echo "  W samples: still=$still moving=$moving"
if [ "$still" -ge 15 ] && [ "$moving" -ge 3 ]; then echo "PASS W strolls with pauses"; pass=$((pass+1)); else echo "FAIL W stroll: still=$still moving=$moving"; fail=$((fail+1)); fi

out=$(rcon "data get block 402 -60 400 Items"); check "C diamonds in the chest" "diamond" "$out"; check "C apples in the chest" "apple" "$out"
out=$(rcon "execute if entity @e[type=item,x=419,y=-61,z=399,dx=2,dy=2,dz=2]"); check "C item outside the radius untouched" "Test passed" "$out"

# ---- scaffold lifetime: still there before two minutes, gone after ----
sleep $(( 45 - ($(date +%s) - t_left) > 0 ? 45 - ($(date +%s) - t_left) : 0 ))
n3=$(count_scaffold 313 315 -60 -55 299 301); check_eq "L column still standing 45 s after the last touch" "$n1" "$n3"

out=$(wait_status "$S" 300 "job: Rest"); check "S shaft finished" "job: Rest" "$out"
out=$(wait_cmd 150 "$(in_box Digger 340 -51 345 10 4 10)"); check "S climbed out of the shaft and went home" "Test passed" "$out"
n4=$(count_scaffold 359 361 -60 -52 349 351); echo "  S scaffold blocks in the shaft: $n4"
if [ "$n4" -ge 6 ]; then echo "PASS S climbed on scaffold"; pass=$((pass+1)); else echo "FAIL S scaffold in shaft: $n4"; fail=$((fail+1)); fi

wait_left=$(( 135 - ($(date +%s) - t_left) ))
[ "$wait_left" -gt 0 ] && sleep "$wait_left"
n5=$(count_scaffold 313 315 -60 -55 299 301); check_eq "L column gone after two minutes without a goblin" "0" "$n5"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
