package com.gridline.wiring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置，目前只负责跨域（CORS）。
 *
 * <p>开发环境下前端由 Vite 在 5173 端口提供，后端在 8080 端口，必须放行跨域请求。
 * 默认允许本机及常见私网网段，方便局域网内的其他设备访问；生产环境建议通过环境变量
 * {@code CORS_ALLOWED_ORIGIN_PATTERNS} 收紧为具体域名。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许的来源模式数组，由逗号分隔的配置项解析而来。 */
    private final String[] allowedOriginPatterns;

    /**
     * @param allowedOriginPatterns 逗号分隔的来源模式，支持 {@code *} 通配；
     *                              通过 {@code app.cors.allowed-origin-patterns} 注入
     */
    public WebConfig(
            @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns.split("\\s*,\\s*");
    }

    /**
     * 为 {@code /api/**} 配置跨域规则。
     *
     * <p>只放行接口路径，静态资源不参与跨域；允许常用 HTTP 方法及全部请求头，
     * 预检结果缓存 1 小时（3600 秒），减少浏览器重复发送 OPTIONS 请求。</p>
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
