package com.krassedudes.streaming_systems.models.commands;

import java.util.HashMap;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.krassedudes.streaming_systems.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.Position;
import com.krassedudes.streaming_systems.models.VehicleInfo;

public class VehicleCommandCreate extends VehicleCommand {
    Position startPosition;

    public VehicleCommandCreate(String name, Position startPosition) {
        super(name);
        this.startPosition = startPosition;
    }

    @Override
    public void applyToDomainModel(HashMap<String, VehicleDTO> domainModel) {
        VehicleInfo info = new VehicleInfo(this.name, this.startPosition, 0);
        domainModel.put(this.name, info);
    }

    @Override
    public String toJsonString()
    {
        String startPositionJson = this.startPosition.toJsonString();
        return "{" + "\"type\": \"create\", \"name\": \"" + this.name + "\", \"startPosition\": " + startPositionJson + "}";
    }
}