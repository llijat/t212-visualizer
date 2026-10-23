package com.llijat.t212visualizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. @EnableScheduling is on from day one so that when you add the
 * refresh job in Phase 1.5 (poll T212 every N minutes) you don't have to
 * remember to come back and add it later.
 */
@SpringBootApplication
@EnableScheduling
public class T212VisualizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(T212VisualizerApplication.class, args);
    }
}
