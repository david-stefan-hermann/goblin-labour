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
rcon "forceload add -16 -16 16 16" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" "setblock 10 -60 10 air" "setblock 10 -60 10 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 2
rcon "goblinlabour spawn 10 -60 10 Testgob" | grep -v '^>'
for i in 1 2 3 4 5 6; do sleep 1; echo "before kill t=$i: $(rcon 'data get entity @e[type=goblinlabour:goblin,name=Testgob,limit=1] Pos' | tail -1)"; done
rcon "kill @e[type=goblinlabour:goblin,name=Testgob]" > /dev/null
for i in $(seq 1 14); do sleep 1; echo "after kill t=$i: $(rcon 'data get entity @e[type=goblinlabour:goblin,name=Testgob,limit=1] Pos' | tail -1) | $(rcon 'goblinlabour status 10 -60 10' | grep -o 'phase=.*' | cut -c1-60)"; done
rcon "stop" | tail -1
wait
