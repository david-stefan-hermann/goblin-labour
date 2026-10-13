#!/usr/bin/env bash
# Feedback round 9 over RCON (x/z 2980..3140 on the flat world; stone blocks on top of it, above the bedrock at -64):
#   D  a shaft 11 deep without stairs and a 3x3 tunnel 28 long off its floor, three goblins full: all climb out (no blink)
#   P  four full goblins in a 3x3 pit 11 deep share one column, one after the other, and nobody falls back
#   N  a 1x1 tunnel off the floor of such a pit: the goblin digs it and climbs out afterwards instead of blinking home
#   E  a goblin heading home to unload is emptied by hand: it turns back to its tunnel at once
#   R  ring crew and Tech Reborn ores (only with Tech Reborn in libs/): exposed, in the crosshair, during a dig, a vein
# Usage: bash tools/dev-test-round9.sh [scene ...]   (no scene: all). Prints PASS/FAIL and RESULT.
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
ypos() { pos "$1" | cut -d, -f2; }
st() { rcon "goblinlabour status $1" | grep -v '^>' | grep -o 'phase=[A-Z_]*'; }
blinks() { grep -c "Dev: $1 blinked" run/runServer.out; }
R=$(( $(date +%s) % 100000 )) # killed goblins respawn at their old beds: fresh names every run
rcon "forceload add 2980 2980 3140 3140" > /dev/null
rcon "kill @e[type=goblinlabour:goblin,x=2980,y=-80,z=2980,dx=160,dy=60,dz=160]" "kill @e[type=item,x=2980,y=-80,z=2980,dx=160,dy=60,dz=160]" > /dev/null

# ---- D: shaft plus tunnel ----
if wanted D; then
  for x in 3040 3043 3046; do rcon "setblock $x -49 3002 air" > /dev/null; done
  rcon "fill 3030 -63 2998 3070 -50 3025 stone" "fill 3030 -63 3026 3070 -50 3055 stone" "fill 3030 -49 2998 3070 -40 3055 air" > /dev/null
  rcon "fill 3042 -60 3012 3044 -50 3014 air" "fill 3042 -60 3015 3044 -58 3042 air" > /dev/null
  rcon "setblock 3040 -49 3002 goblinlabour:goblin_straw_bed[facing=north]" "setblock 3043 -49 3002 goblinlabour:goblin_straw_bed[facing=north]" \
       "setblock 3046 -49 3002 goblinlabour:goblin_straw_bed[facing=north]" "setblock 3049 -49 3002 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  for g in "3040 Deep1 3043.5 -60 3039.5" "3043 Deep2 3042.5 -60 3030.5" "3046 Deep3 3043.5 -60 3013.5"; do
    set -- $g
    rcon "goblinlabour spawn $1 -49 3002 $2$R" "goblinlabour job $1 -49 3002 chop 8" "tp @e[type=goblinlabour:goblin,name=$2$R] $3 $4 $5" \
         "goblinlabour fillstorage $1 -49 3002 minecraft:cobblestone" > /dev/null
  done
  out3=0; took=0
  for t in $(seq 1 45); do
    sleep 2; out3=0
    for g in Deep1 Deep2 Deep3; do y=$(ypos $g$R); [ "${y%.*}" -ge -49 ] 2>/dev/null && out3=$((out3 + 1)); done
    [ "$out3" = 3 ] && { took=$((t * 2)); break; }
  done
  b=$(( $(blinks Deep1$R) + $(blinks Deep2$R) + $(blinks Deep3$R) ))
  ok "D all three climbed out of the shaft, the one deepest in the tunnel too ($out3 of 3, ${took}s)" "$(( out3 == 3 ? 1 : 0 ))"
  ok "D nobody blinked home ($b)" "$(( b == 0 ? 1 : 0 ))"
  out=$(rcon "data get block 3049 -49 3002 Items"); check "D the chest got their loot" "cobblestone" "$out"
fi

