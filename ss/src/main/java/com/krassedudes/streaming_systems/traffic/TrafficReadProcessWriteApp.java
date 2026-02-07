package com.krassedudes.streaming_systems.traffic;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task 8: Read -> Process -> Write (Kafka) mit EOS (Transactions).
 *
 * Input Topic:  traffic-data
 * Output Topic: traffic-processed
 *
 * Erwartetes Input-Format (String):
 *   <timestamp> <sensorId> <speedMs>
 * Beispiel:
 *   2025-10-03T05:22:40.000Z 1 9.8
 *
 * Output-Format (String):
 *   <timestamp> SensorId=<id> SpeedKmh=<value>
 *
 * WICHTIG: Negative Geschwindigkeiten werden verworfen (nicht produziert),
 * aber die Offsets werden trotzdem im selben Transaction-Commit committed.
 */
public class TrafficReadProcessWriteApp {

    private static final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) {

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        String inputTopic = System.getenv().getOrDefault("TRAFFIC_TOPIC_IN", "traffic-data");
        String outputTopic = System.getenv().getOrDefault("TRAFFIC_TOPIC_OUT", "traffic-processed");

        String groupId = System.getenv().getOrDefault("TRAFFIC_GROUP_ID", "traffic-rpw-eos");
        int maxPollRecords = Integer.parseInt(System.getenv().getOrDefault("TRAFFIC_MAX_POLL_RECORDS", "200"));

        // transactional.id muss stabil sein pro App-Instanz; hier eindeutig pro Start.
        // (Für "exakt-einmal" in Prod würde man das bewusst stabil/konfigurierbar machen.)
        String transactionalId = System.getenv().getOrDefault(
                "TRAFFIC_TRANSACTIONAL_ID",
                "traffic-rpw-" + (System.currentTimeMillis() / 1000)
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            System.out.println("Shutdown requested...");
        }));

        Properties consumerProps = buildConsumerProps(bootstrap, groupId, maxPollRecords);
        Properties producerProps = buildProducerProps(bootstrap, transactionalId);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {

            // Init transactions
            producer.initTransactions();

            consumer.subscribe(Collections.singletonList(inputTopic));

            System.out.printf("Starting Task 8 Processor (input=%s, output=%s)%n", inputTopic, outputTopic);

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                if (records.isEmpty()) {
                    continue;
                }

                try {
                    producer.beginTransaction();

                    // Für commitTransaction brauchen wir die Offsets pro Partition
                    Map<TopicPartition, Long> consumedOffsets = new HashMap<>();

                    for (ConsumerRecord<String, String> rec : records) {
                        // Offset merken (immer!), damit wir auch bei verworfenen Events vorankommen
                        consumedOffsets.put(new TopicPartition(rec.topic(), rec.partition()), rec.offset());

                        String line = rec.value();
                        if (line == null || line.isBlank()) {
                            // leer => verwerfen, aber Offset wird committed
                            continue;
                        }

                        String outValue = processLineToOutput(line);
                        String key = extractSensorKey(line);

                        // outValue==null => z.B. negative speed oder parse error => NICHT produzieren
                        if (outValue != null) {
                            ProducerRecord<String, String> outRec =
                                    new ProducerRecord<>(outputTopic, key, outValue);

                            // optional synchron warten (debug/lernzwecke). Für Performance eher async.
                            RecordMetadata md = producer.send(outRec).get();
                        }
                    }

                    // Offsets im Rahmen der Transaction committen (EOS)
                    // Kafka erwartet "next offset", also +1
                    Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsetsToCommit =
                            new HashMap<>();
                    for (Map.Entry<TopicPartition, Long> e : consumedOffsets.entrySet()) {
                        offsetsToCommit.put(e.getKey(),
                                new org.apache.kafka.clients.consumer.OffsetAndMetadata(e.getValue() + 1));
                    }

                    producer.sendOffsetsToTransaction(offsetsToCommit, groupId);
                    producer.commitTransaction();

                } catch (ProducerFencedException fenced) {
                    // transactional.id wird von einer anderen Instanz verwendet
                    System.err.println("Producer fenced (another instance uses same transactional.id). Exiting.");
                    throw fenced;

                } catch (Exception ex) {
                    System.err.println("Error in transaction => aborting. " + ex.getMessage());
                    try {
                        producer.abortTransaction();
                    } catch (Exception ignore) {
                    }
                }
            }

        }
    }

    /**
     * Parse + Filter + Convert.
     *
     * Regel:
     * - speedMs < 0  => return null (verwerfen)
     * - parse error  => return null (verwerfen)
     */
    private static String processLineToOutput(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 3) return null;

            // timestamp kann "2025-...Z" sein
            Instant ts;
            try {
                ts = Instant.parse(parts[0]);
            } catch (DateTimeParseException dtpe) {
                // falls Timestamp ohne Millis o.ä. kommt, trotzdem verwerfen statt BAD-topic
                return null;
            }

            int sensorId = Integer.parseInt(parts[1]);
            double speedMs = Double.parseDouble(parts[2]);

            // >>> HIER IST DER FIX: keine negativen Geschwindigkeiten durchlassen
            if (speedMs < 0) {
                return null;
            }

            double speedKmh = speedMs * 3.6;

            // Format wie bei dir im Output
            return String.format("%s SensorId=%d SpeedKmh=%.2f", ts.toString(), sensorId, speedKmh);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Key = SensorId (als String) für Partitionierung/Grouping.
     */
    private static String extractSensorKey(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2) return null;
            int sensorId = Integer.parseInt(parts[1]);
            return String.valueOf(sensorId);
        } catch (Exception e) {
            return null;
        }
    }

    private static Properties buildConsumerProps(String bootstrap, String groupId, int maxPollRecords) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // Offsets NICHT automatisch committen
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        // Nur committed Messages lesen (Transaktionen)
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));

        return props;
    }

    private static Properties buildProducerProps(String bootstrap, String transactionalId) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // EOS / Idempotence
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));

        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);

        // optional tuning
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

        return props;
    }
}