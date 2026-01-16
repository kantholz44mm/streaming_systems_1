
package com.krassedudes.streaming_systems.traffic;

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