package com.krassedudes.streaming_systems.models;

import java.util.ArrayList;
import java.util.List;

import com.krassedudes.streaming_systems.interfaces.VehicleDTO;

public class VehicleInfo implements VehicleDTO {

    private String name;
    private List<Position> positionHistory;

    public VehicleInfo(String name, Position position) {
        this.name = name;
        this.positionHistory = new ArrayList<>();
        this.positionHistory.add(position);
    }

    public VehicleInfo(String name, List<Position> positionHistory) {
        this.name = name;
        this.positionHistory = positionHistory;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Position getPosition() {
        return this.positionHistory.getLast();
    }

    @Override
    public int getNumberOfMoves() {
        return Math.max(this.positionHistory.size() - 1, 0);
    }

    @Override
    public List<Position> getPreviousPositions() {
        return positionHistory.subList(0, positionHistory.size() - 1);
    }
    
}
