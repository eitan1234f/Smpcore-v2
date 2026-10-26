#!/usr/bin/env bash
# Boots a real Paper server with SmpCore installed, waits for startup, runs a few
# console commands and fails if SmpCore logged any error.
set -euo pipefail

JAR_DIR="$1"
MC_VERSION="${MC_VERSION:-1.21.11}"

mkdir -p server/plugins
cp "$JAR_DIR"/SmpCore-*.jar server/plugins/
cd server

echo "Fetching latest Paper build for $MC_VERSION"
URL=$(curl -fsSL -H "User-Agent: SmpCore-CI (github.com/eitan1234f/Smpcore-v2)" \
  "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest" | jq -r '.downloads."server:default".url')
curl -fsSL -o paper.jar "$URL"
echo "eula=true" > eula.txt
printf 'online-mode=false\nlevel-type=minecraft:flat\nspawn-protection=0\n' > server.properties

mkfifo console
tail -f console | java -Xmx2G -jar paper.jar --nogui > out.log 2>&1 &
SERVER_PID=$!
exec 3>console

for i in $(seq 1 240); do
  if grep -q "Done (" out.log; then break; fi
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then echo "Server exited early"; cat out.log; exit 1; fi
  sleep 1
done
grep -q "Done (" out.log || { echo "Server did not start in time"; cat out.log; exit 1; }

for cmd in "smpcore version" "plugins" "inspector lookup r:#global t:1h" "anticheat" "bancommand list" "kit list" "npc list" "arenarepair list"; do
  echo "$cmd" >&3
  sleep 2
done
echo "stop" >&3
wait "$SERVER_PID" || true

cat out.log
if ! grep -q "SmpCore enabled" out.log; then
  echo "::error::SmpCore did not enable"
  exit 1
fi
if grep -nE "(Error occurred while enabling SmpCore|Could not pass event .* to SmpCore|Unhandled exception executing command .*SmpCore|dev\.smpcore\.)" out.log \
   | grep -vE "^\s*$" | grep -E "Exception|Error|Could not pass|at dev\.smpcore" ; then
  echo "::error::SmpCore logged errors during the smoke test"
  exit 1
fi
echo "Smoke test passed"
