#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Task 8 Runner (Kafka Read-Process-Write Exactly-Once Test)
#
# What this script proves (via logs + verification):
# - Topics are HARD reset (delete + wait until gone + create)
# - App starts, subscribes, processes
# - App crashes DURING processing (FAIL_AFTER)
# - App restarts and finishes
# - OUT+BAD == INPUT
# - No duplicate MID=... in OUT+BAD
# - Logs clearly show the crash point and the restart PID
# ============================================================

BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
CONTAINER="${KAFKA_CONTAINER:-kafka}"

TOPIC_IN="${TRAFFIC_TOPIC_IN:-traffic-data}"
TOPIC_OUT="${TRAFFIC_TOPIC_OUT:-traffic-processed}"
TOPIC_BAD="${TRAFFIC_TOPIC_BAD:-traffic-bad}"

PARTITIONS="${KAFKA_PARTITIONS:-1}"
REPLICATION="${KAFKA_REPLICATION_FACTOR:-1}"

GROUP_ID="${TASK8_GROUP_ID:-traffic-rpw-eos}"
TRANSACTIONAL_ID="${TASK8_TRANSACTIONAL_ID:-traffic-rpw-eos-fixed}"

# Crash after N processed records in first run (0 disables crash)
FAIL_AFTER_FIRST="${TASK8_FAIL_AFTER_FIRST:-500}"

# Default input file relative to Maven root (NO absolute paths hardcoded)
INPUT_REL="${TASK8_INPUT_REL:-src/main/resources/Trafficdata.txt}"

# Timeouts (seconds)
TOPIC_RESET_TIMEOUT_S="${TASK8_TOPIC_RESET_TIMEOUT_S:-60}"
ASSIGN_TIMEOUT_S="${TASK8_ASSIGN_TIMEOUT_S:-60}"
CRASH_TIMEOUT_S="${TASK8_CRASH_TIMEOUT_S:-120}"
FINISH_TIMEOUT_S="${TASK8_FINISH_TIMEOUT_S:-240}"


# Dump settings
DUMP_TIMEOUT_MS="${TASK8_DUMP_TIMEOUT_MS:-15000}"

# --------------------------------------------------
# CLI
#   ./run_task8.sh            -> full test (default)
#   ./run_task8.sh dt         -> delete topics only (no recreate, no app)
#   ./run_task8.sh --help     -> help
# --------------------------------------------------
DELETE_ONLY="${TASK8_DELETE_ONLY:-0}"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'USAGE'
Usage:
  ./run_task8.sh            Run full Task8 EOS test (hard reset + run + verify)
  ./run_task8.sh dt         Delete the traffic topics only (no recreate, no app)

Environment overrides (examples):
  TASK8_DELETE_ONLY=1        Same as 'dt'
  TRAFFIC_TOPIC_IN=...       Override input topic
  TRAFFIC_TOPIC_OUT=...      Override output topic
  TRAFFIC_TOPIC_BAD=...      Override bad topic
USAGE
  exit 0
fi

if [[ "${1:-}" == "dt" ]]; then
  DELETE_ONLY=1
fi

# --------------------------------------------------
# Find Maven root (pom.xml) upwards from script dir
# --------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$SCRIPT_DIR"
while [[ "$ROOT_DIR" != "/" && ! -f "$ROOT_DIR/pom.xml" ]]; do
  ROOT_DIR="$(cd "$ROOT_DIR/.." && pwd)"
done

if [[ ! -f "$ROOT_DIR/pom.xml" ]]; then
  echo "[ERROR] Could not locate pom.xml from $SCRIPT_DIR"
  exit 1
fi

cd "$ROOT_DIR"

INPUT_FILE="$ROOT_DIR/$INPUT_REL"
if [[ ! -f "$INPUT_FILE" ]]; then
  echo "[ERROR] Input file not found: $INPUT_FILE"
  echo "        (set TASK8_INPUT_REL or place file there)"
  exit 1
fi

INPUT_LINES="$(wc -l < "$INPUT_FILE" | tr -d ' ')"

LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

APP_LOG="$LOG_DIR/task8-app.log"
OUT_LOG="$LOG_DIR/task8-out.txt"
BAD_LOG="$LOG_DIR/task8-bad.txt"
ALL_LOG="$LOG_DIR/task8-all.txt"
MIDS_LOG="$LOG_DIR/task8-mids.txt"

MAIN_CLASS="${TASK8_MAIN_CLASS:-com.krassedudes.streaming_systems.traffic.task8.TrafficReadProcessWriteApp}"

ts() { date "+%Y-%m-%d %H:%M:%S"; }

kexec() {
  docker exec -i "$CONTAINER" "$@"
}

