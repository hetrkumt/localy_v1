package com.localy.userservice.user_service.service;

import com.localy.userservice.user_service.UserRegistrationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KeycloakAdminService {

    private final WebClient keycloakAdminWebClient;
    private final WebClient keycloakTokenWebClient;

    @Value("${keycloak.user-service.client-id}")
    private String clientId;
    @Value("${keycloak.user-service.client-secret}")
    private String clientSecret;
    @Value("${keycloak.default-new-user-role:consumer}")
    private String defaultNewUserRole;

    private final Map<String, AdminToken> adminTokenCache = new ConcurrentHashMap<>();
    private static final String ADMIN_TOKEN_KEY = "admin_api_token";
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 30;

    private static class AdminToken {
        String accessToken;
        long expiresAtMillis;

        AdminToken(String accessToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            this.expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds - TOKEN_EXPIRY_BUFFER_SECONDS) * 1000;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }

    public KeycloakAdminService(WebClient keycloakAdminWebClient, WebClient keycloakTokenWebClient) {
        this.keycloakAdminWebClient = keycloakAdminWebClient;
        this.keycloakTokenWebClient = keycloakTokenWebClient;
    }

    private Mono<String> getAdminAccessToken() {
        AdminToken cachedToken = adminTokenCache.get(ADMIN_TOKEN_KEY);
        if (cachedToken != null && !cachedToken.isExpired()) {
            System.out.println("--- KeycloakAdminService: 유효한 Admin 토큰을 캐시에서 사용합니다. ---");
            return Mono.just(cachedToken.accessToken);
        }

        System.out.println("--- KeycloakAdminService: Admin 토큰이 캐시에 없거나 만료되어 새로 요청합니다. Client ID: " + clientId + " ---");
        return keycloakTokenWebClient.post()
                .uri("/token")
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret)) // clientSecret 로깅은 보안상 주의
                .retrieve()
                // 상세 응답 로깅 추가
                .onStatus(
                        status -> true, // 모든 상태 코드에 대해 로깅
                        response -> response.bodyToMono(String.class)
                                .doOnNext(body -> {
                                    // 응답 본문 로깅은 민감한 정보를 포함할 수 있으므로 주의해서 사용하고, 디버깅 후에 제거하거나 마스킹 처리하는 것이 좋습니다.
                                    System.err.println("--- KeycloakAdminService: 토큰 엔드포인트 응답 상태: " + response.statusCode() + " ---");
                                    System.err.println("--- KeycloakAdminService: 토큰 엔드포인트 응답 본문: " + body + " ---"); // 실제 응답 확인용
                                })
                                .then(Mono.empty()) // 로깅 후에는 본문을 소모하고, 실제 상태 코드에 따른 처리는 retrieve()가 계속 진행
                )
                .bodyToMono(Map.class) // Map으로 변환 시도
                .flatMap(responseMap -> {
                    String newToken = (String) responseMap.get("access_token");
                    Number expiresInNumber = (Number) responseMap.get("expires_in");

                    if (newToken != null && expiresInNumber != null) {
                        long expiresIn = expiresInNumber.longValue();
                        AdminToken newAdminToken = new AdminToken(newToken, expiresIn);
                        adminTokenCache.put(ADMIN_TOKEN_KEY, newAdminToken);
                        System.out.println("--- KeycloakAdminService: 새 Admin 토큰을 발급받아 캐시했습니다. 만료까지 (초): " + expiresIn + " ---");
                        return Mono.just(newToken);
                    } else {
                        // 이 경우는 bodyToMono(Map.class)는 성공했으나, Map 안에 필요한 키가 없는 경우입니다.
                        // 위 onStatus 로깅에서 이미 오류 본문이 출력되었을 가능성이 높습니다.
                        System.err.println("--- KeycloakAdminService: 토큰 엔드포인트 응답에서 access_token 또는 expires_in이 null입니다. 응답 Map: " + responseMap);
                        return Mono.error(new RuntimeException("Keycloak 토큰 엔드포인트 응답에서 access_token 또는 expires_in을 가져오지 못했습니다."));
                    }
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    // 이 블록은 HTTP 상태 코드가 4xx 또는 5xx일 때 실행됩니다.
                    // onStatus에서 이미 본문을 로깅했지만, 여기서도 추가 정보를 얻을 수 있습니다.
                    System.err.println("--- KeycloakAdminService: Admin 토큰 발급 중 WebClientResponseException 발생: " + e.getStatusCode() + ", 응답 본문: " + e.getResponseBodyAsString() + " ---");
                    return Mono.error(new RuntimeException("Keycloak Admin 토큰 발급에 실패했습니다 (HTTP 오류).", e));
                })
                .onErrorResume(Exception.class, e -> {
                    // WebClientResponseException 외의 다른 예외 (예: Map 변환 실패, 네트워크 문제 등)
                    if (!(e instanceof WebClientResponseException || e.getCause() instanceof WebClientResponseException)) {
                        System.err.println("--- KeycloakAdminService: Admin 토큰 발급 중 예상치 못한 오류 발생: " + e.getMessage() + " ---");
                        e.printStackTrace(); // 스택 트레이스 로깅
                    }
                    return Mono.error(new RuntimeException("Keycloak Admin 토큰 발급 중 처리 오류가 발생했습니다.", e));
                });
    }

    // createUser, deleteUser 메소드는 이전과 동일하게 유지

    public Mono<String> createUser(UserRegistrationRequest registrationRequest) {
        Map<String, Object> userRepresentation = new HashMap<>();
        userRepresentation.put("username", registrationRequest.getUsername());
        userRepresentation.put("email", registrationRequest.getEmail());
        userRepresentation.put("firstName", registrationRequest.getFirstName());
        userRepresentation.put("lastName", registrationRequest.getLastName());
        userRepresentation.put("enabled", true);
        userRepresentation.put("emailVerified", false);
        userRepresentation.put("requiredActions", Collections.singletonList("VERIFY_EMAIL"));

        Map<String, Object> passwordCredential = new HashMap<>();
        passwordCredential.put("type", "password");
        passwordCredential.put("value", registrationRequest.getPassword());
        passwordCredential.put("temporary", false);

        userRepresentation.put("credentials", Collections.singletonList(passwordCredential));

        if (defaultNewUserRole != null && !defaultNewUserRole.trim().isEmpty()) {
            userRepresentation.put("realmRoles", Collections.singletonList(defaultNewUserRole));
            System.out.println("--- KeycloakAdminService: 사용자에게 기본 역할 할당: " + defaultNewUserRole + " ---");
        }

        System.out.println("--- KeycloakAdminService: 사용자 생성 시도: " + registrationRequest.getUsername() + " ---");

        return getAdminAccessToken()
                .flatMap(token -> keycloakAdminWebClient.post()
                        .uri("/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .body(BodyInserters.fromValue(userRepresentation))
                        .retrieve()
                        .toBodilessEntity()
                        .map(responseEntity -> {
                            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                                List<String> locationHeaders = responseEntity.getHeaders().get(HttpHeaders.LOCATION);
                                if (locationHeaders != null && !locationHeaders.isEmpty()) {
                                    String location = locationHeaders.get(0);
                                    String userId = location.substring(location.lastIndexOf('/') + 1);
                                    System.out.println("Keycloak에 사용자 생성 성공. 사용자 ID: " + userId);
                                    return userId;
                                } else {
                                    System.out.println("Keycloak에 사용자 생성 성공. Location 헤더에 ID 없음 (성공으로 간주).");
                                    return "User created successfully (ID not in Location header)";
                                }
                            } else {
                                System.err.println("Keycloak 사용자 생성 실패: 예상치 못한 상태 코드 " + responseEntity.getStatusCode());
                                throw new RuntimeException("Keycloak 사용자 생성 실패: 예상치 못한 상태 코드 " + responseEntity.getStatusCode());
                            }
                        })
                        .onErrorResume(WebClientResponseException.class, e -> {
                            String errorBody = e.getResponseBodyAsString();
                            System.err.println("Keycloak 사용자 생성 중 오류: " + e.getStatusCode() + " - " + errorBody);
                            if (e.getStatusCode().equals(HttpStatus.CONFLICT)) {
                                return Mono.error(new IllegalArgumentException("이미 사용 중인 사용자명 또는 이메일입니다."));
                            }
                            // 403 FORBIDDEN이 여기서 발생할 수 있습니다.
                            if (e.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                                System.err.println("--- KeycloakAdminService: 사용자 생성 권한 부족 (403 FORBIDDEN). Admin 토큰 또는 서비스 계정 역할 확인 필요 ---");
                                return Mono.error(new RuntimeException("Keycloak 사용자 생성 권한이 없습니다.", e));
                            }
                            return Mono.error(new RuntimeException("Keycloak 사용자 생성에 실패했습니다.", e));
                        })
                );
    }

    public Mono<Void> deleteUser(String userId) {
        System.out.println("--- KeycloakAdminService: 사용자 삭제 시도 (ID: " + userId + ") ---");

        return getAdminAccessToken()
                .flatMap(token -> keycloakAdminWebClient.delete()
                        .uri("/users/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .toBodilessEntity()
                        .then()
                        .onErrorResume(WebClientResponseException.class, e -> {
                            String errorBody = e.getResponseBodyAsString();
                            System.err.println("Keycloak 사용자 삭제 중 오류: " + e.getStatusCode() + " - " + errorBody);
                            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                                return Mono.error(new NoSuchElementException("ID가 " + userId + "인 사용자를 Keycloak에서 찾을 수 없습니다."));
                            }
                            if (e.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                                System.err.println("--- KeycloakAdminService: 사용자 삭제 권한 부족 (403 FORBIDDEN). Admin 토큰 또는 서비스 계정 역할 확인 필요 ---");
                                return Mono.error(new RuntimeException("Keycloak 사용자 삭제 권한이 없습니다.", e));
                            }
                            return Mono.error(new RuntimeException("Keycloak 사용자 삭제에 실패했습니다.", e));
                        })
                );
    }
}
