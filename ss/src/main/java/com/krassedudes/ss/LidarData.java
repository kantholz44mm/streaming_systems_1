package com.krassedudes.ss;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class LidarData {
    public double angle = 0.0;
    public double distance = 0.0;
    public int quality = 0;

    public LidarData(double angle, double distance, int quality)
    {
        this.angle = angle;
        this.distance = distance;
        this.quality = quality;
    }

    public LidarData()
    {
        this.angle = 0;
        this.distance = 0;
        this.quality = 0;
    }

    public String toJsonString()
    {
        return "{" + "\"winkel\": " + String.valueOf(angle) + ", \"distanz\": " + String.valueOf(distance) + ", \"qualitaet\": " + String.valueOf(quality) + "}";
    }

    public static LidarData fromJsonString(String json)
    {
        try {
            JsonObject jsonObject = (JsonObject)Jsoner.deserialize(json);
    
            double angle = ((Number)jsonObject.get("winkel")).doubleValue();
            double distance = ((Number)jsonObject.get("distanz")).doubleValue();
            int quality = ((Number)jsonObject.get("qualitaet")).intValue();
            
            return new LidarData(angle, distance, quality);

        } catch(JsonException e)
        {
            // ignore invalid lines
            return null;
        }
    }

}
