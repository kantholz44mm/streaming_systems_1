package com.krassedudes.streaming_systems.traffic;

import org.apache.beam.sdk.io.kafka.KafkaRecord;
import org.apache.beam.sdk.io.kafka.TimestampPolicy;
import org.apache.beam.sdk.io.kafka.TimestampPolicyFactory;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.joda.time.Instant;
import org.apache.kafka.common.TopicPartition;
import java.util.Optional;

public class TrafficTimestampPolicy implements TimestampPolicyFactory<String, String> {

    public TimestampPolicy<String, String> createTimestampPolicy(TopicPartition tp, Optional<Instant> previousWatermark) {
        
        return new TimestampPolicy<String, String>() {
            private Instant lastSeen = previousWatermark.orElse(BoundedWindow.TIMESTAMP_MIN_VALUE);

            @Override
            public Instant getTimestampForRecord(PartitionContext ctx, KafkaRecord<String, String> record) {
                lastSeen = new Instant(record.getTimestamp());
                return lastSeen;
            }

            @Override
            public Instant getWatermark(PartitionContext ctx) {
                return lastSeen;
            }
        };
    }
}