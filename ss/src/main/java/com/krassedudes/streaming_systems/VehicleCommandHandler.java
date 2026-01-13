package com.krassedudes.streaming_systems;

import java.security.InvalidKeyException;
import java.util.HashSet;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;

import com.krassedudes.streaming_systems.interfaces.VehicleCommands;
import com.krassedudes.streaming_systems.models.Position;
import com.krassedudes.streaming_systems.models.commands.VehicleCommand;
import com.krassedudes.streaming_systems.models.commands.VehicleCommandCreate;
import com.krassedudes.streaming_systems.models.commands.VehicleCommandMove;
import com.krassedudes.streaming_systems.models.commands.VehicleCommandRemove;

public class VehicleCommandHandler implements VehicleCommands {

    protected Publisher eventStore = null;
    protected ReadRepository readRepository = null;
    private static VehicleCommandHandler instance = null;

    private VehicleCommandHandler(String host, String topic) {
        this.eventStore = new Publisher(host, topic);
        this.readRepository = ReadRepository.getInstance();
    }

    public static VehicleCommandHandler getInstance() {
        if(instance == null) {
            instance = new VehicleCommandHandler(App.SERVER_HOST, App.VEHICLE_TOPIC);
        }
        return instance;
    }

    public void close() {
        this.eventStore.close();
    }

    private boolean vehicleExists(String name) {
        return readRepository.getVehicleByName(name) != null;
    }

    @Override
    public void createVehicle(String name, Position startPosition) throws Exception {
        if(vehicleExists(name)) {
            throw new DuplicateKeyException("The name " + name + " is already created.");
        }

        VehicleCommand createCommand = new VehicleCommandCreate(name, startPosition);
        this.eventStore.publish(createCommand.toJsonString());
    }

    @Override
    public void moveVehicle(String name, Position moveVector) throws Exception {
        if(vehicleExists(name)) {
            throw new InvalidKeyException("The name " + name + " has not been created yet.");
        }

        VehicleCommand moveCommand = new VehicleCommandMove(name, moveVector);
        this.eventStore.publish(moveCommand.toJsonString());
    }

    @Override
    public void removeVehicle(String name) throws Exception {
        if(!vehicleExists(name)) {
            throw new InvalidKeyException("The name " + name + " has not been created yet.");
        }

        VehicleCommand removeCommand = new VehicleCommandRemove(name);
        this.eventStore.publish(removeCommand.toJsonString());
    }
    
}