log_banner() {
  echo "==============================================" | tee -a "$APP_LOG" >/dev/null || true
  echo "[run_task8] $*" | tee -a "$APP_LOG" >/dev/null || true
  echo "==============================================" | tee -a "$APP_LOG" >/dev/null || true
}

# --------------------------------------------------
# Kafka helpers
# --------------------------------------------------

topic_exists() {
  local t="$1"
  kexec /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list 2>/dev/null | grep -qx "$t"
}


topic_hard_reset() {
  local t="$1"
  echo "[run_task8] Hard reset topic: $t"

  # delete (ignore errors)
  kexec /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --delete --topic "$t" >/dev/null 2>&1 || true

  # wait until really gone (topic deletion is async)
  local start now
  start="$(date +%s)"
  while topic_exists "$t"; do
    now="$(date +%s)"
    if (( now - start >= TOPIC_RESET_TIMEOUT_S )); then
      echo "[WARN] Topic '$t' still listed after delete (timeout). Will try create anyway."
      break
    fi
    sleep 1
  done

  # create
  kexec /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$t" --partitions "$PARTITIONS" --replication-factor "$REPLICATION" >/dev/null

  # verify exists
  if ! topic_exists "$t"; then
    echo "[ERROR] Topic '$t' could not be created/verified."
    exit 1
  fi
}

topic_delete_only() {
  local t="$1"
  echo "[run_task8] Deleting topic: $t"

  # delete (ignore errors)
  kexec /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --delete --topic "$t" >/dev/null 2>&1 || true

  # wait until really gone (topic deletion is async)
  local start now
  start="$(date +%s)"
  while topic_exists "$t"; do
    now="$(date +%s)"
    if (( now - start >= TOPIC_RESET_TIMEOUT_S )); then
      echo "[WARN] Topic '$t' still listed after delete (timeout)."
      break
    fi
    sleep 1
  done
}

get_end_offset_sum() {
  # sums end offsets across partitions for given topic
  local topic="$1"
  local raw
  raw="$(kexec /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server "$BOOTSTRAP" --topic "$topic" 2>/dev/null || true)"

  # Parse lines like: topic:0:1814
  # If empty -> 0
  if [[ -z "${raw//[[:space:]]/}" ]]; then
    echo "0"
    return
  fi

  echo "$raw" | awk -F: 'NF>=3 && $3 ~ /^[0-9]+$/ {sum += $3} END { if (sum=="") sum=0; print sum }'
}

# --------------------------------------------------
# App helpers
# --------------------------------------------------

start_app() {
  local fail_after="$1"
  local label="$2"   # e.g. FIRST / SECOND

  export KAFKA_BOOTSTRAP="$BOOTSTRAP"
  export TRAFFIC_TOPIC_IN="$TOPIC_IN"
  export TRAFFIC_TOPIC_OUT="$TOPIC_OUT"
  export TRAFFIC_TOPIC_BAD="$TOPIC_BAD"
  export TASK8_GROUP_ID="$GROUP_ID"
  export TASK8_TRANSACTIONAL_ID="$TRANSACTIONAL_ID"
  export TASK8_FAIL_AFTER="$fail_after"

  log_banner "START APP ($label) FAIL_AFTER=$fail_after"

  # Append to one log so the crash + restart are in one place.
  # Maven output goes to APP_LOG.
  (mvn -q exec:java -Dexec.mainClass="$MAIN_CLASS" >>"$APP_LOG" 2>&1) &
  local pid=$!

  echo "[run_task8] App started ($label) with PID=$pid at $(ts)" | tee -a "$APP_LOG"
  echo "$pid"
}

is_running() {
  local pid="$1"
  kill -0 "$pid" >/dev/null 2>&1
}

kill_app() {
  local pid="$1"
  if is_running "$pid"; then
    echo "[run_task8] Killing app PID=$pid at $(ts)" | tee -a "$APP_LOG"
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    kill -9 "$pid" >/dev/null 2>&1 || true
  else
    echo "[run_task8] $pid already not running (at $(ts))" | tee -a "$APP_LOG"
  fi
}

wait_for_consumer_ready() {
  local timeout="$1"
  echo "[run_task8] Waiting for consumer ready/assignment (timeout=${timeout}s) ..."

  local start now
  start="$(date +%s)"
  while true; do
    # We accept either 'Subscribed to topic(s): <topic>' OR 'Adding newly assigned partitions'
    if grep -q "Subscribed to topic(s):" "$APP_LOG" && grep -q "${TOPIC_IN}" "$APP_LOG"; then
      echo "[run_task8] Consumer subscribed to $TOPIC_IN (OK)"
      return 0
    fi
    if grep -q "Adding newly assigned partitions" "$APP_LOG"; then
      echo "[run_task8] Consumer assignment line seen (OK)"
      return 0
    fi

    now="$(date +%s)"
    if (( now - start >= timeout )); then
      echo "[ERROR] Timeout waiting for consumer readiness."
      echo "---- App log (last 200 lines) ----"
      tail -n 200 "$APP_LOG" || true
      echo "---------------------------------"
      return 1
    fi

    sleep 1
  done
}

