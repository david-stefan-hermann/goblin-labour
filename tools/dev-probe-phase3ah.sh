#!/usr/bin/env bash
# Phase 3 scenes A (3x3 shaft with stairs) and H (dig up with scaffold): status every 3 s, then the blocks in both
# shafts. For debugging the stair and climbing behaviour.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
st() { rcon "goblinlabour status $1" | grep -v '^>' | sed 's/inventory:.*health=//; s/ | order=.*//' | cut -c1-260; }
block_at() { # prints the first matching block name for a position
  for b in air cobblestone cobblestone_stairs goblinlabour:goblin_scaffold dirt grass_block stone torch wall_torch bedrock; do
    if rcon "execute if block $1 $b" | grep -q "Test passed"; then echo "$b"; return; fi
  done
  echo "?"
}
( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
rcon "forceload add 0 0 220 220" "time set day" "setblock 30 -60 30 air" "setblock 210 -60 210 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=20,y=-64,z=20,dx=20,dy=20,dz=30]" "kill @e[type=goblinlabour:goblin,x=200,y=-64,z=200,dx=20,dy=20,dz=30]" \
     "fill 25 -63 25 40 -50 45 air" "fill 25 -63 25 40 -62 45 dirt" "fill 25 -61 25 40 -61 45 grass_block" \
     "fill 205 -61 205 220 -45 225 air" "fill 205 -63 205 220 -62 225 dirt" "fill 205 -61 205 220 -61 225 grass_block" > /dev/null
rcon "setblock 30 -60 30 goblinlabour:goblin_straw_bed[facing=south]" "fill 208 -59 216 212 -50 220 stone" "setblock 210 -60 210 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn 30 -60 30 Stairs" "goblinlabour tool 30 -60 30 0 minecraft:iron_shovel" "goblinlabour dig 30 -60 30 down 30 -60 37 3 -64 true" \
     "goblinlabour spawn 210 -60 210 Climber" "goblinlabour tool 210 -60 210 0 minecraft:iron_pickaxe" "goblinlabour dig 210 -60 210 up 210 -59 218 3 -50 false" > /dev/null
for i in $(seq 1 40); do
  sleep 3; echo "--- t=$((i*3))s"
  st "30 -60 30"; st "210 -60 210"
  rcon "data get entity @e[type=goblinlabour:goblin,name=Climber,limit=1] Motion" | grep -o '\[[-0-9.d, E]*\]' | tail -1
done
echo "=== A shaft x29..31 z36..38 y-60..-63"
for y in -60 -61 -62 -63; do for x in 29 30 31; do for z in 36 37 38; do echo "A $x $y $z $(block_at "$x $y $z")"; done; done; done
echo "=== H column x210 z217 y-60..-50"
for y in $(seq -60 -50); do echo "H 210 $y 217 $(block_at "210 $y 217")"; done
grep -E '<(Stairs|Climber)>' run/runServer.out | cut -c1-120
rcon "stop" | tail -1
wait
echo DONE=0
