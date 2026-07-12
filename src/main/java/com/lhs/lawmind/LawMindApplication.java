package com.lhs.lawmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LawMindApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LawMindApplication.class);
        app.setWebApplicationType(WebApplicationType.SERVLET); // 显式指定为 Web 应用
        app.run(args);
    }

}
