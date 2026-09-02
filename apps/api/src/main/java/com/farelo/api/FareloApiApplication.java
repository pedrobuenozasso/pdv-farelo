package com.farelo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: required by com.farelo.api.outbox.OutboxWorker's
// @Scheduled polling loop (FARELO-060) — the first scheduled task in the
// app, hence the first place this annotation is needed.
@EnableScheduling
@SpringBootApplication
public class FareloApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FareloApiApplication.class, args);
    }

}
