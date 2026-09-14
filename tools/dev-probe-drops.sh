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
rcon "forceload add 170 170 200 200" "time set day" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" \
     "setblock 180 -60 180 air" "setblock 180 -60 180 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn 180 -60 180 Phoenix" "goblinlabour fillstorage 180 -60 180 minecraft:coal" "goblinlabour status 180 -60 180" "kill @e[type=goblinlabour:goblin,name=Phoenix]" "execute as @e[type=item] run data get entity @s Item.count"
sleep 1
echo "--- after 1s"; rcon "execute as @e[type=item] run data get entity @s Item.count"
sleep 3
echo "--- after 4s"; rcon "execute as @e[type=item] run data get entity @s Item.count"
echo "--- bed break test"; rcon "kill @e[type=item]" > /dev/null
sleep 6
rcon "goblinlabour status 180 -60 180" "goblinlabour fillstorage 180 -60 180 minecraft:coal" "setblock 180 -60 180 air destroy" "execute as @e[type=item] run data get entity @s Item.count"
grep -E 'Exception|Caused' run/runServer.out | grep -v Perf | head -5
rcon "stop" | tail -1
wait
