package com.lhs.lawmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * 安全配置类
 * 该类负责配置应用的安全性，包括 CORS、CSRF、表单登录、HTTP 基本认证等。
 * 它与 HttpSecurity 交互，配置应用的安全策略。
 * 它在应用启动时初始化，配置应用的安全策略。
 * 
 * 安全加固说明：
 * - 核心业务接口（ai-chat、conversation等）已移除permitAll()
 * - 这些接口现在需要JWT拦截器保护
 * 
 * */


@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors()
            .and()
            .authorizeHttpRequests()
                // 所有请求都放行，使用 JwtInterceptor 进行认证
                .anyRequest().permitAll()
            .and()
            .csrf().disable()
            .formLogin().disable()
            .httpBasic().disable();

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}