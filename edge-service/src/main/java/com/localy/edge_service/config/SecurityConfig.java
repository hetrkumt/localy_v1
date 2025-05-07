package com.localy.edge_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.Customizer; // 이 임포트 문을 추가해야 합니다.
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig {
    // application.yml에서 issuer-uri 값을 직접 주입받습니다.
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    // ReactiveJwtDecoder 빈을 수동으로 정의합니다.
    // Spring Security는 이 빈을 사용하여 JWT를 디코딩하고 검증합니다.
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        System.out.println("--- SecurityConfig: ReactiveJwtDecoder 빈 생성 시작 ---"); // 로그 추가
        System.out.println("--- SecurityConfig: issuerUri 값 확인: " + issuerUri + " ---"); // application.yml에서 가져온 값 확인 로그
        // NimbusReactiveJwtDecoder.withIssuerLocation()을 사용하면
        // 자동으로 .well-known/openid-configuration에서 Discovery 문서를 가져오고
        // 그 안의 jwks_uri를 찾아 공개 키를 다운로드합니다.
        ReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();
        System.out.println("--- SecurityConfig: ReactiveJwtDecoder 빈 생성 완료 ---"); // 로그 추가
        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // ReactiveClientRegistrationRepository clientRegistrationRepository
        // 이 파라미터는 이 securityWebFilterChain 빈 정의 자체에서는 사용되지 않으므로 제거했습니다.
        // 다른 곳에서 필요하다면 해당 메서드 시그니처는 유지해야 합니다.

        return http
                // === 변경: csrf().disable() 대신 람다 사용 ===
                .csrf(csrf -> csrf.disable()) // CSRF 비활성화
                // =======================================
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/auth/**", "/api/token/**").permitAll() // auth/** 및 token/** 요청 허용
                        .anyExchange().authenticated() // 나머지 요청 차단
                )
                // === 변경: oauth2ResourceServer(...::jwt) 대신 람다 및 Customizer 사용 ===
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())) // OAuth 2.0 Resource Server 활성화 및 JWT 기본 설정 사용
                // =====================================================================
                .build();
    }

    // 만약 OAuth2 Client 기능도 필요하다면 별도의 SecurityWebFilterChain 빈을 정의하거나
    // 위 체인에 OAuth2 Client 설정을 추가해야 합니다.
    // 현재 제공된 코드는 Resource Server 기능만 설정하고 있습니다.
}