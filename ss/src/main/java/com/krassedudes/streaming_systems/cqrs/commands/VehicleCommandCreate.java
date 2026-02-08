package com.krassedudes.streaming_systems.cqrs.commands;

import java.util.HashMap;
import java.util.HashSet;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.krassedudes.streaming_systems.cqrs.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.Position;
import com.krassedudes.streaming_systems.models.VehicleInfo;

public class VehicleCommandCreate extends VehicleCommand {
    Position startPosition;

    public VehicleCommandCreate(String name, Position startPosition) {
        super(name);
        this.startPosition = startPosition;
    }

    @Override
    public void applyToQueryModel(HashMap<String, VehicleDTO> queryModel) {
        VehicleInfo info = new VehicleInfo(this.name, this.startPosition);
        queryModel.put(this.name, info);
    }

    @Override
    public String toJsonString()
    {
        String startPositionJson = this.startPosition.toJsonString();
        return "{" + "\"type\": \"create\", \"name\": \"" + this.name + "\", \"startPosition\": " + startPositionJson + "}";
    }

    @Override
    public void applyToDomainModel(HashSet<String> domainModel) {
        domainModel.add(this.name);
    }
}