#!/usr/bin/env bash
# Runs dev-test suites one after the other (only one dev server may run at a time) and writes a summary.
# Usage: bash tools/dev-test-all.sh <tag> [suite ...]    (no suites given: every tools/dev-test-*.sh)
# Per suite: run/<tag>-<suite>.out (checks) and run/<tag>-<suite>.server.log (copy of run/runServer.out).
# Summary: run/<tag>-full.txt with start time, RESULT line and FAIL lines per suite, last line "ALLDONE <time>".
set -u
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
if [ "${OS:-}" = Windows_NT ]; then export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"; fi
if [ "${OS:-}" = Windows_NT ]; then export TEMP='C:\jtmp' TMP='C:\jtmp'; fi
rcon() { "$JAVA_HOME/bin/java" tools/Rcon.java 127.0.0.1 25599 gl "$@"; }

tag="${1:?usage: dev-test-all.sh <tag> [suite ...]}"; shift
suites=("$@")
if [ ${#suites[@]} -eq 0 ]; then
  for f in tools/dev-test-*.sh; do
    s="${f#tools/dev-test-}"; s="${s%.sh}"
    [ "$s" = all ] || suites+=("$s")
  done
fi

sum="run/$tag-full.txt"
: > "$sum"
for s in "${suites[@]}"; do
  echo "=== $s started $(date +%H:%M:%S) ===" >> "$sum"
  start=$(date +%s)
  bash "tools/dev-test-$s.sh" > "run/$tag-$s.out" 2>&1
  rc=$?
  cp run/runServer.out "run/$tag-$s.server.log" 2>/dev/null
  echo "$s exit=$rc $(grep '^RESULT' "run/$tag-$s.out" | tail -1) ($(( $(date +%s) - start )) s)" >> "$sum"
  grep '^FAIL' "run/$tag-$s.out" >> "$sum"
  # A suite that died half-way leaves its server running and would block the next one.
  rcon "stop" > /dev/null 2>&1 && sleep 10
done
echo "ALLDONE $(date +%H:%M:%S)" >> "$sum"
