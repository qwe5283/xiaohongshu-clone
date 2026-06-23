package com.xiaohongshu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.common.result.ResultCode;
import com.xiaohongshu.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 公开接口 - 登录注册
                        .requestMatchers("/user/login", "/user/register").permitAll()
                        // 公开接口 - 只读查询（GET）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/user/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/post/list").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/post/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/post/user/*").permitAll()
                        // 评论查询（公开）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/comment/post/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/comment/replies/*").permitAll()
                        // 关注列表/粉丝列表/数量查询（公开）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/follow/following/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/follow/followers/*").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/follow/count/*").permitAll()
                        // 静态资源
                        .requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )
                // 未认证时返回401 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            Result<?> result = Result.error(ResultCode.USER_NOT_LOGIN);
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
