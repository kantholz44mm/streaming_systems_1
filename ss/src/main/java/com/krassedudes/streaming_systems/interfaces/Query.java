package com.krassedudes.streaming_systems.interfaces;

import java.util.Enumeration;

import com.krassedudes.streaming_systems.models.Position;

public interface Query {
    public VehicleDTO getVehicleByName(String name);
    public Enumeration<VehicleDTO> getVehicles();
    public Enumeration<VehicleDTO> getVehiclesAtPosition(Position position);
}
