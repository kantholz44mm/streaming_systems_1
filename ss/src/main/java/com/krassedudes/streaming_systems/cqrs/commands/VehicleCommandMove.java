package com.krassedudes.streaming_systems.cqrs.commands;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.krassedudes.streaming_systems.cqrs.interfaces.VehicleDTO;
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
        Position newPosition = info.getPosition().add(this.moveVector);
        List<Position> positionHistory = info.getPreviousPositions();
        positionHistory.add(info.getPosition());
        positionHistory.add(newPosition);

        VehicleInfo newInfo = new VehicleInfo(this.name, positionHistory);
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