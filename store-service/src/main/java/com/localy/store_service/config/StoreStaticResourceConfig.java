package com.localy.store_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
// WebFluxConfigurer 인터페이스를 구현하여 웹 설정을 커스터마이징합니다.
public class StoreStaticResourceConfig implements WebFluxConfigurer {

    // 정적 리소스 핸들러를 추가하는 메소드입니다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // "/images/" 경로로 들어오는 모든 요청에 대해
        registry.addResourceHandler("/images/**")
                // 파일 시스템의 "/opt/app/images/" 디렉토리에서 리소스를 찾도록 설정합니다.
                // "file:" 접두사는 파일 시스템 경로임을 나타냅니다.
                // 이 경로는 store-service가 실행되는 서버의 파일 시스템 경로입니다.
                .addResourceLocations("file:/opt/app/images/");
    }
}
