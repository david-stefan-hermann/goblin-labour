#!/usr/bin/env bash
# Goblin Ring over RCON, with a stand-in player (/goblinlabour ring ...): the crew of three mines the ores it can see
# in a stone wall (a buried one stays), picks up loose items into the ring (A2: even ones the player threw),
# stays on its leash of 8 blocks and out of the lane the player looks along (A), catches up when the player is
# suddenly far away (B), carries the ring's loot into a goblin chest (C), leaves on a second right-click and when
# its time is up (D), and digs a whole staff order off the leash before coming back (E); picked with the staff it
# stops and follows, and goes back to the order when let go (E); a vein is mined out completely, hidden blocks
# included, before any other ore (F); when the player digs natural blocks the crew helps around them, but never below
# the player's feet and never the block the player looks at (G); enchanted books in the ring's book slots put their
# enchantments on the crew's tools (H).
# Scenes live at x/z 1190..1270 on the flat world. Prints PASS/FAIL and RESULT.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
check_not() { if [[ "$3" != *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: did not expect '$2' in: $3"; fail=$((fail+1)); fi; }
ok() { if [ "$2" = 1 ]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1${3:+: $3}"; fail=$((fail+1)); fi; }
ring() { rcon "goblinlabour ring $1" | grep -v '^>' | tail -1; }
air() { [[ "$(rcon "execute if block $1 air")" == *"Test passed"* ]]; }
maxaway() { echo "$1" | grep -o 'away=[0-9.]*' | cut -d= -f2 | sort -g | tail -1; }
# "<overlapping samples> <samples>": a sample overlaps when two goblins share a block or stand closer than 0.6
overlaps() {
  echo "$1" | awk '
    function fl(v) { return (v < 0 && v != int(v)) ? int(v) - 1 : int(v) }
    /pos=/ {
      n = 0; line = $0
      while (match(line, /pos=[-0-9.]+,[-0-9.]+,[-0-9.]+/)) {
        split(substr(line, RSTART + 4, RLENGTH - 4), c, ",")
        n++; x[n] = c[1] + 0; y[n] = c[2] + 0; z[n] = c[3] + 0
        line = substr(line, RSTART + RLENGTH)
      }
      if (n < 2) next
      samples++; bad = 0
      for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++) {
        dx = x[i] - x[j]; dz = z[i] - z[j]; dy = y[i] - y[j]; if (dy < 0) dy = -dy
        if (fl(x[i]) == fl(x[j]) && fl(y[i]) == fl(y[j]) && fl(z[i]) == fl(z[j])) bad = 1
        if (dy < 1.0 && dx * dx + dz * dz < 0.36) bad = 1
      }
      over += bad
      if (bad) print $0 > "run/ring-overlaps.txt"
    }
    END { printf "%d %d\n", over, samples }'
}
goblins_in_area() { rcon "execute if entity @e[type=goblinlabour:goblin,x=1150,y=-64,z=1150,dx=160,dy=40,dz=160]" | grep -v '^>' | tail -1; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add 1150 1150 1300 1300" "time set day" "weather clear" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=1150,y=-64,z=1150,dx=160,dy=40,dz=160]" "kill @e[type=item,x=1150,y=-64,z=1150,dx=160,dy=40,dz=160]" > /dev/null
rcon "fill 1190 -60 1190 1209 -45 1270 air" "fill 1210 -60 1190 1229 -45 1270 air" "fill 1230 -60 1190 1249 -45 1270 air" "fill 1250 -60 1190 1270 -45 1270 air" "fill 1190 -61 1190 1270 -61 1270 grass_block" > /dev/null
sleep 1

# ---- A: a stone wall east of the player with ores on its face and one buried inside ----
rcon "fill 1214 -60 1196 1216 -56 1212 stone" \
     "setblock 1214 -59 1201 iron_ore" "setblock 1214 -58 1203 coal_ore" "setblock 1214 -60 1205 diamond_ore" "setblock 1214 -57 1207 copper_ore" \
     "setblock 1215 -58 1204 gold_ore" \
     "summon item 1206.5 -60 1208.5 {Item:{id:\"minecraft:emerald\",count:5}}" "summon item 1207.5 -60 1201.5 {Item:{id:\"minecraft:bone\",count:2}}" > /dev/null
# The stand-in player stands at 1209.5,-60,1204.5, 4.5 blocks from the wall (the work radius is 6), and looks north
# along it (yaw 180): looking at the wall from this close would put the whole wall in its look lane, where a crew
# does not work.
out=$(rcon "goblinlabour ring start 1209.5 -60 1204.5 180" | grep -v '^>'); check "A crew of three called" "crew of 3" "$out"
samples=""; ores_done=0
end=$(( $(date +%s) + 120 ))
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 2
  st=$(ring status); samples="$samples
$st"
  n=0; for b in "1214 -59 1201" "1214 -58 1203" "1214 -60 1205" "1214 -57 1207"; do air "$b" && n=$((n+1)); done
  [ "$n" = 4 ] && [[ "$st" == *emerald* ]] && [[ "$st" == *bone* ]] && { ores_done=1; break; }
done
sleep 10 # a few more samples of the idle crew
for _ in 1 2 3 4 5; do sleep 2; samples="$samples
$(ring status)"; done
st=$(ring status)
echo "  A: $(echo "$st" | cut -c1-600)"
echo "  A items left near the wall: $(rcon "execute as @e[type=item,x=1204,y=-61,z=1192,dx=16,dy=8,dz=24] run data get entity @s Pos" | grep -v '^>' | tr '\n' ' ' | cut -c1-400)"
ok "A the four visible ores were mined and the items picked up" "$ores_done"
out=$(rcon "execute if block 1215 -58 1204 gold_ore"); check "A the buried gold ore is still there" "Test passed" "$out"
for item in raw_iron coal diamond raw_copper emerald bone; do check "A ring holds $item" "minecraft:$item" "$st"; done
m=$(maxaway "$samples"); echo "  A max distance from the player: $m"
ok "A the crew stayed on its leash" "$(awk -v m="${m:-99}" 'BEGIN { print (m <= 8.5) ? 1 : 0 }')" "max away=$m"
total=$(echo "$samples" | grep -o 'way=[a-z]*' | wc -l); inway=$(echo "$samples" | grep -o 'way=true' | wc -l)
echo "  A goblin samples in the player's way: $inway of $total, by mode: $(echo "$samples" | tr '|' '\n' | grep 'way=true' | grep -o 'mode=[A-Z]*' | sort | uniq -c | tr '\n' ' ')"
echo "$samples" | tr '|' '\n' | grep 'way=true' | cut -c1-200 | head -8 | sed 's/^/    /'
ok "A the crew kept out of the player's way (at most 1 in 10 samples)" "$(( total > 0 && inway * 10 <= total ? 1 : 0 ))" "$inway of $total"
# the idle crew, 30 samples two seconds apart: no two goblins in one block or closer than 0.6
idle=""
for _ in $(seq 1 30); do sleep 2; idle="$idle
$(ring status)"; done
: > run/ring-overlaps.txt
read -r over total_o <<< "$(overlaps "$idle")"
echo "  A idle samples: $(echo "$idle" | grep -c 'pos=') lines, $total_o with two or more goblins, $over overlapping"
tr '|' '\n' < run/ring-overlaps.txt | grep 'pos=' | cut -c1-160 | sed 's/^/    /'
ok "A the idle crew goblins did not stand in one another (at most 1 in 20 samples)" "$(( total_o >= 20 && over * 20 <= total_o ? 1 : 0 ))" "$over of $total_o"
positions=$(echo "$samples" | grep -o 'pos=[-0-9]*\.[0-9],[-0-9.]*,[-0-9]*\.[0-9]' | sed 's/\.[0-9]//g' | sort -u | wc -l)
ok "A the crew moved about ($positions distinct spots)" "$(( positions >= 8 ? 1 : 0 ))"

# ---- A2: an item the player threw lands at its feet and is fetched anyway, after a moment's grace ----
out=$(ring "drop minecraft:gold_nugget"); check "A2 the tester threw a gold nugget" "threw" "$out"
picked=0
for _ in $(seq 1 15); do
  sleep 2
  [[ "$(ring status)" == *gold_nugget* ]] && { picked=1; break; }
done
ok "A2 the crew picked up what the player threw" "$picked" "ring: $(ring status | cut -c1-200)"

# ---- B: the player is suddenly 45 blocks away ----
rcon "goblinlabour ring move 1245.5 -60 1235.5 180" > /dev/null
caught=0
for _ in $(seq 1 10); do
  sleep 2
  st=$(ring status); m=$(maxaway "$st")
  if [ -n "$m" ] && awk -v m="$m" 'BEGIN { exit !(m <= 16.0) }'; then caught=1; break; fi
done
echo "  B: max away after the jump: $m"
ok "B the crew caught up within 20 s" "$caught"

# ---- C: loot in the ring and a goblin chest 6 blocks away ----
rcon "goblinlabour ring give minecraft:cobblestone 200" > /dev/null
# a fresh chest every run: setblock keeps an identical chest (and its contents) that is already there
rcon "setblock 1239 -60 1239 air" "setblock 1239 -60 1239 goblinlabour:goblin_chest[facing=north]" > /dev/null
emptied=0
for _ in $(seq 1 45); do
  sleep 2
  st=$(ring status)
  [[ "$st" == "ring: | tester="* ]] && { emptied=1; break; }
done
echo "  C: $(echo "$st" | cut -c1-400)"
ok "C the ring was emptied into the chest" "$emptied"
out=$(rcon "execute if data block 1239 -60 1239 Items[{id:\"minecraft:cobblestone\"}]"); check "C the chest holds the cobblestone" "Test passed" "$out"
out=$(rcon "execute if data block 1239 -60 1239 Items[{id:\"minecraft:raw_iron\"}]"); check "C the chest holds the raw iron" "Test passed" "$out"

# ---- D: a second right-click sends the crew home, the time running out too ----
out=$(ring toggle); check "D toggle sends the crew home" "crew of 0" "$out"
sleep 2
out=$(goblins_in_area); check_not "D no goblins left" "Test passed" "$out"
out=$(ring toggle); check "D toggle calls a new crew" "crew of 3" "$out"
rcon "goblinlabour ring expire 2" > /dev/null
sleep 5
out=$(ring status); check "D the crew left when its time was up" "crew=-" "$out"
out=$(goblins_in_area); check_not "D no goblins left after the time ran out" "Test passed" "$out"

# ---- E: the crew picked with the staff and sent into a 24 long tunnel; an order takes it off the leash ----
# The player stays at the tunnel mouth the whole time: the crew has to dig all 24 blocks (the far end is 25 blocks
# away, three times the leash) and come back to the player once the order is done.
rcon "fill 1194 -60 1245 1206 -57 1269 stone" > /dev/null
out=$(rcon "goblinlabour ring start 1200.5 -60 1242.5 0" | grep -v '^>'); check "E crew of three called" "crew of 3" "$out"
out=$(ring select); check "E all three picked with the staff" "Selected 3" "$out"
out=$(ring "tunnel 1200 -60 1245 south 3 2 24"); check "E tunnel order taken" "started" "$out"
rcon "goblinlabour ring expire 1200" > /dev/null # the five minute limit is tested in D; 24 blocks take longer
samples=""; done_e=0; paused=""
end=$(( $(date +%s) + 600 ))
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 2
  st=$(ring status); samples="$samples
$st"
  # once the first slices are dug: pick the crew with the staff, walk off, and let go again
  if [ -z "$paused" ] && air "1200 -60 1250" && air "1200 -59 1250"; then
    rcon "goblinlabour ring select" "goblinlabour ring move 1210.5 -60 1238.5 0" > /dev/null
    sleep 8
    ps=$(ring status); echo "  E picked: $(echo "$ps" | cut -c1-500)"
    near=$(maxaway "$ps"); orders=$(echo "$ps" | grep -o 'mode=ORDER' | wc -l)
    ok "E picked goblins followed the player (max away $near)" "$(awk -v m="${near:-99}" 'BEGIN { print (m < 4.0) ? 1 : 0 }')"
    ok "E picked goblins paused their order" "$(( orders == 0 ? 1 : 0 ))" "$orders in mode ORDER"
    dug_before=$(for zz in $(seq 1245 1268); do air "1200 -60 $zz" && echo x; done | wc -l)
    sleep 6
    dug_after=$(for zz in $(seq 1245 1268); do air "1200 -60 $zz" && echo x; done | wc -l)
    ok "E no digging while picked" "$(( dug_after == dug_before ? 1 : 0 ))" "$dug_before -> $dug_after blocks"
    rcon "goblinlabour ring deselect" "goblinlabour ring move 1200.5 -60 1242.5 0" > /dev/null
    paused=1
  fi
  if air "1200 -60 1268" && air "1200 -59 1268" && air "1199 -59 1268" && air "1201 -60 1268"; then done_e=1; break; fi
done
sleep 4
st=$(ring status)
echo "  E: $(echo "$st" | cut -c1-600)"
ok "E the tunnel was finished without the player following, after a pause" "$done_e"
m=$(maxaway "$samples"); echo "  E max distance from the player while digging: $m"
ok "E the order took the crew off the leash" "$(awk -v m="${m:-0}" 'BEGIN { print (m > 8.5) ? 1 : 0 }')" "max away=$m"
check_not "E the order is done" "order=MINE_AHEAD" "$(ring status)"
check "E the tunnel's cobblestone is in the ring" "minecraft:cobblestone" "$st"
# with the order done the leash is back on: the crew walks out of the tunnel to the player
sleep 20
back=""; for _ in 1 2 3 4 5; do sleep 2; back="$back
$(ring status)"; done
mb=$(maxaway "$back"); echo "  E max distance after the order: $mb"
ok "E the crew came back on the leash once the order was done" "$(awk -v m="${mb:-99}" 'BEGIN { print (m <= 8.5) ? 1 : 0 }')" "max away=$mb"

# ---- F: a vein of 18 iron ore, two thirds of it inside stone, and a lone coal ore nearby ----
# The crew has to take out the whole vein, hidden blocks included, before it touches the coal. The player stands at
# 1254.5,-60,1199.5 and looks north, away from both; the vein's open face is 5 blocks east. The coal (6 blocks off,
# in plain sight) is only placed once the crew has started on the vein: which ore it starts on is not the point.
rcon "fill 1260 -60 1197 1263 -57 1201 stone" "fill 1259 -60 1198 1261 -59 1200 iron_ore" "setblock 1251 -60 1204 air" > /dev/null
out=$(rcon "goblinlabour ring start 1254.5 -60 1199.5 180" | grep -v '^>'); check "F crew at the vein" "RingTester at 1254, -60, 1199" "$out"
VEIN=()
for x in 1259 1260 1261; do for y in -60 -59; do for z in 1198 1199 1200; do VEIN+=("execute if block $x $y $z iron_ore"); done; done; done
iron_left=18; coal_early=0; vein_seen=0; coal_placed=0
end=$(( $(date +%s) + 180 ))
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 2
  iron_left=$(rcon "${VEIN[@]}" | grep -c "Test passed")
  if [ "$vein_seen" = 0 ] && [[ "$(ring status)" =~ vein=([0-9]+) ]] && [ "${BASH_REMATCH[1]}" -ge 2 ]; then
    vein_seen=1
    rcon "setblock 1251 -60 1204 coal_ore" > /dev/null; coal_placed=1
    echo "  F vein started with $iron_left iron left, coal placed"
  fi
  if [ "$coal_placed" = 1 ] && [ "$iron_left" -gt 0 ] && [[ "$(rcon "execute if block 1251 -60 1204 coal_ore")" != *"Test passed"* ]]; then coal_early=1; fi
  [ "$iron_left" = 0 ] && break
done
echo "  F: iron left $iron_left, $(ring status | cut -c1-300)"
ok "F the crew followed the vein into the stone" "$vein_seen"
ok "F all 18 iron ores of the vein were mined" "$(( iron_left == 0 ? 1 : 0 ))" "$iron_left left"
ok "F the coal ore was left alone while the vein lasted" "$(( coal_early == 0 ? 1 : 0 ))"
coal_done=0
for _ in $(seq 1 15); do
  sleep 2
  [[ "$(rcon "execute if block 1251 -60 1204 coal_ore")" != *"Test passed"* ]] && { coal_done=1; break; }
done
ok "F the coal ore was mined once the vein was gone" "$coal_done"

# ---- G: the player digs into a stone wall and the crew helps ----
# The wall (2 thick, reaching two blocks into the ground) stands 2.5 blocks east of the player, who looks at it. The
# break event cannot be fired from a command, so "ring assist" stands in for the player breaking the block at eye
# height, every 10 seconds as if the player kept digging. The crew digs around it, but never below the player's feet.
rcon "fill 1231 -62 1211 1232 -56 1222 stone" > /dev/null
out=$(rcon "goblinlabour ring start 1228.5 -60 1216.5 -90" | grep -v '^>'); check "G crew at the wall" "RingTester at 1228, -60, 1216" "$out"
WALL=(); FLOOR=(); GROUND=()
for x in 1231 1232; do for y in -60 -59 -58 -57 -56; do for z in $(seq 1212 1220); do WALL+=("execute if block $x $y $z air"); done; done; done
for x in 1231 1232; do for z in $(seq 1211 1222); do FLOOR+=("execute if block $x -61 $z stone"); done; done
for x in 1227 1228 1229 1230; do for z in $(seq 1212 1221); do GROUND+=("execute if block $x -61 $z grass_block"); done; done
assisting=0
for round in 1 2 3 4 5 6; do
  out=$(ring "assist 1231 -59 1216"); [ "$round" = 1 ] && check "G assist started" "helps dig at 1231, -59, 1216" "$out"
  for _ in 1 2 3 4 5; do
    sleep 2
    [[ "$(ring status)" == *"mode=ASSIST"* ]] && assisting=1
  done
done
dug=$(rcon "${WALL[@]}" | grep -c "Test passed")
floor=$(rcon "${FLOOR[@]}" | grep -c "Test passed")
ground=$(rcon "${GROUND[@]}" | grep -c "Test passed")
echo "  G: $dug wall blocks dug, floor row $floor/24 stone, ground $ground/40 grass, $(ring status | cut -c1-250)"
ok "G a crew goblin went to help" "$assisting"
ok "G the crew dug at least 6 wall blocks around the player's spot" "$(( dug >= 6 ? 1 : 0 ))" "$dug dug"
ok "G nothing below the player's feet was dug (wall)" "$(( floor == 24 ? 1 : 0 ))" "$floor of 24 left"
ok "G nothing below the player's feet was dug (ground)" "$(( ground == 40 ? 1 : 0 ))" "$ground of 40 left"
out=$(rcon "execute if block 1231 -59 1216 stone"); check "G the block the player looks at was left to the player" "Test passed" "$out"
# with no more help asked for, the crew stops after 20 seconds
sleep 24
st=$(ring status)
check "G the help ended 20 s after the player's last block" "assist=-" "$st"
check_not "G no goblin still helping" "mode=ASSIST" "$st"

# ---- H: enchanted books in the ring's two book slots go onto the crew's tools ----
out=$(ring "book minecraft:fortune 3"); check "H a Fortune III book goes into the first book slot" "Ring book slot 0: minecraft:fortune 3" "$out"
out=$(ring "book minecraft:efficiency 5"); check "H an Efficiency V book goes into the second book slot" "Ring book slot 1: minecraft:efficiency 5" "$out"
out=$(rcon "goblinlabour ring book minecraft:silk_touch 1" | grep -v '^>'); check "H a third book does not fit" "Both book slots are full" "$out"
st=$(ring status); echo "  H: $(echo "$st" | grep -o 'pick=\[[^]]*\]' | tr '\n' ' ')"
ok "H all three crew pickaxes carry Fortune III" "$(( $(echo "$st" | grep -o 'fortune 3' | wc -l) == 3 ? 1 : 0 ))"
ok "H all three crew pickaxes carry Efficiency V" "$(( $(echo "$st" | grep -o 'efficiency 5' | wc -l) == 3 ? 1 : 0 ))"
# a new crew gets the books' enchantments too
rcon "goblinlabour ring toggle" > /dev/null; sleep 1
out=$(ring toggle); check "H a new crew is called" "crew of 3" "$out"
st=$(ring status)
ok "H the new crew's pickaxes carry both enchantments" "$(( $(echo "$st" | grep -o 'fortune 3' | wc -l) == 3 && $(echo "$st" | grep -o 'efficiency 5' | wc -l) == 3 ? 1 : 0 ))"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
