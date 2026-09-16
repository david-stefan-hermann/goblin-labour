#!/usr/bin/env bash
# Feedback round 7 over RCON: a goblin goes back to the job a staff order interrupted (J), a tunnel of height 1
# digs exactly the row that was clicked and leaves the row above it alone (T), four goblins in a pit without stairs
# all climb out, one after the other on a shared column (P), and a shaft up from a ceiling with air below it gets its
# stair steps through the air down to the floor (U).
# Scenes live at x/z 1395..1515 on the flat world. Prints PASS/FAIL and RESULT.
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
resp() { echo "$ALL" | grep -A1 -F "> $1" | tail -1; }
statusof() { echo "$ALL" | grep -A1 -F "> goblinlabour status $1" | tail -1; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

J="1400 -60 1390"; T="1440 -60 1390"
P1="1455 -55 1470"; P2="1457 -55 1470"; P3="1459 -55 1470"; P4="1461 -55 1470"; U="1492 -60 1485"
rcon "forceload add 1395 1385 1450 1415" "forceload add 1445 1450 1515 1515" "time set day" "weather clear" > /dev/null
rcon "setblock $J air" "setblock $T air" "setblock $P1 air" "setblock $P2 air" "setblock $P3 air" "setblock $P4 air" "setblock $U air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1395,y=-64,z=1385,dx=55,dy=40,dz=35]" \
     "kill @e[type=item,x=1395,y=-64,z=1385,dx=55,dy=40,dz=35]" \
     "kill @e[type=goblinlabour:goblin,x=1445,y=-64,z=1450,dx=70,dy=40,dz=65]" \
     "kill @e[type=item,x=1445,y=-64,z=1450,dx=70,dy=40,dz=65]" > /dev/null
rcon "fill 1396 -60 1386 1414 -50 1410 air" "fill 1396 -61 1386 1414 -61 1410 grass_block" \
     "fill 1398 -64 1398 1402 -62 1402 dirt" \
     "fill 1430 -60 1386 1450 -50 1412 air" "fill 1430 -61 1386 1450 -61 1412 grass_block" \
     "fill 1445 -60 1450 1480 -50 1515 air" "fill 1481 -60 1450 1515 -50 1515 air" "fill 1445 -61 1450 1515 -61 1515 grass_block" > /dev/null
sleep 2

# ---- J: a lumberjack (no trees around, so it idles) is sent down a 1x1 shaft and must go back to chopping ----
rcon "setblock $J goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $J Switch" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Switch] 1400.5 -60 1396.5" > /dev/null
rcon "goblinlabour job $J chop 16" > /dev/null
out=$(status "$J"); check "J the goblin starts as a lumberjack" "(job: Chop)" "$out"
out=$(rcon "goblinlabour dig $J down 1400 -61 1400 1 -63 false"); check "J shaft order given" "Order for Switch" "$out"
sleep 3
out=$(status "$J"); check "J the order took the goblin off chopping" "(job: Dig Down)" "$out"

# ---- T: a 1 wide, 1 high tunnel of 4 into a stone wall; the row above the clicked block must stay ----
rcon "fill 1436 -60 1400 1444 -55 1410 stone" "setblock $T goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $T Crawler" "goblinlabour tool $T 0 minecraft:iron_pickaxe" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Crawler] 1440.5 -60 1397.5" > /dev/null
out=$(rcon "goblinlabour tunnel $T 1440 -60 1400 south 1 1 4"); check "T tunnel order of height 1 given" "Order for Crawler" "$out"

