package com.krassedudes.streaming_systems.models.commands;

import java.util.HashMap;
import java.util.HashSet;

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
    public void applyToQueryModel(HashMap<String, VehicleDTO> queryModel) {
        VehicleInfo info = (VehicleInfo)queryModel.get(this.name);
        VehicleInfo newInfo = new VehicleInfo(
            this.name,
            info.getPosition().add(this.moveVector),
            info.getNumberOfMoves() + 1);

        queryModel.replace(this.name, newInfo);
    }

    @Override
    public String toJsonString()
    {
        String moveVectorJson = this.moveVector.toJsonString();
        return "{" + "\"type\": \"move\", \"name\": \"" + this.name + "\", \"moveVector\": " + moveVectorJson + "}";
    }

    @Override
    public void applyToDomainModel(HashSet<String> domainModel) {
        // nothing
    }
}