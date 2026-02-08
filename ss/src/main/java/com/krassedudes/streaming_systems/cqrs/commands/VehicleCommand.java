package com.krassedudes.streaming_systems.cqrs.commands;

import java.util.HashMap;
import java.util.HashSet;

import javax.naming.directory.InvalidAttributeIdentifierException;

import org.apache.beam.repackaged.core.org.apache.commons.lang3.NotImplementedException;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonKey;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.krassedudes.streaming_systems.cqrs.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.LidarDistance;
import com.krassedudes.streaming_systems.models.Position;

public abstract class VehicleCommand {
    String name;

    public VehicleCommand(String name) {
        this.name = name;
    }

    public abstract void applyToQueryModel(HashMap<String, VehicleDTO> queryModel);
    public abstract void applyToDomainModel(HashSet<String> domainModel);

    public String toJsonString()
    {
        throw new NotImplementedException();
    }

    public static VehicleCommand fromJsonString(String json) throws Exception
    {
        try {
            JsonObject jsonObject = (JsonObject)Jsoner.deserialize(json);
            String type = (String)jsonObject.get("type");
            String name = (String)jsonObject.get("name");

            switch (type) {
                case "create":
                    Position startPosition = Position.fromJson((JsonObject)jsonObject.get("startPosition"));
                    return new VehicleCommandCreate(name, startPosition);
                case "move":
                    Position moveVector = Position.fromJson((JsonObject)jsonObject.get("moveVector"));
                    return new VehicleCommandMove(name, moveVector);
                case "remove":
                    return new VehicleCommandRemove(name);
                default:
                    return null;
            }
        }
        catch(JsonException e)
        {
            return null;
        }
    }
}
