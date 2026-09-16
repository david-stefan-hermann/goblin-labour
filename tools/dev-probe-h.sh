#!/usr/bin/env bash
# Probe: phase 3's scene H (3x3 shaft dug up through a stone block). Prints the goblin's position, runner state,
# running goals and rest state every 2 s, through the end of the job and a minute of resting after it.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

H="210 -60 210"
rcon "forceload add 190 190 240 240" "time set day" "weather clear" "setblock $H air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=190,y=-64,z=190,dx=50,dy=40,dz=50]" "kill @e[type=item,x=190,y=-64,z=190,dx=50,dy=40,dz=50]" > /dev/null
rcon "fill 205 -60 205 215 -45 225 air" "fill 208 -59 216 212 -50 220 stone" "setblock $H goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $H Climber" "goblinlabour tool $H 0 minecraft:iron_pickaxe" > /dev/null
rcon "goblinlabour dig $H up 210 -59 218 3 -50 false" | grep -v '^>'
rest=0
for i in $(seq 1 240); do
  sleep 2
  line=$(rcon "goblinlabour status $H" | grep -v '^>' | tail -1 | sed 's/^inventory:[^|]*| health=[0-9.]* //; s/ tree=- stumps=0//; s/ breaks=[^ |]*//')
  echo "t=$((i*2)) $line" | cut -c1-420
  if [[ "$line" == *"Rest"* || "$line" == *"RestGoal"* ]]; then rest=$((rest+1)); fi
  [ "$rest" -ge 40 ] && break
done
rcon "stop" | tail -1
wait
