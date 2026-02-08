package com.krassedudes.streaming_systems.traffic.task8;

import java.time.Instant;


public final class TrafficProcessor {

    public ProcessingResult process(String line) {
        try {
            if (line == null || line.isBlank()) {
                return ProcessingResult.bad("BAD: <blank line>");
            }

            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3) {
                return ProcessingResult.bad("BAD: " + line + " | reason=expected 3 tokens");
            }

            Instant ts = Instant.parse(parts[0]);
            int sensorId = Integer.parseInt(parts[1]);
            double speedMs = Double.parseDouble(parts[2]);

            if (speedMs < 0) {
                return ProcessingResult.bad("BAD: " + line + " | reason=negative speed");
            }

            double speedKmh = speedMs * 3.6;

            String out = String.format("%s SensorId=%d SpeedKmh=%.2f", parts[0], sensorId, speedKmh);
            return ProcessingResult.ok(out);

        } catch (Exception ex) {
            return ProcessingResult.bad("BAD: " + line + " | " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}