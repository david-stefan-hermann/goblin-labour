#!/usr/bin/env bash
# Probe: four resting goblins in a 3x3 pit 5 deep without stairs (dev-test-round7 scene P). Prints every 3 s, per
# goblin, its position and the goals it runs (ClimbOutGoal with its stage WALK/WAIT/CLIMB/STEP_OUT), to see where
# the time goes while they climb out one after the other. Usage: tools/dev-probe-pit.sh [seconds]
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
LIMIT="${1:-330}"

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

P1="1455 -55 1470"; P2="1457 -55 1470"; P3="1459 -55 1470"; P4="1461 -55 1470"
rcon "forceload add 1445 1450 1515 1515" "time set day" "weather clear" \
     "setblock $P1 air" "setblock $P2 air" "setblock $P3 air" "setblock $P4 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1445,y=-64,z=1450,dx=70,dy=40,dz=65]" "kill @e[type=item,x=1445,y=-64,z=1450,dx=70,dy=40,dz=65]" \
     "fill 1445 -60 1450 1480 -50 1515 air" "fill 1481 -60 1450 1515 -50 1515 air" "fill 1445 -61 1450 1515 -61 1515 grass_block" > /dev/null
sleep 1
rcon "fill 1450 -60 1455 1485 -56 1485 stone" "fill 1471 -60 1469 1473 -56 1471 air" \
     "setblock $P1 goblinlabour:goblin_straw_bed[facing=north]" "setblock $P2 goblinlabour:goblin_straw_bed[facing=north]" \
     "setblock $P3 goblinlabour:goblin_straw_bed[facing=north]" "setblock $P4 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null
sleep 1
rcon "goblinlabour spawn $P1 Pit1" "goblinlabour spawn $P2 Pit2" "goblinlabour spawn $P3 Pit3" "goblinlabour spawn $P4 Pit4" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Pit1] 1471.5 -60 1469.5" "tp @e[type=goblinlabour:goblin,name=Pit2] 1473.5 -60 1469.5" \
     "tp @e[type=goblinlabour:goblin,name=Pit3] 1471.5 -60 1471.5" "tp @e[type=goblinlabour:goblin,name=Pit4] 1473.5 -60 1471.5" > /dev/null
rcon "goblinlabour job $P1 rest" "goblinlabour job $P2 rest" "goblinlabour job $P3 rest" "goblinlabour job $P4 rest" > /dev/null

t0=$(date +%s)
while [ $(( $(date +%s) - t0 )) -lt "$LIMIT" ]; do
  sleep 3
  line=$(printf "%3ds" $(( $(date +%s) - t0 )))
  home=0
  for b in "$P1" "$P2" "$P3" "$P4"; do
    st=$(rcon "goblinlabour status $b" | grep -v '^>' | tail -1)
    pos=$(echo "$st" | grep -o 'pos=[-0-9]*, [-0-9]*, [-0-9]*' | sed 's/pos=//; s/, /,/g')
    goal=$(echo "$st" | grep -o 'goals:.*' | sed 's/goals://; s/RestGoal/Rest/; s/ClimbOutGoal/Climb/; s/^ //')
    y=$(echo "$pos" | cut -d, -f2); x=$(echo "$pos" | cut -d, -f1)
    [ "${y:--99}" -ge -55 ] && [ "${x:-9999}" -le 1468 ] && home=$((home + 1))
    line="$line | $pos ${goal:--}"
  done
  echo "$line"
  [ "$home" = 4 ] && { echo "RESULT all home after $(( $(date +%s) - t0 )) s"; break; }
done
rcon "stop" | tail -1
wait
