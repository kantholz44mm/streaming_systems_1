package com.krassedudes.streaming_systems.traffic;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;

public class TrafficEsperApp {
    /*
    private static final String DEFAULT_TOPIC = "traffic-data";

    public static void main(String[] args) {

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        String topic = System.getenv().getOrDefault("TRAFFIC_TOPIC", DEFAULT_TOPIC);

        // -------------------------
        // 1) Esper: Event Types
        // -------------------------
        Configuration cfg = new Configuration();

        // Input event (from Kafka lines)
        Map<String, Object> speedEventProps = new LinkedHashMap<>();
        speedEventProps.put("sensorId", Integer.class);
        speedEventProps.put("speedMs", Double.class);
        speedEventProps.put("eventTimeMs", Long.class);
        cfg.getCommon().addEventType("SpeedEventMap", speedEventProps);

        // Derived average event (Task 6)
        Map<String, Object> avgProps = new LinkedHashMap<>();
        avgProps.put("sensorId", Integer.class);
        avgProps.put("avgSpeedKmh", Double.class);
        avgProps.put("windowStartMs", Long.class);
        avgProps.put("windowEndMs", Long.class);
        cfg.getCommon().addEventType("AverageSpeedEvent", avgProps);

        // Complex event (Task 7)
        Map<String, Object> changeProps = new LinkedHashMap<>();
        changeProps.put("sensorId", Integer.class);
        changeProps.put("prevAvgSpeedKmh", Double.class);
        changeProps.put("currAvgSpeedKmh", Double.class);
        changeProps.put("deltaKmh", Double.class);
        changeProps.put("prevWindowStartMs", Long.class);
        changeProps.put("prevWindowEndMs", Long.class);
        changeProps.put("currWindowStartMs", Long.class);
        changeProps.put("currWindowEndMs", Long.class);
        cfg.getCommon().addEventType("SpeedChangeEvent", changeProps);

        String runtimeUri = "traffic-" + UUID.randomUUID();
        EPRuntime runtime = EPRuntimeProvider.getRuntime(runtimeUri, cfg);

        // -------------------------
        // 2) EPL: Task 6 -> AverageSpeedEvent
        // -------------------------
        String eplAvg = "insert into AverageSpeedEvent " +
                "select " +
                "  sensorId as sensorId, " +
                "  avg(speedMs) * 3.6 as avgSpeedKmh, " +
                "  min(eventTimeMs) as windowStartMs, " +
                "  max(eventTimeMs) as windowEndMs " +
                "from SpeedEventMap(speedMs >= 0) " +
                "#ext_timed_batch(eventTimeMs, 10 sec) " +
                "group by sensorId " +
                "having count(*) > 0";

        
        String eplAvgSelect = "select sensorId, avgSpeedKmh, windowStartMs, windowEndMs from AverageSpeedEvent";

        // -------------------------
        // 3) EPL: Task 7 -> SpeedChangeEvent (complex event)
        // Temporal operator -> between consecutive avg events
        // -------------------------
        String eplChange = "insert into SpeedChangeEvent " +
                "select " +
                "  a.sensorId as sensorId, " +
                "  a.avgSpeedKmh as prevAvgSpeedKmh, " +
                "  b.avgSpeedKmh as currAvgSpeedKmh, " +
                "  (b.avgSpeedKmh - a.avgSpeedKmh) as deltaKmh, " +
                "  a.windowStartMs as prevWindowStartMs, " +
                "  a.windowEndMs as prevWindowEndMs, " +
                "  b.windowStartMs as currWindowStartMs, " +
                "  b.windowEndMs as currWindowEndMs " +
                "from pattern [" +
                "  every a=AverageSpeedEvent -> " +
                "        b=AverageSpeedEvent(" +
                "             sensorId = a.sensorId and " +
                "             ( (b.avgSpeedKmh - a.avgSpeedKmh) > 20 or (a.avgSpeedKmh - b.avgSpeedKmh) > 20 )" +
                "        )" +
                "]";

        String eplChangeSelect = "select sensorId, prevAvgSpeedKmh, currAvgSpeedKmh, deltaKmh, " +
                "prevWindowStartMs, prevWindowEndMs, currWindowStartMs, currWindowEndMs " +
                "from SpeedChangeEvent";

        try {
            EPCompiled c1 = EPCompilerProvider.getCompiler().compile(eplAvg, new CompilerArguments(cfg));
            runtime.getDeploymentService().deploy(c1);

            EPCompiled c2 = EPCompilerProvider.getCompiler().compile(eplChange, new CompilerArguments(cfg));
            runtime.getDeploymentService().deploy(c2);

            // Listener: Avg-Ausgabe
            EPCompiled c3 = EPCompilerProvider.getCompiler().compile(eplAvgSelect, new CompilerArguments(cfg));
            EPDeployment d3 = runtime.getDeploymentService().deploy(c3);
            EPStatement sAvg = d3.getStatements()[0];
            sAvg.addListener((newData, oldData, st, rt) -> {
                if (newData == null || newData.length == 0)
                    return;
                EventBean b = newData[0];
                Integer sensorId = (Integer) b.get("sensorId");
                Number avg = (Number) b.get("avgSpeedKmh");
                Number ws = (Number) b.get("windowStartMs");
                Number we = (Number) b.get("windowEndMs");
                if (sensorId == null || avg == null || ws == null || we == null)
                    return;

                System.out.printf("[ws=%d, we=%d] SensorId=%d AvgSpeed=%.2f km/h%n",
                        ws.longValue(), we.longValue(), sensorId, avg.doubleValue());
            });

            // Listener: Complex-Event-Ausgabe (Task 7)
            EPCompiled c4 = EPCompilerProvider.getCompiler().compile(eplChangeSelect, new CompilerArguments(cfg));
            EPDeployment d4 = runtime.getDeploymentService().deploy(c4);
            EPStatement sChange = d4.getStatements()[0];
            sChange.addListener((newData, oldData, st, rt) -> {
                if (newData == null || newData.length == 0)
                    return;
                EventBean b = newData[0];

                Integer sensorId = (Integer) b.get("sensorId");
                Number prev = (Number) b.get("prevAvgSpeedKmh");
                Number curr = (Number) b.get("currAvgSpeedKmh");
                Number delta = (Number) b.get("deltaKmh");
                Number pws = (Number) b.get("prevWindowStartMs");
                Number pwe = (Number) b.get("prevWindowEndMs");
                Number cws = (Number) b.get("currWindowStartMs");
                Number cwe = (Number) b.get("currWindowEndMs");

                if (sensorId == null || prev == null || curr == null || delta == null ||
                        pws == null || pwe == null || cws == null || cwe == null)
                    return;

                System.out.printf(
                        "!!! SPEED CHANGE (>20 km/h) SensorId=%d prev=%.2f curr=%.2f delta=%+.2f | prev[ws=%d,we=%d] curr[ws=%d,we=%d]%n",
                        sensorId,
                        prev.doubleValue(),
                        curr.doubleValue(),
                        delta.doubleValue(),
                        pws.longValue(), pwe.longValue(),
                        cws.longValue(), cwe.longValue());
            });

        } catch (Exception ex) {
            throw new RuntimeException("Esper setup failed: " + ex.getMessage(), ex);
        }

        // -------------------------
        // 4) Kafka Consumer
        // -------------------------
        Properties kprops = new Properties();
        kprops.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        kprops.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        kprops.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        kprops.put(ConsumerConfig.GROUP_ID_CONFIG, "esper-traffic-" + UUID.randomUUID());
        kprops.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kprops.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(kprops)) {
            consumer.subscribe(Collections.singletonList(topic));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    consumer.wakeup();
                } catch (Exception ignored) {
                }
            }));

            System.out.println("TrafficEsperApp (Task 6+7) running... runtimeUri=" + runtimeUri + ", topic=" + topic);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> r : records) {
                    try {
                        SpeedEvent ev = CustomTransforms.parse(r.value());

                        Map<String, Object> event = new HashMap<>();
                        event.put("sensorId", ev.getSensorId());
                        event.put("speedMs", ev.getSpeedMs());
                        event.put("eventTimeMs", ev.getTimestamp().toEpochMilli());

                        runtime.getEventService().sendEventMap(event, "SpeedEventMap");
                    } catch (Exception ignoreBadLine) {
                        // optional: System.err.println("[BAD] " + r.value());
                    }
                }
            }
        } catch (WakeupException expectedOnShutdown) {
            
        }
    }
        */
}