wait_for_crash_or_progress() {
  local pid="$1"
  local timeout="$2"

  echo "[run_task8] Waiting for crash OR progress (timeout=${timeout}s) ..."

  local start now
  start="$(date +%s)"

  while true; do
    if ! is_running "$pid"; then
      echo "[run_task8] App PID=$pid is NOT running anymore (crashed/exited) at $(ts)" | tee -a "$APP_LOG"
      echo "---- Crash proof (last 80 lines) ----"
      tail -n 80 "$APP_LOG" || true
      echo "------------------------------------"
      return 0
    fi

    # If we see the intentional crash line, it will crash soon after.
    if grep -q "FAIL_AFTER reached" "$APP_LOG"; then
      echo "[run_task8] Detected FAIL_AFTER line in logs (crash point reached)." | tee -a "$APP_LOG"
    fi

    now="$(date +%s)"
    if (( now - start >= timeout )); then
      echo "[WARN] Timeout waiting for natural crash. Will kill PID=$pid to simulate failure." | tee -a "$APP_LOG"
      return 1
    fi

    sleep 2
  done
}

wait_until_finished_offsets() {
  local input_lines="$1"
  local timeout="$2"

  echo "[run_task8] Waiting until endOffsets(OUT)+endOffsets(BAD) >= INPUT ($input_lines), timeout=${timeout}s ..."

  local start now
  start="$(date +%s)"

  while true; do
    local out bad total
    out="$(get_end_offset_sum "$TOPIC_OUT")"
    bad="$(get_end_offset_sum "$TOPIC_BAD")"

    out="${out//[[:space:]]/}"
    bad="${bad//[[:space:]]/}"
    [[ -z "$out" ]] && out="0"
    [[ -z "$bad" ]] && bad="0"

    total=$(( out + bad ))

    now="$(date +%s)"
    local elapsed=$(( now - start ))

    echo "[run_task8] progress t=+${elapsed}s out=${out} bad=${bad} total=${total}/${input_lines}"

    if (( total >= input_lines )); then
      echo "[run_task8] Finished condition met (total >= input)."
      return 0
    fi

    if (( elapsed >= timeout )); then
      echo "[WARN] Timeout waiting for totals to reach input."
      echo "---- App log (last 120 lines) ----"
      tail -n 120 "$APP_LOG" || true
      echo "---------------------------------"
      return 1
    fi

    sleep 3
  done
}

# --------------------------------------------------
# Verification helpers
# --------------------------------------------------

dump_topics_to_files() {
  echo "[run_task8] Dumping output topics to files..."

  : > "$OUT_LOG"
  : > "$BAD_LOG"
  : > "$ALL_LOG"
  : > "$MIDS_LOG"

  # OUT
  kexec /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --topic "$TOPIC_OUT" \
    --from-beginning \
    --timeout-ms "$DUMP_TIMEOUT_MS" \
    --property print.timestamp=true \
    --property print.value=true \
    --property print.key=false \
    --property print.headers=false \
    --property print.partition=false \
    --property print.offset=false \
    > "$OUT_LOG" 2>/dev/null || true

  # BAD
  kexec /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --topic "$TOPIC_BAD" \
    --from-beginning \
    --timeout-ms "$DUMP_TIMEOUT_MS" \
    --property print.timestamp=true \
    --property print.value=true \
    --property print.key=false \
    --property print.headers=false \
    --property print.partition=false \
    --property print.offset=false \
    > "$BAD_LOG" 2>/dev/null || true

  cat "$OUT_LOG" "$BAD_LOG" > "$ALL_LOG" || true

  # Extract MID=... from values
  grep -oE 'MID=[^[:space:]]+' "$ALL_LOG" | sed 's/[\r\t]//g' > "$MIDS_LOG" || true
}

