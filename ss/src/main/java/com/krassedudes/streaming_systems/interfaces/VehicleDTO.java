package com.krassedudes.streaming_systems.interfaces;

import com.krassedudes.streaming_systems.models.Position;

public interface VehicleDTO {
    public String getName();
    public Position getPosition();
    public int getNumberOfMoves();
}
