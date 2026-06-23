package com.xiaohongshu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 小红书应用启动类
 */
@SpringBootApplication
@MapperScan("com.xiaohongshu.**.mapper")
public class XiaohongshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohongshuApplication.class, args);
    }
}
