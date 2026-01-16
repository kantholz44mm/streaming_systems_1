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

    private static final String TOPIC = "traffic-data";

    public static void main(String[] args) {

        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
        String topic = System.getenv().getOrDefault("TRAFFIC_TOPIC", TOPIC);

        // --------------------------------------------------------------------
        // Esper configuration (IMPORTANT: define Map event type WITH PROPERTIES)
        // --------------------------------------------------------------------
        Configuration cfg = new Configuration();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("sensorId", Integer.class);
        props.put("speedMs", Double.class);
        props.put("eventTimeMs", Long.class);
        cfg.getCommon().addEventType("SpeedEventMap", props);

        // Avoid cached "default" runtime: use unique URI
        String runtimeUri = "traffic-" + UUID.randomUUID();
        EPRuntime runtime = EPRuntimeProvider.getRuntime(runtimeUri, cfg);

        String epl =
                "select " +
                        "  sensorId as sensorId, " +
                        "  avg(speedMs) * 3.6 as avgSpeedKmh, " +
                        "  min(eventTimeMs) as windowStartMs, " +
                        "  max(eventTimeMs) as windowEndMs " +
                "from SpeedEventMap(speedMs >= 0) " +
                "#ext_timed_batch(eventTimeMs, 10 sec) " +
                "group by sensorId " +
                "having count(speedMs) > 0";

        try {
            EPCompiled compiled = EPCompilerProvider.getCompiler()
                    .compile(epl, new CompilerArguments(cfg));

            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            EPStatement stmt = deployment.getStatements()[0];

            // NULL-safe listener
            stmt.addListener((newData, oldData, st, rt) -> {
                if (newData == null || newData.length == 0) return;

                EventBean b = newData[0];

                Integer sensorId = (Integer) b.get("sensorId");
                Number avgNum = (Number) b.get("avgSpeedKmh");
                Number wsNum = (Number) b.get("windowStartMs");
                Number weNum = (Number) b.get("windowEndMs");

                if (sensorId == null || avgNum == null || wsNum == null || weNum == null) return;

                System.out.printf("[ws=%d, we=%d] SensorId=%d AvgSpeed=%.2f km/h%n",
                        wsNum.longValue(),
                        weNum.longValue(),
                        sensorId,
                        avgNum.doubleValue());
            });

        } catch (Exception ex) {
            throw new RuntimeException("Esper setup failed: " + ex.getMessage(), ex);
        }

        // --------------------------------------------------------------------
        // Kafka consumer (try-with-resources -> no leak)
        // --------------------------------------------------------------------
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
                try { consumer.wakeup(); } catch (Exception ignored) {}
            }));

            System.out.println("TrafficEsperApp running... runtimeUri=" + runtimeUri + ", topic=" + topic + " (Ctrl+C to stop)");

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> r : records) {
                    try {
                        SpeedEvent ev = SpeedEventParser.parse(r.value());

                        Map<String, Object> event = new HashMap<>();
                        event.put("sensorId", ev.getSensorId());
                        event.put("speedMs", ev.getSpeedMs());
                        event.put("eventTimeMs", ev.getTimestamp().toEpochMilli());

                        runtime.getEventService().sendEventMap(event, "SpeedEventMap");
                    } catch (Exception ignoreBadLine) {
                        // optional debug:
                        // System.err.println("[BAD] " + r.value());
                    }
                }
            }
        } catch (WakeupException expectedOnShutdown) {
            // normal on Ctrl+C
        }
    }
}