package com.gridline.wiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GRIDLINE 接线图后端启动类。
 *
 * <p>启动后默认监听 {@code 8080} 端口（见 {@code application.yml}），提供
 * {@code /api/diagrams} 系列接口。应用启动时 Flyway 会自动执行
 * {@code classpath:db/migration} 下的数据库迁移脚本；JPA 负责实体映射，
 * 但不会自动建表（{@code ddl-auto=validate}，只校验实体与表结构是否一致），
 * 表结构以迁移脚本为唯一事实来源。</p>
 */
@SpringBootApplication
public class WiringDiagramApplication {

    public static void main(String[] args) {
        SpringApplication.run(WiringDiagramApplication.class, args);
    }
}
