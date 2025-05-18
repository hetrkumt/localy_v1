package com.localy.edge_service.config;

import jakarta.annotation.PostConstruct;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication; // Authentication 임포트
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken; // JwtAuthenticationToken 임포트
import org.springframework.stereotype.Component; // Component 임포트
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component // Spring 빈으로 등록
public class UserIdForwardingFilter implements GlobalFilter, Ordered {

    @PostConstruct
    public void init() {
        System.out.println("--- UserIdForwardingFilter: Bean Created and Initialized Successfully! ---");
    }

    @Override
    public int getOrder() {
        // Ordered.HIGHEST_PRECEDENCE는 Integer.MIN_VALUE (가장 먼저)
        // TokenRelay는 보통 Ordered.HIGHEST_PRECEDENCE + 1000 정도의 순서를 가집니다.
        // 여기서는 예시로 0을 사용했습니다. 필요에 따라 조정하세요.
        return 0;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        System.out.println("--- UserIdForwardingFilter: 필터 시작 ---");

        // ReactiveSecurityContextHolder에서 SecurityContext를 가져옵니다.
        // getContext()는 Mono<SecurityContext>를 반환합니다.
        // 인증되지 않은 요청의 경우, 이 Mono는 비어있거나 (Mono.empty()) null 값을 발행할 수 있습니다.
        return ReactiveSecurityContextHolder.getContext()
                // flatMap을 사용하여 SecurityContext Mono가 값을 발행할 때만 다음 로직을 실행합니다.
                // 람다의 인자 context는 Mono가 발행한 값이며, 경우에 따라 null일 수 있습니다.
                .flatMap(context -> {
                    // *** SecurityContext가 null인지 안전하게 먼저 확인합니다. ***
                    if (context != null) {
                        System.out.println("--- UserIdForwardingFilter: SecurityContext 존재 ---");
                        // SecurityContext가 null이 아닐 때만 Authentication 객체를 가져옵니다.
                        Authentication authentication = context.getAuthentication();

                        if (authentication != null) {
                            System.out.println("--- UserIdForwardingFilter: 인증 객체 존재, 타입: " + authentication.getClass().getName() + " ---");

                            // 인증 객체가 JwtAuthenticationToken 타입인지 확인합니다.
                            if (authentication instanceof JwtAuthenticationToken) {
                                JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) authentication;
                                // JWT 토큰에서 subject (사용자 ID) 클레임을 추출합니다.
                                String userId = jwtAuthenticationToken.getToken().getSubject();
                                System.out.println("--- UserIdForwardingFilter: JWT 인증 성공, 추출된 사용자 ID (sub): " + userId + " ---");

                                // 추출된 사용자 ID가 유효하면 (null 또는 비어있지 않으면)
                                if (userId != null && !userId.isEmpty()) {
                                    System.out.println("--- UserIdForwardingFilter: X-User-Id 헤더 추가 중: " + userId + " ---");
                                    // 요청 객체를 변경하여 X-User-Id 헤더를 추가합니다.
                                    ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                                            .header("X-User-Id", userId)
                                            .build();
                                    System.out.println("--- UserIdForwardingFilter: X-User-Id 헤더 추가 완료 ---");
                                    // 헤더가 추가된 변경된 요청으로 다음 필터 체인을 계속 진행합니다.
                                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                                } else {
                                    System.out.println("--- UserIdForwardingFilter: JWT 인증은 성공했으나 사용자 ID(sub)가 null 또는 비어있음 ---");
                                }
                            } else {
                                System.out.println("--- UserIdForwardingFilter: 인증 객체가 JWT 인증 토큰 타입이 아님 ---");
                            }
                        } else {
                            System.out.println("--- UserIdForwardingFilter: 인증 객체가 null입니다. ---"); // Authentication이 null인 경우 로그
                        }
                    } else {
                        System.out.println("--- UserIdForwardingFilter: SecurityContext가 null입니다. ---"); // SecurityContext가 null인 경우 로그
                    }

                    // SecurityContext가 없거나, 유효한 인증/JWT 토큰이 없었거나, 사용자 ID를 추출할 수 없었으면
                    // X-User-Id 헤더를 추가하지 않고 원래 요청으로 다음 필터 체인을 계속 진행합니다.
                    System.out.println("--- UserIdForwardingFilter: 헤더 추가 없이 체인 계속 진행 ---");
                    return chain.filter(exchange);
                })
                // switchIfEmpty는 getContext() Mono가 아무 값도 발행하지 않고 비어있을 때 (Mono.empty()일 때) 실행됩니다.
                // 이 경우 flatMap 람다는 실행되지 않고 switchIfEmpty 블록이 실행됩니다.
                .switchIfEmpty(Mono.defer(() -> {
                    System.out.println("--- UserIdForwardingFilter: Security Context Mono 비어있음 (switchIfEmpty) ---"); // Mono.empty()인 경우 로그
                    // 헤더 추가 없이 원래 요청으로 다음 필터 체인을 계속 진행합니다.
                    return chain.filter(exchange);
                }))
                // doFinally는 스트림이 완료(성공 또는 오류)될 때 실행됩니다.
                .doFinally(signalType -> System.out.println("--- UserIdForwardingFilter: 필터 종료 (" + signalType + ") ---"));
    }
}
