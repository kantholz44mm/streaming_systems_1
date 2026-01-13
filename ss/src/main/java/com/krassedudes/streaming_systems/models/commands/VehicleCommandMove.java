package com.krassedudes.streaming_systems.models.commands;

import java.util.HashMap;

import com.krassedudes.streaming_systems.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.Position;
import com.krassedudes.streaming_systems.models.VehicleInfo;

public class VehicleCommandMove extends VehicleCommand {
    Position moveVector;

    public VehicleCommandMove(String name, Position moveVector) {
        super(name);
        this.moveVector = moveVector;
    }

    @Override
    public void applyToDomainModel(HashMap<String, VehicleDTO> domainModel) {
        VehicleInfo info = (VehicleInfo)domainModel.get(this.name);
        VehicleInfo newInfo = new VehicleInfo(
            this.name,
            info.getPosition().add(this.moveVector),
            info.getNumberOfMoves() + 1);

        domainModel.replace(this.name, newInfo);
    }

    @Override
    public String toJsonString()
    {
        String moveVectorJson = this.moveVector.toJsonString();
        return "{" + "\"type\": \"move\", \"name\": \"" + this.name + "\", \"moveVector\": " + moveVectorJson + "}";
    }
}