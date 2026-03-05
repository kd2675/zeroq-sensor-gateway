package com.zeroq.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ZeroqSensorGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZeroqSensorGatewayApplication.class, args);
    }
}
