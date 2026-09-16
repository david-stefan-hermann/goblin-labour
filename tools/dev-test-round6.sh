#!/usr/bin/env bash
# Feedback round 6 over RCON: drops land on the ground and the digger walks around picking them up (D), a block
# hidden behind an unbreakable pillar is only broken once the goblin can see it (V), a lumberjack replants its felled
# tree with a sapling it picked up from the ground, and one with replanting off does not (R).
# Scenes live at x/z 1000..1175 on the flat world. Prints PASS/FAIL and RESULT.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
check_not() { if [[ "$3" != *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: did not expect '$2' in: $3"; fail=$((fail+1)); fi; }
status() { rcon "goblinlabour status $1" | grep -v '^>' ; }
# answer of one command inside a batched rcon output
resp() { echo "$ALL" | grep -A1 -F "> $1" | tail -1; }
statusof() { echo "$ALL" | grep -A1 -F "> goblinlabour status $1" | tail -1; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

D="1010 -60 996"; V="1060 -60 994"; RA="1100 -60 1100"; RB="1150 -60 1100"
rcon "forceload add 995 990 1180 1130" "time set day" "weather clear" > /dev/null
rcon "setblock $D air" "setblock $V air" "setblock $RA air" "setblock $RB air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=995,y=-64,z=990,dx=185,dy=40,dz=140]" "kill @e[type=item,x=995,y=-64,z=990,dx=185,dy=40,dz=140]" > /dev/null
rcon "fill 1000 -60 990 1020 -50 1035 air" "fill 1000 -61 990 1020 -61 1035 grass_block" \
     "fill 1050 -60 990 1070 -50 1025 air" "fill 1050 -61 990 1070 -61 1025 grass_block" \
     "fill 1095 -60 1095 1125 -40 1125 air" "fill 1095 -61 1095 1125 -61 1125 grass_block" \
     "fill 1145 -60 1095 1175 -40 1125 air" "fill 1145 -61 1095 1175 -61 1125 grass_block" > /dev/null
sleep 2

# ---- D: 3x3 tunnel of 8 into a stone hill (bed 1010,-60,996, no chest: everything stays in the storage) ----
rcon "fill 1004 -60 1012 1016 -56 1030 stone" "setblock $D goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $D Pebble" "goblinlabour tool $D 0 minecraft:iron_pickaxe" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Pebble] 1010.5 -60 1009.5" > /dev/null
out=$(rcon "goblinlabour tunnel $D 1010 -60 1012 south 3 3 8"); check "D tunnel order" "Order for Pebble" "$out"

# ---- V: 3 wide, 2 high tunnel of 4 whose first slice has a bedrock pillar in the middle (bed 1060,-60,994) ----
# the middle blocks of the second slice (1060,-60/-59,1011) hide right behind the pillar
rcon "fill 1054 -60 1010 1066 -56 1020 stone" "fill 1060 -60 1010 1060 -59 1010 bedrock" "setblock $V goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $V Peeper" "goblinlabour tool $V 0 minecraft:iron_pickaxe" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Peeper] 1060.5 -60 1008.5" > /dev/null
out=$(rcon "goblinlabour tunnel $V 1060 -60 1010 south 3 2 4"); check "V tunnel order" "Order for Peeper" "$out"

# ---- R: two lumberjacks, one oak each; Sprout (replant on, no sapling, one lies on the ground), Stumpy (off, carries one) ----
rcon "place feature minecraft:oak 1112 -60 1112" "place feature minecraft:oak 1162 -60 1112" \
     "setblock $RA goblinlabour:goblin_straw_bed[facing=south]" "setblock $RB goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $RA Sprout" "goblinlabour spawn $RB Stumpy" "goblinlabour tool $RA 0 minecraft:iron_axe" "goblinlabour tool $RB 0 minecraft:iron_axe" \
     "goblinlabour tool $RB 9 minecraft:oak_sapling" > /dev/null
rcon "summon item 1115.5 -60 1108.5 {Item:{id:\"minecraft:oak_sapling\",count:1}}" > /dev/null
out=$(rcon "goblinlabour job $RA chop 16" "goblinlabour job $RB chop 16" "goblinlabour replant $RB false")
check "R replanting off for Stumpy" "Replant at 1150, -60, 1100: false" "$out"

# ---- watch all scenes ----
seen_items=0; positions=""; v_breaks=""; d_done=0; v_done=0; ra_done=0; rb_felled=0
end=$(( $(date +%s) + 360 ))
while [ "$(date +%s)" -lt "$end" ]; do
  Q=("goblinlabour status $D" "goblinlabour status $V" "goblinlabour status $RB"
     "execute if entity @e[type=item,x=1004,y=-61,z=1008,dx=13,dy=5,dz=24]"
     "execute if block 1009 -60 1019 air" "execute if block 1010 -59 1019 air" "execute if block 1011 -58 1019 air"
     "execute if block 1059 -60 1013 air" "execute if block 1060 -59 1013 air" "execute if block 1061 -60 1013 air"
     "execute if block 1060 -60 1011 air" "execute if block 1060 -59 1011 air"
     "execute if block 1112 -60 1112 #minecraft:saplings"
     "execute if block 1162 -60 1112 air" "execute if block 1162 -57 1112 air")
  ALL=$(rcon "${Q[@]}")
  if [ "$d_done" = 0 ]; then
    positions="$positions $(statusof "$D" | grep -o 'pos=[-0-9, ]*' | head -1 | tr -d ' ')"
    [[ "$(resp "execute if entity @e[type=item,x=1004,y=-61,z=1008,dx=13,dy=5,dz=24]")" == *"Test passed"* ]] && seen_items=$((seen_items+1))
    n=0; for b in "1009 -60 1019" "1010 -59 1019" "1011 -58 1019"; do [[ "$(resp "execute if block $b air")" == *"Test passed"* ]] && n=$((n+1)); done
    [ "$n" = 3 ] && d_done=1
  fi
  if [ "$v_done" = 0 ]; then
    v_breaks="$v_breaks $(statusof "$V" | grep -o 'breaks=[^ |]*' | head -1 | sed 's/breaks=//; s/;/ /g')"
    n=0; for b in "1059 -60 1013" "1060 -59 1013" "1061 -60 1013" "1060 -60 1011" "1060 -59 1011"; do [[ "$(resp "execute if block $b air")" == *"Test passed"* ]] && n=$((n+1)); done
    [ "$n" = 5 ] && v_done=1
  fi
  [[ "$(resp "execute if block 1112 -60 1112 #minecraft:saplings")" == *"Test passed"* ]] && ra_done=1
  if [[ "$(resp "execute if block 1162 -60 1112 air")" == *"Test passed"* && "$(resp "execute if block 1162 -57 1112 air")" == *"Test passed"* \
        && "$(statusof "$RB")" == *"tree=-"* ]]; then rb_felled=1; fi
  [ "$d_done" = 1 ] && [ "$v_done" = 1 ] && [ "$ra_done" = 1 ] && [ "$rb_felled" = 1 ] && break
  sleep 2
done
sleep 15 # last drops, Stumpy's chance to plant anyway

echo "  D: $(status "$D" | tail -1 | cut -c1-260)"
if [ "$d_done" = 1 ]; then echo "PASS D tunnel finished"; pass=$((pass+1)); else echo "FAIL D tunnel not finished"; fail=$((fail+1)); fi
if [ "$seen_items" -ge 1 ]; then echo "PASS D drops lay on the ground ($seen_items samples)"; pass=$((pass+1)); else echo "FAIL D never saw a drop on the ground"; fail=$((fail+1)); fi
distinct=$(echo "$positions" | tr ' ' '\n' | grep 'pos=' | sort -u | wc -l)
if [ "$distinct" -ge 5 ]; then echo "PASS D digger moved around ($distinct distinct spots)"; pass=$((pass+1)); else echo "FAIL D digger stood still: $distinct distinct spots"; fail=$((fail+1)); fi
out=$(status "$D"); check "D cobblestone picked up into the storage" "minecraft:cobblestone" "$out"
out=$(rcon "execute if entity @e[type=item,x=1004,y=-61,z=1008,dx=13,dy=5,dz=24]"); check_not "D no drops left lying in the tunnel" "Test passed" "$out"
[[ "$out" == *"Test passed"* ]] && echo "  D items left: $(rcon "execute as @e[type=item,x=1004,y=-61,z=1008,dx=13,dy=5,dz=24] run data get entity @s Pos" "execute as @e[type=item,x=1004,y=-61,z=1008,dx=13,dy=5,dz=24] run data get entity @s Item" | grep -v '^>' | tr '\n' ' ' | cut -c1-500)"

echo "  V: $(status "$V" | tail -1 | cut -c1-260)"
if [ "$v_done" = 1 ]; then echo "PASS V tunnel with pillar finished"; pass=$((pass+1)); else echo "FAIL V tunnel with pillar not finished"; fail=$((fail+1)); fi
hidden=$(echo "$v_breaks" | tr ' ' '\n' | grep -E '^1060,-(60|59),1011@' | sort -u)
echo "  V hidden blocks broken from: $(echo $hidden)"
through=$(echo "$hidden" | awk -F'@' '$2 != "" { split($2, f, ","); if (f[1] == 1060 && f[3] <= 1009) print }')
if [ -n "$hidden" ] && [ -z "$through" ]; then echo "PASS V hidden blocks only broken with a clear view"; pass=$((pass+1));
else echo "FAIL V hidden blocks: recorded='$(echo $hidden)' through the pillar='$(echo $through)'"; fail=$((fail+1)); fi

echo "  R: Sprout $(status "$RA" | tail -1 | sed 's/ | rest:.*//' | cut -c1-260)"
echo "  R: Stumpy $(status "$RB" | tail -1 | sed 's/ | rest:.*//' | cut -c1-260)"
if [ "$ra_done" = 1 ]; then echo "PASS R Sprout replanted its oak"; pass=$((pass+1)); else echo "FAIL R Sprout did not replant"; fail=$((fail+1)); fi
out=$(rcon "execute if entity @e[type=item,x=1113,y=-61,z=1106,dx=4,dy=3,dz=4,nbt={Item:{id:\"minecraft:oak_sapling\"}}]"); check_not "R Sprout picked the sapling up" "Test passed" "$out"
if [ "$rb_felled" = 1 ]; then echo "PASS R Stumpy felled its oak"; pass=$((pass+1)); else echo "FAIL R Stumpy did not fell its oak"; fail=$((fail+1)); fi
out=$(rcon "execute if block 1162 -60 1112 #minecraft:saplings"); check_not "R Stumpy did not replant (replanting off)" "Test passed" "$out"
out=$(status "$RB"); check "R Stumpy still carries its sapling" "oak_sapling" "$out"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
