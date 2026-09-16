package com.sinognss.cloud.vantix;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.sinognss.cloud.vantix.integration.usercenter.UserCenterFeignService;

@SpringBootApplication(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
@MapperScan("com.sinognss.cloud.vantix.infrastructure.mapper")
@EnableFeignClients(basePackageClasses = UserCenterFeignService.class)
@EnableScheduling
public class VantixApplication {

    public static void main(String[] args) {
        SpringApplication.run(VantixApplication.class, args);
    }
}
