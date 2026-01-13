package com.krassedudes.streaming_systems.models.commands;

import java.util.HashMap;
import java.util.HashSet;

import com.krassedudes.streaming_systems.interfaces.VehicleDTO;
import com.krassedudes.streaming_systems.models.VehicleInfo;

public class VehicleCommandRemove extends VehicleCommand {
    public VehicleCommandRemove(String name) {
        super(name);
    }

    @Override
    public void applyToQueryModel(HashMap<String, VehicleDTO> queryModel) {
        queryModel.remove(this.name);
    }

    @Override
    public String toJsonString()
    {
        return "{" + "\"type\": \"remove\", \"name\": \"" + this.name + "}";
    }

    @Override
    public void applyToDomainModel(HashSet<String> domainModel) {
        domainModel.remove(this.name);
    }
}
