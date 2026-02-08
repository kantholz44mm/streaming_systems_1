package com.krassedudes.streaming_systems.lidar;

import com.krassedudes.streaming_systems.models.LidarData;

public class ScanGrouper {

	private int current_index = 0;
	private double current_angle = 0.0;

    public int processLidarData(LidarData data)
    {
        if(data.angle < current_angle)
        {
            current_index++;
        }

        current_angle = data.angle;
        return current_index;
    }
}
