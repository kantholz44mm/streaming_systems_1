package com.krassedudes.streaming_systems;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.krassedudes.streaming_systems.cqrs.commands.VehicleCommand;
import com.krassedudes.streaming_systems.models.VehicleInfo;

public class Consumer implements AutoCloseable {
    private final KafkaConsumer<String, String> consumer;
    private final Thread poller;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final java.util.function.Consumer<String> callback;

    /**
     * @param host e.g. "localhost:9092"
     * @param topic Kafka topic to subscribe
     * @param callback java.util.function.Consumer that handles each message value
     */
    public Consumer(String host, String topic, java.util.function.Consumer<String> callback) {
        this.callback = callback;
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrap(host));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ss-" + topic + "-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));

        this.poller = new Thread(() -> {
            try {
                while (running.get()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    records.forEach(r -> {
                        try {
                            callback.accept(r.value());
                        } catch (Exception e) {
                            // swallow exceptions in handler to keep polling
                        }
                    });
                    consumer.commitAsync();
                }
            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("Kafka poller error: " + e.getMessage());
                }
            } finally {
                try { consumer.commitSync(); } catch (Exception ignore) {}
                consumer.close();
            }
        }, "kafka-consumer-" + topic);
        this.poller.setDaemon(true);
        this.poller.start();
    }

    private static String normalizeBootstrap(String s) {
        if (s == null) return App.SERVER_HOST;
        return s.replace("tcp://", "").replace("kafka://", "");
    }

    @Override
    public void close() {
        running.set(false);
        try {
            poller.join(1500);
        } catch (InterruptedException ignored) {}
    }
}
