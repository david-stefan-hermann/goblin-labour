#!/usr/bin/env bash
# Probe: watch a goblin chop a 4-log column and report block states + inventory every 5 s.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
B="60 -60 60"
rcon "forceload add 40 40 80 80" "time set day" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" "setblock $B air" \
     "fill 60 -60 67 60 -57 67 oak_log" "setblock 60 -61 67 grass_block" "setblock $B goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $B Chopper" "goblinlabour tool $B 0 minecraft:stone_axe" "goblinlabour tool $B 9 minecraft:oak_sapling" "goblinlabour job $B chop 3 16" | grep -v '^>'
for i in $(seq 1 12); do
  sleep 5
  echo "--- t=$((i*5))s"
  for y in -60 -59 -58 -57; do rcon "execute if block 60 $y 67 oak_log" "execute if block 60 $y 67 oak_sapling" "execute if block 60 $y 67 air" | grep -v '^>' | tr '\n' ' '; echo " <- y=$y"; done
  rcon "goblinlabour status $B" | grep inventory
done
rcon "stop" | tail -1
wait
