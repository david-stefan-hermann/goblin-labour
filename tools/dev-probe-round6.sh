#!/usr/bin/env bash
# Probe: phase 3's scene H (3x3 shaft dug up through a stone block, the goblin has to come down its scaffold after)
# and phase 2's second step (the top layer of a finished shaft refilled with stone, goblin at the bottom). Prints both
# goblins' runner state every 3 s.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
st() { rcon "goblinlabour status $1" | grep -v '^>' | tail -1 | sed 's/.*| health=[0-9.]* //; s/ | rest:.*//; s/ breaks=[^ |]*//'; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

H="210 -60 210"; P="20 -60 20"
rcon "forceload add -16 -16 260 260" "time set day" "weather clear" "setblock $H air" "setblock $P air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" > /dev/null
rcon "fill 205 -60 205 215 -45 225 air" "fill 208 -59 216 212 -50 220 stone" "setblock $H goblinlabour:goblin_straw_bed[facing=south]" \
     "fill 18 -63 25 22 -60 29 air" "fill 18 -63 25 22 -61 29 dirt" "fill 18 -61 25 22 -61 29 grass_block" \
     "setblock $P goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $H Climber" "goblinlabour tool $H 0 minecraft:iron_pickaxe" "goblinlabour spawn $P Digger" "goblinlabour tool $P 0 minecraft:iron_pickaxe" > /dev/null
rcon "goblinlabour dig $H up 210 -59 218 3 -50 false" "goblinlabour dig $P down 20 -60 27 3 -64 false" | grep -v '^>'
stone=0
for i in $(seq 1 70); do
  sleep 3
  echo "t=$((i*3)) H $(st "$H")"
  sp=$(st "$P"); echo "t=$((i*3)) P $sp"
  if [ "$stone" = 0 ] && [[ "$sp" == *"order=-"* || "$(rcon "goblinlabour status $P")" != *"order="* ]]; then
    if [[ "$(rcon "execute if block 20 -63 27 air")" == *"Test passed"* ]]; then
      rcon "fill 19 -61 26 21 -61 28 stone" "goblinlabour dig $P down 20 -60 27 3 -64 false" | grep -v '^>'
      stone=1; echo "---- P: top layer refilled with stone, new order"
    fi
  fi
done
rcon "stop" | tail -1
wait
