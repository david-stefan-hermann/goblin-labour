#!/usr/bin/env bash
# Probe: phase3 scene B (a lumberjack fells an 8-high oak log column from its scaffold) several times in one server
# run. Prints the Chopper's phase, target, branch, skips and tree every 3 s and, per round, when the top log fell or
# that it was still standing when the goblin let the tree go (the phase3 B flake). Runs with GOBLINLABOUR_TRACE=Chopper,
# the tick trace lands in run/runServer.out.
# Scene at x/z 55..70 like phase3 B. Usage: tools/dev-probe-chop.sh [rounds] [seconds per round]
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
export GOBLINLABOUR_TRACE="${GOBLINLABOUR_TRACE:-Chopper}"
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
ROUNDS="${1:-5}"
LIMIT="${2:-360}"

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

B="60 -60 60"
rcon "forceload add 50 50 80 80" "time set day" "weather clear" > /dev/null
for round in $(seq 1 "$ROUNDS"); do
  rcon "setblock $B air" > /dev/null
  sleep 2
  rcon "kill @e[type=goblinlabour:goblin,x=50,y=-64,z=50,dx=30,dy=30,dz=30]" "kill @e[type=item,x=50,y=-64,z=50,dx=30,dy=30,dz=30]" \
       "fill 55 -61 55 70 -45 75 air" "fill 55 -63 55 70 -62 75 dirt" "fill 55 -61 55 70 -61 75 grass_block" > /dev/null
  rcon "fill 60 -60 67 60 -53 67 oak_log" "setblock $B goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $B Chopper" "goblinlabour tool $B 0 minecraft:stone_axe" "goblinlabour tool $B 9 minecraft:oak_sapling" \
       "goblinlabour job $B chop 16" > /dev/null
  echo "== round $round"
  t0=$(date +%s); top=""; verdict="timeout"
  while [ $(( $(date +%s) - t0 )) -lt "$LIMIT" ]; do
    sleep 3
    t=$(( $(date +%s) - t0 ))
    st=$(rcon "goblinlabour status $B" | grep -v '^>' | tail -1)
    topair=$(rcon "execute if block 60 -53 67 air" | grep -c "Test passed")
    sapling=$(rcon "execute if block 60 -60 67 oak_sapling" | grep -c "Test passed")
    printf "%3ds top=%s sapling=%s %s\n" "$t" "$([ "$topair" = 1 ] && echo gone || echo log)" "$sapling" \
      "$(echo "$st" | grep -oE 'pos=[-0-9, ]*|phase=[A-Z_]*|target=[^ ]*|progress=[0-9.]*|stuck=[^ ]*|up=[a-z]*|skipped=[0-9]*|retryIn=[-0-9]*|branch=[^ ]*|tree=[^ ]*' | tr '\n' ' ')"
    if [ "$topair" = 1 ] && [ -z "$top" ]; then top=$t; fi
    if [ -n "$top" ] && [[ "$st" == *"up=false"* ]] && [ "$sapling" = 1 ]; then verdict="felled, top log at ${top}s, done at ${t}s"; break; fi
    if [ "$topair" = 0 ] && [ "$sapling" = 1 ] && [[ "$st" == *"tree=-"* ]]; then verdict="FLAKE: tree let go with the top log standing at ${t}s"; break; fi
  done
  echo "== round $round: $verdict"
done
rcon "stop" | tail -1
wait
