package com.krassedudes.streaming_systems.traffic;

import java.io.Serializable;

public class AverageSpeedEvent implements Serializable {
    private final int sensorId;
    private final double avgSpeedKmh;
    private final long windowStartMs;
    private final long windowEndMs;

    public AverageSpeedEvent(int sensorId, double avgSpeedKmh, long windowStartMs, long windowEndMs) {
        this.sensorId = sensorId;
        this.avgSpeedKmh = avgSpeedKmh;
        this.windowStartMs = windowStartMs;
        this.windowEndMs = windowEndMs;
    }

    public int getSensorId() { return sensorId; }
    public double getAvgSpeedKmh() { return avgSpeedKmh; }
    public long getWindowStartMs() { return windowStartMs; }
    public long getWindowEndMs() { return windowEndMs; }
}