package com.krassedudes.streaming_systems.models;

import java.io.Serializable;

public class SpeedChangeEvent implements Serializable {

    private final int sensorId;
    private final double previousSpeed;
    private final double currentSpeed;

    public SpeedChangeEvent(int sensorId, double previousSpeed, double currentSpeed) {
        this.sensorId = sensorId;
        this.previousSpeed = previousSpeed;
        this.currentSpeed = currentSpeed;
    }

    public int getSensorId() { return sensorId; }
    public double getPreviousSpeed() { return previousSpeed; }
    public double getCurrentSpeed() { return currentSpeed; }
}
