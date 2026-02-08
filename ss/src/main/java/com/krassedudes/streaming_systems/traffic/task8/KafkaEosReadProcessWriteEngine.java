package com.krassedudes.streaming_systems.traffic.task8;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KafkaEosReadProcessWriteEngine {

    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> producer;
    private final String inputTopic;
    private final String outTopic;
    private final String badTopic;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public KafkaEosReadProcessWriteEngine(
            String bootstrap,
            String inputTopic,
            String outTopic,
            String badTopic,
            String groupId,
            String transactionalId
    ) {
        this.inputTopic = inputTopic;
        this.outTopic = outTopic;
        this.badTopic = badTopic;

        Properties cProps = new Properties();
        cProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        cProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        cProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        cProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        cProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        cProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        cProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");

        this.consumer = new KafkaConsumer<>(cProps);

        Properties pProps = new Properties();
        pProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        pProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        pProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        pProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        pProps.put(ProducerConfig.ACKS_CONFIG, "all");
        pProps.put(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
        pProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        pProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);

        this.producer = new KafkaProducer<>(pProps);
    }

    public void start(TrafficProcessor processor, long failAfter) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        System.out.println("[task8] initTransactions()");
        producer.initTransactions();

        System.out.println("[task8] subscribe(" + inputTopic + ")");
        consumer.subscribe(Collections.singletonList(inputTopic));

        long processedTotal = 0;

        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) continue;

                try {
                    producer.beginTransaction();

                    long okCount = 0;
                    long badCount = 0;

                    for (ConsumerRecord<String, String> r : records) {
                        final String mid = "MID=" + r.topic() + "-" + r.partition() + "-" + r.offset();
                        final ProcessingResult res = processor.process(r.value());

                        if (res.isOk()) {
                            final String out = mid + " " + res.outValue().orElseThrow();
                            producer.send(new ProducerRecord<>(outTopic, null, r.timestamp(), null, out));
                            okCount++;
                        } else {
                            final String bad = mid + " " + res.badValue().orElseThrow();
                            producer.send(new ProducerRecord<>(badTopic, null, r.timestamp(), null, bad));
                            badCount++;
                        }

                        processedTotal++;

                        if (failAfter > 0 && processedTotal >= failAfter) {
                            System.out.println("[task8] FAIL_AFTER reached (" + failAfter + ") -> crashing BEFORE commitTransaction()");
                            throw new RuntimeException("Intentional crash for EOS test (FAIL_AFTER=" + failAfter + ")");
                        }
                    }

                    Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                    for (TopicPartition tp : records.partitions()) {
                        List<ConsumerRecord<String, String>> tpRecords = records.records(tp);
                        long lastOffset = tpRecords.get(tpRecords.size() - 1).offset();
                        offsets.put(tp, new OffsetAndMetadata(lastOffset + 1));
                    }

                    producer.sendOffsetsToTransaction(offsets, consumer.groupMetadata());
                    producer.commitTransaction();

                    System.out.printf("[task8] committed tx: records=%d out=%d bad=%d partitions=%d%n",
                            records.count(), okCount, badCount, records.partitions().size());

                } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException fatal) {
                    System.err.println("[task8][FATAL] " + fatal.getClass().getSimpleName() + " -> stopping");
                    running.set(false);

                } catch (Exception ex) {
                    System.err.println("[task8][WARN] abortTransaction due to error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    try {
                        producer.abortTransaction();
                    } catch (Exception abortEx) {
                        System.err.println("[task8][WARN] abortTransaction failed: " + abortEx.getMessage());
                    }

                    if (ex.getMessage() != null && ex.getMessage().contains("Intentional crash")) {
                        System.exit(2);
                    }
                }
            }
        } catch (WakeupException we) {
            // expected on shutdown
        } finally {
            close();
        }
    }

    public void stop() {
        System.out.println("[task8] stopping...");
        running.set(false);
    }

    private void close() {
        System.out.println("[task8] closing kafka clients...");
        try { consumer.wakeup(); } catch (Exception ignored) {}
        try { consumer.close(); } catch (Exception ignored) {}
        try { producer.close(); } catch (Exception ignored) {}
        System.out.println("[task8] stopped.");
    }
}
