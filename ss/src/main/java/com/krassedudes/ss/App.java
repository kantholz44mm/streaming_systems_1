package com.krassedudes.ss;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
        try (FileReader reader = new FileReader("resources/Lidar-scans.json")) {
            BufferedReader lineReader = new BufferedReader(reader);
            lineReader.lines().map(LidarData::fromJsonString)
                              .filter((x) -> x != null)
                              .forEach((LidarData d) -> System.out.println(d.toJsonString()));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
