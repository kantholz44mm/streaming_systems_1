package com.krassedudes.streaming_systems.cqrs.interfaces;

import com.krassedudes.streaming_systems.models.Position;

public interface VehicleCommands {
    void createVehicle(String name, Position starPosition) throws Exception;
    void moveVehicle(String name, Position moveVector) throws Exception;
    void removeVehicle(String name) throws Exception;
}
