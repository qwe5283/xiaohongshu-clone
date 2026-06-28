package com.xiaohongshu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / SpringDoc OpenAPI 配置
 *
 * <p>访问地址：
 * <ul>
 *   <li>Knife4j UI: <a href="http://localhost:8080/api/doc.html">/api/doc.html</a></li>
 *   <li>OpenAPI JSON: <a href="http://localhost:8080/api/v3/api-docs">/api/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("小红书（复刻）后端 API 文档")
                        .description("基于 Spring Boot 3 + Knife4j 的 RESTful API 接口文档。"
                                + "包含用户管理、笔记管理、评论、点赞、收藏、关注、文件上传等模块。")
                        .version("1.0.0")
                        .contact(new Contact()
                                .url("https://github.com")
                        )
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")
                        )
                );
    }
}
