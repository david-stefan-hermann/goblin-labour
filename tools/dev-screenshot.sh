#!/usr/bin/env bash
# Starts the dev server, runs RCON setup commands, joins with the dev client (which saves
# run/screenshots/<shot>.png and <shot>-b.png, see DevClientHooks), runs "after join" commands between the
# two screenshots, then stops everything.
#
# Usage: tools/dev-screenshot.sh <shot> "<x,y,z,yaw,pitch>" <setup-file> [after-join-file]
# The command files hold one server command per line (without leading slash).
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
export JAVA_HOME="${JAVA_HOME:-C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.101-hotspot}"
export TEMP='C:\jtmp' TMP='C:\jtmp'

SHOT="${1:-goblin}"
VIEW="${2:-4.5,-58,4.5,135,20}"
SETUP="${3:-/dev/null}"
AFTER="${4:-/dev/null}"
MENU="${5:-}"
MENUARG=()
BOOKARG=()
# CLIENT_EXTRA="-PdevStaffClick=1" adds Gradle properties to the client run
read -r -a EXTRA_CLIENT <<< "${CLIENT_EXTRA:-}"
case "$MENU" in
  book:*) BOOKARG=(-PdevBook="${MENU#book:}") ;;
  ?*) MENUARG=(-PdevMenu="$MENU") ;;
esac
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }

mkdir -p run/screenshots
rm -f "run/screenshots/$SHOT.png" "run/screenshots/$SHOT-b.png"

( ./gradlew runServer -PdevScreenshot -PdevView="$VIEW" "${MENUARG[@]}" --no-daemon -q > run/runServer.out 2>&1; echo "EXIT=$?" >> run/runServer.out ) &
sleep 20
for _ in $(seq 1 100); do
  grep -q 'RCON running\|EXIT=' run/runServer.out 2>/dev/null && break
  sleep 3
done
if grep -q 'EXIT=' run/runServer.out; then echo "server did not start"; tail -40 run/runServer.out; exit 1; fi

mapfile -t SETUP_CMDS < <(grep -v '^\s*$' "$SETUP")
if [ "${#SETUP_CMDS[@]}" -gt 0 ]; then rcon "${SETUP_CMDS[@]}"; fi

JOIN_LINES_BEFORE=$(grep -c 'joined the game' run/runServer.out)
( ./gradlew runClient -PdevScreenshot -PdevView="$VIEW" -PdevShot="$SHOT" "${BOOKARG[@]}" "${EXTRA_CLIENT[@]}" --no-daemon -q > run/runClient.out 2>&1; echo "EXIT=$?" >> run/runClient.out ) &

for _ in $(seq 1 100); do
  [ "$(grep -c 'joined the game' run/runServer.out)" -gt "$JOIN_LINES_BEFORE" ] && break
  grep -q 'EXIT=' run/runClient.out 2>/dev/null && break
  sleep 3
done
sleep 7
mapfile -t AFTER_CMDS < <(grep -v '^\s*$' "$AFTER")
if [ "${#AFTER_CMDS[@]}" -gt 0 ]; then rcon "${AFTER_CMDS[@]}"; fi

for _ in $(seq 1 100); do
  grep -q 'EXIT=' run/runClient.out 2>/dev/null && break
  sleep 3
done
echo "client: $(grep 'EXIT=' run/runClient.out)"
ls -la run/screenshots
rcon "stop" | tail -1
wait
