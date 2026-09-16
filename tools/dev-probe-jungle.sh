#!/usr/bin/env bash
# Probe: a mega jungle tree and a small jungle tree for one lumberjack with a diamond axe (round 7 point 17: branch
# logs were left standing). Prints the goblin's tree (logs left/total), skipped blocks, whether it is up on scaffold
# and where it stands every 5 s; at the end every log left in the area, by position, with the "columns" report: why
# the goblin could or could not put a scaffold column next to it. Jungle bushes stand on the ground around the tree,
# as in a real jungle.
# Scene at x/z 1755..1805 on the flat world. Usage: tools/dev-probe-jungle.sh [seconds] [replant true|false]
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
LIMIT="${1:-480}"; REPLANT="${2:-true}"

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

B="1766 -60 1780"; C="1768 -60 1780"
rcon "forceload add 1750 1750 1810 1810" "time set day" "weather clear" "setblock $B air" "setblock $C air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1750,y=-64,z=1750,dx=60,dy=60,dz=60]" "kill @e[type=item,x=1750,y=-64,z=1750,dx=60,dy=60,dz=60]" > /dev/null
# 51 x 30 x 51 in slabs below Minecraft's fill limit
for x in 1755 1772 1789; do rcon "fill $x -60 1755 $((x + 16)) -31 1805 air" > /dev/null; done
for x in 1755 1772 1789; do rcon "fill $x -30 1755 $((x + 16)) -1 1805 air" > /dev/null; done
rcon "fill 1755 -61 1755 1805 -61 1805 grass_block" > /dev/null
sleep 1
echo "mega: $(rcon "place feature minecraft:mega_jungle_tree 1780 -60 1780" | grep -v '^>' | tail -1)"
echo "small: $(rcon "place feature minecraft:jungle_tree 1782 -60 1796" | grep -v '^>' | tail -1)"
bushes=0
for spot in "1775 1778" "1776 1784" "1778 1774" "1783 1775" "1786 1779" "1786 1785" "1782 1787" "1777 1789" "1788 1782" "1774 1781"; do
  set -- $spot
  rcon "place feature minecraft:jungle_bush $1 -60 $2" | grep -q Placed && bushes=$((bushes + 1))
done
echo "bushes: $bushes"
rcon "setblock $B goblinlabour:goblin_straw_bed[facing=east]" "setblock $C goblinlabour:goblin_chest[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $B Jungler" "goblinlabour tool $B 0 minecraft:diamond_axe" "goblinlabour job $B chop 24" "goblinlabour replant $B $REPLANT" > /dev/null

t0=$(date +%s)
while [ $(( $(date +%s) - t0 )) -lt "$LIMIT" ]; do
  st=$(rcon "goblinlabour status $B" | grep -v '^>' | tail -1)
  left=$(echo "$st" | grep -oE 'tree=[^(]*[(][0-9]+' | grep -oE '[0-9]+$')
  # dense samples in the endgame of a tree (the high logs), sparse otherwise
  if [ -n "$left" ] && [ "$left" -le 30 ]; then sleep 2; else sleep 5; fi
  [ -n "$left" ] && [ "$left" -le 30 ] && echo "      $(echo "$st" | grep -oE 'onGround=[a-z]*|branch=[^ ]*|stand=[^ ]*|column=[^ ]*' | tr '
' ' ' | cut -c1-150)"
  printf "%3ds %s %s %s %s %s\n" $(( $(date +%s) - t0 )) "$(echo "$st" | grep -o 'pos=[-0-9, ]*' | tr -d ' ')" \
    "$(echo "$st" | grep -oE 'tree=(-|[^)]*[)])' | head -1 | tr -d ' ')" "$(echo "$st" | grep -o 'skipped=[0-9]*')" \
    "$(echo "$st" | grep -o 'up=[a-z]*')" "$(echo "$st" | grep -o 'target=[^ ]*')"
done
echo "--- logs left:"
: > run/probe-jungle-left.txt
for y in $(seq -60 -24); do
  Q=(); for x in $(seq 1770 1792); do for z in $(seq 1770 1802); do Q+=("execute if block $x $y $z #minecraft:logs"); done; done
  rcon "${Q[@]:0:380}" | grep -B1 "Test passed" | grep '^>' | sed 's/> execute if block/  log at/; s/ #minecraft:logs//' | tee -a run/probe-jungle-left.txt
  rcon "${Q[@]:380}" | grep -B1 "Test passed" | grep '^>' | sed 's/> execute if block/  log at/; s/ #minecraft:logs//' | tee -a run/probe-jungle-left.txt
done
# the columns report for the first few logs left
head -4 run/probe-jungle-left.txt | while read -r _ _ x y z; do
  echo "--- $(rcon "goblinlabour columns $B $x $y $z" | grep -v '^>')"
done
st=$(rcon "goblinlabour status $B" | grep -v '^>' | tail -1)
echo "final: $(echo "$st" | grep -o 'tree=[^ ]*\|skipped=[0-9]*\|stand=[^ ]*\|column=[^ ]*\|branch=[^ ]*' | tr '\n' ' ')"
rcon "stop" | tail -1
wait
