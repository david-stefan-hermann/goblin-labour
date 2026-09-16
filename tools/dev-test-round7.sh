#!/usr/bin/env bash
# Feedback round 7 over RCON: a goblin goes back to the job a staff order interrupted (J), a tunnel of height 1
# digs exactly the row that was clicked and leaves the row above it alone (T), four goblins in a pit without stairs
# all climb out, one after the other on a shared column (P), and a shaft up from a ceiling with air below it gets its
# stair steps through the air down to the floor (U), a lumberjack plants the saplings from its tool row 3 to 6 blocks
# apart inside its radius and unloads every sapling it carries into the chest (S), and a lumberjack fells a mega jungle
# tree, a small jungle tree and the jungle bushes around them completely, hacking its way through the leaves, without
# falling to its death (W).
# Scenes live at x/z 1395..1565 and 1755..1805 on the flat world. Prints PASS/FAIL and RESULT.
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
P1="1455 -55 1470"; P2="1457 -55 1470"; P3="1459 -55 1470"; P4="1461 -55 1470"; U="1492 -60 1485"; S="1542 -60 1462"; SC="1544 -60 1462"
rcon "forceload add 1395 1385 1450 1415" "forceload add 1445 1450 1515 1515" "forceload add 1520 1440 1565 1485" "time set day" "weather clear" > /dev/null
rcon "setblock $J air" "setblock $T air" "setblock $P1 air" "setblock $P2 air" "setblock $P3 air" "setblock $P4 air" "setblock $U air" "setblock $S air" "setblock $SC air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1395,y=-64,z=1385,dx=55,dy=40,dz=35]" \
     "kill @e[type=item,x=1395,y=-64,z=1385,dx=55,dy=40,dz=35]" \
     "kill @e[type=goblinlabour:goblin,x=1445,y=-64,z=1450,dx=70,dy=40,dz=65]" \
     "kill @e[type=item,x=1445,y=-64,z=1450,dx=70,dy=40,dz=65]" \
     "kill @e[type=goblinlabour:goblin,x=1520,y=-64,z=1440,dx=45,dy=40,dz=45]" \
     "kill @e[type=item,x=1520,y=-64,z=1440,dx=45,dy=40,dz=45]" > /dev/null
rcon "fill 1396 -60 1386 1414 -50 1410 air" "fill 1396 -61 1386 1414 -61 1410 grass_block" \
     "fill 1398 -64 1398 1402 -62 1402 dirt" \
     "fill 1430 -60 1386 1450 -50 1412 air" "fill 1430 -61 1386 1450 -61 1412 grass_block" \
     "fill 1445 -60 1450 1480 -50 1515 air" "fill 1481 -60 1450 1515 -50 1515 air" "fill 1445 -61 1450 1515 -61 1515 grass_block" \
     "fill 1520 -60 1440 1565 -50 1485 air" "fill 1520 -61 1440 1565 -61 1485 grass_block" > /dev/null
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

# ---- S: a lumberjack with 8 oak saplings in its tool row on a meadow, radius 8, no trees ----
# Its home (11 x 11 around the bed) is never planted, so the saplings go into the ring of ground around it.
rcon "setblock $S goblinlabour:goblin_straw_bed[facing=south]" "setblock $SC goblinlabour:goblin_chest[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $S Planter" "goblinlabour tool $S 0 minecraft:iron_axe" > /dev/null
for slot in 1 2 3 4 5 6 7 8; do rcon "goblinlabour tool $S $slot minecraft:oak_sapling" > /dev/null; done
rcon "goblinlabour job $S chop 8" > /dev/null
SQ=(); for x in $(seq 1534 1550); do for z in $(seq 1454 1470); do SQ+=("execute if block $x -60 $z oak_sapling"); done; done
planted=0
end=$(( $(date +%s) + 180 ))
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 5
  planted=$(rcon "${SQ[@]}" | grep -c "Test passed")
  [ "$planted" -ge 8 ] && break
