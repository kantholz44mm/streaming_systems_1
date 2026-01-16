package com.krassedudes.streaming_systems.traffic;

import java.time.Instant;

public class SpeedEventParser {

    
    public static SpeedEvent parse(String line) {
        String[] parts = line.split("\\s+");

        Instant timestamp = Instant.parse(parts[0]);
        int sensorId = Integer.parseInt(parts[1]);
        double speedMs = Double.parseDouble(parts[2]);

        return new SpeedEvent(sensorId, speedMs, timestamp);
    }
}