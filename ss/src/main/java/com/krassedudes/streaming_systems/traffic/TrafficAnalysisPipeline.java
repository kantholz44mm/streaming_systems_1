package com.krassedudes.streaming_systems.traffic;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.*;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.windowing.*;
import org.apache.beam.sdk.values.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.joda.time.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrafficAnalysisPipeline {

    private static final TupleTag<SpeedEvent> GOOD = new TupleTag<>() {};
    private static final TupleTag<String> BAD = new TupleTag<>() {};

    public static void main(String[] args) {

        String topic = System.getenv().getOrDefault("TRAFFIC_TOPIC", "traffic-data");

        Pipeline pipeline = Pipeline.create();

        // Force earliest + unique consumer group (so every run can read from the beginning of the topic)
        Map<String, Object> consumerCfg = new HashMap<>();
        consumerCfg.put("auto.offset.reset", "earliest");
        consumerCfg.put("group.id", "beam-traffic-" + UUID.randomUUID());

        PCollection<String> input =
                pipeline
                        .apply("ReadKafka",
                                KafkaIO.<String, String>read()
                                        .withBootstrapServers("localhost:9092")
                                        .withTopic(topic)
                                        .withKeyDeserializer(StringDeserializer.class)
                                        .withValueDeserializer(StringDeserializer.class)
                                        .withConsumerConfigUpdates(consumerCfg)
                                        .withoutMetadata())
                        .apply("ValuesOnly", Values.create())
                        .setCoder(StringUtf8Coder.of());

        // DEBUG: raw lines -> output/raw-traffic/
        PCollection<String> rawWindowed =
                input.apply("RawWindow",
                        Window.<String>into(FixedWindows.of(Duration.standardSeconds(10)))
                                .triggering(Repeatedly.forever(
                                        AfterProcessingTime.pastFirstElementInPane()
                                                .plusDelayOf(Duration.standardSeconds(10))
                                ))
                                .discardingFiredPanes()
                                .withAllowedLateness(Duration.ZERO)
                );

        rawWindowed.apply("WriteRaw",
                TextIO.write()
                        .to("output/raw-traffic/raw")
                        .withSuffix(".txt")
                        .withNumShards(1)
                        .withWindowedWrites()
        );

        // Parse safely -> GOOD (SpeedEvent) + BAD (String)
        PCollectionTuple parsed =
                input.apply("ParseSafely",
                        ParDo.of(new DoFn<String, SpeedEvent>() {
                                    @ProcessElement
                                    public void processElement(@Element String line, MultiOutputReceiver out) {
                                        try {
                                            if (line == null || line.isBlank()) return;

                                            SpeedEvent e = SpeedEventParser.parse(line);

                                            // Use event-time from the record
                                            out.get(GOOD).outputWithTimestamp(
                                                    e, new org.joda.time.Instant(e.getTimestamp().toEpochMilli())
                                            );
                                        } catch (Exception ex) {
                                            out.get(BAD).output(
                                                    "BAD: " + line + " | " +
                                                            ex.getClass().getSimpleName() + ": " + ex.getMessage()
                                            );
                                        }
                                    }
                                })
                                .withOutputTags(GOOD, TupleTagList.of(BAD))
                );

        PCollection<String> badLines = parsed.get(BAD).setCoder(StringUtf8Coder.of());
        PCollection<SpeedEvent> events = parsed.get(GOOD).setCoder(SerializableCoder.of(SpeedEvent.class));

        // BAD lines -> output/bad-lines/
        PCollection<String> badWindowed =
                badLines.apply("BadWindow",
                        Window.<String>into(FixedWindows.of(Duration.standardSeconds(10)))
                                .triggering(Repeatedly.forever(
                                        AfterProcessingTime.pastFirstElementInPane()
                                                .plusDelayOf(Duration.standardSeconds(10))
                                ))
                                .discardingFiredPanes()
                                .withAllowedLateness(Duration.ZERO)
                );

        badWindowed.apply("WriteBad",
                TextIO.write()
                        .to("output/bad-lines/bad")
                        .withSuffix(".txt")
                        .withNumShards(1)
                        .withWindowedWrites()
        );

        // Main window for computation
        PCollection<SpeedEvent> windowed =
                events
                        .apply("Filter", Filter.by(e -> e.getSpeedMs() >= 0))
                        .apply("Window10s",
                                Window.<SpeedEvent>into(FixedWindows.of(Duration.standardSeconds(10)))
                                        .triggering(Repeatedly.forever(
                                                AfterProcessingTime.pastFirstElementInPane()
                                                        .plusDelayOf(Duration.standardSeconds(10))
                                        ))
                                        .discardingFiredPanes()
                                        .withAllowedLateness(Duration.ZERO)
                        );

        // Key by sensorId
        PCollection<KV<Integer, SpeedEvent>> keyed =
                windowed
                        .apply("KeyBySensor",
                                MapElements.into(
                                                TypeDescriptors.kvs(
                                                        TypeDescriptors.integers(),
                                                        TypeDescriptor.of(SpeedEvent.class)
                                                )
                                        )
                                        .via(e -> KV.of(e.getSensorId(), e))
                        )
                        .setCoder(KvCoder.of(VarIntCoder.of(), SerializableCoder.of(SpeedEvent.class)));

        // Average per sensor
        PCollection<KV<Integer, Double>> averages =
                keyed
                        .apply("AverageSpeed", Combine.perKey(new AverageSpeedFn()))
                        .setCoder(KvCoder.of(VarIntCoder.of(), DoubleCoder.of()));

        // Format output
        PCollection<String> output =
                averages.apply("Format", ParDo.of(new DoFn<KV<Integer, Double>, String>() {
                            @ProcessElement
                            public void processElement(@Element KV<Integer, Double> e,
                                                       BoundedWindow w,
                                                       OutputReceiver<String> out) {
                                IntervalWindow iw = (IntervalWindow) w;
                                out.output(String.format("[%s, %s) SensorId=%d AverageSpeed=%.2f",
                                        iw.start(), iw.end(), e.getKey(), e.getValue()));
                            }
                        }))
                        .setCoder(StringUtf8Coder.of());

        // Windowed write (unbounded!)
        output.apply("WriteAverages",
                TextIO.write()
                        .to("output/average-speeds/avg")
                        .withSuffix(".txt")
                        .withNumShards(1)
                        .withWindowedWrites()
        );

        pipeline.run().waitUntilFinish();
    }
}