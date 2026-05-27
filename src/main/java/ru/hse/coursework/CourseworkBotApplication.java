package ru.hse.coursework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CourseworkBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseworkBotApplication.class, args);
    }
}
