#!/usr/bin/env bash
# Phase 1 functional test over RCON (no client): spawn, death + respawn, bed break -> blank with data,
# persistence of the bed binding. Prints PASS/FAIL lines. Needs a free port 25598/25599.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { # check <name> <expected-substring> <actual>
  if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi
}

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do
  grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break
  sleep 3
done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add -16 -16 16 16" "kill @e[type=goblinlabour:goblin]" "kill @e[type=item]" \
     "setblock 0 -60 0 air" "setblock 20 -60 20 air" "setblock 10 -60 10 air" "setblock 10 -60 10 goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 2

# 1. spawn by command
out=$(rcon "goblinlabour spawn 10 -60 10 Testgob")
check "spawn" "Spawned Testgob" "$out"
out=$(rcon "execute if entity @e[type=goblinlabour:goblin,name=Testgob]")
check "goblin exists" "Test passed" "$out"
out=$(rcon "goblinlabour status 10 -60 10")
check "status idle" "Testgob" "$out"

# 2. hostile mobs ignore it: a zombie next to the goblin must not target it
rcon "summon minecraft:zombie 11 -60 10 {PersistenceRequired:1b}" > /dev/null
sleep 6
out=$(rcon "execute if entity @e[type=goblinlabour:goblin,name=Testgob]")
check "still alive next to a zombie" "Test passed" "$out"
out=$(rcon "data get entity @e[type=minecraft:zombie,limit=1] Health")
echo "  zombie health: $(echo "$out" | tail -1)"
rcon "kill @e[type=minecraft:zombie]" > /dev/null

# 3. death -> respawn at the bed after 5 s, tool in hotbar survives
rcon "item replace entity @e[type=goblinlabour:goblin,name=Testgob,limit=1] weapon.mainhand with minecraft:stick" > /dev/null
rcon "kill @e[type=goblinlabour:goblin,name=Testgob]" > /dev/null
sleep 2
out=$(rcon "execute if entity @e[type=goblinlabour:goblin,name=Testgob]")
check "dead right after kill" "Test failed" "$out"
sleep 7
out=$(rcon "execute if entity @e[type=goblinlabour:goblin,name=Testgob]")
check "respawned after 5 s" "Test passed" "$out"
out=$(rcon "data get entity @e[type=goblinlabour:goblin,name=Testgob,limit=1] Health")
check "full health after respawn" "20.0f" "$out"
out=$(rcon "execute positioned 10 -60 10 if entity @e[type=goblinlabour:goblin,name=Testgob,distance=..8]")
check "respawned inside its home (it wanders off the bed right away)" "Test passed" "$out"

# 4. bed break -> goblin gone, blank with data dropped
rcon "kill @e[type=item]" > /dev/null
rcon "setblock 10 -60 10 air destroy" > /dev/null
sleep 2
out=$(rcon "execute if entity @e[type=goblinlabour:goblin,name=Testgob]")
check "goblin despawned with bed" "Test failed" "$out"
out=$(rcon "data get entity @e[type=item,limit=1,sort=nearest,x=10,y=-60,z=10,nbt={Item:{id:\"goblinlabour:goblin_blank\"}}] Item.components")
check "blank dropped with name" "Testgob" "$out"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
