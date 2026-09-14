#!/usr/bin/env bash
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
st() { rcon "goblinlabour status $1" | grep -v '^>' | sed 's/inventory:/ inv:/' | cut -c1-200; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
rcon "forceload add 40 40 270 270" "time set day" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" \
     "fill 55 -61 62 65 -45 72 air" "setblock 60 -60 60 air" "fill 60 -60 67 60 -53 67 oak_log" "setblock 60 -61 67 grass_block" "setblock 60 -60 60 goblinlabour:goblin_straw_bed[facing=south]" \
     "setblock 120 -60 120 air" "setblock 122 -60 120 air" "setblock 122 -60 120 copper_chest" "setblock 120 -60 120 goblinlabour:goblin_straw_bed[facing=south]" \
     "fill 205 -61 213 215 -45 223 air" "setblock 210 -60 210 air" "fill 208 -59 216 212 -50 220 stone" "setblock 210 -60 210 goblinlabour:goblin_straw_bed[facing=south]" \
     "fill 238 -61 244 252 -50 262 air" "setblock 240 -60 240 air" "fill 240 -60 246 250 -56 260 stone" "setblock 240 -60 240 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
PRE=(); for n in $(seq 0 26); do PRE+=("item replace block 122 -60 120 container.$n with minecraft:cobblestone 64"); done
rcon "${PRE[@]}" > /dev/null
sleep 1
rcon "goblinlabour spawn 60 -60 60 Chopper" "goblinlabour tool 60 -60 60 0 minecraft:stone_axe" "goblinlabour job 60 -60 60 chop 16" \
     "goblinlabour spawn 120 -60 120 Porter" "goblinlabour fillstorage 120 -60 120 minecraft:cobblestone" "goblinlabour job 120 -60 120 chop 8" \
     "goblinlabour spawn 210 -60 210 Climber" "goblinlabour tool 210 -60 210 0 minecraft:iron_pickaxe" "goblinlabour dig 210 -60 210 up 210 -59 218 3 -50 false" \
     "goblinlabour spawn 240 -60 240 Tunneler" "goblinlabour tool 240 -60 240 0 minecraft:iron_pickaxe" "goblinlabour tunnel 240 -60 240 245 -60 246 south 3 3 8" | grep -v '^>' | cut -c1-120
for i in $(seq 1 12); do
  sleep 15; echo "--- t=$((i*15))s"
  st "60 -60 60"; st "120 -60 120"; st "210 -60 210"; st "240 -60 240"
  rcon "execute if block 60 -53 67 oak_log" "execute if block 210 -50 218 stone" "execute if block 245 -59 253 stone" | grep -v '^>' | tr '\n' ' '; echo
done
grep -E '<(Porter|Chopper|Climber|Tunneler)>|Exception|Caused' run/runServer.out | grep -v Perf | head -20
rcon "stop" | tail -1
wait
