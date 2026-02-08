#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
CONTAINER="${KAFKA_CONTAINER:-kafka}"

TOPIC_IN="${TRAFFIC_TOPIC_IN:-traffic-data}"
TOPIC_OUT="${TRAFFIC_TOPIC_OUT:-traffic-processed}"
TOPIC_BAD="${TRAFFIC_TOPIC_BAD:-traffic-bad}"

PARTITIONS="${KAFKA_PARTITIONS:-1}"
REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"

GROUP_ID="${TASK8_GROUP_ID:-traffic-rpw-eos}"
TX_ID="${TASK8_TRANSACTIONAL_ID:-traffic-rpw-eos-fixed-1}"

# --------------------------------------------------
# Projekt-Root finden (pom.xml)
# --------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SS_DIR="$SCRIPT_DIR"

while [[ "$SS_DIR" != "/" && ! -f "$SS_DIR/pom.xml" ]]; do
  SS_DIR="$(cd "$SS_DIR/.." && pwd)"
done

if [[ ! -f "$SS_DIR/pom.xml" ]]; then
  echo "[ERROR] Could not locate pom.xml from $SCRIPT_DIR"
  exit 1
fi

cd "$SS_DIR"

# --------------------------------------------------
# HARDCODED TEST FILE (RELATIV ZUM MAVEN ROOT)
# --------------------------------------------------
TEST_FILE="$SS_DIR/src/main/resources/Trafficdata.txt"

if [[ ! -f "$TEST_FILE" ]]; then
  echo "[ERROR] Test file not found at: $TEST_FILE"
  echo "[ERROR] Current detected project root: $SS_DIR"
  exit 1
fi

LOG_DIR="$SS_DIR/logs/task8"
mkdir -p "$LOG_DIR"

APP_LOG="$LOG_DIR/app.log"
PID_FILE="$LOG_DIR/app.pid"

echo "=============================================="
echo "[task8] Project dir : $SS_DIR"
echo "[task8] Bootstrap   : $BOOTSTRAP"
echo "[task8] Test file   : $TEST_FILE"
echo "[task8] Topics      : IN=$TOPIC_IN OUT=$TOPIC_OUT BAD=$TOPIC_BAD"
echo "=============================================="

# --------------------------------------------------
# Helpers
# --------------------------------------------------
reset_topic () {
  local t="$1"
  echo "[task8] Reset topic $t"
  docker exec -i "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" --delete --topic "$t" >/dev/null 2>&1 || true
  sleep 1
  docker exec -i "$CONTAINER" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$t" --partitions "$PARTITIONS" --replication-factor "$REPLICATION" >/dev/null
}

cmd_start () {
  echo "[task8] Starting app..."

  export KAFKA_BOOTSTRAP="$BOOTSTRAP"
  export TRAFFIC_TOPIC_IN="$TOPIC_IN"
  export TRAFFIC_TOPIC_OUT="$TOPIC_OUT"
  export TRAFFIC_TOPIC_BAD="$TOPIC_BAD"
  export TASK8_GROUP_ID="$GROUP_ID"
  export TASK8_TRANSACTIONAL_ID="$TX_ID"

  : > "$APP_LOG"

  mvn -q exec:java \
    -Dexec.mainClass="com.krassedudes.streaming_systems.traffic.task8.TrafficReadProcessWriteApp" \
    > "$APP_LOG" 2>&1 &

  APP_PID=$!
  echo "$APP_PID" > "$PID_FILE"

  echo "[task8] App started with PID=$APP_PID"
  sleep 2
}

cmd_stop () {
  if [[ -f "$PID_FILE" ]]; then
    PID=$(cat "$PID_FILE")
    echo "[task8] Stopping app $PID"
    kill "$PID" 2>/dev/null || true
    rm -f "$PID_FILE"
  else
    echo "[task8] No PID file"
  fi
}

kill_app_hard () {
  if [[ -f "$PID_FILE" ]]; then
    PID=$(cat "$PID_FILE")
    echo "[task8] HARD KILL of app $PID"
    kill -9 "$PID" 2>/dev/null || true
    rm -f "$PID_FILE"
  fi
}

cmd_feed () {
  echo "[task8] Feeding hardcoded file: $TEST_FILE"

  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "${line// /}" ]] && continue
    printf '%s\n' "$line"
    sleep 0.002
  done < "$TEST_FILE" | docker exec -i "$CONTAINER" \
      /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server "$BOOTSTRAP" \
      --topic "$TOPIC_IN" >/dev/null

  echo "[task8] Feed done."
}

cmd_verify () {
  echo "[task8] Verifying results..."

  PROC_COUNT=$(docker exec -i "$CONTAINER" \
    /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --topic "$TOPIC_OUT" \
    --from-beginning \
    --timeout-ms 5000 2>/dev/null | wc -l)

  BAD_COUNT=$(docker exec -i "$CONTAINER" \
    /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --topic "$TOPIC_BAD" \
    --from-beginning \
    --timeout-ms 5000 2>/dev/null | wc -l)

  echo "=============================================="
  echo "[task8] RESULT COUNTS"
  echo "Processed lines : $PROC_COUNT"
  echo "Bad lines       : $BAD_COUNT"
  echo "=============================================="

  echo "[task8] Check for duplicates..."

  DUP=$(docker exec -i "$CONTAINER" \
    /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --topic "$TOPIC_OUT" \
    --from-beginning \
    --timeout-ms 5000 2>/dev/null \
    | sort | uniq -d | wc -l)

  if [[ "$DUP" -eq 0 ]]; then
    echo "[task8] NO duplicates detected (Exactly-Once OK)"
  else
    echo "[task8] DUPLICATES DETECTED: $DUP"
  fi
}

cmd_eos_test () {
  echo "[task8] Starting EOS test with file: $TEST_FILE"

  reset_topic "$TOPIC_IN"
  reset_topic "$TOPIC_OUT"
  reset_topic "$TOPIC_BAD"

  mvn -q clean package

  cmd_start

  echo "[task8] Feeding file..."
  ( cmd_feed ) &
  FEED_PID=$!

  echo "[task8] Simulating crash..."
  sleep 2
  kill_app_hard

  wait "$FEED_PID" || true

  echo "[task8] Restarting app..."
  cmd_start

  echo "[task8] Waiting for processing..."
  sleep 5

  cmd_verify

  echo "[task8] EOS test finished."
}

# --------------------------------------------------
# CLI
# --------------------------------------------------
case "${1:-}" in
  start)    cmd_start ;;
  stop)     cmd_stop ;;
  feed)     cmd_feed ;;
  verify)   cmd_verify ;;
  eos-test) cmd_eos_test ;;
  *)
    echo "Usage:"
    echo "  $0 start"
    echo "  $0 stop"
    echo "  $0 feed"
    echo "  $0 verify"
    echo "  $0 eos-test"
    exit 1
    ;;
esac