package com.krassedudes.streaming_systems.interfaces;

import java.util.List;

import com.krassedudes.streaming_systems.models.Position;

public interface VehicleDTO {
    public String getName();
    public Position getPosition();
    public int getNumberOfMoves();
    public List<Position> getPreviousPositions();
}
