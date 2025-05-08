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


/**
 * Spring Cloud Gateway의 Global Filter로 동작하며,
 * 인증된 사용자의 JWT 토큰에서 사용자 ID (subject)를 추출하여
 * 다운스트림 서비스로 전달되는 요청 헤더에 X-User-Id로 추가하는 필터입니다.
 * 인증되지 않은 요청에 대해서는 헤더를 추가하지 않고 요청을 그대로 통과시킵니다.
 */
@Component // Spring 빈으로 등록
public class UserIdForwardingFilter implements GlobalFilter, Ordered {

    /**
     * 빈 생성 및 초기화 후 호출되는 메서드.
     * 필터가 정상적으로 로드되었는지 확인하기 위한 로그를 출력합니다.
     */
    @PostConstruct
    public void init() {
        System.out.println("--- UserIdForwardingFilter: Bean Created and Initialized Successfully! ---");
    }

    /**
     * 필터 체인에서 이 필터의 실행 순서를 정의합니다.
     * 낮은 값일수록 먼저 실행됩니다.
     * Spring Security 필터들 (예: TokenRelay) 이후에 실행되어야 SecurityContext에 인증 정보가 채워집니다.
     * 하지만 Gateway의 라우팅 관련 필터들보다는 먼저 실행되어야 헤더가 추가된 요청이 라우팅됩니다.
     * 일반적으로 0 또는 양수 값을 사용하여 Security 필터들 뒤에 위치시킵니다.
     * 다른 필터와의 순서 충돌 시 조정이 필요할 수 있습니다.
     */
    @Override
    public int getOrder() {
        // Ordered.HIGHEST_PRECEDENCE는 Integer.MIN_VALUE (가장 먼저)
        // TokenRelay는 보통 Ordered.HIGHEST_PRECEDENCE + 1000 정도의 순서를 가집니다.
        // 여기서는 예시로 0을 사용했습니다. 필요에 따라 조정하세요.
        return 0;
    }

    /**
     * 필터의 핵심 로직을 구현합니다.
     * 요청을 가로채고, SecurityContext에서 사용자 ID를 추출하여 헤더에 추가한 후 다음 필터로 전달합니다.
     *
     * @param exchange 요청 및 응답 정보를 담는 ServerWebExchange 객체
     * @param chain 다음 필터 체인
     * @return 다음 필터 체인의 처리 결과 (Mono<Void>)
     */
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
