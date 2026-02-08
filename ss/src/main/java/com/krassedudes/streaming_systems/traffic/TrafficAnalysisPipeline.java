package com.krassedudes.streaming_systems.traffic;

import org.apache.avro.generic.GenericData.Fixed;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.*;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.io.kafka.TimestampPolicyFactory;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.windowing.*;
import org.apache.beam.sdk.values.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrafficAnalysisPipeline {
    public void performTrafficAnalysis(String host, String topic, String outputfile) {

        Pipeline pipeline = Pipeline.create();

        Map<String, Object> consumerCfg = new HashMap<>();
        consumerCfg.put("auto.offset.reset", "earliest");
        consumerCfg.put("group.id", "beam-traffic-" + UUID.randomUUID());

        var ioIn = KafkaIO.<String, String>read()
            .withBootstrapServers(host)
            .withTopic(topic)
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializer(StringDeserializer.class)
            .withConsumerConfigUpdates(consumerCfg)
            .withTimestampPolicyFactory(new TrafficTimestampPolicy())
            .withoutMetadata();

        var rawInput         = pipeline.apply(ioIn);
        var parsedEvents     = rawInput.apply(ParDo.of(new CustomTransforms.SpeedEventParser()));
        var validEvents      = parsedEvents.apply(Filter.by(e -> e.speedMs() >= 0));
        var windowedEvents   = validEvents.apply(Window.<SpeedEvent>into(FixedWindows.of(Duration.standardSeconds(10))).discardingFiredPanes());
        var sensorSpeeds     = windowedEvents.apply(ParDo.of(new CustomTransforms.ExtractSensorSpeedFn()));
        var averageSpeeds    = sensorSpeeds.apply(Combine.perKey(Mean.<Double>of()));
        var averageSpeedsKmh = averageSpeeds.apply(ParDo.of(new CustomTransforms.MetersPerSecondToKilometersPerHour()));
        var formatted        = averageSpeedsKmh.apply(ParDo.of(new CustomTransforms.FormatForOutput()));

        // debugging
        // parsedEvents.apply(ParDo.of(new CustomTransforms.LogSpeedEvent()));

        var ioOut = TextIO.write()
            .to(outputfile)
            .withNumShards(1)
            .withSuffix(".txt")
            .withWindowedWrites();

        formatted.apply(ioOut);

        pipeline.run().waitUntilFinish();
    }
}