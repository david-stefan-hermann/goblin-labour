#!/usr/bin/env bash
# Probe: a lumberjack fells a single pillar of 14 logs from a scaffold column and sneaks back down (round 7: the jungle
# lumberjack fell to its death beside its column). Needs GOBLINLABOUR_TRACE=Probe in the environment of the server; the
# trace lines (every tick the goblin spends up, in the air or sneaking) go to run/trace-descend.txt, and every descent
# is summed up: where it started, how far the goblin got from the middle of its column, where it ended.
# Scene at x/z 1890..1910 on the flat world. Usage: GOBLINLABOUR_TRACE=Probe tools/dev-probe-descend.sh [seconds]
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
LIMIT="${1:-120}"

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

B="1895 -60 1900"; C="1895 -60 1902"
rcon "forceload add 1880 1880 1920 1920" "time set day" "weather clear" "setblock $B air" "setblock $C air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1880,y=-64,z=1880,dx=40,dy=60,dz=40]" "kill @e[type=item,x=1880,y=-64,z=1880,dx=40,dy=60,dz=40]" > /dev/null
rcon "fill 1885 -60 1885 1915 -20 1915 air" "fill 1885 -61 1885 1915 -61 1915 grass_block" "fill 1903 -60 1900 1903 -47 1900 jungle_log" > /dev/null
rcon "setblock $B goblinlabour:goblin_straw_bed[facing=east]" "setblock $C goblinlabour:goblin_chest[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $B Probe" "goblinlabour tool $B 0 minecraft:diamond_axe" "goblinlabour job $B chop 12" "goblinlabour replant $B false" > /dev/null

t0=$(date +%s)
while [ $(( $(date +%s) - t0 )) -lt "$LIMIT" ]; do
  sleep 5
  st=$(rcon "goblinlabour status $B" | grep -v '^>' | tail -1)
  printf "%3ds %s %s %s\n" $(( $(date +%s) - t0 )) "$(echo "$st" | grep -o 'pos=[-0-9, ]*' | tr -d ' ')" \
    "$(echo "$st" | grep -oE 'tree=(-|[^)]*[)])' | head -1 | tr -d ' ')" "$(echo "$st" | grep -o 'up=[a-z]*')"
done
echo "logs left: $(rcon "fill 1903 -60 1900 1903 -47 1900 air replace #minecraft:logs" | grep -v '^>')"
rcon "stop" | tail -1
wait
grep 'died:' run/runServer.out | sed 's/.*died: /died: /'
grep ' Trace ' run/runServer.out | sed 's/.* Trace //' > run/trace-descend.txt
echo "trace lines: $(wc -l < run/trace-descend.txt)"
awk '
  function num(key,   s) { if (!match($0, key "=[-0-9.]+,[-0-9.]+,[-0-9.]+")) return ""; s = substr($0, RSTART + length(key) + 1, RLENGTH - length(key) - 1); return s }
  {
    split(num("pos"), c, ","); sneak = ($0 ~ / sneak=true /)
    col = ""; if (match($0, /col=[-0-9]+, [-0-9]+, [-0-9]+/)) col = substr($0, RSTART + 4, RLENGTH - 4)
    if (sneak && !prev) { start = $1; sx = c[1]; sy = c[2]; sz = c[3]; maxoff = 0; mx = 0; mz = 0 }
    if (sneak && col != "") {
      split(col, k, ", "); ox = c[1] - (k[1] + 0.5); oz = c[3] - (k[3] + 0.5); off = sqrt(ox * ox + oz * oz)
      if (off > maxoff) { maxoff = off; mx = ox; mz = oz }
    }
    if (!sneak && prev) printf "descent ticks %s..%s from %.3f,%.2f,%.3f: max off the middle %.3f (%.3f,%.3f), ended at %s\n", start, last, sx, sy, sz, maxoff, mx, mz, lastpos
    prev = sneak; last = $1; lastpos = num("pos")
  }' run/trace-descend.txt
