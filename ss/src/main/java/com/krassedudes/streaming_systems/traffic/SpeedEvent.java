package com.krassedudes.streaming_systems.traffic;

import java.io.Serializable;
import java.time.Instant;

/**
 * Immutable event representing a speed measurement.
 * Implemented as Java record to automatically provide
 * equals, hashCode and toString.
 */
public record SpeedEvent(
        int sensorId,
        double speedMs,
        Instant timestamp
) implements Serializable {

    public int getSensorId() {
        return sensorId;
    }

    public double getSpeedMs() {
        return speedMs;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}