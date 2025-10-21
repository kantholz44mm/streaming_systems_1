package com.krassedudes.ss;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import com.github.cliftonlabs.json_simple.JsonException;

public class App {

    private static void run_consumer_group() throws Exception
    {
        var scan_grouper = new ScanGrouper();
        var publisher = new Publisher("tcp://localhost:61616", "LIDARGROUPED", "admin", "admin");

        var consumer = new Consumer("tcp://localhost:61616", "LIDARRAW", "admin", "admin", (String message) -> {
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

    private static void run_consumer_distance() throws Exception
    {
        var publisher = new Publisher("tcp://localhost:61616", "LIDARDISTANCE", "admin", "admin");
        AtomicReference<Vector2D> last_coordinate = new AtomicReference<Vector2D>(null);
        AtomicReference<LidarDataGrouped> last_message = new AtomicReference<LidarDataGrouped>(null);
        
        var consumer = new Consumer("tcp://localhost:61616", "LIDARGROUPED", "admin", "admin", (String message) -> {
            try
            {
                var lidar_data = LidarDataGrouped.fromJsonString(message);
                Vector2D coordinate = Vector2D.fromPolar(Math.toRadians(lidar_data.angle), lidar_data.distance);
                
                if(last_message.get() != null && last_message.get().group == lidar_data.group)
                {
                    Vector2D delta = coordinate.delta(last_coordinate.get());
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

    private static void run_publisher() throws Exception
    {
        Publisher publisher = new Publisher("tcp://localhost:61616", "LIDARRAW", "admin", "admin");
    
        try (FileReader reader = new FileReader("resources/Lidar-scans.json"))
        {
            BufferedReader lineReader = new BufferedReader(reader);
            lineReader.lines().map(LidarData::fromJsonString)
                              .filter((x) -> x != null)
                              .map((LidarData d) -> d.toJsonString())
                              .forEach((String payload) -> publisher.publish(payload));
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        publisher.close();
    }

    public static void main(String[] args) throws Exception
    {
        for(String arg : args)
        {
            if(arg.compareTo("--publisher") == 0)
            {
                App.run_publisher();
                break;
            }

            if(arg.compareTo("--consumer_group") == 0)
            {
                App.run_consumer_group();
                break;
            }

            if(arg.compareTo("--consumer_distance") == 0)
            {
                App.run_consumer_distance();
                break;
            }
        }
    }
}
