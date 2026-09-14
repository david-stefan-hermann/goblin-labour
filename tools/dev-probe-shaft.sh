#!/usr/bin/env bash
# Probe: round 4's scene S (a 3x3 shaft without stairs dug from a raised platform); prints the digger's exact
# position and runner state every 3 s.
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

S="345 -51 350"
rcon "forceload add 330 330 380 380" "time set day" "weather clear" "setblock $S air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=330,y=-64,z=330,dx=50,dy=40,dz=50]" "kill @e[type=item,x=330,y=-64,z=330,dx=50,dy=40,dz=50]" \
     "fill 336 -60 336 376 -45 364 air" "fill 336 -61 336 376 -61 364 grass_block" > /dev/null
rcon "fill 338 -60 340 372 -52 360 stone" "setblock $S goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $S Digger" "goblinlabour tool $S 0 minecraft:iron_pickaxe" > /dev/null
rcon "goblinlabour dig $S down 360 -52 350 3 -60 false" | grep -v '^>'
for i in $(seq 1 40); do
  sleep 3
  s=$(rcon "goblinlabour status $S" | grep -v '^>' | tail -1 | sed 's/.*health=[0-9.]* //; s/ | rest:[^|]*//; s/progress=[0-9.]* //; s/ | order=[^|]*//')
  echo "t=$((i*3)) $(pos Digger) $s"
done
rcon "stop" | tail -1
wait
