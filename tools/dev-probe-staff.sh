#!/usr/bin/env bash
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
rcon "forceload add 170 170 250 250" "time set day" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" \
     "setblock 180 -60 180 air" "setblock 180 -60 180 goblinlabour:goblin_straw_bed[facing=south]" \
     "setblock 210 -60 210 air" "setblock 210 -60 210 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
echo "=== commands"
rcon "goblinlabour spawn 210 -60 210 Climber" "goblinlabour dig 210 -60 210 up 210 -59 218 3 -50 false" "goblinlabour tunnel 210 -60 210 215 -60 216 south 3 3 8" "goblinlabour job 210 -60 210 chop 16" "goblinlabour status 210 -60 210"
echo "=== death recovery"
rcon "goblinlabour spawn 180 -60 180 Phoenix" "goblinlabour fillstorage 180 -60 180 minecraft:coal" "kill @e[type=goblinlabour:goblin,name=Phoenix]"
sleep 2
rcon "execute if entity @e[type=item,distance=..8,x=180,y=-60,z=180]" "data get entity @e[type=item,limit=1,sort=nearest,x=180,y=-60,z=180] Item"
sleep 8
rcon "goblinlabour status 180 -60 180" "execute if entity @e[type=item,distance=..8,x=180,y=-60,z=180]"
sleep 10
rcon "goblinlabour status 180 -60 180"
grep -E '<Phoenix>|Exception|Caused' run/runServer.out | grep -v Perf | head
rcon "stop" | tail -1
wait