# ---- P: four in a pit, one column ----
if wanted P; then
  for x in 3034 3037 3040 3043; do rcon "setblock $x -49 3058 air" > /dev/null; done
  rcon "fill 3030 -63 3056 3070 -50 3084 stone" "fill 3030 -49 3056 3070 -40 3084 air" "fill 3037 -60 3068 3039 -50 3070 air" > /dev/null
  for x in 3034 3037 3040 3043; do rcon "setblock $x -49 3058 goblinlabour:goblin_straw_bed[facing=north]" > /dev/null; done
  rcon "setblock 3046 -49 3058 goblinlabour:goblin_chest" "setblock 3046 -49 3061 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  fell0=$(grep -c "fell while climbing out" run/runServer.out); set0=$(grep -c "settled from" run/runServer.out)
  i=0
  for x in 3034 3037 3040 3043; do
    i=$((i + 1)); tx=$(( 3037 + (i % 3) )); tz=$(( 3068 + (i / 2) ))
    rcon "goblinlabour spawn $x -49 3058 Pit$i$R" "goblinlabour job $x -49 3058 chop 8" "tp @e[type=goblinlabour:goblin,name=Pit$i$R] $tx.5 -60 $tz.5" \
         "goblinlabour fillstorage $x -49 3058 minecraft:cobblestone" > /dev/null
  done
  up=0; took=0
  for t in $(seq 1 30); do
    sleep 2; up=0
    for i in 1 2 3 4; do y=$(ypos Pit$i$R); [ "${y%.*}" -ge -49 ] 2>/dev/null && up=$((up + 1)); done
    [ "$up" = 4 ] && { took=$((t * 2)); break; }
  done
  fell=$(( $(grep -c "fell while climbing out" run/runServer.out) - fell0 )); settled=$(( $(grep -c "settled from" run/runServer.out) - set0 ))
  b=0; for i in 1 2 3 4; do b=$(( b + $(blinks Pit$i$R) )); done
  cols=$(grep -E "Dev: Pit[1-4]$R climb-out: stuck" run/runServer.out | grep -o 'column [-0-9, ]* to' | sort -u | wc -l)
  ok "P all four climbed out within a minute ($up of 4, ${took}s)" "$(( up == 4 ? 1 : 0 ))"
  ok "P nobody fell back into the pit ($fell) or was snapped down ($settled)" "$(( fell == 0 && settled == 0 ? 1 : 0 ))"
  ok "P nobody blinked home ($b)" "$(( b == 0 ? 1 : 0 ))"
  ok "P they shared one column ($cols planned)" "$(( cols == 1 ? 1 : 0 ))"
fi

# ---- N: a 1x1 tunnel off the floor of a pit ----
if wanted N; then
  B="3005 -49 3088"
  rcon "setblock $B air" "fill 3000 -63 3085 3025 -50 3120 stone" "fill 3000 -49 3085 3025 -40 3120 air" > /dev/null
  rcon "fill 3004 -60 3094 3006 -50 3096 air" "setblock $B goblinlabour:goblin_straw_bed[facing=north]" "setblock 3008 -49 3088 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $B Pitx$R" "goblinlabour tool $B 0 minecraft:iron_pickaxe" "goblinlabour tunnel $B 3005 -59 3097 south 1 1 16" \
       "tp @e[type=goblinlabour:goblin,name=Pitx$R] 3005.5 -60 3095.5" > /dev/null
  out=$(wait_out 60 "Test passed" "execute if block 3005 -59 3112 air"); check "N the 1x1 tunnel was dug to its end" "Test passed" "$out"
  y=""; for t in $(seq 1 30); do sleep 2; y=$(ypos Pitx$R); [ "${y%.*}" -ge -49 ] 2>/dev/null && break; done
  ok "N the goblin climbed out of the pit afterwards (y=$y)" "$( [ "${y%.*}" -ge -49 ] 2>/dev/null && echo 1 || echo 0)"
  b=$(blinks Pitx$R); ok "N and did not blink ($b)" "$(( b == 0 ? 1 : 0 ))"
fi

