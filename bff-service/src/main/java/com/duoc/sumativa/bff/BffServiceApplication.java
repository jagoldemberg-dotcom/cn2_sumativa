package com.duoc.sumativa.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.duoc.sumativa.bff.config.FunctionsProperties;

/**
 * Entry point for the BFF (Backend for Frontend) service.
 *
 * This service does NOT talk to the database directly. It only orchestrates
 * HTTP calls to the users-function and roles-function Azure Functions, which
 * are the ones performing the actual CRUD operations against Oracle.
 */
@SpringBootApplication
@EnableConfigurationProperties(FunctionsProperties.class)
public class BffServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffServiceApplication.class, args);
    }
}
