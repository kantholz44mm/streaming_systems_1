package com.krassedudes.streaming_systems.traffic;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompileException;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;
import com.krassedudes.streaming_systems.Consumer;
import com.krassedudes.streaming_systems.models.AverageSpeedEvent;
import com.krassedudes.streaming_systems.models.SpeedChangeEvent;
import com.krassedudes.streaming_systems.models.SpeedEvent;
import java.time.Instant;
import java.util.*;

public class TrafficEsperApp {

    public static void performTrafficAnalysis(String host, String topic)
        throws EPCompileException, EPDeployException {

        Configuration cfg = new Configuration();

        cfg.getCommon().addEventType("SpeedEvent", SpeedEvent.class);
        cfg.getCommon().addEventType("AverageSpeedEvent", AverageSpeedEvent.class);
        cfg.getCommon().addEventType("SpeedChangeEvent", SpeedChangeEvent.class);

        String runtimeUri = "traffic-" + UUID.randomUUID();
        EPRuntime runtime = EPRuntimeProvider.getRuntime(runtimeUri, cfg);

        // initialize the internal timestamp to the first event since we have 2026.
        runtime.getEventService().advanceTime(Instant.parse("2025-10-03T05:22:40.000Z").toEpochMilli());

        String eplAvg = """
            insert into AverageSpeedEvent 
            select 
                sensorId, 
                coalesce(avg(speedMs) * 3.6, 0.0),
                min(timestampMs), 
                max(timestampMs) 
            from SpeedEvent 
                .win:ext_timed_batch(timestampMs, 10 sec) 
            group by sensorId""";


        EPCompiled compiled = EPCompilerProvider.getCompiler().compile(eplAvg, new CompilerArguments(cfg));
        EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
        EPStatement statement = deployment.getStatements()[0];

        statement.addListener((newData, oldData, st, rt) -> {
            if (newData == null) return;

            for(EventBean b : newData) {
                if(b.getUnderlying() instanceof AverageSpeedEvent avgEvent) {

                    System.out.printf("[%s, %s) SensorId=%d AvgSpeed=%.2f km/h%n",
                        Instant.ofEpochMilli(avgEvent.getWindowStartMs()).toString(),
                        Instant.ofEpochMilli(avgEvent.getWindowEndMs()).toString(),
                        avgEvent.getSensorId(),
                        avgEvent.getAvgSpeedKmh());
                }
            }
        });

        String eplChange = """
                insert into SpeedChangeEvent
                select 
                    a.sensorId as sensorId,
                    a.avgSpeedKmh as previousSpeed, 
                    b.avgSpeedKmh as currentSpeed
                from pattern [
                    every a = AverageSpeedEvent 
                    -> b = AverageSpeedEvent(sensorId = a.sensorId)
                ]
                where Math.abs(b.avgSpeedKmh - a.avgSpeedKmh) > 20.0
                """;

        compiled = EPCompilerProvider.getCompiler().compile(eplChange, new CompilerArguments(cfg));
        deployment = runtime.getDeploymentService().deploy(compiled);
        statement = deployment.getStatements()[0];

        statement.addListener((newData, oldData, st, rt) -> {
            if (newData == null) return;

            for (EventBean b : newData) {
                if (b.getUnderlying() instanceof SpeedChangeEvent changeEvent) {
                    System.out.printf("Sensor %d | %.2f km/h --> %.2f km/h (Delta: %.2f)%n",
                        changeEvent.getSensorId(),
                        changeEvent.getPreviousSpeed(),
                        changeEvent.getCurrentSpeed(),
                        changeEvent.getCurrentSpeed() - changeEvent.getPreviousSpeed());
                }
            }
        });

        Consumer kafkaConsumer = new Consumer(host, topic, message -> {
            var event = SpeedEvent.parse(message);
            runtime.getEventService().advanceTime(event.getTimestampMs());
            runtime.getEventService().sendEventBean(event, "SpeedEvent");
        });

        synchronized(System.in)
        {
            try {
                System.in.wait();
            } catch (Exception ex) {}
        }

        kafkaConsumer.close();

        return;
    }
}