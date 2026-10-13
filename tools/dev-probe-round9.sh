#!/usr/bin/env bash
# Probe for the round 9 feedback (x/z 3000..3120): measures, changes nothing.
#   T1  1x1 tunnel one block above the floor: does the goblin walk home afterwards or blink?
#   D   pit ten deep without stairs, a 3x3 tunnel 28 long from its floor, three goblins full: do they get out?
#   E   a goblin on its way home to unload, emptied by hand: does it still walk home?
#   R   ring crew and Tech Reborn ores: an exposed tin ore next to an exposed iron ore; tin ore in the crosshair
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

pos() { rcon "data get entity @e[type=goblinlabour:goblin,name=$1,limit=1] Pos" | grep -v '^>' | grep -o '\[.*\]' | tr -d 'd[]' | awk -F', ' '{printf "%.1f,%.1f,%.1f", $1, $2, $3}'; }
st() { rcon "goblinlabour status $1" | grep -v '^>' | grep -o 'phase=[A-Z_]*\|(job: [A-Za-z]*)' | tr '\n' ' '; }
rcon "forceload add 3000 3000 3120 3125" > /dev/null
# goblins left from an earlier run (beds gone) wander off and share names with this run's: all of them go
rcon "kill @e[type=goblinlabour:goblin,x=2980,y=-80,z=2980,dx=160,dy=60,dz=160]" "kill @e[type=item,x=2980,y=-80,z=2980,dx=160,dy=60,dz=160]" > /dev/null

if wanted T1; then
  B="3005 -60 3002"
  rcon "setblock $B air" "fill 3000 -60 2998 3012 -50 3050 air" "fill 3000 -61 2998 3012 -61 3050 grass_block" > /dev/null
  rcon "fill 3000 -60 3010 3012 -55 3050 stone" "setblock $B goblinlabour:goblin_straw_bed[facing=north]" \
       "setblock 3008 -60 3002 air" "setblock 3008 -60 3002 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $B Onebyone" "goblinlabour tool $B 0 minecraft:iron_pickaxe" > /dev/null
  rcon "goblinlabour tunnel $B 3005 -59 3010 south 1 1 32" | grep -v '^>' | sed 's/^/  T1: /'
  for i in $(seq 1 30); do sleep 5; echo "  T1 $((i*5))s: $(pos Onebyone) $(rcon "goblinlabour status $B" | grep -o 'phase=[A-Z_]*\|(job: [A-Za-z]*)' | tr '\n' ' ')"; done
  grep "Onebyone" run/runServer.out | grep -E "blinked|climb-out|settled" | cut -c1-220 | sed 's/^/  T1 log: /'
fi

if wanted T2; then
  # a 1x1 tunnel off the floor of a pit without stairs: home again means climbing, not blinking
  B="3005 -49 3088"
  rcon "setblock $B air" "fill 3000 -63 3085 3025 -50 3120 stone" "fill 3000 -49 3085 3025 -40 3120 air" > /dev/null
  rcon "fill 3004 -60 3094 3006 -50 3096 air" "setblock $B goblinlabour:goblin_straw_bed[facing=north]" "setblock 3008 -49 3088 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $B Pitnine" "goblinlabour tool $B 0 minecraft:iron_pickaxe" "goblinlabour tunnel $B 3005 -59 3097 south 1 1 16"        "tp @e[type=goblinlabour:goblin,name=Pitnine] 3005.5 -60 3095.5" | grep -v '^>' | grep Order | cut -c1-80 | sed 's/^/  T2: /'
  for i in $(seq 1 36); do sleep 5; echo "  T2 $((i*5))s: $(pos Pitnine) $(st "$B")"; done
  grep "Pitnine" run/runServer.out | grep -E "blinked|climb-out|settled|fell" | cut -c1-230 | sed 's/^/  T2 log: /'
fi

