#!/usr/bin/env bash
# Probe: a real oak (6 logs, canopy of leaves) next to a bed; prints the goblin's status and the trunk column every
# 4 s to see the scaffold climb, the leaf handling and the cleanup.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
mapfile -t SETUP < <(grep -v '^\s*$' tools/scenes/tree-setup.txt)
rcon "${SETUP[@]}" > /dev/null
sleep 2
rcon "goblinlabour job 60 -60 60 chop 16" | grep -v '^>'
for i in $(seq 1 25); do
  sleep 4
  echo "--- t=$((i*4))s"
  rcon "goblinlabour status 60 -60 60" | grep -v '^>' | sed 's/inventory:/\n   inventory:/; s/ | order=.*//'
  col=""; for y in -60 -59 -58 -57 -56 -55 -54; do a=$(rcon "execute if block 60 $y 67 oak_log" | grep -c passed); col="$col $y:$([ "$a" = 1 ] && echo L || echo .)"; done
  echo "   trunk:$col"
done
echo "leftover scaffolds: $(rcon 'fill 55 -61 62 70 -45 75 air replace goblinlabour:goblin_scaffold' | tail -1)"
rcon "stop" | tail -1
wait