# ---- E: emptied by hand on the way home ----
if wanted E; then
  B="3090 -60 3002"
  rcon "setblock $B air" "fill 3080 -60 2998 3110 -50 3040 air" "fill 3080 -61 2998 3110 -61 3040 grass_block" > /dev/null
  rcon "fill 3080 -60 3030 3110 -55 3040 stone" "setblock $B goblinlabour:goblin_straw_bed[facing=north]" "setblock 3093 -60 3002 air" "setblock 3093 -60 3002 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $B Empty$R" "goblinlabour tool $B 0 minecraft:iron_pickaxe" "goblinlabour tunnel $B 3090 -59 3030 south 3 2 10" \
       "tp @e[type=goblinlabour:goblin,name=Empty$R] 3090.5 -60 3028.5" > /dev/null
  sleep 6
  rcon "goblinlabour fillstorage $B minecraft:cobblestone" > /dev/null
  out=$(wait_out 10 "phase=DEPOSIT" "goblinlabour status $B"); check "E full, it heads home to unload" "phase=DEPOSIT" "$out"
  sleep 2
  z0=$(pos Empty$R | cut -d, -f3)
  rcon "goblinlabour emptystorage $B" > /dev/null
  sleep 2
  out=$(st "$B"); check "E emptied on the way, it drops the trip home" "phase=WORK" "$out"
  sleep 4
  z1=$(pos Empty$R | cut -d, -f3)
  echo "  E: z when emptied $z0, four seconds later $z1 (the tunnel is south, home north)"
  ok "E and heads back to its tunnel" "$(awk -v a="$z0" -v b="$z1" 'BEGIN { print (b > a + 1) ? 1 : 0 }')"
fi

# ---- R: ring crew and Tech Reborn ores ----
if wanted R; then
  tr=$(rcon "setblock 3013 -59 3130 techreborn:tin_ore" | grep -v '^>')
  if [[ "$tr" == *"nknown"* ]]; then
    echo "  R skipped: Tech Reborn is not in libs/"
  else
    rcon "fill 3000 -60 3120 3030 -50 3140 air" "fill 3000 -61 3120 3030 -61 3140 grass_block" "fill 3010 -60 3130 3020 -56 3130 stone" \
         "setblock 3013 -59 3130 techreborn:tin_ore" "setblock 3017 -59 3130 minecraft:iron_ore" > /dev/null
    out=$(rcon "execute if block 3013 -59 3130 #c:ores"); check "R Tech Reborn tin ore is in c:ores" "Test passed" "$out"
    rcon "goblinlabour ring start 3015.5 -60 3125.5 0" "goblinlabour ring empty" "goblinlabour ring expire 600" > /dev/null
    out=$(wait_out 30 "Test passed" "execute if block 3013 -59 3130 air"); check "R the crew mines an exposed tin ore" "Test passed" "$out"
    out=$(wait_out 10 "raw_tin" "goblinlabour ring status"); check "R its raw tin is in the ring" "raw_tin" "$out"
    # tin ore in the crosshair at the end of a corridor (open to the north, where the crew comes from; scene N's stone
    # block ends at z 3120, so the corridor stays south of it)
    rcon "fill 3024 -61 3131 3030 -56 3140 stone" "fill 3026 -60 3131 3028 -58 3137 air" "setblock 3027 -59 3138 techreborn:tin_ore" \
         "goblinlabour ring move 3027.5 -60 3135.5 0" > /dev/null
    out=$(wait_out 30 "Test passed" "execute if block 3027 -59 3138 air"); check "R tin ore in the crosshair is mined" "Test passed" "$out"
    # tin ore turning up while the crew helps dig a wall
    rcon "fill 3020 -60 3120 3030 -56 3120 stone" "goblinlabour ring move 3025.5 -60 3125.5 180" > /dev/null
    sleep 4
    rcon "goblinlabour ring assist 3025 -59 3120" > /dev/null
    sleep 4
    rcon "setblock 3022 -58 3121 techreborn:tin_ore" > /dev/null
    out=$(wait_out 15 "Test passed" "execute if block 3022 -58 3121 air"); check "R tin ore turning up during the help is mined (15 s)" "Test passed" "$out"
    # a deepslate vein: two exposed, one behind them
    rcon "fill 3008 -60 3135 3018 -56 3139 deepslate" "setblock 3012 -59 3135 techreborn:deepslate_tin_ore" "setblock 3013 -59 3135 techreborn:deepslate_lead_ore" \
         "setblock 3013 -59 3136 techreborn:deepslate_tin_ore" "goblinlabour ring move 3012.5 -60 3132.5 0" > /dev/null
    out=$(wait_out 30 "Test passed" "execute if block 3013 -59 3136 air"); check "R a whole vein comes out, the hidden one too" "Test passed" "$out"
    rcon "goblinlabour ring toggle" > /dev/null
  fi
fi

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
