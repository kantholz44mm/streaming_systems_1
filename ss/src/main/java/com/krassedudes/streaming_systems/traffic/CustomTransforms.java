package com.krassedudes.streaming_systems.traffic;

import java.time.Instant;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.values.KV;
import org.joda.time.Duration;

public class CustomTransforms {
    static class SpeedEventParser extends DoFn<KV<String, String>, SpeedEvent> {
    
        @Override
        public Duration getAllowedTimestampSkew() {
            return Duration.standardDays(500);
        }

        @ProcessElement
        public void processElement(@Element KV<String, String> element, OutputReceiver<SpeedEvent> out) {
    
            String[] parts = element.getValue().split("\\s+");
    
            Instant timestamp = Instant.parse(parts[0]);
            int sensorId = Integer.parseInt(parts[1]);
            double speedMs = Double.parseDouble(parts[2]);
    
            SpeedEvent event = new SpeedEvent(sensorId, speedMs, timestamp);
            var joda_timestamp = new org.joda.time.Instant(timestamp.toEpochMilli());
            out.outputWithTimestamp(event, joda_timestamp);
        }
    }

    static class LogSpeedEvent extends DoFn<SpeedEvent, Void> {

        @ProcessElement
        public void processElement(@Element SpeedEvent event) {
            System.out.println("Event: " + event + ", event-time: " + event.timestamp());
        }
    }

    static class ExtractSensorSpeedFn extends DoFn<SpeedEvent, KV<Integer, Double>> {
        @ProcessElement
        public void processElement(@Element SpeedEvent event, OutputReceiver<KV<Integer, Double>> out) {
            out.output(KV.of(event.sensorId(), event.speedMs()));
        }
    }

    static class MetersPerSecondToKilometersPerHour extends DoFn<KV<Integer, Double>, KV<Integer, Double>> {
        @ProcessElement
        public void processElement(@Element KV<Integer, Double> event, OutputReceiver<KV<Integer, Double>> out) {
            out.output(KV.of(event.getKey(), event.getValue() * 3.6));
        }
    }

    static class FormatForOutput extends DoFn<KV<Integer, Double>, String> {
        @ProcessElement
        public void processElement(@Element KV<Integer, Double> event, BoundedWindow window, OutputReceiver<String> out) {
            IntervalWindow iwindow = (IntervalWindow)window;
            var sensorID = event.getKey();
            var avgSpeed = event.getValue();
            String output = String.format("[%s, %s) SensorId=%d AverageSpeed=%.2f", iwindow.start(), iwindow.end(), sensorID, avgSpeed);
            out.output(output);
        }
    }
}