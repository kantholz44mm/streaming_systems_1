package com.krassedudes.ss;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.Instant;

public class App {

    private static void run_consumer() throws Exception
    {
        var consumer = new Consumer("tcp://localhost:61616", "LIDARRAW", "admin", "admin", (String message) -> {
            System.out.println(message);
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
