package com.krassedudes.streaming_systems;

import com.krassedudes.streaming_systems.models.*;

import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

public class Publisher implements AutoCloseable {
    private final KafkaProducer<String, String> producer;
    private final String topic;

    /**
     * @param host e.g. "localhost:9092"
     * @param topic Kafka topic
     */
    public Publisher(String host, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrap(host));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        this.producer = new KafkaProducer<>(props);
    }

    private static String normalizeBootstrap(String s) {
        if (s == null) return App.SERVER_HOST;
        return s.replace("tcp://", "").replace("kafka://", "");
    }

    public void publish(String payload) {
        ProducerRecord<String,String> record = new ProducerRecord<>(topic, payload);
        producer.send(record, (RecordMetadata md, Exception ex) -> {
            if (ex != null) {
                System.err.println("Kafka publish failed: " + ex.getMessage());
            }
        });
    }

    @Override
    public void close() {
        try {
            producer.flush();
        } finally {
            producer.close();
        }
    }
}
