#!/usr/bin/env bash
# The straw bed, a block and a half long, over RCON (x/z 2720..2760 on the flat world):
#   A  placing it like a player: the foot end needs a free block behind (a stone there stops it, grass is cleared,
#      a block at the head end does not matter)
#   C  the foot end half block has collision: an armour stand dropped onto it stops on the straw
#   G  a working goblin put onto the foot end steps off it
#   O  nothing goes into the half block a foot end reaches into: not another bed, not a second foot end, not a stone
#   K  bunk beds: a bed placed onto a bed turns like it, the lower one gets tall foot posts (bunk=true) and loses
#      them when the upper one goes; stacks set by command too; both goblins get into their beds
# Usage: bash tools/dev-test-bed.sh [scene ...]   (no scene: all). Prints PASS/FAIL and RESULT.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
if [ "${OS:-}" = Windows_NT ]; then export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"; fi
if [ "${OS:-}" = Windows_NT ]; then export TEMP='C:\jtmp' TMP='C:\jtmp'; fi
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
pass=0; fail=0
check() { if [[ "$3" == *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: expected '$2' in: $3"; fail=$((fail+1)); fi; }
check_not() { if [[ "$3" != *"$2"* ]]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1: did not expect '$2' in: $3"; fail=$((fail+1)); fi; }
ok() { if [ "$2" = 1 ]; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1${3:+: $3}"; fail=$((fail+1)); fi; }
wait_out() { # wait_out <seconds> <substring> <command...>: runs the command every 2 s until its output has the substring
  local end=$(( $(date +%s) + $1 )) want="$2"; shift 2
  while [ "$(date +%s)" -lt "$end" ]; do
    local s; s=$(rcon "$@" | grep -v '^>')
    if [[ "$s" == *"$want"* ]]; then echo "$s"; return 0; fi
    sleep 2
  done
  rcon "$@" | grep -v '^>'
}
wanted() { [ ${#SCENES[@]} -eq 0 ] && return 0; for s in "${SCENES[@]}"; do [ "$s" = "$1" ] && return 0; done; return 1; }
SCENES=("$@")

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if ! grep -q 'RCON running' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi
rcon "time set day" "weather clear" > /dev/null

rcon "forceload add 2720 2720 2760 2760" > /dev/null
rcon "kill @e[type=goblinlabour:goblin,x=2720,y=-64,z=2720,dx=40,dy=20,dz=40]" "kill @e[type=armor_stand,x=2720,y=-64,z=2720,dx=40,dy=20,dz=40]" > /dev/null
for x in 2725 2729 2733 2737 2741; do rcon "setblock $x -60 2725 air" > /dev/null; done
for z in 2735 2736 2737; do rcon "setblock 2725 -60 $z air" > /dev/null; done
rcon "fill 2720 -60 2720 2760 -50 2760 air" "fill 2720 -61 2720 2760 -61 2760 grass_block" > /dev/null
rcon "kill @e[type=item,x=2720,y=-64,z=2720,dx=40,dy=20,dz=40]" > /dev/null

# ---- A: placing ----
if wanted A; then
  # looking south (yaw 0): the pillow ends up south, the foot end reaches north into 2725 -60 2724
  out=$(rcon "goblinlabour place 2725 -60 2725 goblinlabour:goblin_straw_bed 0" | grep -v '^>'); check "A placed looking south" "Placed" "$out"
  check "A it faces south (pillow away from the player)" "facing=south" "$out"
  # looking north (yaw 180) with stone where the foot end goes (south)
  rcon "setblock 2729 -60 2726 stone" > /dev/null
  out=$(rcon "goblinlabour place 2729 -60 2725 goblinlabour:goblin_straw_bed 180" | grep -v '^>'); check "A a stone behind stops it" "Not placed" "$out"
  out=$(rcon "execute if block 2729 -60 2725 air"); check "A nothing was placed there" "Test passed" "$out"
  # grass where the foot end goes: placed, and the grass is gone
  rcon "setblock 2733 -60 2726 short_grass" > /dev/null
  out=$(rcon "goblinlabour place 2733 -60 2725 goblinlabour:goblin_straw_bed 180" | grep -v '^>'); check "A grass behind does not stop it" "Placed" "$out"
  out=$(rcon "execute if block 2733 -60 2726 air"); check "A the grass under the foot end is cleared" "Test passed" "$out"
  # looking west (yaw 90): the foot end reaches east; a stone at the head end (west) is no obstacle
  rcon "setblock 2736 -60 2725 stone" > /dev/null
  out=$(rcon "goblinlabour place 2737 -60 2725 goblinlabour:goblin_straw_bed 90" | grep -v '^>'); check "A a block at the head end does not stop it" "Placed" "$out"
  check "A it faces west" "facing=west" "$out"
fi

# ---- C: collision of the foot end ----
if wanted C; then
  rcon "setblock 2741 -60 2725 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null
  # foot end of a bed facing north: 2741 -60 2726; next to it (2742) is plain grass
  rcon "summon armor_stand 2741.5 -58 2726.5 {Tags:[\"bedfoot\"]}" "summon armor_stand 2742.5 -58 2726.5 {Tags:[\"bedgrass\"]}" > /dev/null
  sleep 3
  on=$(rcon "data get entity @e[type=armor_stand,tag=bedfoot,limit=1] Pos[1]" | grep -o '\-[0-9.]*d' | head -1)
  off=$(rcon "data get entity @e[type=armor_stand,tag=bedgrass,limit=1] Pos[1]" | grep -o '\-[0-9.]*d' | head -1)
  echo "  C: on the foot end y=$on, on the grass y=$off"
  check "C an armour stand stops on the foot end's straw (y -59.4375)" "-59.4375" "$on"
  check "C one on the grass next to it stands on the ground" "-60.0" "$off"
  rcon "kill @e[type=armor_stand,x=2720,y=-64,z=2720,dx=40,dy=20,dz=40]" > /dev/null
fi

# ---- G: a goblin on the foot end ----
if wanted G; then
  rcon "setblock 2750 -60 2740 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null
  sleep 1
  rcon "goblinlabour spawn 2750 -60 2740 Footy" "goblinlabour job 2750 -60 2740 chop 16" > /dev/null
  rcon "tp @e[type=goblinlabour:goblin,name=Footy] 2750.5 -59.4 2741.5" > /dev/null
  sleep 12
  rcon "data get entity @e[type=goblinlabour:goblin,name=Footy,limit=1] Pos" | grep -v '^>' | sed 's/^/  G: /'
  # on the bed or its foot end a goblin stands 9 px up (y -59.4375), on the grass at y -60
  y=$(rcon "data get entity @e[type=goblinlabour:goblin,name=Footy,limit=1] Pos[1]" | grep -o '\-[0-9.]*d' | head -1)
  check "G a working goblin put onto the foot end steps down onto the ground (y=$y)" "-60.0" "$y"
  out=$(rcon "execute positioned 2750.5 -60 2740.5 if entity @e[type=goblinlabour:goblin,name=Footy,distance=..0.9]")
  check "G and not onto the bed itself" "Test failed" "$out"
fi

# ---- O: no overlapping ----
if wanted O; then
  # a bed facing north: its foot end reaches into 2725 -60 2736
  rcon "setblock 2725 -60 2735 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null
  out=$(rcon "goblinlabour place 2725 -60 2736 goblinlabour:goblin_straw_bed 180" | grep -v '^>'); check "O no bed in another bed's foot end" "Not placed" "$out"
  out=$(rcon "goblinlabour place 2725 -60 2737 goblinlabour:goblin_straw_bed 0" | grep -v '^>'); check "O no second foot end in the same block" "Not placed" "$out"
  out=$(rcon "goblinlabour place 2725 -60 2736 minecraft:stone 0" | grep -v '^>'); check "O no stone in a foot end" "Not placed" "$out"
  out=$(rcon "execute if block 2725 -60 2736 air"); check "O the foot end stays empty" "Test passed" "$out"
  out=$(rcon "goblinlabour place 2727 -60 2736 minecraft:stone 0" | grep -v '^>'); check "O a stone next to it is fine" "Placed" "$out"
  out=$(rcon "goblinlabour place 2725 -60 2737 goblinlabour:goblin_straw_bed 180" | grep -v '^>'); check "O a bed right behind the foot end, pointing away, is fine" "Placed" "$out"
fi

# ---- K: bunk beds ----
if wanted K; then
  KA="2745 -60 2750"; KU="2745 -59 2750"
  out=$(rcon "goblinlabour place $KA goblinlabour:goblin_straw_bed 0" | grep -v '^>'); check "K the lower bed is placed facing south" "facing=south" "$out"
  # looking west onto it: the upper bed still faces south, like the lower one
  out=$(rcon "goblinlabour place $KU goblinlabour:goblin_straw_bed 90" | grep -v '^>'); check "K a bed placed onto it turns like the lower one" "facing=south" "$out"
  check "K the upper bed knows it stands on a bed" "upper=true" "$out"
  out=$(rcon "execute if block $KA goblinlabour:goblin_straw_bed[bunk=true,upper=false]"); check "K the lower bed becomes a bunk (tall foot posts)" "Test passed" "$out"
  rcon "setblock $KU air" > /dev/null
  out=$(rcon "execute if block $KA goblinlabour:goblin_straw_bed[bunk=false]"); check "K without the upper bed it is a plain bed again" "Test passed" "$out"
  # set by command: a pair facing the same way is a bunk, a pair turned differently is not
  rcon "setblock 2755 -60 2750 goblinlabour:goblin_straw_bed[facing=north]" "setblock 2755 -59 2750 goblinlabour:goblin_straw_bed[facing=north]" \
       "setblock 2759 -60 2750 goblinlabour:goblin_straw_bed[facing=north]" "setblock 2759 -59 2750 goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
  out=$(rcon "execute if block 2755 -60 2750 goblinlabour:goblin_straw_bed[bunk=true]" "execute if block 2755 -59 2750 goblinlabour:goblin_straw_bed[upper=true,bunk=false]" | grep -c "Test passed")
  ok "K beds stacked by command make a bunk too ($out of 2)" "$(( out == 2 ? 1 : 0 ))"
  out=$(rcon "execute if block 2759 -60 2750 goblinlabour:goblin_straw_bed[bunk=false]" "execute if block 2759 -59 2750 goblinlabour:goblin_straw_bed[upper=false]" | grep -c "Test passed")
  ok "K beds turned differently do not ($out of 2)" "$(( out == 2 ? 1 : 0 ))"
  # a goblin in each bed of a bunk: the lower one lies at y -59.4375, the upper one climbs in from the ground (-58.4375)
  rcon "goblinlabour place $KU goblinlabour:goblin_straw_bed 0" > /dev/null
  rcon "goblinlabour spawn $KA Lowbunk" "goblinlabour spawn $KU Highbunk" "goblinlabour job $KA rest" "goblinlabour job $KU rest" > /dev/null
  low=0; high=0
  end=$(( $(date +%s) + 150 ))
  while [ "$(date +%s)" -lt "$end" ] && { [ $low = 0 ] || [ $high = 0 ]; }; do
    sleep 2
    [[ "$(rcon "execute positioned 2745.5 -59.4375 2750.5 if entity @e[type=goblinlabour:goblin,name=Lowbunk,distance=..0.1]")" == *"Test passed"* ]] && low=1
    [[ "$(rcon "execute positioned 2745.5 -58.4375 2750.5 if entity @e[type=goblinlabour:goblin,name=Highbunk,distance=..0.1]")" == *"Test passed"* ]] && high=1
  done
  rcon "goblinlabour status $KU" | grep -v '^>' | sed 's/^/  K upper: /' | cut -c1-240
  ok "K the goblin of the lower bed lies down in it" "$low"
  ok "K the goblin of the upper bed climbs into it" "$high"
fi

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