if wanted D; then
  for x in 3040 3043 3046; do rcon "setblock $x -49 3002 air" > /dev/null; done
  rcon "fill 3030 -63 2998 3070 -50 3025 stone" "fill 3030 -63 3026 3070 -50 3055 stone" "fill 3030 -49 2998 3070 -40 3055 air" > /dev/null
  rcon "fill 3042 -60 3012 3044 -50 3014 air" "fill 3042 -60 3015 3044 -58 3042 air" > /dev/null
  rcon "setblock 3040 -49 3002 goblinlabour:goblin_straw_bed[facing=north]" "setblock 3043 -49 3002 goblinlabour:goblin_straw_bed[facing=north]" \
       "setblock 3046 -49 3002 goblinlabour:goblin_straw_bed[facing=north]" "setblock 3049 -49 3002 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  for g in "3040 Deep1 3043.5 -60 3039.5" "3043 Deep2 3042.5 -60 3030.5" "3046 Deep3 3043.5 -60 3013.5"; do
    set -- $g
    rcon "goblinlabour spawn $1 -49 3002 $2" "goblinlabour tool $1 -49 3002 0 minecraft:iron_pickaxe" "tp @e[type=goblinlabour:goblin,name=$2] $3 $4 $5" > /dev/null
  done
  sleep 2
  for x in 3040 3043 3046; do rcon "goblinlabour job $x -49 3002 chop 8" "goblinlabour fillstorage $x -49 3002 minecraft:cobblestone" > /dev/null; done
  settled0=$(grep -c "settled from" run/runServer.out)
  for i in $(seq 1 36); do
    sleep 5
    echo "  D $((i*5))s: Deep1 $(pos Deep1) | Deep2 $(pos Deep2) | Deep3 $(pos Deep3)"
  done
  grep -E "Deep[123]" run/runServer.out | grep -E "blinked|climb-out|settled|died|fell" | cut -c1-240 | sed 's/^/  D log: /' | tail -40
  echo "  D settle lines: $(( $(grep -c 'settled from' run/runServer.out) - settled0 ))"
  rcon "data get block 3049 -49 3002 Items" | grep -v '^>' | grep -o 'Slot: [0-9]*b' | tail -1 | sed 's/^/  D chest last /'
fi

if wanted D2 || wanted D3; then
  rcon "fill 3030 -63 3060 3070 -50 3084 stone" "fill 3030 -49 3056 3070 -40 3084 air" "fill 3030 -61 3056 3070 -61 3059 stone" "fill 3030 -60 3056 3070 -50 3059 air" > /dev/null
  for x in 3034 3037 3040 3043 3060; do rcon "setblock $x -49 3058 air" > /dev/null; done
  rcon "fill 3030 -63 3056 3070 -50 3059 stone" > /dev/null
fi
if wanted D2; then
  R=$(( $(date +%s) % 100000 )) # killed goblins respawn at their old beds: fresh names every run
  rcon "fill 3037 -60 3068 3039 -50 3070 air" > /dev/null
  for x in 3034 3037 3040 3043; do rcon "setblock $x -49 3058 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null; done
  rcon "setblock 3046 -49 3058 goblinlabour:goblin_chest" "setblock 3047 -49 3058 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  i=0
  for x in 3034 3037 3040 3043; do
    i=$((i+1)); tx=$(( 3037 + (i % 3) )); tz=$(( 3068 + (i / 2) ))
    rcon "goblinlabour spawn $x -49 3058 Pit$i$R" "goblinlabour tool $x -49 3058 0 minecraft:iron_pickaxe" "goblinlabour job $x -49 3058 chop 8" \
         "tp @e[type=goblinlabour:goblin,name=Pit$i$R] $tx.5 -60 $tz.5" "goblinlabour fillstorage $x -49 3058 minecraft:cobblestone" > /dev/null
  done
  s0=$(grep -c "settled from" run/runServer.out)
  for t in $(seq 1 30); do
    sleep 4; up=0
    for i in 1 2 3 4; do y=$(pos Pit$i$R | cut -d, -f2); [ "${y%.*}" -ge -49 ] 2>/dev/null && up=$((up+1)); done
    echo "  D2 $((t*4))s: out=$up $(for i in 1 2 3 4; do printf '%s ' "$(pos Pit$i$R)"; done)"
    [ $((t % 3)) = 0 ] && echo "  D2 status Pit1: $(rcon "goblinlabour status 3034 -49 3058" | grep -v "^>" | grep -o "health=.*")"
    [ "$up" = 4 ] && break
  done
  grep -E "Pit[1-4]$R " run/runServer.out | grep -E "blinked|climb-out|fell|died|go first" | cut -c1-230 | sed 's/^/  D2 log: /' | tail -30
  echo "  D2 settle lines: $(( $(grep -c 'settled from' run/runServer.out) - s0 ))"
fi
if wanted D3; then
  rcon "fill 3060 -60 3070 3060 -50 3070 air" "setblock 3060 -49 3058 goblinlabour:goblin_straw_bed[facing=north]" "setblock 3063 -49 3058 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn 3060 -49 3058 Shaftnine" "goblinlabour job 3060 -49 3058 chop 8" "tp @e[type=goblinlabour:goblin,name=Shaftnine] 3060.5 -60 3070.5" \
       "goblinlabour fillstorage 3060 -49 3058 minecraft:cobblestone" > /dev/null
  s0=$(grep -c "settled from" run/runServer.out)
  for t in $(seq 1 20); do sleep 3; echo "  D3 $((t*3))s: $(pos Shaftnine)"; done
  grep "Shaftnine" run/runServer.out | grep -E "blinked|climb-out|fell|died" | cut -c1-230 | sed 's/^/  D3 log: /' | tail -20
  echo "  D3 settle lines: $(( $(grep -c 'settled from' run/runServer.out) - s0 ))"