done
spots=$(rcon "${SQ[@]}" | grep -B1 "Test passed" | grep '^>' | awk '{print $5 "," $7}')
echo "  S: $planted saplings planted at $(echo $spots)"
ok "S at least 6 saplings from the tool row were planted within 3 minutes" "$(( planted >= 6 ? 1 : 0 ))" "$planted planted"
close=$(echo "$spots" | awk -F, '{x[NR] = $1; z[NR] = $2} END { c = 0; for (i = 1; i <= NR; i++) for (j = i + 1; j <= NR; j++) { dx = x[i] - x[j]; dz = z[i] - z[j]; if (dx * dx + dz * dz < 9) c++ } print c }')
ok "S the saplings stand at least 3 blocks apart" "$(( close == 0 ? 1 : 0 ))" "$close pairs closer than 3"
outside=$(echo "$spots" | awk -F, '$1 < 1534 || $1 > 1550 || $2 < 1454 || $2 > 1470' | wc -l)
home=$(echo "$spots" | awk -F, '$1 >= 1537 && $1 <= 1547 && $2 >= 1457 && $2 <= 1467' | wc -l)
ok "S all saplings inside the radius and none in the home" "$(( outside == 0 && home == 0 ? 1 : 0 ))" "$outside outside, $home in the home"
# saplings among its storage (what a felled tree drops) all go into the chest, none kept back
rcon "goblinlabour fillstorage $S minecraft:oak_sapling" > /dev/null
kept=1
for _ in $(seq 1 30); do
  sleep 3
  inv=$(status "$S" | grep -o 'inventory:[^|]*')
  if ! echo "$inv" | grep -qE ' (9|1[0-9]|2[0-9])=[0-9]+xminecraft:oak_sapling'; then kept=0; break; fi
done
out=$(rcon "data get block $SC Items")
check "S the saplings from the storage are in the chest" "oak_sapling" "$out"
ok "S no sapling kept back in the storage" "$(( kept == 0 ? 1 : 0 ))" "inventory: $(status "$S" | grep -o 'inventory:[^|]*' | cut -c1-160)"

# ---- W: a mega jungle tree, a small jungle tree and ten jungle bushes (a log in a ball of leaves) for one lumberjack ----
# Replanting is off, so no new tree grows back in the scene while it is being counted.
W="1766 -60 1780"; WC="1768 -60 1780"
rcon "forceload add 1750 1750 1810 1810" "setblock $W air" "setblock $WC air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1750,y=-64,z=1750,dx=60,dy=60,dz=60]" "kill @e[type=item,x=1750,y=-64,z=1750,dx=60,dy=60,dz=60]" > /dev/null
for x in 1755 1772 1789; do rcon "fill $x -60 1755 $((x + 16)) -31 1805 air" "fill $x -30 1755 $((x + 16)) -1 1805 air" > /dev/null; done
rcon "fill 1755 -61 1755 1805 -61 1805 grass_block" > /dev/null
sleep 1
out=$(rcon "place feature minecraft:mega_jungle_tree 1780 -60 1780" "place feature minecraft:jungle_tree 1782 -60 1796")
check "W the jungle trees stand" "Placed" "$out"
for spot in "1775 1778" "1776 1784" "1778 1774" "1783 1775" "1786 1779" "1786 1785" "1782 1787" "1777 1789" "1788 1782" "1774 1781"; do
  set -- $spot
  rcon "place feature minecraft:jungle_bush $1 -60 $2" > /dev/null
done
rcon "setblock $W goblinlabour:goblin_straw_bed[facing=east]" "setblock $WC goblinlabour:goblin_chest[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $W Jungler" "goblinlabour tool $W 0 minecraft:diamond_axe" "goblinlabour job $W chop 24" "goblinlabour replant $W false" > /dev/null
w_start=$(date +%s); w_idle=0; w_time=""
end=$(( w_start + 480 ))
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 10
  st=$(status "$W")
  # done: no tree claimed, nothing to do and nothing skipped, twice in a row (and not right at the start)
  if [ $(( $(date +%s) - w_start )) -ge 60 ] && [[ "$st" == *"tree=-"* && "$st" == *"target=- "* && "$st" == *"skipped=0 "* ]]; then
    w_idle=$((w_idle + 1)); [ "$w_idle" -ge 2 ] && { w_time=$(( $(date +%s) - w_start )); break; }
  else
    w_idle=0
  fi
done
echo "  W: done after ${w_time:-never} s; $(status "$W" | tail -1 | grep -oE 'pos=[-0-9, ]*|tree=(-|[^)]*[)])|skipped=[0-9]*' | tr '\n' ' ')"
out=$(rcon "fill 1770 -60 1770 1792 -24 1802 air replace #minecraft:logs")
check_not "W every log of the jungle trees and bushes felled" "Successfully" "$out"
check_not "W log count command ran" "Too many blocks" "$out"
deaths=$(grep 'died:' run/runServer.out | grep -c 'Jungler')
ok "W the lumberjack never fell to its death" "$(( deaths == 0 ? 1 : 0 ))" "$(grep 'died:' run/runServer.out | grep 'Jungler' | sed 's/.*died: //' | tr '\n' ';')"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
