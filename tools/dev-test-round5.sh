#!/usr/bin/env bash
# Feedback round 5 over RCON: lumberjacks fell a forest of generated trees one tree each (branches and scaffold
# climbing included), a scaffold column collapses without its foot, a miner tunnels under a hill without walking
# across the top, collectors carry a backpack (18 storage slots) and unload into goblin chests.
# Scenes live at x/z 790..990 on the flat world. Prints PASS/FAIL and RESULT.
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
tree_of() { status "$1" | grep -o 'tree=[^ ]*' | head -1; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add 785 785 990 990" "time set day" "weather clear" > /dev/null
rcon "setblock 805 -60 805 air" "setblock 802 -60 815 air" "setblock 910 -50 902 air" "setblock 960 -60 960 air" "setblock 980 -60 960 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=785,y=-64,z=785,dx=205,dy=80,dz=205]" "kill @e[type=item,x=785,y=-64,z=785,dx=205,dy=80,dz=205]" > /dev/null
rcon "fill 778 -60 778 794 -36 849 air" "fill 795 -60 778 811 -36 849 air" "fill 812 -60 778 828 -36 849 air" "fill 829 -60 778 845 -36 849 air" \
     "fill 778 -61 778 855 -61 855 grass_block" "fill 846 -60 846 855 -50 855 air" \
     "fill 895 -61 895 920 -40 945 air" "fill 921 -61 895 945 -40 945 air" "fill 950 -60 950 990 -55 970 air" "fill 950 -61 950 990 -61 970 grass_block" > /dev/null
sleep 2

# ---- F: forest, two lumberjacks (beds 805,-60,805 and 802,-60,815) ----
FA="805 -60 805"; FB="802 -60 815"
rcon "place feature minecraft:oak 815 -60 815" "place feature minecraft:fancy_oak 823 -60 809" "place feature minecraft:birch 818 -60 824" \
     "place feature minecraft:spruce 827 -60 822" > /dev/null
rcon "setblock $FA goblinlabour:goblin_straw_bed[facing=south]" "setblock $FB goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $FA Axel" "goblinlabour spawn $FB Birk" "goblinlabour tool $FA 0 minecraft:iron_axe" "goblinlabour tool $FB 0 minecraft:iron_axe" \
     "goblinlabour tool $FA 9 minecraft:oak_sapling" > /dev/null
out=$(rcon "goblinlabour job $FA chop 24" "goblinlabour job $FB chop 24"); check "F set jobs" "CHOP" "$out"
# right from the start (a tree falls in about ten seconds): both goblins on a tree at the same time, never the same one
sleep 4
both=0; same=0
for _ in $(seq 1 10); do
  ta=$(tree_of "$FA"); tb=$(tree_of "$FB"); echo "  F trees: Axel $ta, Birk $tb"
  if [ "$ta" != "tree=-" ] && [ "$tb" != "tree=-" ]; then
    if [ "$ta" = "$tb" ]; then same=$((same+1)); else both=$((both+1)); fi
  fi
  sleep 2
done
if [ "$both" -ge 1 ] && [ "$same" = 0 ]; then echo "PASS F one tree per goblin ($both samples)"; pass=$((pass+1)); else echo "FAIL F one tree per goblin: different=$both same=$same"; fail=$((fail+1)); fi

# ---- K: scaffold column without its foot ----
rcon "fill 850 -60 850 850 -53 850 goblinlabour:goblin_scaffold[distance=0]" > /dev/null

# ---- T: tunnel under a hill (bed on top at 910,-50,902, ramp down at x 909..911 z 909..918, tunnel z 919..932) ----
T="910 -50 902"
rcon "fill 900 -61 900 930 -52 940 stone" "fill 900 -51 900 930 -51 940 grass_block" \
     "fill 909 -60 919 911 -58 922 andesite" "fill 909 -60 923 911 -58 926 granite" "fill 909 -60 927 911 -58 929 diorite" \
     "fill 909 -60 930 911 -58 932 tuff" "setblock 910 -59 921 coal_ore" "setblock 909 -58 925 iron_ore" \
     "setblock 911 -60 928 copper_ore" "setblock 910 -58 931 dirt" > /dev/null
RAMP=(); for i in $(seq 0 9); do RAMP+=("fill 909 $((-51 - i)) $((909 + i)) 911 -50 $((909 + i)) air"); done
rcon "${RAMP[@]}" > /dev/null
rcon "setblock 912 -50 902 goblinlabour:goblin_chest[facing=north]" "setblock $T goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $T Tunny" "goblinlabour tool $T 0 minecraft:iron_pickaxe" "goblinlabour tool $T 1 minecraft:iron_shovel" > /dev/null
rcon "tp @e[type=goblinlabour:goblin,name=Tunny] 910.5 -60 917.5" > /dev/null
out=$(rcon "goblinlabour tunnel $T 910 -60 919 south 3 3 14"); check "T tunnel order" "Order for Tunny" "$out"

# ---- C: collector with backpack (bed 960,-60,960, goblin chest 962,-60,960) and a lumberjack without (980,-60,960) ----
C="960 -60 960"; L="980 -60 960"
rcon "setblock 962 -60 960 goblinlabour:goblin_chest[facing=north]" "setblock $C goblinlabour:goblin_straw_bed[facing=south]" \
     "setblock $L goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $C Magpie" "goblinlabour spawn $L Lumber" > /dev/null
rcon "goblinlabour job $C collect 8" > /dev/null
sleep 1
rcon "goblinlabour fillstorage $C minecraft:cobblestone" "goblinlabour fillstorage $L minecraft:cobblestone" > /dev/null
out=$(status "$C"); check "C collector storage reaches slot 26" "26=64xminecraft:cobblestone" "$out"
out=$(status "$L"); check_not "L lumberjack storage stops at slot 17" "18=64xminecraft:cobblestone" "$out"

# ---- results ----
sleep 3
out=$(rcon "execute if block 850 -53 850 goblinlabour:goblin_scaffold"); check "K column stands" "Test passed" "$out"
rcon "setblock 850 -60 850 air" > /dev/null
sleep 3
out=$(rcon "execute if block 850 -53 850 goblinlabour:goblin_scaffold" "execute if block 850 -57 850 goblinlabour:goblin_scaffold")
check_not "K column fell without its foot" "Test passed" "$out"


out=$(rcon "data get block 962 -60 960 Items"); check "C collector unloaded all 18 stacks into the goblin chest" "Slot: 17b" "$out"

# tunnel and forest run side by side: sample the tunnel goblin, wait for both
on_top=0; tunnel_done=0; forest_idle=0; forest_done=0
end=$(( $(date +%s) + 420 ))
while [ "$(date +%s)" -lt "$end" ]; do
  if [ "$tunnel_done" = 0 ]; then
    s=$(rcon "execute if entity @e[type=goblinlabour:goblin,name=Tunny,x=900,y=-56,z=919,dx=31,dy=10,dz=22]")
    if [[ "$s" == *"Test passed"* ]]; then on_top=$((on_top+1)); echo "  T on top of the hill: $(status "$T" | tail -1 | cut -c1-200)"; fi
    s=$(rcon "execute if block 910 -59 932 air" "execute if block 909 -60 932 air" "execute if block 911 -58 932 air")
    [ "$(echo "$s" | grep -c 'Test passed')" = 3 ] && tunnel_done=1
  fi
  if [ "$forest_done" = 0 ]; then
    sa=$(status "$FA"); sb=$(status "$FB")
    if [[ "$sa" == *"target=- "* && "$sa" == *"tree=-"* && "$sa" == *"skipped=0 "* && "$sb" == *"target=- "* && "$sb" == *"tree=-"* && "$sb" == *"skipped=0 "* ]]; then
      forest_idle=$((forest_idle+1)); [ "$forest_idle" -ge 2 ] && forest_done=1
    else
      forest_idle=0
    fi
  fi
  [ "$tunnel_done" = 1 ] && [ "$forest_done" = 1 ] && break
  sleep 3
done
echo "  T: $(status "$T" | tail -1 | cut -c1-220)"
if [ "$tunnel_done" = 1 ]; then echo "PASS T tunnel finished"; pass=$((pass+1)); else echo "FAIL T tunnel not finished"; fail=$((fail+1)); fi
if [ "$on_top" = 0 ]; then echo "PASS T never walked across the hill above the tunnel"; pass=$((pass+1)); else echo "FAIL T was on top of the hill $on_top times"; fail=$((fail+1)); fi
# the last load goes to the chest after the tunnel is done
for _ in $(seq 1 20); do out=$(rcon "data get block 912 -50 902 Items"); [[ "$out" == *andesite* ]] && break; sleep 3; done
check "T unloaded into the goblin chest" "andesite" "$out"

echo "  F: Axel $(status "$FA" | tail -1 | sed 's/ | rest:.*//')"; echo "  F: Birk $(status "$FB" | tail -1 | sed 's/ | rest:.*//')"
# where are logs left? (around the trees, layer by layer)
for y in $(seq -60 -44); do
  CMDS=(); for x in $(seq 810 832); do for z in $(seq 800 830); do CMDS+=("execute if block $x $y $z #minecraft:logs"); done; done
  rcon "${CMDS[@]:0:350}" | grep -B1 "Test passed" | grep '^>' | sed 's/> execute if block/  F log left at/; s/ #minecraft:logs//'
  rcon "${CMDS[@]:350}" | grep -B1 "Test passed" | grep '^>' | sed 's/> execute if block/  F log left at/; s/ #minecraft:logs//'
done
out=$(status "$FA"; status "$FB")
check_not "F both lumberjacks back on the ground" "up=true" "$out"
out=$(rcon "fill 778 -60 778 794 -36 849 air replace #minecraft:logs" "fill 795 -60 778 811 -36 849 air replace #minecraft:logs" \
           "fill 812 -60 778 828 -36 849 air replace #minecraft:logs" "fill 829 -60 778 845 -36 849 air replace #minecraft:logs")
check_not "F every log of the forest felled" "Successfully" "$out"
check_not "F log count commands ran" "Too many blocks" "$out"

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
