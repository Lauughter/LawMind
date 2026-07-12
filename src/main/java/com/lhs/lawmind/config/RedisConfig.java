package com.lhs.lawmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 配置类
 * 使用 Jedis 连接池，连接失败时由连接池自动重试，不会返回 null
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password}")
    private String password;

    @Value("${spring.data.redis.database}")
    private int database;

    /**
     * 配置 Redis 连接工厂，使用 Jedis 连接池
     * <p>连接池会自动管理连接的重试和恢复，不会返回 null，即使 Redis 服务暂时不可用</p>
     * <p>连接配置包括主机、端口、密码和数据库索引，以及连接和读取超时设置</p>
     * <p>日志记录连接配置，帮助调试和监控 Redis 连接状态</p>
     * @return RedisConnectionFactory 配置好的连接工厂实例
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection: host={}, port={}, database={}", host, port, database);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        config.setPassword(password.isEmpty() ? null : password);
        config.setDatabase(database);

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .usePooling()
                .build();

        JedisConnectionFactory factory = new JedisConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();

        log.info("Redis 连接工厂已创建（Jedis）。实际连接由连接池在运行时管理。");
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
