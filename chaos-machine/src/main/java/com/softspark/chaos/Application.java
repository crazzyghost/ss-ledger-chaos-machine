package com.softspark.chaos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Ledger Chaos Machine application.
 * <p>
 * The Chaos Machine is a testing tool that publishes controlled event sequences
 * to Kafka topics consumed by the ledger service, enabling validation of event
 * processing, edge cases, and error handling under various conditions.
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
