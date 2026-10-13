#!/usr/bin/env bash
# Feedback round 8 over RCON. Scenes (x/z 2450..2700 on the flat world; 2400..2449 belongs to the milk can screenshots):
#   K  goblin chests are mined with a pickaxe, no longer with an axe
#   M  a milk can placed by a player turns its handles to the player's left and right (facing towards the player)
#   G  bed goblins among leftover scaffold columns never hang stuck in one (dev watchdog "stuck in scaffold")
#   C  a collector fetches items on the ground and leaves those on a scaffold column or on leaves alone
#   U  a shaft up through stone with a cave cutting into its side is dug from inside the shaft only
#   O  a ring crew digs a shaft up with its player right below: nobody is snapped down, a full ring pauses them up there
#   F  two lumberjacks fell ten spruce trees with touching crowns completely (replanting off, 12 minutes at most)
#   S  a collector unloads into the barrel the staff assigned, and goes back home when it is gone
#   B  goblins do not stand about on straw beds
#   P  a goblin that died in a pit fetches its things again; with lava around them it gives up at once
#   Q/T/I/J/L  ring crew: idle movement, a thrown item, ore in the crosshair, ore during assist, torches in the dark
# Usage: bash tools/dev-test-round8.sh [scene ...]   (no scene: all). Prints PASS/FAIL and RESULT.
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

# ---- K: goblin chests (green and one dyed) are pickaxe blocks ----
if wanted K; then
  rcon "forceload add 2450 2450 2470 2470" > /dev/null
  rcon "fill 2450 -60 2450 2470 -55 2470 air" "fill 2450 -61 2450 2470 -61 2470 grass_block" > /dev/null
  rcon "setblock 2455 -60 2455 goblinlabour:goblin_chest" "setblock 2458 -60 2455 goblinlabour:red_goblin_chest" > /dev/null
  for c in "2455 -60 2455" "2458 -60 2455"; do
    out=$(rcon "execute if block $c #minecraft:mineable/pickaxe"); check "K chest at $c is mineable with a pickaxe" "Test passed" "$out"
    out=$(rcon "execute if block $c #minecraft:mineable/axe"); check "K chest at $c is no longer an axe block" "Test failed" "$out"
  done
fi

# ---- M: milk cans placed like a player would, looking south (yaw 0) and west (yaw 90) ----
if wanted M; then
  rcon "forceload add 2480 2450 2500 2470" > /dev/null
  rcon "fill 2480 -60 2450 2500 -55 2470 air" "fill 2480 -61 2450 2500 -61 2470 grass_block" > /dev/null
  rcon "kill @e[type=item,x=2480,y=-64,z=2450,dx=20,dy=10,dz=20]" > /dev/null
  rcon "summon item 2485.5 -60 2455.5 {Item:{id:\"goblinlabour:milk_churn\",count:1},PickupDelay:32767}" \
       "summon item 2490.5 -60 2455.5 {Item:{id:\"goblinlabour:milk_churn\",count:1},PickupDelay:32767}" > /dev/null
  sleep 1
  out=$(rcon "goblinlabour churn 2485 -60 2455 placedrop 0"); check "M can placed looking south" "milk=" "$out"
  out=$(rcon "goblinlabour churn 2490 -60 2455 placedrop 90"); check "M can placed looking west" "milk=" "$out"
  out=$(rcon "execute if block 2485 -60 2455 goblinlabour:milk_churn[facing=north]"); check "M looking south: the can faces north, towards the player" "Test passed" "$out"
  out=$(rcon "execute if block 2490 -60 2455 goblinlabour:milk_churn[facing=east]"); check "M looking west: the can faces east, towards the player" "Test passed" "$out"
  out=$(rcon "setblock 2495 -60 2455 goblinlabour:milk_churn" "execute if block 2495 -60 2455 goblinlabour:milk_churn[facing=north,level=0]")
  check "M a can set without properties defaults to north" "Test passed" "$out"
fi


