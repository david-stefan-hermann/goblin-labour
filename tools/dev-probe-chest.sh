#!/usr/bin/env bash
# Probe: a ring crew carries 200 cobblestone into a goblin chest 7 blocks from the player. Prints every 2 s how many
# items are left in the ring and what each goblin does (mode, position, chest, stuck), and how long emptying took.
# Scene at x/z 1640..1720 on the flat world. Usage: tools/dev-probe-chest.sh [seconds] [teleport]
# With "teleport" the crew is called 45 blocks away first and the player is moved to the chest scene, as in
# dev-test-ring scene B -> C.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
ring() { rcon "goblinlabour ring $1" | grep -v '^>' | tail -1; }
LIMIT="${1:-150}"; MODE="${2:-}"

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add 1640 1640 1725 1725" "time set day" "weather clear" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1640,y=-64,z=1640,dx=85,dy=40,dz=85]" "kill @e[type=item,x=1640,y=-64,z=1640,dx=85,dy=40,dz=85]" > /dev/null
rcon "fill 1640 -60 1640 1666 -50 1720 air" "fill 1667 -60 1640 1693 -50 1720 air" "fill 1694 -60 1640 1720 -50 1720 air" "fill 1640 -61 1640 1720 -61 1720 grass_block" > /dev/null
sleep 1
# same geometry as dev-test-ring scene C: the chest 6 blocks west and 4 south of a player looking north
if [ "$MODE" = teleport ]; then
  out=$(rcon "goblinlabour ring start 1670.5 -60 1670.5 -90" | grep -v '^>'); echo "start far: $out"
  sleep 10
  rcon "goblinlabour ring move 1706.5 -60 1700.5 180" > /dev/null
  caught=""
  for i in $(seq 1 10); do
    sleep 2
    m=$(ring status | grep -o 'away=[0-9.]*' | cut -d= -f2 | sort -g | tail -1)
    if awk -v m="${m:-99}" 'BEGIN { exit !(m <= 8.5) }'; then caught=$((i * 2)); break; fi
  done
  echo "caught up after ${caught:-never} s"
else
  out=$(rcon "goblinlabour ring start 1706.5 -60 1700.5 180" | grep -v '^>'); echo "start: $out"
fi
rcon "goblinlabour ring give minecraft:cobblestone 200" > /dev/null
rcon "setblock 1700 -60 1704 goblinlabour:goblin_chest[facing=north]" > /dev/null
echo "shape at the chest: $(rcon "execute if block 1700 -60 1704 goblinlabour:goblin_chest" | grep -v '^>' | tail -1)"

t0=$(date +%s); emptied=""
while [ $(( $(date +%s) - t0 )) -lt "$LIMIT" ]; do
  sleep 2
  st=$(ring status)
  left=$(echo "$st" | sed 's/ | tester=.*//' | grep -o '[0-9]*x' | tr -d 'x' | awk '{s+=$1} END {print s+0}')
  goblins=$(echo "$st" | tr '|' '\n' | grep ' pos=' | sed 's/order=[^ ]* //; s/progress=[^ ]* //; s/dy=[^ ]* //' | awk '{$1=$1; print}' | cut -c1-110 | tr '\n' ';')
  printf "%3ds left=%3s %s\n" $(( $(date +%s) - t0 )) "$left" "$goblins"
  if [ "$left" = 0 ]; then emptied=$(( $(date +%s) - t0 )); break; fi
done
echo "chest now: $(rcon "data get block 1700 -60 1704 Items" | grep -v '^>' | tail -1 | grep -o 'count: [0-9]*' | awk '{s+=$2} END {print s+0}') items"
echo "RESULT emptied_after=${emptied:-never}"
rcon "stop" | tail -1
wait
