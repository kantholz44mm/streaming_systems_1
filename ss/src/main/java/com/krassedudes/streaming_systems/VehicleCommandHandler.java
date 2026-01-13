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

    protected HashSet<String> usedNames = new HashSet<>();
    protected Publisher eventStore = null;

    public VehicleCommandHandler(String bootstrapServers, String topic, String user, String pass) {
        this.eventStore = new Publisher(bootstrapServers, topic, user, pass);
    }

    public void close() {
        this.eventStore.close();
    }

    @Override
    public void createVehicle(String name, Position startPosition) throws Exception {
        if(!usedNames.add(name)) {
            throw new DuplicateKeyException("The name " + name + " is already created.");
        }

        VehicleCommand createCommand = new VehicleCommandCreate(name, startPosition);
        this.eventStore.publish(createCommand.toJsonString());
    }

    @Override
    public void moveVehicle(String name, Position moveVector) throws Exception {
        if(!usedNames.contains(name)) {
            throw new InvalidKeyException("The name " + name + " has not been created yet.");
        }

        VehicleCommand moveCommand = new VehicleCommandMove(name, moveVector);
        this.eventStore.publish(moveCommand.toJsonString());
    }

    @Override
    public void removeVehicle(String name) throws Exception {
        if(!usedNames.remove(name)) {
            throw new InvalidKeyException("The name " + name + " has not been created yet.");
        }

        VehicleCommand removeCommand = new VehicleCommandRemove(name);
        this.eventStore.publish(removeCommand.toJsonString());
    }
    
}
