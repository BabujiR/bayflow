package com.bayflow.workshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkshopServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkshopServiceApplication.class, args);
    }
}
