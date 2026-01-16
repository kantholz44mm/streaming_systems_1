#!/usr/bin/env bash
set -euo pipefail

# ---- locate project directories ----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# traffic/ -> .../ss/src/main/java/com/.../traffic  => ss/ is 8 levels up
SS_DIR="$(cd "${SCRIPT_DIR}/../../../../../../.." && pwd)"

if [[ ! -f "${SS_DIR}/pom.xml" ]]; then
  echo "[error] Could not find pom.xml at: ${SS_DIR}"
  echo "        Script must be inside ss/src/main/java/.../traffic/"
  exit 1
fi

GIT_DIR="$(cd "${SS_DIR}/.." && pwd)"

echo "[run] traffic dir:  ${SCRIPT_DIR}"
echo "[run] ss dir:      ${SS_DIR}"
echo "[run] git dir:     ${GIT_DIR}"

# ---- start docker/kafka ----
if [[ -f "${GIT_DIR}/docker-compose.yml" || -f "${GIT_DIR}/compose.yml" ]]; then
  echo "[run] Starting docker compose (Kafka)..."
  (cd "${GIT_DIR}" && docker compose up -d)
else
  echo "[warn] No docker-compose.yml found in ${GIT_DIR} -> skipping docker compose up -d"
fi

# ---- build ----
echo "[run] Building Maven project..."
(cd "${SS_DIR}" && mvn -q clean package)

# ---- logs ----
LOG_DIR="${SS_DIR}/logs"
mkdir -p "${LOG_DIR}"
ESPER_LOG="${LOG_DIR}/esper-task6.log"

# ---- run Esper in background ----
echo "[run] Starting Esper (Task 6) in background..."
(cd "${SS_DIR}" && mvn -q exec:java -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.TrafficEsperApp") \
  > "${ESPER_LOG}" 2>&1 &
ESPER_PID=$!

echo "[run] Esper PID: ${ESPER_PID}"
echo "[run] Esper log: ${ESPER_LOG}"

cleanup() {
  echo
  echo "[run] Stopping Esper (PID ${ESPER_PID})..."
  kill "${ESPER_PID}" 2>/dev/null || true
  wait "${ESPER_PID}" 2>/dev/null || true
  echo "[run] Done."
}
trap cleanup INT TERM EXIT

# give Esper a moment to subscribe
sleep 2

# ---- run Producer (fills topic) ----
echo "[run] Running TrafficDataProducer..."
(cd "${SS_DIR}" && mvn -q exec:java -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.TrafficDataProducer")

echo "[run] Producer finished. Waiting a bit so Esper can print results..."
sleep 12

echo "[run] Finished. (Esper output is in ${ESPER_LOG})"