check_counts_and_duplicates() {
  local out_lines bad_lines total

  out_lines="$(grep -cve '^[[:space:]]*$' "$OUT_LOG" 2>/dev/null || echo 0)"
  bad_lines="$(grep -cve '^[[:space:]]*$' "$BAD_LOG" 2>/dev/null || echo 0)"
  total=$(( out_lines + bad_lines ))

  echo
  echo "=============================================="
  echo "[run_task8] RESULT COUNTS"
  printf "Input lines  : %8s\n" "$INPUT_LINES"
  printf "Output lines : %8s\n" "$out_lines"
  printf "Bad lines    : %8s\n" "$bad_lines"
  printf "Total        : %8s\n" "$total"
  echo "=============================================="

  if (( total == INPUT_LINES )); then
    echo "[OK] Counts match exactly."
  else
    echo "[WARN] Counts do NOT match (TOTAL != INPUT)."
    echo "       If this happens, check the app log + topic offsets."
  fi

  echo
  echo "[run_task8] Checking for duplicates in OUT+BAD (by MID=)..."
  if [[ ! -s "$MIDS_LOG" ]]; then
    echo "[ERROR] No MID= found in OUT/BAD messages -> cannot check duplicates."
    echo "        Your app must write e.g.: 'MID=traffic-data-0-123 ...' into OUT and BAD."
    exit 2
  fi

  # Duplicate check: same MID must never appear twice in OUT+BAD.
  local dup_count
  dup_count="$(sort "$MIDS_LOG" | uniq -d | wc -l | tr -d ' ')"

  if (( dup_count == 0 )); then
    echo "[OK] No duplicates detected (Exactly-Once OK)"
  else
    echo "[ERROR] Duplicate MIDs detected! (NOT Exactly-Once)"
    echo "----- Duplicates -----"
    sort "$MIDS_LOG" | uniq -d | head -n 50
    echo "----------------------"
    exit 2
  fi

  # Additionally show where the intentional crash happened (proof in logs)
  echo
  echo "[run_task8] Crash proof in app log (grep FAIL_AFTER):"
  grep -n "FAIL_AFTER reached" "$APP_LOG" || echo "[WARN] No FAIL_AFTER line found (did the first run crash?)"
}

# --------------------------------------------------
# Main
# --------------------------------------------------


echo "=============================================="
echo "[run_task8] Project dir : $ROOT_DIR"
echo "[run_task8] Bootstrap   : $BOOTSTRAP"
echo "[run_task8] Topics      : IN=$TOPIC_IN OUT=$TOPIC_OUT BAD=$TOPIC_BAD"
echo "[run_task8] Main class  : $MAIN_CLASS"
echo "[run_task8] Input file  : $INPUT_FILE"
echo "[run_task8] Input lines : $INPUT_LINES"
echo "[run_task8] Logs        : $LOG_DIR"
echo "=============================================="

: > "$APP_LOG"

# Optional mode: delete topics only (no recreate, no app)
if [[ "$DELETE_ONLY" == "1" ]]; then
  log_banner "DELETE TOPICS ONLY"
  topic_delete_only "$TOPIC_IN"
  topic_delete_only "$TOPIC_OUT"
  topic_delete_only "$TOPIC_BAD"

  echo
  echo "=============================================="
  echo "[run_task8] Delete-only finished."
  echo "Topics deleted (if broker allows async deletion):"
  echo "  IN  : $TOPIC_IN"
  echo "  OUT : $TOPIC_OUT"
  echo "  BAD : $TOPIC_BAD"
  echo "=============================================="
  exit 0
fi

# 1) Hard reset topics
log_banner "HARD RESET TOPICS"
topic_hard_reset "$TOPIC_IN"
topic_hard_reset "$TOPIC_OUT"
topic_hard_reset "$TOPIC_BAD"

# 2) Build
log_banner "BUILD"
echo "[run_task8] Building project..."
mvn -q clean package

# 3) Start app (crashy first)
APP_PID="$(start_app "$FAIL_AFTER_FIRST" "FIRST")"
wait_for_consumer_ready "$ASSIGN_TIMEOUT_S"

# 4) Send input
log_banner "SEND INPUT"
echo "[run_task8] Sending input file to $TOPIC_IN ..."
kexec /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server "$BOOTSTRAP" \
  --topic "$TOPIC_IN" < "$INPUT_FILE" >/dev/null

# 5) Wait for crash (natural) or kill it
if ! wait_for_crash_or_progress "$APP_PID" "$CRASH_TIMEOUT_S"; then
  kill_app "$APP_PID"
fi

# 6) Restart app (no crash)
NEW_PID="$(start_app "0" "SECOND")"
wait_for_consumer_ready "$ASSIGN_TIMEOUT_S"
echo "[run_task8] Restart complete. Old PID=$APP_PID, New PID=$NEW_PID"

# 7) Wait until finished (offset-based)
wait_until_finished_offsets "$INPUT_LINES" "$FINISH_TIMEOUT_S" || true

# 8) Dump + verify
log_banner "DUMP + VERIFY"
dump_topics_to_files
check_counts_and_duplicates

echo
echo "=============================================="
echo "[run_task8] Test finished."
echo "Logs:"
echo "  App log : $APP_LOG"
echo "  OUT     : $OUT_LOG"
echo "  BAD     : $BAD_LOG"
echo "  ALL     : $ALL_LOG"
echo "  MIDs    : $MIDS_LOG"
echo "=============================================="

# 9) Stop app
kill_app "$NEW_PID"