# ---- watch both scenes ----
j_back=0; j_dug=0; t_done=0
end=$(( $(date +%s) + 300 ))
while [ "$(date +%s)" -lt "$end" ]; do
  Q=("goblinlabour status $J" "goblinlabour status $T"
     "execute if block 1400 -63 1400 air"
     "execute if block 1440 -60 1400 air" "execute if block 1440 -60 1401 air"
     "execute if block 1440 -60 1402 air" "execute if block 1440 -60 1403 air")
  ALL=$(rcon "${Q[@]}")
  [[ "$(resp "execute if block 1400 -63 1400 air")" == *"Test passed"* ]] && j_dug=1
  [ "$j_dug" = 1 ] && [[ "$(statusof "$J")" == *"(job: Chop)"* ]] && j_back=1
  n=0; for b in "1440 -60 1400" "1440 -60 1401" "1440 -60 1402" "1440 -60 1403"; do
    [[ "$(resp "execute if block $b air")" == *"Test passed"* ]] && n=$((n+1)); done
  [ "$n" = 4 ] && t_done=1
  [ "$j_back" = 1 ] && [ "$t_done" = 1 ] && break
  sleep 2
done
sleep 5

echo "  J: $(status "$J" | head -1 | cut -c1-200)"
ok "J the shaft was dug" "$j_dug"
ok "J the goblin went back to chopping" "$j_back" "status: $(status "$J" | head -1 | cut -c1-160)"
out=$(status "$J"); check_not "J no order left on the bed" "order=" "$out"

echo "  T: $(status "$T" | head -1 | cut -c1-200)"
ok "T the 1 high tunnel was dug" "$t_done"
out=$(rcon "execute if block 1440 -59 1400 stone"); check "T the row above the clicked block is untouched" "Test passed" "$out"
out=$(rcon "execute if block 1440 -61 1400 grass_block"); check "T the floor below the tunnel is untouched" "Test passed" "$out"
out=$(rcon "execute if block 1440 -60 1404 stone"); check "T the tunnel stopped after 4 blocks" "Test passed" "$out"

# ---- P: a stone platform 5 high with a 3x3 pit 5 deep and no stairs; four goblins in the pit, their beds on top ----
# The beds stand 10 blocks off, so the pit is outside their (merged) home.
rcon "fill 1450 -60 1455 1485 -56 1485 stone" "fill 1471 -60 1469 1473 -56 1471 air" \
     "setblock $P1 goblinlabour:goblin_straw_bed[facing=north]" "setblock $P2 goblinlabour:goblin_straw_bed[facing=north]" \
     "setblock $P3 goblinlabour:goblin_straw_bed[facing=north]" "setblock $P4 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null
sleep 1
rcon "goblinlabour spawn $P1 Pit1" "goblinlabour spawn $P2 Pit2" "goblinlabour spawn $P3 Pit3" "goblinlabour spawn $P4 Pit4" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Pit1] 1471.5 -60 1469.5" "tp @e[type=goblinlabour:goblin,name=Pit2] 1473.5 -60 1469.5" \
     "tp @e[type=goblinlabour:goblin,name=Pit3] 1471.5 -60 1471.5" "tp @e[type=goblinlabour:goblin,name=Pit4] 1473.5 -60 1471.5" > /dev/null
rcon "goblinlabour job $P1 rest" "goblinlabour job $P2 rest" "goblinlabour job $P3 rest" "goblinlabour job $P4 rest" > /dev/null
out=$(status "$P4"); check "P the goblins rest (want to go home)" "(job: Rest)" "$out"
p_start=$(date +%s)

# ---- U: floor, 4 blocks of air, a stone ceiling 7 thick; a 3x3 shaft up from the ceiling with stairs ----
rcon "fill 1497 -56 1495 1507 -50 1505 stone" "setblock $U goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $U Stepper" "goblinlabour tool $U 0 minecraft:iron_pickaxe" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Stepper] 1502.5 -60 1497.5" > /dev/null
out=$(rcon "goblinlabour dig $U up 1502 -56 1500 3 -50 true"); check "U shaft order given" "Order for Stepper" "$out"
check "U the shaft starts on the floor below the ceiling" "floorY=-60" "$out"

