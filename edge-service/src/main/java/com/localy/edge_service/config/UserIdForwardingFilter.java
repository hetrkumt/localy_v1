package com.localy.edge_service.config;

import jakarta.annotation.PostConstruct;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest; // Reactor Netty 기반 요청 객체 import
import org.springframework.security.core.Authentication; // Spring Security 인증 객체 import
import org.springframework.security.core.context.ReactiveSecurityContextHolder; // Reactive Security Context import
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken; // JWT 인증 토큰 import
import org.springframework.stereotype.Component; // Spring Bean으로 등록하기 위한 Component 어노테이션
import org.springframework.web.server.ServerWebExchange; // Gateway 요청/응답 컨텍스트 import
import reactor.core.publisher.Mono; // Reactive 프로그래밍 Mon

@Component
public class UserIdForwardingFilter implements GlobalFilter, Ordered {
    @PostConstruct // 이 메서드가 빈 생성 후 호출됩니다.
    public void init() {
        System.out.println("--- UserIdForwardingFilter: Bean Created and Initialized Successfully! ---"); // 이 로그가 보이면 빈 등록 성공!
    }


    // 필터의 실행 순서 정의 (낮을수록 먼저 실행)
    // Spring Security 필터 체인 이후, 라우팅 필터 이전에 실행되도록 적절한 순서를 부여합니다.
    // 기본 필터들보다 먼저 실행되도록 -1000과 같이 낮은 값을 줄 수도 있습니다.
    // 여기서는 예시로 -1을 사용했습니다. 다른 필터와의 순서 충돌 시 조정이 필요합니다.
    @Override
    public int getOrder() {
        // Spring Security 필터들(TokenRelay 등) 이후에 실행되어야 인증 객체 접근 가능
        // Gateway 기본 필터들 (Path, Route 등) 보다는 먼저 실행되어야 헤더 추가 후 라우팅 가능
        // TokenRelay 필터의 기본 순서보다 약간 낮게 설정하는 것이 일반적입니다.
        // TokenRelay 기본 순서는 Ordered.HIGHEST_PRECEDENCE + 1000 (매우 낮은 숫자)
        // 따라서 0이나 양수로 설정하면 TokenRelay 뒤에 실행될 가능성이 높습니다.
        // 안전하게 -100 정도로 설정해 볼 수 있습니다.
        return -100; // 예시 순서, 필요에 따라 조정
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        System.out.println("--- UserIdForwardingFilter: 필터 시작 ---"); // 필터 진입 로그
        return ReactiveSecurityContextHolder.getContext()
                .doOnSuccess(context -> { // 인증 객체 확인 성공 시 로그
                    if (context.getAuthentication() != null) {
                        System.out.println("--- UserIdForwardingFilter: 인증 객체 존재, 타입: " + context.getAuthentication().getClass().getName() + " ---");
                    } else {
                        System.out.println("--- UserIdForwardingFilter: 인증 객체 없음 ---");
                    }
                })
                .map(context -> context.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication instanceof JwtAuthenticationToken) {
                        JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) authentication;
                        String userId = jwtAuthenticationToken.getToken().getSubject();
                        System.out.println("--- UserIdForwardingFilter: JWT 인증 성공, 추출된 사용자 ID (sub): " + userId + " ---"); // 사용자 ID 추출 로그

                        if (userId != null && !userId.isEmpty()) {
                            System.out.println("--- UserIdForwardingFilter: X-User-Id 헤더 추가 중: " + userId + " ---"); // 헤더 추가 시도 로그
                            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                    .header("X-User-Id", userId)
                                    .build();
                            System.out.println("--- UserIdForwardingFilter: X-User-Id 헤더 추가 완료 ---"); // 헤더 추가 완료 로그
                            return chain.filter(exchange.mutate().request(modifiedRequest).build());
                        } else {
                            System.out.println("--- UserIdForwardingFilter: JWT 인증은 성공했으나 사용자 ID(sub)가 null 또는 비어있음 ---"); // 사용자 ID 누락 로그
                        }
                    } else {
                        System.out.println("--- UserIdForwardingFilter: JWT 인증 토큰 타입 아님 ---"); // JWT 토큰 타입 아님 로그
                    }
                    System.out.println("--- UserIdForwardingFilter: 헤더 추가 없이 체인 계속 진행 ---"); // 헤더 미추가 로그
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> { // Security Context가 비어있는 경우 로그
                    System.out.println("--- UserIdForwardingFilter: Security Context 비어있음 ---");
                    return chain.filter(exchange);
                }))
                .doFinally(signalType -> System.out.println("--- UserIdForwardingFilter: 필터 종료 (" + signalType + ") ---")); // 필터 종료 로그
    }

}