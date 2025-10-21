package com.krassedudes.ss;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

public class LidarDataGrouped extends LidarData {

    public int group = 0;

    public LidarDataGrouped(double angle, double distance, int quality, int group)
    {
        super(angle, distance, quality);
        this.group = group;
    }

    public LidarDataGrouped(LidarData payload, int group)
    {
        super(payload.angle, payload.distance, payload.quality);
        this.group = group;
    }

    public LidarDataGrouped()
    {
        super();
    }

    public String toJsonString()
    {
        String payload = super.toJsonString();
        return "{" + "\"group\": " + String.valueOf(group) + ", \"data\": " + payload + "}";
    }

    public static LidarDataGrouped fromJsonString(String json)
    {
        try {
            JsonObject jsonObject = (JsonObject)Jsoner.deserialize(json);
    
            int group = ((Number)jsonObject.get("group")).intValue();
            JsonObject payload = (JsonObject)jsonObject.get("data");
            
            double angle = ((Number)payload.get("winkel")).doubleValue();
            double distance = ((Number)payload.get("distanz")).doubleValue();
            int quality = ((Number)payload.get("qualitaet")).intValue();
            
            return new LidarDataGrouped(angle, distance, quality, group);

        }
        catch(JsonException e)
        {
            // ignore invalid lines
            return null;
        }
    }
}