# ---- G: bed goblins (a stroller at home, a collector fetching items behind them) among leftover scaffold columns ----
# A goblin that hangs in a column it has no business climbing is stuck: the dev watchdog logs "stuck in scaffold".
if wanted G; then
  GR="2640 -60 2460"; GC="2644 -60 2460"
  rcon "forceload add 2630 2450 2670 2490" > /dev/null
  rcon "setblock $GR air" "setblock $GC air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2630,y=-64,z=2450,dx=40,dy=20,dz=40]" "kill @e[type=item,x=2630,y=-64,z=2450,dx=40,dy=20,dz=40]" > /dev/null
  rcon "fill 2630 -60 2450 2670 -50 2490 air" "fill 2630 -61 2450 2670 -61 2490 grass_block" > /dev/null
  rcon "setblock $GR goblinlabour:goblin_straw_bed[facing=south]" "setblock $GC goblinlabour:goblin_straw_bed[facing=south]" \
       "setblock 2642 -60 2457 air" "setblock 2642 -60 2457 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $GR Stroller" "goblinlabour spawn $GC Fetcher" "goblinlabour job $GC collect 14" > /dev/null
  g_columns() {
    for c in "2638 2464" "2642 2466" "2646 2464" "2650 2468" "2654 2470" "2648 2472" "2641 2462" "2645 2468"; do
      set -- $c; rcon "fill $1 -60 $2 $1 -58 $2 goblinlabour:goblin_scaffold[distance=0]" > /dev/null
    done
  }
  g_items() {
    for c in "2651.5 2471.5" "2655.5 2471.5" "2639.5 2466.5" "2647.5 2473.5" "2643.5 2467.5"; do
      set -- $c; rcon "summon item $1 -60 $2 {Item:{id:\"minecraft:cobblestone\",count:1}}" > /dev/null
    done
  }
  g_columns; g_items
  stuck_before=$(grep -c "stuck in scaffold" run/runServer.out)
  hanging=0; samples=0
  end=$(( $(date +%s) + 110 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 1
    out=$(rcon "execute as @e[type=goblinlabour:goblin,x=2630,y=-64,z=2450,dx=40,dy=20,dz=40] at @s if block ~ ~ ~ goblinlabour:goblin_scaffold if block ~ ~-1 ~ goblinlabour:goblin_scaffold")
    [[ "$out" == *"Test passed"* ]] && hanging=$((hanging + 1))
    samples=$((samples + 1))
    [ $((samples % 30)) = 0 ] && g_items
  done
  stuck=$(( $(grep -c "stuck in scaffold" run/runServer.out) - stuck_before ))
  echo "  G: watchdog lines=$stuck, samples with a goblin hanging inside a column=$hanging of $samples"
  grep "stuck in scaffold" run/runServer.out | tail -3 | cut -c1-300
  ok "G no goblin got stuck in a scaffold column (watchdog lines: $stuck)" "$(( stuck == 0 ? 1 : 0 ))"
  ok "G goblins hardly ever hang inside a column they have no business climbing ($hanging of $samples samples, at most 3)" "$(( hanging <= 3 ? 1 : 0 ))"
fi

# ---- C: a collector with items on top of a scaffold column and on a leaf platform, and items on the ground ----
if wanted C; then
  CB="2535 -60 2455"
  rcon "forceload add 2528 2448 2562 2482" > /dev/null
  rcon "setblock $CB air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2528,y=-64,z=2448,dx=34,dy=20,dz=34]" "kill @e[type=item,x=2528,y=-64,z=2448,dx=34,dy=20,dz=34]" > /dev/null
  rcon "fill 2528 -60 2448 2562 -50 2482 air" "fill 2528 -61 2448 2562 -61 2482 grass_block" > /dev/null
  rcon "setblock $CB goblinlabour:goblin_straw_bed[facing=south]" "setblock 2537 -60 2453 air" "setblock 2537 -60 2453 goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $CB Grabber" "goblinlabour job $CB collect 20" > /dev/null
  # closer to the bed than the ground items, so the old code went for them first
  rcon "fill 2541 -60 2460 2541 -56 2460 goblinlabour:goblin_scaffold[distance=0]" \
       "fill 2543 -56 2462 2545 -56 2464 oak_leaves[persistent=true]" > /dev/null
  sleep 1
  rcon "summon item 2541.5 -55 2460.5 {Item:{id:\"minecraft:oak_sapling\",count:1}}" \
       "summon item 2544.5 -55 2463.5 {Item:{id:\"minecraft:stick\",count:1}}" \
       "summon item 2550.5 -60 2470.5 {Item:{id:\"minecraft:diamond\",count:1}}" \
       "summon item 2553.5 -60 2466.5 {Item:{id:\"minecraft:apple\",count:1}}" \
       "summon item 2547.5 -60 2474.5 {Item:{id:\"minecraft:emerald\",count:1}}" > /dev/null
  in_row=0; max_row=0; high=0
  end=$(( $(date +%s) + 75 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 1
    ALL=$(rcon "execute as @e[type=goblinlabour:goblin,name=Grabber] at @s if block ~ ~ ~ goblinlabour:goblin_scaffold" \
               "data get entity @e[type=goblinlabour:goblin,name=Grabber,limit=1] Pos[1] 10")
    if [[ "$(echo "$ALL" | sed -n 2p)" == *"Test passed"* ]]; then in_row=$((in_row + 1)); else in_row=0; fi
    [ "$in_row" -gt "$max_row" ] && max_row=$in_row
    y10=$(echo "$ALL" | sed -n 4p | grep -o '\-\?[0-9]*$')
    [ -n "$y10" ] && [ "$y10" -gt -589 ] && high=$((high + 1))
  done
  out=$(rcon "data get block 2537 -60 2453 Items")
  check "C the diamond from the ground is in the chest" "minecraft:diamond" "$out"
  check "C the apple from the ground is in the chest" "minecraft:apple" "$out"
  check "C the emerald from the ground is in the chest" "minecraft:emerald" "$out"
  ok "C the collector never stood in a scaffold block for more than 5 s (longest: $max_row s)" "$(( max_row <= 5 ? 1 : 0 ))"
  ok "C the collector never got up above the ground ($high samples higher than a block)" "$(( high == 0 ? 1 : 0 ))"
fi

# ---- U: shafts up through a stone block with a cave pocket cutting into one side (without and with stairs) ----
if wanted U; then
  rcon "forceload add 2575 2450 2625 2480" > /dev/null
  rcon "setblock 2582 -60 2464 air" "setblock 2602 -60 2464 air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2575,y=-64,z=2450,dx=50,dy=40,dz=30]" "kill @e[type=item,x=2575,y=-64,z=2450,dx=50,dy=40,dz=30]" > /dev/null
  rcon "fill 2575 -60 2450 2600 -30 2480 air" "fill 2601 -60 2450 2625 -30 2480 air" "fill 2575 -61 2450 2625 -61 2480 grass_block" > /dev/null
  u_scene() { # $1 x offset, $2 name, $3 stairs
    local x0=$((2590 + $1)) bed="$((2582 + $1)) -60 2464"
    rcon "fill $x0 -56 2460 $((x0 + 8)) -37 2468 stone" \
         "fill $((x0 + 5)) -50 2462 $((x0 + 9)) -47 2466 air" \
         "setblock $bed goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
    # the cave can be walked into from outside: a stone ramp from the ground up to its floor
    rcon "fill $((x0 + 9)) -60 2462 $((x0 + 9)) -51 2466 stone" > /dev/null
    for k in 0 1 2 3 4 5 6 7 8; do rcon "fill $((x0 + 9)) -60 $((2467 + k)) $((x0 + 9)) $((-52 - k)) $((2467 + k)) stone" > /dev/null; done
    sleep 1
    rcon "goblinlabour spawn $bed $2" "goblinlabour tool $bed 0 minecraft:iron_pickaxe" > /dev/null
    rcon "tp @e[type=goblinlabour:goblin,name=$2] $((x0 + 4)).5 -60 2461.5" > /dev/null
    out=$(rcon "goblinlabour dig $bed up $((x0 + 4)) -56 2464 3 -37 $3"); check "U $2 shaft order given" "Order for $2" "$out"
  }
  u_scene 0 Upward false
  u_scene 20 Stairway true
  u_out_up=0; u_out_st=0; u_done_up=0; u_done_st=0
  end=$(( $(date +%s) + 420 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 2
    ALL=$(rcon "goblinlabour status 2582 -60 2464" "goblinlabour status 2602 -60 2464")
    for pair in "Upward 2594 u_out_up u_done_up 2582" "Stairway 2614 u_out_st u_done_st 2602"; do
      set -- $pair
      st=$(echo "$ALL" | grep -A1 -F "> goblinlabour status $5 -60 2464" | tail -1)
      [[ "$st" == *"(job: Rest)"* ]] && [[ "$st" != *"order="* ]] && eval "$4=1"
      p=$(echo "$st" | grep -o 'pos=[-0-9]*, [-0-9]*, [-0-9]*' | head -1 | sed 's/pos=//; s/, /,/g')
      [ -z "$p" ] && continue
      x=$(echo "$p" | cut -d, -f1); y=$(echo "$p" | cut -d, -f2); z=$(echo "$p" | cut -d, -f3)
      if [ "$y" -gt -56 ] && { [ "$x" -lt $(( $2 - 2 )) ] || [ "$x" -gt $(( $2 + 2 )) ] || [ "$z" -lt 2462 ] || [ "$z" -gt 2466 ]; }; then eval "$3=\$(( $3 + 1 ))"; fi
    done
    [ "$u_done_up" = 1 ] && [ "$u_done_st" = 1 ] && break
  done
  for pair in "Upward 2594 $u_out_up $u_done_up" "Stairway 2614 $u_out_st $u_done_st"; do
    set -- $pair
    ok "U $1 finished its shaft in time" "$4"
    out=$(rcon "fill $(( $2 - 1 )) -56 2463 $(( $2 + 1 )) -37 2465 air replace stone"); check "U $1 no stone left in the shaft" "No blocks were filled" "$out"
    rcon "fill $(( $2 - 1 )) -60 2463 $(( $2 + 1 )) -37 2465 air replace goblinlabour:goblin_scaffold" > /dev/null
    out=$(rcon "fill $(( $2 - 12 )) -60 2455 $(( $2 + 8 )) -37 2473 air replace goblinlabour:goblin_scaffold"); check "U $1 no scaffold outside the shaft" "No blocks were filled" "$out"
    ok "U $1 never more than a block outside the shaft up in the stone ($3 samples)" "$(( $3 == 0 ? 1 : 0 ))"
  done
fi

# ---- O: a ring crew digs a shaft up while its player stands right below it; no goblin is snapped down its column,
#        and with the ring full half-way the goblins up there wait where they are ----
if wanted O; then
  rcon "forceload add 2450 2500 2490 2530" > /dev/null
  rcon "kill @e[type=goblinlabour:goblin,x=2450,y=-64,z=2500,dx=40,dy=40,dz=30]" "kill @e[type=item,x=2450,y=-64,z=2500,dx=40,dy=40,dz=30]" > /dev/null
  rcon "fill 2450 -60 2500 2490 -40 2530 air" "fill 2450 -61 2500 2490 -61 2530 grass_block" \
       "fill 2460 -56 2510 2466 -44 2516 stone" "fill 2476 -56 2510 2482 -44 2516 stone" > /dev/null
  settled_before=$(grep -c "settled from" run/runServer.out)
  # the player stands at the edge of the shaft, right below the goblins on the columns of its near row
  out=$(rcon "goblinlabour ring start 2463.5 -60 2510.5 0" | grep -v '^>'); check "O crew of three called" "crew of 3" "$out"
  rcon "goblinlabour ring expire 900" > /dev/null
  sleep 3
  rcon "goblinlabour ring select" > /dev/null
  out=$(rcon "goblinlabour ring digup 2463 -56 2513 3 -49"); check "O shaft order given" "Order" "$out"
  o_done=0
  end=$(( $(date +%s) + 300 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 5
    [ $(( ($(date +%s) - end + 300) % 60 )) -lt 5 ] && rcon "goblinlabour ring status" | grep -v '^>' | cut -c1-400 | sed 's/^/  O status: /'
    # the shaft is done when it looks like the empty sky above (a fill that changes nothing always says "no blocks")
    out=$(rcon "execute if blocks 2462 -56 2512 2464 -49 2514 2462 200 2512 all")
    if [[ "$out" == *"Test passed"* ]]; then o_done=1; break; fi
  done
  settled=$(( $(grep -c "settled from" run/runServer.out) - settled_before ))
  ok "O the crew dug the whole shaft with its player below it" "$o_done"
  ok "O no crew goblin was snapped down its column ($settled settle lines)" "$(( settled == 0 ? 1 : 0 ))"
  # second shaft: fill the ring as soon as a goblin is up, then watch for ten seconds
  sleep 5
  rcon "goblinlabour ring move 2479.5 -60 2510.5 0" > /dev/null
  sleep 3
  rcon "goblinlabour ring select" "goblinlabour ring digup 2479 -56 2513 3 -49" > /dev/null
  up_at_fill=0
  end=$(( $(date +%s) + 120 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 1
    ys=$(rcon "goblinlabour ring status" | grep -o 'pos=[-0-9.]*,[-0-9.]*,[-0-9.]*' | cut -d, -f2)
    up_at_fill=0; for y in $ys; do [ "${y%.*}" -ge -56 ] && up_at_fill=$((up_at_fill + 1)); done
    [ "$up_at_fill" -ge 1 ] && break
  done
  settled_before=$(grep -c "settled from" run/runServer.out)
  crew_names=$(rcon "goblinlabour ring status" | grep -o '| [A-Za-z]* pos=' | awk '{print $2}')
  blinks_before=0; for n in $crew_names; do blinks_before=$(( blinks_before + $(grep -c "Dev: $n blinked" run/runServer.out) )); done
  rcon "goblinlabour ring give minecraft:cobblestone 3456" > /dev/null
  sleep 10
  settled=$(( $(grep -c "settled from" run/runServer.out) - settled_before ))
  blinks=0; for n in $crew_names; do blinks=$(( blinks + $(grep -c "Dev: $n blinked" run/runServer.out) )); done
  blinks=$(( blinks - blinks_before ))
  ok "O2 a crew goblin was up in the second shaft when the ring filled ($up_at_fill)" "$(( up_at_fill >= 1 ? 1 : 0 ))"
  ok "O2 with the ring full no goblin was snapped down ($settled settle lines)" "$(( settled == 0 ? 1 : 0 ))"
  # climbing down by itself once there is nowhere to put the stone is allowed; a teleport down is not (every blink is
  # logged since round 9; the height a second later was no measure: a goblin at the lowest step is down in a second)
  ok "O2 nobody was teleported down either ($blinks blinks of $(echo $crew_names | wc -w) crew goblins)" "$(( blinks == 0 && $(echo $crew_names | wc -w) == 3 ? 1 : 0 ))"
  rcon "goblinlabour ring toggle" > /dev/null
fi

# ---- F: two lumberjacks fell ten spruce trees with touching crowns completely (replanting off, at most 12 minutes) ----
if wanted F; then
  F1="2455 -60 2555"; F2="2459 -60 2555"; FC="2457 -60 2553"
  f_logs() { # logs left in the forest, counted by cloning them into the sky
    rcon "fill 2462 100 2556 2492 129 2586 air" > /dev/null
    rcon "clone 2462 -60 2556 2492 -31 2586 2462 100 2556 filtered #minecraft:logs force" | grep -o 'cloned [0-9]*' | grep -o '[0-9]*' || echo 0
  }
  rcon "forceload add 2445 2545 2505 2605" > /dev/null
  rcon "setblock $F1 air" "setblock $F2 air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2445,y=-64,z=2545,dx=60,dy=40,dz=60]" "kill @e[type=item,x=2445,y=-64,z=2545,dx=60,dy=40,dz=60]" > /dev/null
  rcon "fill 2450 -60 2550 2466 -31 2600 air" "fill 2467 -60 2550 2483 -31 2600 air" "fill 2484 -60 2550 2500 -31 2600 air" \
       "fill 2450 -61 2550 2500 -61 2600 grass_block" "fill 2462 100 2556 2492 129 2586 air" > /dev/null
  placed=0
  for t in "2470 2565" "2475 2567" "2480 2564" "2485 2566" "2471 2571" "2476 2573" "2481 2570" "2486 2572" "2473 2577" "2479 2578"; do
    set -- $t
    out=$(rcon "place feature minecraft:spruce $1 -60 $2"); [[ "$out" == *"Placed"* ]] && placed=$((placed + 1))
  done
  rcon "setblock $F1 goblinlabour:goblin_straw_bed[facing=south]" "setblock $F2 goblinlabour:goblin_straw_bed[facing=south]" \
       "setblock $FC air" "setblock $FC goblinlabour:goblin_chest" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $F1 Sprucer" "goblinlabour tool $F1 0 minecraft:iron_axe" "goblinlabour job $F1 chop 32" "goblinlabour replant $F1 false" \
       "goblinlabour spawn $F2 Needles" "goblinlabour tool $F2 0 minecraft:iron_axe" "goblinlabour job $F2 chop 32" "goblinlabour replant $F2 false" > /dev/null
  start_logs=$(f_logs); f_start=$(date +%s); left=$start_logs
  ok "F ten spruce trees stand ($placed placed, $start_logs logs)" "$(( placed >= 9 && start_logs >= 40 ? 1 : 0 ))"
  end=$(( f_start + 720 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 30
    left=$(f_logs)
    echo "  F: $(( $(date +%s) - f_start )) s, $left logs left"
    [ "$left" = 0 ] && break
  done
  if [ "$left" != 0 ]; then
    echo "  F Sprucer: $(rcon "goblinlabour status $F1" | grep -v '^>' | grep -o 'pos=.*' | cut -c1-500)"
    echo "  F Needles: $(rcon "goblinlabour status $F2" | grep -v '^>' | grep -o 'pos=.*' | cut -c1-500)"
  fi
  ok "F every log of the forest is down ($left of $start_logs left after $(( $(date +%s) - f_start )) s)" "$(( left == 0 ? 1 : 0 ))"
  deaths=$(grep 'died:' run/runServer.out | grep -c 'Sprucer\|Needles')
  ok "F no lumberjack died ($deaths)" "$(( deaths == 0 ? 1 : 0 ))"
  rcon "fill 2462 100 2556 2492 129 2586 air" > /dev/null
fi
# ---- S: a collector unloads into the barrel the staff assigned, not into the goblin chest at home ----
if wanted S; then
  SB="2670 -60 2570"; SC="2672 -60 2568"; SBARREL="2682 -60 2570"
  rcon "forceload add 2660 2560 2700 2600" > /dev/null
  rcon "setblock $SB air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2660,y=-64,z=2560,dx=40,dy=20,dz=40]" "kill @e[type=item,x=2660,y=-64,z=2560,dx=40,dy=20,dz=40]" > /dev/null
  rcon "fill 2660 -60 2560 2700 -50 2600 air" "fill 2660 -61 2560 2700 -61 2600 grass_block" > /dev/null
  rcon "setblock $SB goblinlabour:goblin_straw_bed[facing=south]" "setblock $SC air" "setblock $SC goblinlabour:goblin_chest" \
       "setblock $SBARREL air" "setblock $SBARREL barrel" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $SB Porter" "goblinlabour job $SB collect 16" > /dev/null
  out=$(rcon "goblinlabour chest $SB 2682 -60 2570"); check "S the barrel was assigned" "2682, -60, 2570" "$out"
  for c in "2674.5 2574.5 minecraft:diamond" "2676.5 2571.5 minecraft:emerald" "2673.5 2576.5 minecraft:gold_ingot"; do
    set -- $c; rcon "summon item $1 -60 $2 {Item:{id:\"$3\",count:1},Age:-32768}" > /dev/null
  done
  out=$(wait_out 90 "minecraft:diamond" "data get block $SBARREL Items"); check "S the loot went into the assigned barrel" "minecraft:diamond" "$out"
  out=$(rcon "data get block $SC Items"); check_not "S the goblin chest at home stayed empty" "minecraft:diamond" "$out"
  # the barrel is gone: the assignment is dropped and the goblin chest at home takes over again
  rcon "setblock $SBARREL air" > /dev/null
  for c in "2674.5 2574.5 minecraft:redstone" "2676.5 2571.5 minecraft:lapis_lazuli"; do
    set -- $c; rcon "summon item $1 -60 $2 {Item:{id:\"$3\",count:1},Age:-32768}" > /dev/null
  done
  out=$(wait_out 90 "minecraft:redstone" "data get block $SC Items"); check "S without the barrel the loot goes home again" "minecraft:redstone" "$out"
  out=$(rcon "goblinlabour status $SB"); check_not "S the assignment was dropped" "chest=2682" "$out"
fi

# ---- B: goblins do not stand about on straw beds ----
if wanted B; then
  BA="2664 -60 2590"; BB="2666 -60 2590"
  rcon "forceload add 2655 2580 2680 2605" > /dev/null
  rcon "setblock $BA air" "setblock $BB air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2655,y=-64,z=2580,dx=25,dy=20,dz=25]" > /dev/null
  rcon "fill 2655 -60 2580 2680 -50 2605 air" "fill 2655 -61 2580 2680 -61 2605 grass_block" > /dev/null
  rcon "setblock $BA goblinlabour:goblin_straw_bed[facing=south]" "setblock $BB goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $BA Napper" "goblinlabour job $BA chop 16" > /dev/null
  rcon "tp @e[type=goblinlabour:goblin,name=Napper] 2664.5 -59 2590.5" > /dev/null
  sleep 12
  out=$(rcon "execute as @e[type=goblinlabour:goblin,name=Napper] at @s if block ~ ~ ~ goblinlabour:goblin_straw_bed" \
             "execute as @e[type=goblinlabour:goblin,name=Napper] at @s if block ~ ~-1 ~ goblinlabour:goblin_straw_bed")
  check_not "B a working goblin put on a bed steps off it" "Test passed" "$out"
  rcon "goblinlabour job $BA rest" > /dev/null
  awake_on_bed=0; on_other=0
  end=$(( $(date +%s) + 120 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 5
    st=$(rcon "goblinlabour status $BA" | grep -v '^>' | tr '\n' ' ')
    on=$(rcon "execute as @e[type=goblinlabour:goblin,name=Napper] at @s if block ~ ~-1 ~ goblinlabour:goblin_straw_bed")
    if [[ "$on" == *"Test passed"* ]] && [[ "$st" != *"mode=NAP"* ]]; then awake_on_bed=$((awake_on_bed + 1)); fi
    p=$(rcon "execute as @e[type=goblinlabour:goblin,name=Napper] at @s if block ~ ~-1 ~ goblinlabour:goblin_straw_bed positioned 2666 -61 2590 if entity @s[distance=..0.8]")
    [[ "$p" == *"Test passed"* ]] && on_other=$((on_other + 1))
  done
  ok "B a resting goblin stands on a bed only while it sleeps ($awake_on_bed samples awake on a bed)" "$(( awake_on_bed == 0 ? 1 : 0 ))"
  ok "B the bed next door is no place to stand ($on_other samples)" "$(( on_other == 0 ? 1 : 0 ))"
fi

# ---- P: a goblin dies in a pit it cannot climb out of and fetches its things again; with lava it gives up at once ----
if wanted P; then
  PB="2690 -60 2560"
  rcon "forceload add 2680 2550 2700 2580" > /dev/null
  rcon "setblock $PB air" > /dev/null; sleep 1
  rcon "kill @e[type=goblinlabour:goblin,x=2680,y=-70,z=2550,dx=20,dy=30,dz=30]" "kill @e[type=item,x=2680,y=-70,z=2550,dx=20,dy=30,dz=30]" > /dev/null
  # a 3x3 shaft with five high walls on the flat ground: a goblin inside cannot walk out, and nobody can walk in
  rcon "fill 2680 -60 2550 2700 -50 2580 air" "fill 2680 -61 2550 2700 -61 2580 grass_block" \
       "fill 2693 -60 2563 2697 -56 2567 stone" "fill 2694 -60 2564 2696 -56 2566 air" > /dev/null
  rcon "setblock $PB goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
  sleep 1
  rcon "goblinlabour spawn $PB Faller" "goblinlabour fillstorage $PB minecraft:cobblestone" > /dev/null
  deaths_before=$(grep -c 'died:' run/runServer.out)
  rcon "tp @e[type=goblinlabour:goblin,name=Faller] 2695.5 -60 2565.5" > /dev/null
  sleep 2
  rcon "kill @e[type=goblinlabour:goblin,name=Faller]" > /dev/null
  out=$(wait_out 90 "inventory:" "goblinlabour status $PB")
  sleep 40
  out=$(rcon "goblinlabour status $PB" | grep -v '^>' | tr '\n' ' ')
  n=$(echo "$out" | grep -o '=[0-9]*xminecraft:cobblestone' | wc -l)
  ok "P the goblin fetched its dropped storage out of the pit ($n stacks back)" "$(( n >= 1 ? 1 : 0 ))" "status: $(echo "$out" | cut -c1-200)"
  deaths=$(( $(grep -c 'died:' run/runServer.out) - deaths_before ))
  ok "P it died exactly once ($deaths)" "$(( deaths == 1 ? 1 : 0 ))"
  # items walled into a gap one block high: nowhere for a goblin to stand, so it gives up instead of waiting it out
  # (lava around them would do the same in the code, but the drops fall into it and burn, so there is nothing to test)
  rcon "kill @e[type=item,x=2680,y=-70,z=2550,dx=20,dy=30,dz=30]" "goblinlabour fillstorage $PB minecraft:cobblestone" > /dev/null
  rcon "fill 2694 -60 2569 2696 -57 2571 stone" "fill 2695 -60 2570 2695 -59 2570 air" > /dev/null
  said_before=$(grep -c 'no getting to it' run/runServer.out)
  deaths_before=$(grep -c 'died:' run/runServer.out)
  # in one go: walled in, the goblin would blink home again within a second or two (ClimbOutGoal)
  rcon "tp @e[type=goblinlabour:goblin,name=Faller] 2695.5 -60 2570.5" "kill @e[type=goblinlabour:goblin,name=Faller]" > /dev/null
  sleep 3
  rcon "setblock 2695 -59 2570 stone" > /dev/null
  sleep 45
  said=$(( $(grep -c 'no getting to it' run/runServer.out) - said_before ))
  deaths=$(( $(grep -c 'died:' run/runServer.out) - deaths_before ))
  ok "P walled in, it gives up on its things instead of waiting ($said)" "$(( said >= 1 ? 1 : 0 ))"
  ok "P and does not die a second time ($deaths)" "$(( deaths == 1 ? 1 : 0 ))"
  rcon "fill 2693 -60 2563 2697 -56 2567 air" "fill 2694 -60 2569 2696 -57 2571 air" "fill 2693 -61 2563 2697 -61 2571 grass_block" > /dev/null
fi

# ---- P5 ring crew scenes (x 2440..2650, z 2610..2690); one crew walks with the tester from scene to scene ----
if wanted Q || wanted T || wanted I || wanted J || wanted L; then
  rcon "forceload add 2440 2610 2650 2690" "time set day" > /dev/null
  rcon "kill @e[type=item,x=2440,y=-64,z=2610,dx=210,dy=40,dz=80]" > /dev/null
  for x0 in 2440 2470 2500 2530 2560 2590 2620; do
    rcon "fill $x0 -60 2610 $((x0 + 29)) -50 2690 air" "fill $x0 -61 2610 $((x0 + 29)) -61 2690 grass_block" > /dev/null
  done
  out=$(rcon "goblinlabour ring start 2460.5 -60 2640.5 0" | grep -v '^>'); check "P5 crew of three called" "crew of 3" "$out"
  # scene O2 leaves the ring full on purpose: without this the crew here has nowhere to put what it mines
  rcon "goblinlabour ring empty" "goblinlabour ring expire 1800" > /dev/null
  crew_ys() { rcon "goblinlabour ring status" | grep -o 'pos=[-0-9.]*,[-0-9.]*,[-0-9.]*' | sed 's/pos=//'; }
fi

# Q: an idle crew in an open field for 60 s; in how many seconds did a goblin move since the second before
if wanted Q; then
  sleep 30 # the crew first walks over to the player it was called by; the test is about standing still afterwards
  moved=0; samples=0; gmoved=0; prev=""; per=""
  end=$(( $(date +%s) + 60 ))
  while [ "$(date +%s)" -lt "$end" ]; do
    sleep 1
    cur=$(crew_ys | tr '\n' ' ')
    if [ -n "$prev" ]; then
      each=$(echo "$prev|$cur" | awk -F'|' '{ n=split($1,a," "); split($2,b," "); s=""; for(i=1;i<=n;i++){ split(a[i],p,","); split(b[i],q,","); s=s (((p[1]-q[1])^2+(p[3]-q[3])^2 > 0.09) ? 1 : 0) } print s }')
      m=0; [[ "$each" == *1* ]] && m=1
      per="$per$each "
      gmoved=$((gmoved + $(echo "$each" | tr -cd 1 | wc -c)))
      moved=$((moved + m)); samples=$((samples + 1))
    fi
    prev="$cur"
  done
  echo "  Q: a crew goblin moved in $moved of $samples seconds, $gmoved of $((samples * 3)) goblin seconds"
  echo "  Q: per goblin and sample: $per"
  rcon "goblinlabour ring status" | grep -v '^>' | cut -c1-400 | sed 's/^/  Q status: /' 
  # before round 8 every goblin moved in nearly every sample (39 of 39 seconds, ~100% of the goblin seconds)
  ok "Q an idle crew goblin stands still most of the time ($gmoved of $((samples * 3)) goblin seconds)" "$(( samples > 0 && gmoved * 3 <= samples * 3 ? 1 : 0 ))"
fi

# T: the tester throws an item like a player: it lands a few blocks ahead, in the lane the tester looks along
if wanted T; then
  rcon "goblinlabour ring move 2460.5 -60 2640.5 0" > /dev/null
  sleep 3
  rcon "goblinlabour ring drop minecraft:emerald" > /dev/null
  sleep 2
  out=$(rcon "execute if entity @e[type=item,x=2459,y=-61,z=2641.5,dx=3,dy=3,dz=4,nbt={Item:{id:\"minecraft:emerald\"}}]"); check "T the thrown emerald landed ahead of the tester" "Test passed" "$out"
  out=$(wait_out 12 "minecraft:emerald" "goblinlabour ring status"); check "T the crew fetched what the player threw into the lane ahead" "minecraft:emerald" "$out"
fi

# I: a corridor three wide; iron ore at eye height three blocks ahead, right in the tester's crosshair
if wanted I; then
  rcon "fill 2489 -61 2630 2495 -56 2650 stone" "fill 2491 -60 2630 2493 -58 2643 air" > /dev/null
  rcon "setblock 2492 -59 2644 iron_ore" > /dev/null
  rcon "goblinlabour ring move 2492.5 -60 2641.5 0" > /dev/null
  out=$(wait_out 30 "Test passed" "execute if block 2492 -59 2644 air"); check "I the ore in the crosshair was mined within 30 s" "Test passed" "$out"
  out=$(wait_out 10 "raw_iron" "goblinlabour ring status"); check "I its raw iron is in the ring" "raw_iron" "$out"
fi

# J: the crew helps dig a wall (assist); ore appears in sight: it is mined before the help is over
if wanted J; then
  rcon "fill 2520 -60 2650 2530 -56 2650 stone" "goblinlabour ring move 2525.5 -60 2645.5 0" > /dev/null
  sleep 4
  rcon "goblinlabour ring assist 2525 -59 2650" > /dev/null
  sleep 4
  rcon "setblock 2522 -58 2649 gold_ore" > /dev/null
  out=$(wait_out 15 "Test passed" "execute if block 2522 -58 2649 air"); check "J ore turning up during the help is mined at once (15 s)" "Test passed" "$out"
fi

# L: a dark room 9x9x4 with a stone roof: the crew puts up a few torches; an open field at midnight gets none
if wanted L; then
  rcon "fill 2549 -61 2655 2559 -56 2665 stone" "fill 2550 -60 2656 2558 -57 2664 air" > /dev/null
  rcon "goblinlabour ring move 2554.5 -60 2660.5 0" > /dev/null
  sleep 3
  rcon "tp @e[type=goblinlabour:goblin,x=2440,y=-64,z=2610,dx=210,dy=40,dz=80] 2553.5 -60 2659.5" > /dev/null
  sleep 30
  t1=$(rcon "fill 2550 -60 2656 2558 -57 2664 air replace torch" | grep -o 'filled [0-9]*' | grep -o '[0-9]*')
  t2=$(rcon "fill 2550 -60 2656 2558 -57 2664 air replace wall_torch" | grep -o 'filled [0-9]*' | grep -o '[0-9]*')
  torches=$(( ${t1:-0} + ${t2:-0} ))
  ok "L the crew lit the dark room with 1 to 4 torches ($torches)" "$(( torches >= 1 && torches <= 4 ? 1 : 0 ))"
  rcon "fill 2549 -61 2655 2559 -56 2665 air" "fill 2549 -61 2655 2559 -61 2665 grass_block" "time set midnight" "goblinlabour ring move 2600.5 -60 2650.5 0" > /dev/null
  sleep 30
  t1=$(rcon "fill 2585 -60 2635 2615 -55 2665 air replace torch" | grep -o 'filled [0-9]*' | grep -o '[0-9]*')
  t2=$(rcon "fill 2585 -60 2635 2615 -55 2665 air replace wall_torch" | grep -o 'filled [0-9]*' | grep -o '[0-9]*')
  ok "L no torches in an open field at midnight ($(( ${t1:-0} + ${t2:-0} )))" "$(( ${t1:-0} + ${t2:-0} == 0 ? 1 : 0 ))"
  rcon "time set day" > /dev/null
fi
if wanted Q || wanted T || wanted I || wanted J || wanted L; then
  rcon "goblinlabour ring toggle" > /dev/null
fi

echo "RESULT pass=$pass fail=$fail"
rcon "stop" | tail -1
wait
