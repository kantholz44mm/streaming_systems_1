package com.krassedudes.streaming_systems.models;

import java.io.Serializable;
import java.time.Instant;

public record SpeedEvent(int sensorId, double speedMs, Instant timestamp) implements Serializable {}