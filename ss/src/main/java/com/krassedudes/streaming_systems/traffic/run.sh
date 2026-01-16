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

# -----------------------------
# Locate project root (pom.xml)
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

# -----------------------------
# Reset Kafka topic (DELETE + CREATE)
# -----------------------------
echo "[run] Resetting topic '$TOPIC'"

docker exec -it "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --delete \
  --topic "$TOPIC" >/dev/null 2>&1 || true

sleep 2

docker exec -it "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --create --if-not-exists \
  --topic "$TOPIC" \
  --partitions "$PARTITIONS" \
  --replication-factor "$REPLICATION"

# -----------------------------
# Build project
# -----------------------------
echo "[run] Building project"
mvn -q clean package

# -----------------------------
# Start Pipeline (BACKGROUND)
# -----------------------------
export TRAFFIC_TOPIC="$TOPIC"

echo "[run] Starting TrafficAnalysisPipeline (background)"
mvn -q exec:java \
  -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.TrafficAnalysisPipeline" \
  > "$LOG_DIR/TrafficAnalysisPipeline.log" 2>&1 &

PIPELINE_PID=$!
sleep 3

# -----------------------------
# Start Producer (FOREGROUND)
# -----------------------------
echo "[run] Starting TrafficDataProducer"
mvn -q exec:java \
  -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.TrafficDataProducer" \
  > "$LOG_DIR/TrafficDataProducer.log" 2>&1

# -----------------------------
# Show output
# -----------------------------
echo "[run] Output files:"
find "$OUT_DIR" -type f -maxdepth 3 -print || true

echo ""
echo "[run] Pipeline running (PID=$PIPELINE_PID)"
echo "[run] Logs:"
echo "  - $LOG_DIR/TrafficAnalysisPipeline.log"
echo "  - $LOG_DIR/TrafficDataProducer.log"
echo ""
echo "[run] Ctrl+C to stop pipeline"

wait "$PIPELINE_PID"