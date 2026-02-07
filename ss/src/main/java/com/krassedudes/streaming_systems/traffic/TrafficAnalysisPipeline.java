package com.krassedudes.streaming_systems.traffic;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.*;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.kafka.KafkaIO;
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

    private static final Logger log = LogManager.getLogger(TrafficAnalysisPipeline.class);

    private static final TupleTag<SpeedEvent> PARSED_GOOD = new TupleTag<>() {};
    private static final TupleTag<String> PARSED_BAD = new TupleTag<>() {};

    private static final TupleTag<SpeedEvent> VALID = new TupleTag<>() {};
    private static final TupleTag<String> INVALID = new TupleTag<>() {};

    public static void main(String[] args) {

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        String topic = System.getenv().getOrDefault("TRAFFIC_TOPIC", "traffic-data");
        String outputDir = System.getenv().getOrDefault("OUTPUT_DIR", "output");

        log.info("Starting TrafficAnalysisPipeline");
        log.info("Kafka bootstrap={}", bootstrap);
        log.info("Topic={}", topic);
        log.info("OutputDir={}", outputDir);

        Pipeline pipeline = Pipeline.create();

        Map<String, Object> consumerCfg = new HashMap<>();
        consumerCfg.put("auto.offset.reset", "earliest");
        consumerCfg.put("group.id", "beam-traffic-" + UUID.randomUUID());

        PCollection<String> input =
                pipeline
                        .apply("ReadKafka",
                                KafkaIO.<String, String>read()
                                        .withBootstrapServers(bootstrap)
                                        .withTopic(topic)
                                        .withKeyDeserializer(StringDeserializer.class)
                                        .withValueDeserializer(StringDeserializer.class)
                                        .withConsumerConfigUpdates(consumerCfg)
                                        .withCreateTime(Duration.standardSeconds(5))
                                        .withoutMetadata())
                        .apply("ValuesOnly", Values.create())
                        .setCoder(StringUtf8Coder.of());

        input.apply("LogInput",
                ParDo.of(new DoFn<String, Void>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        log.debug("INPUT BeamTS={} line={}", c.timestamp(), c.element());
                    }
                }));

        input.apply("RawWindow",
                        Window.<String>into(FixedWindows.of(Duration.standardSeconds(10)))
                                .triggering(Repeatedly.forever(
                                        AfterProcessingTime.pastFirstElementInPane()
                                                .plusDelayOf(Duration.standardSeconds(10))
                                ))
                                .discardingFiredPanes()
                                .withAllowedLateness(Duration.ZERO)
                )
                .apply("WriteRaw",
                        TextIO.write()
                                .to(outputDir + "/raw-traffic/raw")
                                .withSuffix(".txt")
                                .withNumShards(1)
                                .withWindowedWrites()
                );

        PCollectionTuple parsed =
                input.apply("ParseSafely",
                        ParDo.of(new DoFn<String, SpeedEvent>() {
                                    @ProcessElement
                                    public void processElement(@Element String line, MultiOutputReceiver out) {
                                        try {
                                            if (line == null || line.isBlank()) return;
                                            SpeedEvent e = SpeedEventParser.parse(line);
                                            out.get(PARSED_GOOD).output(e);
                                        } catch (Exception ex) {
                                            out.get(PARSED_BAD).output(
                                                    "PARSE_BAD: " + line + " | " +
                                                            ex.getClass().getSimpleName() + ": " + ex.getMessage()
                                            );
                                        }
                                    }
                                })
                                .withOutputTags(PARSED_GOOD, TupleTagList.of(PARSED_BAD))
                );

        PCollection<SpeedEvent> parsedGood =
                parsed.get(PARSED_GOOD).setCoder(SerializableCoder.of(SpeedEvent.class));

        PCollection<String> parseBad =
                parsed.get(PARSED_BAD).setCoder(StringUtf8Coder.of());

        PCollectionTuple validated =
                parsedGood.apply("ValidateSpeed",
                        ParDo.of(new DoFn<SpeedEvent, SpeedEvent>() {
                                    @ProcessElement
                                    public void processElement(@Element SpeedEvent e, MultiOutputReceiver out) {
                                        if (e.getSpeedMs() < 0) {
                                            out.get(INVALID).output("NEG_SPEED: " + e);
                                        } else {
                                            out.get(VALID).output(e);
                                        }
                                    }
                                })
                                .withOutputTags(VALID, TupleTagList.of(INVALID))
                );

        PCollection<SpeedEvent> validEvents =
                validated.get(VALID).setCoder(SerializableCoder.of(SpeedEvent.class));

        PCollection<String> invalidEvents =
                validated.get(INVALID).setCoder(StringUtf8Coder.of());

        PCollectionList<String> allBadList = PCollectionList.of(parseBad).and(invalidEvents);
        PCollection<String> allBad = allBadList.apply("FlattenBad", Flatten.pCollections())
                .setCoder(StringUtf8Coder.of());

        allBad.apply("LogBad",
                ParDo.of(new DoFn<String, String>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        log.warn("BAD {}", c.element());
                        c.output(c.element());
                    }
                })).setCoder(StringUtf8Coder.of());

        allBad.apply("BadWindow",
                        Window.<String>into(FixedWindows.of(Duration.standardSeconds(10)))
                                .triggering(Repeatedly.forever(
                                        AfterProcessingTime.pastFirstElementInPane()
                                                .plusDelayOf(Duration.standardSeconds(10))
                                ))
                                .discardingFiredPanes()
                                .withAllowedLateness(Duration.ZERO)
                )
                .apply("WriteBad",
                        TextIO.write()
                                .to(outputDir + "/bad-lines/bad")
                                .withSuffix(".txt")
                                .withNumShards(1)
                                .withWindowedWrites()
                );

        PCollection<SpeedEvent> windowed =
                validEvents.apply("WindowSliding",
                        Window.<SpeedEvent>into(
                                        SlidingWindows.of(Duration.standardSeconds(10))
                                                .every(Duration.standardSeconds(5))
                                )
                                .triggering(
                                        Repeatedly.forever(
                                                AfterWatermark.pastEndOfWindow()
                                                        .withEarlyFirings(
                                                                AfterProcessingTime.pastFirstElementInPane()
                                                                        .plusDelayOf(Duration.standardSeconds(10))
                                                        )
                                        )
                                )
                                .discardingFiredPanes()
                                .withAllowedLateness(Duration.standardSeconds(5))
                );

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

        PCollection<KV<Integer, Double>> averages =
                keyed
                        .apply("AverageSpeed", Combine.perKey(new AverageSpeedFn()))
                        .setCoder(KvCoder.of(VarIntCoder.of(), DoubleCoder.of()));

        PCollection<String> formatted =
                averages.apply("Format",
                                ParDo.of(new DoFn<KV<Integer, Double>, String>() {
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

        formatted.apply("WriteAverages",
                TextIO.write()
                        .to(outputDir + "/average-speeds/avg")
                        .withSuffix(".txt")
                        .withNumShards(1)
                        .withWindowedWrites()
        );

        pipeline.run().waitUntilFinish();
    }
}