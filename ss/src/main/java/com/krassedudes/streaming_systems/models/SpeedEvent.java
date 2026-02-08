package com.krassedudes.streaming_systems.models;

import java.io.Serializable;
import java.time.Instant;

public class SpeedEvent implements Serializable {

    private final int sensorId;
    private final double speedMs;
    private final Instant timestamp;

    public SpeedEvent(int sensorId, double speedMs, Instant timestamp) {
        this.sensorId = sensorId;
        this.speedMs = speedMs;
        this.timestamp = timestamp;
    }

    public static SpeedEvent parse(String input) {
        String[] parts = input.split("\\s+");

        Instant timestamp = Instant.parse(parts[0]);
        int sensorId = Integer.parseInt(parts[1]);
        double speedMs = Double.parseDouble(parts[2]);

        return new SpeedEvent(sensorId, speedMs, timestamp);
    }

    public int getSensorId() {
        return sensorId;
    }

    public double getSpeedMs() {
        return speedMs;
    }

    public long getTimestampMs() {
        return timestamp.toEpochMilli();
    }
}