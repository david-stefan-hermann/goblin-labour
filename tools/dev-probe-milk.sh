#!/usr/bin/env bash
# Probe: one farmer with a bucket, a cow ten blocks away and a Milk Churn at home (farm suite scene B). Prints the
# farmer's phase, branch, target and position every 2 s and the churn's milk, to see where the time goes.
# Scene at x/z 2090..2125, 2130..2150 on the flat world. Usage: tools/dev-probe-milk.sh [seconds]
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
LIMIT="${1:-90}"

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

B="2100 -60 2140"; BC="2102 -60 2140"; BK="2098 -60 2140"
rcon "forceload add 2090 2130 2125 2150" "time set day" "setblock $B air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=2090,y=-64,z=2130,dx=35,dy=20,dz=20]" "kill @e[type=cow,x=2090,y=-64,z=2130,dx=35,dy=20,dz=20]" > /dev/null
rcon "fill 2090 -60 2130 2125 -50 2150 air" "fill 2090 -61 2130 2125 -61 2150 grass_block" > /dev/null
rcon "setblock $BC goblinlabour:milk_churn" "setblock $BK goblinlabour:goblin_chest[facing=east]" "setblock $B goblinlabour:goblin_straw_bed[facing=east]" \
     "summon cow 2110 -60 2140 {NoAI:1b}" > /dev/null
sleep 1
rcon "goblinlabour spawn $B Milker" "goblinlabour tool $B 0 minecraft:bucket" "goblinlabour job $B farm 12" > /dev/null
t0=$(date +%s)
while [ $(( $(date +%s) - t0 )) -lt "$LIMIT" ]; do
  sleep 2
  st=$(rcon "goblinlabour status $B" | grep -v '^>' | tail -1)
  printf "%3ds %s | %s\n" $(( $(date +%s) - t0 )) "$(echo "$st" | grep -oE 'pos=[-0-9, ]*|phase=[A-Z_]*|target=[^ ]*|branch=[^ ]*|retryIn=[-0-9]*|inventory:[^|]*' | tr '\n' ' ' | cut -c1-200)" \
    "$(rcon "goblinlabour churn $BC" | grep -o 'milk=[0-9]*')"
done
rcon "stop" | tail -1
wait
