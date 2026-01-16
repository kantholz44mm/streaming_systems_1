package com.krassedudes.streaming_systems.traffic;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Properties;

public class TrafficDataProducer {

        private static final String TOPIC = "traffic-data";

        public static void main(String[] args) throws Exception {

                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

                Producer<String, String> producer = new KafkaProducer<>(props);

                var is = TrafficDataProducer.class
                                .getClassLoader()
                                .getResourceAsStream("Trafficdata.txt");

                if (is == null) {
                        throw new IllegalStateException(
                                        "Trafficdata.txt not found. It must be in src/main/resources/");
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                String line;
                while ((line = reader.readLine()) != null) {
                        SpeedEvent event = SpeedEventParser.parse(line);

                        ProducerRecord<String, String> record = new ProducerRecord<>(
                                        TOPIC,
                                        null,
                                        event.getTimestamp().toEpochMilli(), // EVENT TIME!
                                        null,
                                        line);

                        producer.send(record);
                }

                producer.flush();
                producer.close();

                System.out.println("TrafficDataProducer finished.");
        }
}