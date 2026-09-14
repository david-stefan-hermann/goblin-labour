#!/usr/bin/env bash
# Probe: one lumberjack and three generated birches (x/z 1000..1030); prints position, target, stand spot and
# climb column every 2 s, and the logs left at the end.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pos() { rcon "data get entity @e[type=goblinlabour:goblin,name=$1,limit=1] Pos" | grep -o '\[[-0-9.d, ]*\]' | tail -1 | tr -d 'd '; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

B="1002 -60 1002"
rcon "forceload add 995 995 1035 1035" "time set day" "weather clear" "setblock $B air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=995,y=-64,z=995,dx=40,dy=40,dz=40]" "kill @e[type=item,x=995,y=-64,z=995,dx=40,dy=40,dz=40]" \
     "fill 995 -60 995 1035 -40 1035 air" "fill 995 -61 995 1035 -61 1035 grass_block" > /dev/null
rcon "place feature minecraft:birch 1012 -60 1010" "place feature minecraft:birch 1018 -60 1016" "place feature minecraft:birch 1010 -60 1020" | grep -v '^>'
rcon "setblock $B goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $B Bircher" "goblinlabour tool $B 0 minecraft:iron_axe" "goblinlabour job $B chop 24" > /dev/null
for i in $(seq 1 75); do
  sleep 2
  s=$(rcon "goblinlabour status $B" | grep -v '^>' | tail -1 | sed 's/.*health=[0-9.]* //; s/ | rest:.*//; s/progress=[0-9.]* //')
  echo "t=$((i*2)) $(pos Bircher) $s"
done
for y in $(seq -60 -50); do
  CMDS=(); for x in $(seq 1006 1024); do for z in $(seq 1004 1026); do CMDS+=("execute if block $x $y $z #minecraft:logs"); done; done
  rcon "${CMDS[@]:0:220}" | grep -B1 "Test passed" | grep '^>' | sed 's/> execute if block/log left at/; s/ #minecraft:logs//'
  rcon "${CMDS[@]:220}" | grep -B1 "Test passed" | grep '^>' | sed 's/> execute if block/log left at/; s/ #minecraft:logs//'
done
rcon "stop" | tail -1
wait
