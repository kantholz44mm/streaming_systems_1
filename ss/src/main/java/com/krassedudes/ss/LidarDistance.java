package com.krassedudes.ss;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class LidarDistance {
    public int group;
    public double distance;

    public LidarDistance(int group, double distance) 
    {
        this.group = group;
        this.distance = distance;
    }

    public String toJsonString()
    {
        return "{" + "\"group\": " + String.valueOf(group) + ", \"distanz\": " + String.valueOf(distance) + "}";
    }

    public static LidarDistance fromJsonString(String json)
    {
        try {
            JsonObject jsonObject = (JsonObject)Jsoner.deserialize(json);
    
            int group = ((Number)jsonObject.get("group")).intValue();
            double distance = ((Number)jsonObject.get("distanz")).doubleValue();
            
            return new LidarDistance(group, distance);

        }
        catch(JsonException e)
        {
            // ignore invalid lines
            return null;
        }
    }
}
