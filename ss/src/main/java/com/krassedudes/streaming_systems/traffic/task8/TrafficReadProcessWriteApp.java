package com.krassedudes.streaming_systems.traffic.task8;

import java.util.Objects;

/**
 * Task 8: Read-Process-Write (Exactly-Once) with Kafka Transactions
 *
 * NOTE: No domain/business logic here.
 * - We treat each input record as an opaque String line.
 * - "Process" step only adds a deterministic MID (= topic-partition-offset).
 * - Empty/blank lines are routed to BAD.
 *
 * Exactly-once is ensured by:
 * - transactional producer (idempotence + transactions)
 * - consumer isolation.level=read_committed
 * - offsets committed via sendOffsetsToTransaction(...)
 */
public class TrafficReadProcessWriteApp {

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public static void main(String[] args) {
        final String bootstrap = env("KAFKA_BOOTSTRAP", "localhost:9092");
        final String inTopic   = env("TRAFFIC_TOPIC_IN", "traffic-data");
        final String outTopic  = env("TRAFFIC_TOPIC_OUT", "traffic-processed");
        final String badTopic  = env("TRAFFIC_TOPIC_BAD", "traffic-bad");

        final String groupId = env("TASK8_GROUP_ID", "traffic-rpw-eos");

        // MUST be stable across restarts for EOS.
        final String transactionalId = env("TASK8_TRANSACTIONAL_ID", "traffic-rpw-eos-fixed");

        // Crash simulation (0 disables): crash after N processed records
        final long failAfter = Long.parseLong(env("TASK8_FAIL_AFTER", "0"));

        System.out.println("[task8] bootstrap=" + bootstrap);
        System.out.println("[task8] inTopic=" + inTopic);
        System.out.println("[task8] outTopic=" + outTopic);
        System.out.println("[task8] badTopic=" + badTopic);
        System.out.println("[task8] groupId=" + groupId);
        System.out.println("[task8] transactionalId=" + transactionalId);
        System.out.println("[task8] failAfter=" + failAfter);

        Objects.requireNonNull(bootstrap, "bootstrap");
        Objects.requireNonNull(inTopic, "inTopic");
        Objects.requireNonNull(outTopic, "outTopic");
        Objects.requireNonNull(badTopic, "badTopic");

        final TrafficProcessor processor = new TrafficProcessor();
        final KafkaEosReadProcessWriteEngine engine = new KafkaEosReadProcessWriteEngine(
                bootstrap,
                inTopic,
                outTopic,
                badTopic,
                groupId,
                transactionalId
        );

        engine.start(processor, failAfter);
    }
}
