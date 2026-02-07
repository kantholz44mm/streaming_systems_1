#!/bin/bash
set -e

# ===== CONFIG =====
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
INPUT_TOPIC="${INPUT_TOPIC:-traffic-data}"
OUTPUT_TOPIC="${OUTPUT_TOPIC:-traffic-processed}"
GROUP_ID="${GROUP_ID:-traffic-rpw-eos}"

MAIN_CLASS="com.krassedudes.streaming_systems.traffic.TrafficReadProcessWriteApp"

# ===== Helpers =====
# Wir sind in: ss/src/main/java/.../traffic
# Projektroot ist: ss  (da liegt pom.xml)
PROJECT_ROOT="$(cd "$(dirname "$0")/../../../../../../.." && pwd)"

ensure_docker_running() {
  if ! docker ps --format "{{.Names}}" | grep -q "^kafka$"; then
    echo "Kafka container not running – starting via docker compose..."
    # compose file liegt im Repo-Root (eine Ebene über ss)
    (cd "$PROJECT_ROOT/.." && docker compose up -d kafka)
    sleep 8
  fi
}

wait_for_kafka_ready() {
  echo "Waiting for Kafka to become ready..."
  for i in {1..20}; do
    if docker exec kafka bash -lc "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list" >/dev/null 2>&1; then
      echo "Kafka is ready."
      return 0
    fi
    echo "Kafka not ready yet... retry ($i/20)"
    sleep 2
  done
  echo "ERROR: Kafka not ready after waiting." >&2
  exit 1
}

create_topic_if_missing() {
  local topic="$1"
  echo "Checking topic $topic ..."
  wait_for_kafka_ready

  local existing
  existing="$(docker exec kafka bash -lc "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list" | tr -d '\r')"

  if echo "$existing" | grep -w "$topic" >/dev/null 2>&1; then
    echo "Topic $topic already exists."
  else
    echo "Creating topic $topic ..."
    docker exec kafka bash -lc "/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --topic $topic --partitions 3 --replication-factor 1"
  fi
}

build_project() {
  echo "Building project (ss/pom.xml)..."
  (cd "$PROJECT_ROOT" && mvn -q clean package -DskipTests)
}

run_processor() {
  echo "Starting Task 8 Processor (via mvn exec:java)..."
  export KAFKA_BOOTSTRAP="$BOOTSTRAP"
  export INPUT_TOPIC="$INPUT_TOPIC"
  export OUTPUT_TOPIC="$OUTPUT_TOPIC"
  export GROUP_ID="$GROUP_ID"
  export TX_ID="traffic-rpw-$(date +%s)"
  unset FAIL_AFTER

  (cd "$PROJECT_ROOT" && mvn -q -DskipTests compile exec:java \
    -Dexec.mainClass="$MAIN_CLASS" \
    -Dexec.classpathScope=runtime)
}

run_failtest() {
  local n="$1"
  echo "Starting FAIL_AFTER=$n (simulate crash)..."
  export KAFKA_BOOTSTRAP="$BOOTSTRAP"
  export INPUT_TOPIC="$INPUT_TOPIC"
  export OUTPUT_TOPIC="$OUTPUT_TOPIC"
  export GROUP_ID="$GROUP_ID"
  export TX_ID="traffic-rpw-$(date +%s)"
  export FAIL_AFTER="$n"

  (cd "$PROJECT_ROOT" && mvn -q -DskipTests compile exec:java \
    -Dexec.mainClass="$MAIN_CLASS" \
    -Dexec.classpathScope=runtime)
}

consume_output() {
  wait_for_kafka_ready
  echo "Consuming $OUTPUT_TOPIC (Ctrl+C to stop)..."
  docker exec kafka bash -lc "/opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:9092 \
    --topic $OUTPUT_TOPIC \
    --from-beginning \
    --isolation-level read_committed"
}

usage() {
  echo ""
  echo "Usage: $0 [command]"
  echo ""
  echo "Commands:"
  echo "  build          - mvn clean package (in ./ss)"
  echo "  prepare        - start docker kafka + create output topic"
  echo "  run            - start processor"
  echo "  failtest N     - crash after N records (EOS proof)"
  echo "  consume        - read output topic"
  echo "  all            - build + prepare + run"
  echo ""
  exit 1
}

# ===== MAIN =====
case "${1:-}" in
  build)
    build_project
    ;;
  prepare)
    ensure_docker_running
    create_topic_if_missing "$OUTPUT_TOPIC"
    ;;
  run)
    ensure_docker_running
    create_topic_if_missing "$OUTPUT_TOPIC"
    run_processor
    ;;
  failtest)
    if [[ -z "${2:-}" ]]; then
      echo "Please provide N for failtest." >&2
      exit 1
    fi
    ensure_docker_running
    create_topic_if_missing "$OUTPUT_TOPIC"
    run_failtest "$2"
    ;;
  consume)
    ensure_docker_running
    consume_output
    ;;
  all)
    build_project
    ensure_docker_running
    create_topic_if_missing "$OUTPUT_TOPIC"
    run_processor
    ;;
  *)
    usage
    ;;
esac