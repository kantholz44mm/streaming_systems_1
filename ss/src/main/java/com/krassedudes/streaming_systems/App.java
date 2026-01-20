package com.krassedudes.streaming_systems;

import com.krassedudes.streaming_systems.models.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.dao.DuplicateKeyException;

public class App {

    public static final String SERVER_HOST = "localhost:9092";
    public static final String VEHICLE_TOPIC = "VEHICLE_EVENT_STORE";

    private static void ex3_run_consumer_group() throws Exception
    {
        var scan_grouper = new ScanGrouper();
        var publisher = new Publisher(SERVER_HOST, "LIDARGROUPED");

        var consumer = new Consumer(SERVER_HOST, "LIDARRAW", (String message) -> {
            try
            {
                var lidar_data = LidarData.fromJsonString(message);
                int scan_group = scan_grouper.processLidarData(lidar_data);
                var grouped_lidar_data = new LidarDataGrouped(lidar_data, scan_group);
                publisher.publish(grouped_lidar_data.toJsonString());
                System.out.println(grouped_lidar_data.toJsonString());
            }
            catch(Exception e)
            {
                // ignore invalid messages
            }
        });

        synchronized(System.in)
        {
            System.in.wait();
        }
        consumer.close();
    }

    private static void ex3_run_consumer_distance() throws Exception
    {
        var publisher = new Publisher(SERVER_HOST, "LIDARDISTANCE");
        AtomicReference<Position> last_coordinate = new AtomicReference<Position>(null);
        AtomicReference<LidarDataGrouped> last_message = new AtomicReference<LidarDataGrouped>(null);
        
        var consumer = new Consumer(SERVER_HOST, "LIDARGROUPED", (String message) -> {
            try
            {
                var lidar_data = LidarDataGrouped.fromJsonString(message);
                Position coordinate = Position.fromPolar(Math.toRadians(lidar_data.angle), lidar_data.distance);
                
                if(last_message.get() != null && last_message.get().group == lidar_data.group)
                {
                    Position delta = coordinate.delta(last_coordinate.get());
                    double distance = delta.length();

                    var payload = new LidarDistance(lidar_data.group, distance);
                    publisher.publish(payload.toJsonString());
                    System.out.println(payload.toJsonString());
                }

                last_message.set(lidar_data);
                last_coordinate.set(coordinate);

            }
            catch(Exception e)
            {
                // ignore invalid messages
            }
        });


        synchronized(System.in)
        {
            System.in.wait();
        }
        consumer.close();
    }

    private static void ex3_run_consumer_summation() throws Exception
    {
        AtomicReference<Double> current_distance = new AtomicReference<Double>(0.0);
        AtomicReference<Integer> current_scan_group = new AtomicReference<Integer>(0);

        var consumer = new Consumer(SERVER_HOST, "LIDARDISTANCE", (String message) -> {
            try
            {
                var lidar_distance = LidarDistance.fromJsonString(message);
                if(lidar_distance.group != current_scan_group.get())
                {
                    var payload = new LidarDistance(current_scan_group.get(), current_distance.get());
                    System.out.println(payload.toJsonString());
                    current_scan_group.set(lidar_distance.group);
                    current_distance.set(0.0);
                }
                else
                {
                    double new_distance = current_distance.get() + lidar_distance.distance;
                    current_distance.set(new_distance);
                }
            }
            catch(Exception e)
            {
                // ignore invalid messages
            }
        });

        synchronized(System.in)
        {
            System.in.wait();
        }
        consumer.close();
    }

    private static void ex3_run_publisher() throws Exception
    {
        Publisher publisher = new Publisher(SERVER_HOST, "LIDARRAW");

        try (FileReader reader = new FileReader("resources/Lidar-scans.json"))
        {
            BufferedReader lineReader = new BufferedReader(reader);
            
            lineReader.lines().map(LidarData::fromJsonString)
                              .filter((x) -> x != null)
                              .filter((LidarData d) -> d.quality >= 15)
                              .map((LidarData d) -> d.toJsonString())
                              .forEach((String payload) -> publisher.publish(payload));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        publisher.close();
    }

    private static void ex4_run() throws Exception {
        ReadRepository readRepository = ReadRepository.getInstance();
        VehicleCommandHandler commandHandler = VehicleCommandHandler.getInstance();

        // give our projection time to get the latest data and build up our query model
        Thread.sleep(10000);

        try {
            commandHandler.createVehicle("VW Golf mit Allrad", new Position(50, 42));
            commandHandler.moveVehicle("VW Golf mit Allrad", new Position(12.4, 4.13));
        } catch(DuplicateKeyException e) {
            // this may happen when we ran this program before and the key 
            // "VW Golf mit Allrad" already exists.
            System.err.println(e);
        }

        synchronized(System.in)
        {
            System.in.wait();
        }

        readRepository.close();
        commandHandler.close();
    }

    public static void main(String[] args) throws Exception
    {
        for(String arg : args)
        {
            if(arg.compareTo("--ex3_publisher") == 0)
            {
                App.ex3_run_publisher();
                break;
            }

            if(arg.compareTo("--ex3_consumer_group") == 0)
            {
                App.ex3_run_consumer_group();
                break;
            }

            if(arg.compareTo("--ex3_consumer_distance") == 0)
            {
                App.ex3_run_consumer_distance();
                break;
            }

            if(arg.compareTo("--ex3_consumer_summation") == 0)
            {
                App.ex3_run_consumer_summation();
                break;
            }

            if(arg.compareTo("--ex4") == 0)
            {
                App.ex4_run();
                break;
            }
        }
    }
}
