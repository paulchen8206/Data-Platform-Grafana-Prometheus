package com.example.spark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SparkKafkaJob {

    public static void main(String[] args) {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        System.setProperty(
                "spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration");
        SpringApplication.run(SparkKafkaJob.class, args);
    }
}
