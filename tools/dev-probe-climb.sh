#!/usr/bin/env bash
# Probe: dig-up shaft under a stone ceiling (phase-3 scenario H); prints the goblin's position, motion and the
# blocks of its column every 3 s to see how the scaffold climb behaves.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
H="210 -60 210"
rcon "forceload add 200 200 230 230" "time set day" "setblock $H air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" "fill 205 -61 213 215 -45 223 air" "fill 205 -61 213 215 -61 223 grass_block" \
     "fill 208 -59 216 212 -50 220 stone" "setblock $H goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $H Climber" "goblinlabour tool $H 0 minecraft:iron_pickaxe" > /dev/null
rcon "goblinlabour dig $H up 210 -59 218 3 -50 false" | grep -v '^>'
for i in $(seq 1 30); do
  sleep 3
  echo "--- t=$((i*3))s"
  rcon "data get entity @e[type=goblinlabour:goblin,limit=1] Pos" "data get entity @e[type=goblinlabour:goblin,limit=1] Motion" "data get entity @e[type=goblinlabour:goblin,limit=1] OnGround" | grep -v '^>' | sed 's/Climber has the following entity data: //' | tr '\n' ' '; echo
  rcon "goblinlabour status $H" | grep -v '^>' | sed 's/.*onGround/onGround/'
  P=$(rcon "data get entity @e[type=goblinlabour:goblin,limit=1] Pos" | grep -o '\[.*\]' | tr -d '[]d' | tr ',' ' ')
  set -- $P; X=${1%.*}; Z=${3%.*}
  col=""; for y in -60 -59 -58 -57 -56 -55; do b=$(rcon "execute if block $X $y $Z goblinlabour:goblin_scaffold" "execute if block $X $y $Z air" "execute if block $X $y $Z stone" | grep -c "Test passed"); s=$(rcon "execute if block $X $y $Z goblinlabour:goblin_scaffold" | grep -c passed); a=$(rcon "execute if block $X $y $Z air" | grep -c passed); col="$col y$y:$([ "$s" = 1 ] && echo S || ([ "$a" = 1 ] && echo . || echo '#'))"; done
  echo "  column x=$X z=$Z:$col"
done
rcon "stop" | tail -1
wait
