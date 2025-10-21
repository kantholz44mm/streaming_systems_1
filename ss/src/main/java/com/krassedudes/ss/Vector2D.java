package com.krassedudes.ss;

public class Vector2D
{
    public double x = 0;
    public double y = 0;

    public Vector2D(double x, double y)
    {
        this.x = x;
        this.y = y;
    }

    public Vector2D()
    {
        this.x = 0;
        this.y = 0;
    }

    public static Vector2D fromPolar(double angle, double distance)
    {
        return new Vector2D(Math.cos(angle) * distance, Math.sin(angle) * distance);
    }

    public double length()
    {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2D delta(Vector2D other)
    {
        return new Vector2D(this.x - other.x, this.y - other.y);
    }

    public double angle() {
        return Math.atan2(this.y, this.x);
    }
}