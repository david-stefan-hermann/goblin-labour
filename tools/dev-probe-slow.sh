#!/usr/bin/env bash
# Tunnel + dig-up on natural ground (grass at -61) with debug status every 5 s.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
st() { rcon "goblinlabour status $1" | grep -v '^>' | sed 's/inventory:/inv:/; s/ | order=.*//' | cut -c1-230; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
rcon "forceload add 200 200 260 260" "time set day" "setblock 210 -60 210 air" "setblock 240 -60 240 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" "fill 205 -61 205 220 -45 225 air" "fill 235 -61 235 255 -45 265 air" \
     "fill 205 -63 205 220 -62 225 dirt" "fill 205 -61 205 220 -61 225 grass_block" "fill 235 -63 235 255 -62 265 dirt" "fill 235 -61 235 255 -61 265 grass_block" \
     "fill 208 -59 216 212 -50 220 stone" "setblock 210 -60 210 goblinlabour:goblin_straw_bed[facing=south]" \
     "fill 240 -60 246 250 -56 260 stone" "setblock 240 -60 240 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn 210 -60 210 Climber" "goblinlabour tool 210 -60 210 0 minecraft:iron_pickaxe" "goblinlabour dig 210 -60 210 up 210 -59 218 3 -50 false" \
     "goblinlabour spawn 240 -60 240 Tunneler" "goblinlabour tool 240 -60 240 0 minecraft:iron_pickaxe" "goblinlabour tunnel 240 -60 240 245 -60 246 south 3 3 8" | grep -v '^>' | cut -c1-80
for i in $(seq 1 18); do
  sleep 5; echo "--- t=$((i*5))s"
  st "210 -60 210"; st "240 -60 240"
done
grep -E '<(Climber|Tunneler)>' run/runServer.out | cut -c1-120
rcon "stop" | tail -1
wait
