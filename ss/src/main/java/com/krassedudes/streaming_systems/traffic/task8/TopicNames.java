package com.krassedudes.streaming_systems.traffic.task8;

public final class TopicNames {
    private TopicNames() {}

    public static String inputTopic() {
        return System.getenv().getOrDefault("TRAFFIC_TOPIC_IN", "traffic-data");
    }

    public static String outTopic() {
        return System.getenv().getOrDefault("TRAFFIC_TOPIC_OUT", "traffic-processed");
    }

    public static String badTopic() {
        return System.getenv().getOrDefault("TRAFFIC_TOPIC_BAD", "traffic-bad");
    }

    public static String bootstrap() {
        return System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
    }
}