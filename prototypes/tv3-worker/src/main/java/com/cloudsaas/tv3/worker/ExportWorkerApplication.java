package com.cloudsaas.tv3.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WorkerProperties.class)
public class ExportWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExportWorkerApplication.class, args);
    }
}
