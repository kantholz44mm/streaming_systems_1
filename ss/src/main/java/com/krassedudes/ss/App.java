package com.krassedudes.ss;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

import org.springframework.jms.JmsException;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import jakarta.jms.JMSException;

public class App {

    private static void run_consumer() throws Exception
    {
        var consumer = new LidarConsumer();
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
        var publisher = new LidarPublisher();
    
        try (FileReader reader = new FileReader("resources/Lidar-scans.json"))
        {
            BufferedReader lineReader = new BufferedReader(reader);
            lineReader.lines().map(LidarData::fromJsonString)
                              .filter((x) -> x != null)
                              .forEach((LidarData d) -> publisher.publishLidarDataSet(d));
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
