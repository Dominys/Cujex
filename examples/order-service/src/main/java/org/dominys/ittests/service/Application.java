package org.dominys.ittests.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Order service Spring Boot application.
 *
 * <p>Running this (or the repackaged jar / Docker image) starts an embedded web
 * server exposing the Order REST API plus an actuator health endpoint the e2e
 * tests wait on before they start sending traffic.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
