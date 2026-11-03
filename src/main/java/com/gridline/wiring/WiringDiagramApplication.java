package com.gridline.wiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GRIDLINE 接线图后端启动类。
 *
 * <p>启动后默认监听 {@code 8080} 端口，提供 {@code /api/diagrams} 系列接口。
 * 数据访问使用 MyBatis-Plus，Mapper 通过 {@code @Mapper} 注解自动扫描；
 * 表结构由 {@code classpath:db/schema.sql} 在启动时幂等创建。
 * 项目不再引入 JPA 与 Flyway。</p>
 */
@SpringBootApplication
public class WiringDiagramApplication {

    public static void main(String[] args) {
        SpringApplication.run(WiringDiagramApplication.class, args);
    }
}
