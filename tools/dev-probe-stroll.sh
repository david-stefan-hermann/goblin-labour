#!/usr/bin/env bash
# Resting goblin on open grass: position and rest-goal state every second for 60 s.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
rcon "forceload add 430 430 452 452" "time set day" "setblock 440 -60 440 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=430,y=-64,z=430,dx=22,dy=20,dz=22]" "fill 430 -60 430 452 -50 452 air" "fill 430 -61 430 452 -61 452 grass_block" \
     "setblock 440 -60 440 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn 440 -60 440 Stroller" > /dev/null
for i in $(seq 1 60); do
  sleep 1
  p=$(rcon "data get entity @e[type=goblinlabour:goblin,name=Stroller,limit=1] Pos" | grep -o '\[.*\]')
  s=$(rcon "goblinlabour status 440 -60 440" | grep -o 'onGround=[a-z]*\|rest: .*' | tr '\n' ' ')
  echo "t=$i $p $s"
done
rcon "stop" | tail -1
wait
