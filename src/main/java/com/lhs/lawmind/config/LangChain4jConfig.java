package com.lhs.lawmind.config;

import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j配置类
 * 使用Spring Boot自动配置，通过application.yml中的配置项来配置LangChain4j
 */
@Configuration
public class LangChain4jConfig {
    // 由于使用了spring-boot-starter，LangChain4j会自动根据application.yml中的配置创建所需的Bean
    // 无需手动创建ChatLanguageModel和EmbeddingModel的Bean
}
