package com.krassedudes.streaming_systems.models;

import java.io.Serializable;

import com.github.cliftonlabs.json_simple.JsonObject;

public record Position(double x, double y) implements Serializable
{
    public static Position fromPolar(double angle, double distance)
    {
        return new Position(Math.cos(angle) * distance, Math.sin(angle) * distance);
    }

    public double length()
    {
        return Math.sqrt(x * x + y * y);
    }

    public Position delta(Position other)
    {
        return new Position(this.x - other.x, this.y - other.y);
    }

    public double angle() {
        return Math.atan2(this.y, this.x);
    }

    public Position add(Position other) {
        return new Position(this.x + other.x, this.y + other.y);
    }

    public double distance(Position to) {
        return this.delta(to).length();
    }

    public String toJsonString() {
        return "{\"x\": " + String.valueOf(this.x) + ", \"y\": " + String.valueOf(this.y) + "}";
    }

    public static Position fromJson(JsonObject jsonObject) {

        double x = ((Number)jsonObject.get("x")).doubleValue();
        double y = ((Number)jsonObject.get("x")).doubleValue();
        return new Position(x, y);
    }
}