# ---- watch P and U ----
p_home=0; p_waits=0; u_done=0; p_time=""
end=$(( $(date +%s) + 330 ))
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 3
  if [ "$p_home" = 0 ]; then
    ALL=$(rcon "goblinlabour status $P1" "goblinlabour status $P2" "goblinlabour status $P3" "goblinlabour status $P4")
    p_waits=$(( p_waits + $(echo "$ALL" | grep -o 'ClimbOutGoal/WAIT' | wc -l) ))
    up=0
    for pos in $(echo "$ALL" | grep -o 'pos=[-0-9]*, [-0-9]*, [-0-9]*' | sed 's/pos=//; s/, /,/g'); do
      y=$(echo "$pos" | cut -d, -f2); x=$(echo "$pos" | cut -d, -f1)
      [ "$y" -ge -55 ] && [ "$x" -le 1468 ] && up=$((up + 1))
    done
    if [ "$up" = 4 ]; then p_home=1; p_time=$(( $(date +%s) - p_start )); fi
  fi
  if [ "$u_done" = 0 ]; then
    st=$(status "$U")
    [[ "$st" == *"(job: Rest)"* ]] && [[ "$st" != *"order="* ]] && u_done=1
  fi
  [ "$p_home" = 1 ] && [ "$u_done" = 1 ] && break
done

for b in "$P1" "$P2" "$P3" "$P4"; do echo "  P: $(status "$b" | tail -1 | grep -o 'pos=[-0-9, ]*\|goals:.*' | tr '\n' ' ' | cut -c1-160)"; done
echo "  P: waits seen in samples: $p_waits"
ok "P all four goblins climbed out of the pit and went home within 4 minutes" "$(( p_home == 1 && ${p_time:-999} <= 240 ? 1 : 0 ))" "home=$p_home after ${p_time:-never} s"
SCAF=()
for x in 1471 1472 1473; do for z in 1469 1470 1471; do for y in -60 -59 -58 -57 -56; do SCAF+=("execute if block $x $y $z goblinlabour:goblin_scaffold"); done; done; done
scaffold=$(rcon "${SCAF[@]}" | grep -c "Test passed")
ok "P they climbed on scaffold (not blinked home)" "$(( scaffold > 0 ? 1 : 0 ))" "$scaffold scaffold blocks in the pit"

echo "  U: $(status "$U" | head -1 | cut -c1-160)"
ok "U the shaft order is done" "$u_done"
steps=0
for y in -60 -59 -58 -57; do
  Q=(); for x in 1501 1502 1503; do for z in 1499 1500 1501; do Q+=("execute unless block $x $y $z air"); done; done
  [ "$(rcon "${Q[@]}" | grep -c "Test passed")" -ge 1 ] && steps=$((steps + 1))
done
ok "U every air layer between floor and ceiling got a stair step" "$(( steps == 4 ? 1 : 0 ))" "$steps of 4 layers"
# stair steps, torches and scaffold stay in a finished shaft; the stone must be gone
Q=(); for y in -56 -55 -54 -53 -52 -51 -50; do for x in 1501 1502 1503; do for z in 1499 1500 1501; do Q+=("execute if block $x $y $z stone"); done; done; done
stone=$(rcon "${Q[@]}" | grep -c "Test passed")
ok "U the shaft was dug up to the target height (no stone left in it)" "$(( stone == 0 ? 1 : 0 ))" "$stone stone blocks left"
upos=""
for _ in $(seq 1 30); do
  upos=$(status "$U" | grep -o 'pos=[-0-9]*, [-0-9]*, [-0-9]*' | head -1 | sed 's/pos=//; s/, /,/g')
  [ "$(echo "$upos" | cut -d, -f2)" = -60 ] && break
  sleep 2
done
ok "U the goblin is back down on the floor" "$(( $(echo "$upos" | cut -d, -f2) == -60 ? 1 : 0 ))" "pos=$upos"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
