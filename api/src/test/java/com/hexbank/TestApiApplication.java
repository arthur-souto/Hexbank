package com.hexbank;

import com.hexbank.bootstrap.ApiApplication;
import org.springframework.boot.SpringApplication;

public class TestApiApplication {
    public static void main(String[] args) {
        SpringApplication.from(ApiApplication::main)
                .with(TestContainersConfiguration.class)
                .run(args);
    }
}