fi

if wanted E; then
  B="3090 -60 3002"
  rcon "setblock $B air" "fill 3080 -60 2998 3110 -50 3040 air" "fill 3080 -61 2998 3110 -61 3040 grass_block" > /dev/null
  rcon "fill 3080 -60 3030 3110 -55 3040 stone" "setblock $B goblinlabour:goblin_straw_bed[facing=north]" "setblock 3093 -60 3002 air" "setblock 3093 -60 3002 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $B Emptynine" "goblinlabour tool $B 0 minecraft:iron_pickaxe" "goblinlabour tunnel $B 3090 -59 3030 south 3 2 10" \
       "tp @e[type=goblinlabour:goblin,name=Emptynine] 3090.5 -60 3028.5" > /dev/null
  sleep 6
  rcon "goblinlabour fillstorage $B minecraft:cobblestone" > /dev/null
  sleep 5
  echo "  E full, after 5 s: $(pos Emptynine) $(st "$B")"
  rcon "goblinlabour emptystorage $B" > /dev/null
  for i in 2 4 6 8 10 12 16 20; do sleep 2; echo "  E emptied +${i}s: $(pos Emptynine) $(st "$B")"; done
fi

if wanted R; then
  rcon "fill 3000 -60 3050 3030 -50 3080 air" "fill 3000 -61 3050 3030 -61 3080 grass_block" > /dev/null
  rcon "fill 3010 -60 3065 3020 -56 3065 stone" "setblock 3013 -59 3065 techreborn:tin_ore" "setblock 3017 -59 3065 minecraft:iron_ore" > /dev/null
  out=$(rcon "execute if block 3013 -59 3065 #c:ores"); echo "  R tin ore in #c:ores: $out"
  rcon "goblinlabour ring start 3015.5 -60 3060.5 0" "goblinlabour ring expire 600" | grep -v '^>' | sed 's/^/  R: /'
  for i in $(seq 1 12); do
    sleep 5
    t=$(rcon "execute if block 3013 -59 3065 techreborn:tin_ore" | grep -o 'Test [a-z]*'); f=$(rcon "execute if block 3017 -59 3065 minecraft:iron_ore" | grep -o 'Test [a-z]*')
    echo "  R $((i*5))s: tin still there: $t, iron still there: $f | $(rcon "goblinlabour ring status" | grep -o 'ores=[0-9]* vein=[0-9]*\|ring:[^|]*' | tr '\n' ' ' | cut -c1-200)"
  done
  # tin ore in the tester's crosshair in a corridor (like round 8 scene I)
  rcon "fill 3000 -61 3040 3006 -56 3049 stone" "fill 3002 -60 3040 3004 -58 3046 air" "setblock 3003 -59 3047 techreborn:tin_ore" > /dev/null
  rcon "goblinlabour ring move 3003.5 -60 3044.5 0" > /dev/null
  for i in $(seq 1 8); do sleep 4; echo "  R crosshair $((i*4))s: $(rcon "execute if block 3003 -59 3047 techreborn:tin_ore" | grep -o 'Test [a-z]*')"; done
  # a wall the crew helps dig; tin ore turns up in sight during the help
  rcon "fill 3020 -60 3040 3030 -56 3040 stone" "goblinlabour ring move 3025.5 -60 3035.5 0" > /dev/null
  sleep 4
  rcon "goblinlabour ring assist 3025 -59 3040" > /dev/null
  sleep 4
  rcon "setblock 3022 -58 3039 techreborn:tin_ore" > /dev/null
  for i in $(seq 1 6); do sleep 3; echo "  R assist $((i*3))s: $(rcon "execute if block 3022 -58 3039 techreborn:tin_ore" | grep -o 'Test [a-z]*')"; done
  # a deepslate vein: two exposed, one behind them
  rcon "fill 3008 -60 3072 3018 -56 3076 deepslate" "setblock 3012 -59 3072 techreborn:deepslate_tin_ore" "setblock 3013 -59 3072 techreborn:deepslate_lead_ore"        "setblock 3013 -59 3073 techreborn:deepslate_tin_ore" "goblinlabour ring move 3012.5 -60 3069.5 0" > /dev/null
  for i in $(seq 1 8); do sleep 4; echo "  R vein $((i*4))s: $(rcon "execute if block 3012 -59 3072 techreborn:deepslate_tin_ore" "execute if block 3013 -59 3072 techreborn:deepslate_lead_ore" "execute if block 3013 -59 3073 techreborn:deepslate_tin_ore" | grep -o 'Test [a-z]*' | tr '\n' ' ')"; done
  rcon "goblinlabour ring toggle" > /dev/null
fi

echo "RESULT probe done"
rcon "stop" | tail -1
wait
