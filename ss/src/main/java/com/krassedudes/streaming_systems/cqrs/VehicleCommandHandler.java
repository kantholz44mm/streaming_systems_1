package com.krassedudes.streaming_systems.cqrs;

import java.security.InvalidKeyException;
import java.util.Enumeration;
import java.util.function.Predicate;

import javax.management.InvalidAttributeValueException;

import org.springframework.dao.DuplicateKeyException;

import com.krassedudes.streaming_systems.App;
import com.krassedudes.streaming_systems.Publisher;
import com.krassedudes.streaming_systems.cqrs.commands.VehicleCommand;
import com.krassedudes.streaming_systems.cqrs.commands.VehicleCommandCreate;
import com.krassedudes.streaming_systems.cqrs.commands.VehicleCommandMove;
import com.krassedudes.streaming_systems.cqrs.commands.VehicleCommandRemove;
import com.krassedudes.streaming_systems.cqrs.interfaces.VehicleCommands;
import com.krassedudes.streaming_systems.cqrs.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.Position;

public class VehicleCommandHandler implements VehicleCommands {

    protected Publisher eventStore = null;
    protected ReadRepository readRepository = null;
    private static VehicleCommandHandler instance = null;

    public static final int MAX_VEHICLE_MOTIONS = 5;

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

    private boolean vehicleMotionLimitReached(String name) {
        VehicleDTO vehicle = readRepository.getVehicleByName(name);
        return vehicle != null && vehicle.getNumberOfMoves() >= MAX_VEHICLE_MOTIONS;
    }

    private void removeObstacles(String name, Position moveVector) {
        VehicleDTO vehicle = readRepository.getVehicleByName(name);

        if(vehicle == null) {
            return;
        }

        Position newPosition = vehicle.getPosition().add(moveVector);

        Enumeration<VehicleDTO> obstacles = readRepository.getVehiclesAtPosition(newPosition);
        while(obstacles.hasMoreElements()) {
            VehicleDTO obstacle = obstacles.nextElement();
            VehicleCommand removeCommand = new VehicleCommandRemove(obstacle.getName());
            this.eventStore.publish(removeCommand.toJsonString());
        }
    }

    private boolean vehicleWouldReturnToPreviousPosition(String name, Position movement) {
        VehicleDTO vehicle = readRepository.getVehicleByName(name);
        if(vehicle == null) {
            return false;
        }
        
        Position newPosition = vehicle.getPosition().add(movement);
        Predicate<Position> findClosePositions = (Position p) -> {
            return p.distance(newPosition) <= ReadRepository.CLOSENESS_THRESHOLD;
        };

        return vehicle.getPreviousPositions().stream().anyMatch(findClosePositions);
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
        
        if(moveVector.x() == 0 && moveVector.y() == 0) {
            throw new InvalidAttributeValueException("Movement vector cannot be 0");
        }

        if(vehicleMotionLimitReached(name) || vehicleWouldReturnToPreviousPosition(name, moveVector)) {
            this.eventStore.publish(new VehicleCommandRemove(name).toJsonString());
        } else {
            removeObstacles(name, moveVector);
            this.eventStore.publish(new VehicleCommandMove(name, moveVector).toJsonString());
        }
        
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
