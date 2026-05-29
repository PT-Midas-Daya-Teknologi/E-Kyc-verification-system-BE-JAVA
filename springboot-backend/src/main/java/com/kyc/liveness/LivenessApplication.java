package com.kyc.liveness;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class LivenessApplication {

    public static void main(String[] args) {
        SpringApplication.run(LivenessApplication.class, args);
    }
}
