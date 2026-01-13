package com.krassedudes.streaming_systems;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.krassedudes.streaming_systems.interfaces.Query;
import com.krassedudes.streaming_systems.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.Position;
import com.krassedudes.streaming_systems.models.commands.VehicleCommand;

public class ReadRepository implements Query {

    // this is rather arbitrary but we should not try to find
    // exact matches on float/double vectors.
    private static final double CLOSENESS_THRESHOLD = 0.5f;

    protected Consumer queryDatabase = null;
    protected HashMap<String, VehicleDTO> vehicles = new HashMap<>();
    protected static ReadRepository instance = null;

    private ReadRepository(String bootstrapServers, String topic) {
        this.queryDatabase = new Consumer(bootstrapServers, topic, payload -> {
            try {
                this.handleEvent(VehicleCommand.fromJsonString(payload));
            } catch(Exception e) {
                System.err.println(e);
            }
        });
    }

    public void close() {
        this.queryDatabase.close();
    }

    public static ReadRepository getInstance() {
        if(instance == null) {
            instance = new ReadRepository(App.SERVER_HOST, App.VEHICLE_TOPIC);
        }

        return instance;
    }

    private void handleEvent(VehicleCommand event) {
        event.applyToQueryModel(this.vehicles);
    }

    @Override
    public VehicleDTO getVehicleByName(String name) {
        return this.vehicles.get(name);
    }

    @Override
    public Enumeration<VehicleDTO> getVehicles() {
        return Collections.enumeration(this.vehicles.values());
    }

    @Override
    public Enumeration<VehicleDTO> getVehiclesAtPosition(Position position) {

        Predicate<VehicleDTO> findCloseVehicles = (VehicleDTO v) -> {
            return v.getPosition().distance(position) <= CLOSENESS_THRESHOLD;
        };

        return Collections.enumeration(this.vehicles
                            .values()
                            .stream()
                            .filter(findCloseVehicles)
                            .toList());
    }
    
}
