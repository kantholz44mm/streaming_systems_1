package com.krassedudes.streaming_systems.models;

import com.krassedudes.streaming_systems.interfaces.VehicleDTO;

public class VehicleInfo implements VehicleDTO {

    private String name;
    private Position position;
    private int numberOfMoves;

    public VehicleInfo(String name, Position position, int moves) {
        this.name = name;
        this.position = position;
        this.numberOfMoves = moves;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Position getPosition() {
        return this.position;
    }

    @Override
    public int getNumberOfMoves() {
        return this.numberOfMoves;
    }
    
}
