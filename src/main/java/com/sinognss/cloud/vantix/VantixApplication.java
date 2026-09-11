package com.sinognss.cloud.vantix;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sinognss.cloud.vantix.infrastructure.mapper")
@EnableScheduling
public class VantixApplication {

    public static void main(String[] args) {
        SpringApplication.run(VantixApplication.class, args);
    }
}
