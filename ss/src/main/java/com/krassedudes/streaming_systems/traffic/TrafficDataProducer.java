package com.krassedudes.streaming_systems.traffic;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Properties;

public class TrafficDataProducer {
    public static void readAndPublishTrafficData(String filename, String topic, String host) throws Exception {

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        Producer<String, String> producer = new KafkaProducer<>(props);

        FileReader stream = new FileReader(filename);
        BufferedReader reader = new BufferedReader(stream);

        reader.lines().forEach(line -> {
            String[] parts = line.split("\\s+");
            long timestamp = java.time.Instant.parse(parts[0]).toEpochMilli();
            producer.send(new ProducerRecord<>(topic, null, timestamp, null, line));
        });
        reader.close();
        producer.flush();
        producer.close();

        System.out.println("Published all trafficdata entries");
    }
}