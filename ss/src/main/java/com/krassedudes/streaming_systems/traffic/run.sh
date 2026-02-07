#!/usr/bin/env bash
set -euo pipefail

# -----------------------------
# CONFIG (dynamisch überschreibbar)
# -----------------------------
TOPIC="${TRAFFIC_TOPIC:-traffic-data}"
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
CONTAINER="${KAFKA_CONTAINER:-kafka}"
PARTITIONS="${KAFKA_PARTITIONS:-1}"
REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"

# Average-Speed Output-Ordner (Task 5)
AVG_DIR_REL="output/average-speeds"
AVG_GLOB_REL="$AVG_DIR_REL/avg"   # Beam schreibt z.B. avg-<window>-pane-0-last-00000-of-00001.txt

# -----------------------------
# Locate ss project root (pom.xml)
# -----------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SS_DIR="$SCRIPT_DIR"

while [[ "$SS_DIR" != "/" && ! -f "$SS_DIR/pom.xml" ]]; do
  SS_DIR="$(cd "$SS_DIR/.." && pwd)"
done

if [[ ! -f "$SS_DIR/pom.xml" ]]; then
  echo "[ERROR] pom.xml not found from $SCRIPT_DIR upwards"
  exit 1
fi

cd "$SS_DIR"

LOG_DIR="$SS_DIR/logs"
OUT_DIR="$SS_DIR/output"
mkdir -p "$LOG_DIR" "$OUT_DIR"

echo "[run] Project dir : $SS_DIR"
echo "[run] Topic       : $TOPIC"
echo "[run] Bootstrap   : $BOOTSTRAP"
echo "[run] Container   : $CONTAINER"

# -----------------------------
# Helpers
# -----------------------------
kafka_topics() {
  # Kein -t (TTY) verwenden, sonst kann es in Scripts hängen
  docker exec "$CONTAINER" bash -lc "/opt/kafka/bin/kafka-topics.sh $*"
}

wait_for_kafka() {
  echo "[run] Waiting for Kafka to be ready..."
  for i in {1..20}; do
    if docker exec "$CONTAINER" bash -lc "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list" >/dev/null 2>&1; then
      echo "[run] Kafka is ready."
      return 0
    fi
    echo "[run] Kafka not ready yet... retry ($i/20)"
    sleep 2
  done
  echo "[ERROR] Kafka not ready after waiting."
  exit 1
}

wait_for_average_output() {
  # Wartet bis mindestens eine avg-Datei existiert (max WAIT_SECONDS)
  local WAIT_SECONDS="${1:-30}"
  local waited=0

  echo "[run] Waiting up to ${WAIT_SECONDS}s for average-speed output to be written..."
  while [[ $waited -lt $WAIT_SECONDS ]]; do
    if find "$SS_DIR/$AVG_DIR_REL" -type f -name "avg*" 2>/dev/null | grep -q .; then
      echo "[run] Average-speed output detected."
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done

  echo "[WARN] No average-speed output found after ${WAIT_SECONDS}s."
  return 1
}

cleanup() {
  echo ""
  echo "[run] Stopping pipeline (PID=$PIPELINE_PID)..."
  kill "$PIPELINE_PID" >/dev/null 2>&1 || true
  wait "$PIPELINE_PID" >/dev/null 2>&1 || true
  echo "[run] Done."
}
trap cleanup INT TERM

# -----------------------------
# Prepare Kafka topic (optional reset)
# -----------------------------
wait_for_kafka

if [[ "${RESET_TOPIC:-1}" == "1" ]]; then
  echo "[run] Resetting topic '$TOPIC' (delete + create)"
  kafka_topics "--bootstrap-server kafka:9092 --delete --topic $TOPIC" >/dev/null 2>&1 || true
  sleep 2
fi

echo "[run] Ensuring topic '$TOPIC' exists"
kafka_topics "--bootstrap-server kafka:9092 --create --if-not-exists --topic $TOPIC --partitions $PARTITIONS --replication-factor $REPLICATION" >/dev/null

# -----------------------------
# Clean outputs (optional)
# -----------------------------
if [[ "${CLEAN_OUTPUT:-1}" == "1" ]]; then
  echo "[run] Cleaning output directory: $OUT_DIR"
  rm -rf "$OUT_DIR"
  mkdir -p "$OUT_DIR"
fi

# -----------------------------
# Build project
# -----------------------------
echo "[run] Building project"
mvn -q clean package -DskipTests

# -----------------------------
# Start Pipeline (BACKGROUND)
# -----------------------------
export TRAFFIC_TOPIC="$TOPIC"

echo "[run] Starting TrafficAnalysisPipeline (background)"
mvn -q -DskipTests exec:java \
  -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.TrafficAnalysisPipeline" \
  > "$LOG_DIR/TrafficAnalysisPipeline.log" 2>&1 &

PIPELINE_PID=$!
echo "[run] Pipeline PID: $PIPELINE_PID"

# Pipeline kurz anlaufen lassen
sleep 3

# -----------------------------
# Start Producer (FOREGROUND)
# -----------------------------
echo "[run] Starting TrafficDataProducer"
mvn -q -DskipTests exec:java \
  -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.TrafficDataProducer" \
  > "$LOG_DIR/TrafficDataProducer.log" 2>&1

# -----------------------------
# Wait for average output (so dass du NICHT nur raw/bad-lines siehst)
# -----------------------------
wait_for_average_output 40 || true

# -----------------------------
# Show output
# -----------------------------
echo ""
echo "[run] Output files under: $OUT_DIR"
find "$OUT_DIR" -type f -maxdepth 4 -print || true

echo ""
echo "[run] Expected average files under: $SS_DIR/$AVG_DIR_REL"
find "$SS_DIR/$AVG_DIR_REL" -type f -name "avg*" -maxdepth 2 -print 2>/dev/null || true

echo ""
echo "[run] Pipeline still running (PID=$PIPELINE_PID)"
echo "[run] Logs:"
echo "  - $LOG_DIR/TrafficAnalysisPipeline.log"
echo "  - $LOG_DIR/TrafficDataProducer.log"
echo ""
echo "[run] Ctrl+C to stop pipeline"

wait "$PIPELINE_PID"