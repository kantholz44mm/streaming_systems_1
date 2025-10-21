package com.krassedudes.ss;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Instant;

import com.github.cliftonlabs.json_simple.JsonException;

public class App {

    private static void run_consumer() throws Exception
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

        Instant wait_until = Instant.now().plusSeconds(60);

        while(Instant.now().isBefore(wait_until))
        {
            // Do a whole lot of nothing.
            Thread.sleep(1000);
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


            if(arg.compareTo("--consumer") == 0)
            {
                App.run_consumer();
                break;
            }
        }
    }
}
