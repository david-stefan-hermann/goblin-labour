#!/usr/bin/env bash
# Probe for feedback round 5 (samples, no PASS/FAIL):
#  F  two lumberjacks in a small forest of generated trees (one tree each, scaffold climbing)
#  K  a goblin scaffold column collapses when its bottom block is removed
#  M  a miner digs a 3x3 stair shaft into a stone hill, then a tunnel through mixed stone: where does it walk?
# Scenes live at x/z 485..640 on the flat world.
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }
status() { rcon "goblinlabour status $1" | grep -v '^>' ; }
short() { status "$1" | tr '\n' ' ' | sed 's/.*inventory:/inv:/; s/ | order=.*//; s/ | rest:.*//; s/skipped=[0-9]* retryIn=-*[0-9]* //'; }
pos() { rcon "data get entity @e[type=goblinlabour:goblin,name=$1,limit=1] Pos" | grep -o '\[[-0-9.d, ]*\]' | tail -1 | tr -d 'd '; }

( ./gradlew runServer --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break; sleep 3; done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

rcon "forceload add 480 480 645 645" "time set day" "weather clear" > /dev/null
rcon "setblock 495 -60 495 air" "setblock 492 -60 505 air" "setblock 600 -50 600 air" > /dev/null
sleep 2
rcon "kill @e[type=goblinlabour:goblin,x=480,y=-64,z=480,dx=165,dy=80,dz=165]" "kill @e[type=item,x=480,y=-64,z=480,dx=165,dy=80,dz=165]" > /dev/null
rcon "fill 485 -60 485 507 -36 535 air" "fill 508 -60 485 535 -36 535 air" "fill 485 -61 485 535 -61 535 grass_block" \
     "fill 580 -61 580 630 -40 610 air" "fill 580 -61 611 630 -40 640 air" > /dev/null
sleep 2

# ---- F: forest (beds 495,-60,495 and 492,-60,505; trees east and south of them) ----
A="495 -60 495"; B="492 -60 505"
rcon "place feature minecraft:oak 505 -60 505" "place feature minecraft:fancy_oak 513 -60 499" "place feature minecraft:birch 508 -60 514" \
     "place feature minecraft:spruce 517 -60 512" "place feature minecraft:oak 503 -60 521" "place feature minecraft:fancy_oak 520 -60 523" | grep -v '^>'
rcon "setblock $A goblinlabour:goblin_straw_bed[facing=south]" "setblock $B goblinlabour:goblin_straw_bed[facing=east]" > /dev/null
sleep 1
rcon "goblinlabour spawn $A Axel" "goblinlabour spawn $B Birk" > /dev/null
rcon "goblinlabour tool $A 0 minecraft:iron_axe" "goblinlabour tool $B 0 minecraft:iron_axe" \
     "goblinlabour tool $A 9 minecraft:oak_sapling" "goblinlabour tool $B 9 minecraft:birch_sapling" > /dev/null
rcon "goblinlabour job $A chop 30" "goblinlabour job $B chop 30" | grep -v '^>'

# ---- K: scaffold collapse ----
rcon "fill 530 -60 530 530 -53 530 goblinlabour:goblin_scaffold[distance=0]" > /dev/null
sleep 2
echo "K top before: $(rcon 'execute if block 530 -53 530 goblinlabour:goblin_scaffold' | tail -1)"
rcon "setblock 530 -60 530 air" > /dev/null
sleep 3
echo "K top after removing the bottom (expect failed): $(rcon 'execute if block 530 -53 530 goblinlabour:goblin_scaffold' | tail -1)"
echo "K middle after (expect failed): $(rcon 'execute if block 530 -57 530 goblinlabour:goblin_scaffold' | tail -1)"

# ---- M: stone hill with a bed on top, shaft south of the home, tunnel further south ----
M="600 -50 600"
rcon "fill 585 -61 585 625 -52 635 stone" "fill 585 -51 585 625 -51 635 grass_block" \
     "fill 597 -61 612 603 -57 616 andesite replace stone" "fill 597 -61 617 603 -57 620 granite replace stone" \
     "fill 597 -61 621 603 -57 624 diorite replace stone" "fill 597 -61 625 603 -57 628 tuff replace stone" \
     "fill 599 -60 614 601 -58 614 coal_ore replace andesite" "fill 598 -59 619 598 -58 622 iron_ore replace granite" \
     "fill 602 -59 625 602 -58 626 copper_ore replace tuff" "fill 599 -56 607 601 -53 609 dirt replace stone" \
     "fill 598 -58 610 602 -54 611 gravel replace stone" > /dev/null
rcon "setblock 602 -50 600 copper_chest" "setblock $M goblinlabour:goblin_straw_bed[facing=south]" > /dev/null
sleep 1
rcon "goblinlabour spawn $M Moria" "goblinlabour tool $M 0 minecraft:iron_pickaxe" "goblinlabour tool $M 1 minecraft:iron_shovel" > /dev/null
rcon "goblinlabour dig $M down 600 -51 608 3 -60 true" | grep -v '^>'

# ---- samples: forest (and shaft) every 5 s for 180 s ----
for i in $(seq 1 36); do
  sleep 5
  echo "--- t=$((i*5))s"
  echo "  Axel $(pos Axel) $(short "$A")"
  echo "  Birk $(pos Birk) $(short "$B")"
  if [ $((i % 3)) -eq 0 ]; then echo "  Moria $(pos Moria) $(short "$M")"; fi
done

# ---- M: wait for the shaft, then the tunnel ----
end=$(( $(date +%s) + 420 ))
while [ "$(date +%s)" -lt "$end" ]; do
  s=$(status "$M")
  if [[ "$s" == *"job: Rest"* ]]; then break; fi
  echo "  shaft: Moria $(pos Moria) $(echo "$s" | tr '\n' ' ' | sed 's/.*inventory:/inv:/; s/ | order=.*//; s/ | rest:.*//')"
  sleep 15
done
echo "M shaft status: $(status "$M" | head -1)"
rcon "goblinlabour tunnel $M 600 -60 610 south 3 3 20" | grep -v '^>'
for i in $(seq 1 60); do
  sleep 5
  echo "  tunnel t=$((i*5))s Moria $(pos Moria) $(short "$M")"
done
echo "M tunnel front: $(for z in 612 616 620 624 628; do echo -n "z$z=$(rcon "execute if block 600 -59 $z air" | grep -c passed) "; done)"
grep -a "Moria\|Axel\|Birk" run/runServer.out | tail -30
rcon "stop" | tail -1
wait
