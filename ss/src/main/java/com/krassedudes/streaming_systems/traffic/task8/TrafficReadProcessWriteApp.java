package com.krassedudes.streaming_systems.traffic.task8;

import java.util.UUID;

public class TrafficReadProcessWriteApp {

    public static void main(String[] args) {
        String bootstrap = TopicNames.bootstrap();

        String inTopic = TopicNames.inputTopic();
        String outTopic = TopicNames.outTopic();
        String badTopic = TopicNames.badTopic();

        String groupId = System.getenv().getOrDefault("TASK8_GROUP_ID", "traffic-rpw-eos");

        String transactionalId = System.getenv().getOrDefault("TASK8_TRANSACTIONAL_ID",
                "traffic-rpw-" + UUID.randomUUID());

        System.out.println("[task8] bootstrap=" + bootstrap);
        System.out.println("[task8] inTopic=" + inTopic);
        System.out.println("[task8] outTopic=" + outTopic);
        System.out.println("[task8] badTopic=" + badTopic);
        System.out.println("[task8] groupId=" + groupId);
        System.out.println("[task8] transactionalId=" + transactionalId);

        TrafficProcessor processor = new TrafficProcessor();

        KafkaEosReadProcessWriteEngine engine = new KafkaEosReadProcessWriteEngine(
                bootstrap, inTopic, outTopic, badTopic, groupId, transactionalId
        );

        engine.start(processor);
    }
}