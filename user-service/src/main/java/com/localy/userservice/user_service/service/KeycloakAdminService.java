package com.localy.userservice.user_service.service;

import com.localy.userservice.user_service.domain.UserRegistrationRequest;
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
    @Value("${keycloak.default-new-user-role:consumer}") // 기본 역할 주입 (설정 파일에서 관리)
    private String defaultNewUserRole;

    // Admin API 토큰 캐시
    private final Map<String, AdminToken> adminTokenCache = new ConcurrentHashMap<>();
    private static final String ADMIN_TOKEN_KEY = "admin_api_token";
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 30; // 토큰 만료 30초 전에 갱신

    // Admin 토큰 정보를 담는 내부 클래스
    private static class AdminToken {
        String accessToken;
        long expiresAtMillis; // 토큰 만료 시간 (밀리초 단위)

        AdminToken(String accessToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            this.expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds - TOKEN_EXPIRY_BUFFER_SECONDS) * 1000;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }

    // 생성자 주입
    public KeycloakAdminService(WebClient keycloakAdminWebClient, WebClient keycloakTokenWebClient) {
        this.keycloakAdminWebClient = keycloakAdminWebClient;
        this.keycloakTokenWebClient = keycloakTokenWebClient;
    }

    /**
     * Keycloak Admin API 호출을 위한 액세스 토큰을 발급받거나 캐시에서 가져옵니다.
     * @return 유효한 액세스 토큰을 담은 Mono<String>
     */
    private Mono<String> getAdminAccessToken() {
        AdminToken cachedToken = adminTokenCache.get(ADMIN_TOKEN_KEY);
        if (cachedToken != null && !cachedToken.isExpired()) {
            System.out.println("--- KeycloakAdminService: 유효한 Admin 토큰을 캐시에서 사용합니다. ---");
            return Mono.just(cachedToken.accessToken);
        }

        System.out.println("--- KeycloakAdminService: Admin 토큰이 캐시에 없거나 만료되어 새로 요청합니다. Client ID: " + clientId + " ---");
        return keycloakTokenWebClient.post()
                .uri("/token") // Keycloak 토큰 엔드포인트
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                // HTTP 오류 상태(4xx, 5xx) 발생 시 상세 로깅 및 예외 변환
                .onStatus(
                        status -> status.isError(), // isError()는 is4xxClientError() 또는 is5xxServerError()
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("[Keycloak 토큰 발급 오류 응답 본문 없음]") // 오류 본문이 비어있을 경우 대비
                                .flatMap(errorBody -> {
                                    System.err.println("--- KeycloakAdminService (Token Fetch): 토큰 엔드포인트 오류 응답 상태: " + response.statusCode() + ", 본문: " + errorBody + " ---");
                                    return Mono.error(WebClientResponseException.create(
                                            response.statusCode().value(),
                                            "Keycloak token endpoint error: " + errorBody,
                                            response.headers().asHttpHeaders(),
                                            errorBody.getBytes(),
                                            null
                                    ));
                                })
                )
                .bodyToMono(Map.class) // 성공(2xx) 시 응답 본문을 Map으로 변환
                .doOnNext(responseMap -> System.out.println("--- KeycloakAdminService (Token Fetch): 토큰 응답 Map 수신 (성공) ---"))
                .flatMap(responseMap -> {
                    String newToken = (String) responseMap.get("access_token");
                    Number expiresInNumber = (Number) responseMap.get("expires_in");

                    if (newToken != null && !newToken.isEmpty() && expiresInNumber != null) {
                        long expiresIn = expiresInNumber.longValue();
                        AdminToken newAdminToken = new AdminToken(newToken, expiresIn);
                        adminTokenCache.put(ADMIN_TOKEN_KEY, newAdminToken);
                        System.out.println("--- KeycloakAdminService: 새 Admin 토큰을 발급받아 캐시했습니다. 만료까지 (초): " + expiresIn + " ---");
                        return Mono.just(newToken); // 성공적으로 토큰 문자열 방출
                    } else {
                        System.err.println("--- KeycloakAdminService: 토큰 엔드포인트 응답에서 access_token 또는 expires_in이 유효하지 않습니다. 응답 Map: " + responseMap);
                        return Mono.error(new RuntimeException("Keycloak 토큰 엔드포인트 응답에서 유효한 토큰 정보를 가져오지 못했습니다."));
                    }
                })
                // bodyToMono(Map.class) 실패 또는 위 flatMap에서 Mono.error() 반환 시 이 onErrorResume이 잡음
                .onErrorResume(Exception.class, e -> {
                    System.err.println("--- KeycloakAdminService: Admin 토큰 발급/처리 중 최종 오류: " + e.getMessage() + " ---");
                    return Mono.error(new RuntimeException("Keycloak Admin 토큰을 가져오는 데 실패했습니다.", e));
                });
    }

    /**
     * Keycloak에 새로운 사용자를 생성합니다.
     * @param registrationRequest 사용자 등록 요청 정보
     * @return 생성된 사용자의 ID 또는 성공 메시지를 담은 Mono<String>
     */
    public Mono<String> createUser(UserRegistrationRequest registrationRequest) {
        // 사용자 정보 Map 구성
        Map<String, Object> userRepresentation = new HashMap<>();
        userRepresentation.put("username", registrationRequest.getUsername());
        userRepresentation.put("email", registrationRequest.getEmail());
        userRepresentation.put("firstName", registrationRequest.getFirstName());
        userRepresentation.put("lastName", registrationRequest.getLastName());
        userRepresentation.put("enabled", true); // 계정 활성화
        userRepresentation.put("emailVerified", false); // 이메일 인증은 별도 처리 가정 (필요시 true 또는 Realm 설정 따름)
        // userRepresentation.put("requiredActions", Collections.singletonList("VERIFY_EMAIL")); // 필요시 이메일 인증 요구

        // 비밀번호 정보 구성
        Map<String, Object> passwordCredential = new HashMap<>();
        passwordCredential.put("type", "password");
        passwordCredential.put("value", registrationRequest.getPassword());
        passwordCredential.put("temporary", false); // 영구 비밀번호
        userRepresentation.put("credentials", Collections.singletonList(passwordCredential));

        // 기본 역할 할당 (설정된 경우)
        if (defaultNewUserRole != null && !defaultNewUserRole.trim().isEmpty()) {
            userRepresentation.put("realmRoles", Collections.singletonList(defaultNewUserRole));
            System.out.println("--- KeycloakAdminService: 사용자 [" + registrationRequest.getUsername() + "]에게 기본 역할 [" + defaultNewUserRole + "] 할당 시도 ---");
        }

        System.out.println("--- KeycloakAdminService: 사용자 생성 시도: " + registrationRequest.getUsername() + " ---");

        return getAdminAccessToken()
                .flatMap(token -> {
                    if (token == null || token.isEmpty()) {
                        System.err.println("--- KeycloakAdminService: Admin 토큰이 유효하지 않아 사용자 생성을 진행할 수 없습니다. ---");
                        return Mono.error(new IllegalStateException("Admin token is not available for user creation."));
                    }
                    System.out.println("--- KeycloakAdminService: Admin 토큰으로 사용자 생성 API 호출 시작. 대상 사용자: " + registrationRequest.getUsername() + " ---");

                    return keycloakAdminWebClient.post()
                            .uri("/users") // Keycloak Admin API 사용자 생성 엔드포인트
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .body(BodyInserters.fromValue(userRepresentation))
                            .retrieve()
                            .onStatus(
                                    status -> status.isError(), // 4xx 또는 5xx 오류일 때
                                    response -> response.bodyToMono(String.class)
                                            .defaultIfEmpty("[Keycloak Admin API 사용자 생성 오류 응답 본문 없음]")
                                            .flatMap(errorBody -> {
                                                System.err.println("--- KeycloakAdminService (User Creation): Keycloak Admin API 오류 응답 상태: " + response.statusCode() + ", 본문: " + errorBody + " ---");
                                                return Mono.error(WebClientResponseException.create(
                                                        response.statusCode().value(),
                                                        "Keycloak Admin API error during user creation: " + errorBody,
                                                        response.headers().asHttpHeaders(),
                                                        errorBody.getBytes(),
                                                        null
                                                ));
                                            })
                            )
                            .toBodilessEntity() // 성공(201 Created 기대) 시에는 본문 없이 ResponseEntity<Void>
                            .map(responseEntity -> {
                                System.out.println("--- KeycloakAdminService (User Creation): Keycloak Admin API 응답 상태: " + responseEntity.getStatusCode() + " ---");
                                if (responseEntity.getStatusCode().equals(HttpStatus.CREATED)) { // 201 확인
                                    List<String> locationHeaders = responseEntity.getHeaders().get(HttpHeaders.LOCATION);
                                    if (locationHeaders != null && !locationHeaders.isEmpty()) {
                                        String location = locationHeaders.get(0);
                                        String userId = location.substring(location.lastIndexOf('/') + 1);
                                        System.out.println("--- KeycloakAdminService: Keycloak에 사용자 생성 성공. ID: " + userId + " ---");
                                        return userId;
                                    } else {
                                        System.out.println("--- KeycloakAdminService: 사용자 생성 성공(201)했으나 Location 헤더에 ID 없음. ---");
                                        return "USER_CREATED_SUCCESSFULLY_NO_ID_IN_LOCATION";
                                    }
                                } else {
                                    // 201이 아닌 다른 2xx 성공 응답은 비정상으로 간주
                                    String errorMessage = "Keycloak 사용자 생성 API가 201 Created가 아닌 성공 상태(" + responseEntity.getStatusCode() + ")를 반환했습니다.";
                                    System.err.println("--- KeycloakAdminService: " + errorMessage + " ---");
                                    throw new RuntimeException(errorMessage); // 이를 통해 onErrorResume으로 전달
                                }
                            });
                })
                .doOnSuccess(userId -> System.out.println("--- KeycloakAdminService: createUser 최종 성공. 반환값 (ID 또는 메시지): " + userId + " ---"))
                .onErrorResume(e -> {
                    // getAdminAccessToken() 또는 위 flatMap 체인에서 발생한 모든 예외를 여기서 처리
                    System.err.println("--- KeycloakAdminService: createUser 전체 과정 중 오류 발생: " + e.getMessage() + " ---");
                    // 컨트롤러로 전달될 예외를 RuntimeException으로 통일하여 메시지 포함
                    return Mono.error(new RuntimeException("사용자 생성 처리 중 오류가 발생했습니다: " + e.getMessage(), e));
                });
    }

    /**
     * Keycloak에서 특정 사용자를 삭제합니다.
     * @param userId 삭제할 사용자의 ID
     * @return 작업 완료를 나타내는 Mono<Void>
     */
    public Mono<Void> deleteUser(String userId) {
        System.out.println("--- KeycloakAdminService: 사용자 삭제 시도 (ID: " + userId + ") ---");
        return getAdminAccessToken()
                .flatMap(token -> keycloakAdminWebClient.delete()
                        .uri("/users/{userId}", userId) // 경로 변수 사용
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .onStatus(
                                status -> status.isError(),
                                response -> response.bodyToMono(String.class)
                                        .defaultIfEmpty("[Keycloak Admin API 사용자 삭제 오류 응답 본문 없음]")
                                        .flatMap(errorBody -> {
                                            System.err.println("--- KeycloakAdminService (User Deletion): Keycloak Admin API 오류 응답 상태: " + response.statusCode() + ", 본문: " + errorBody + " ---");
                                            HttpStatus status = HttpStatus.resolve(response.statusCode().value());
                                            if (status == HttpStatus.NOT_FOUND) {
                                                return Mono.error(new NoSuchElementException("ID가 " + userId + "인 사용자를 Keycloak에서 찾을 수 없습니다."));
                                            } else if (status == HttpStatus.FORBIDDEN) {
                                                return Mono.error(new SecurityException("Keycloak 사용자 삭제 권한이 없습니다."));
                                            }
                                            return Mono.error(WebClientResponseException.create(
                                                    response.statusCode().value(),
                                                    "Keycloak Admin API error during user deletion: " + errorBody,
                                                    response.headers().asHttpHeaders(),
                                                    errorBody.getBytes(),
                                                    null
                                            ));
                                        })
                        )
                        .toBodilessEntity() // 성공(204 No Content 기대) 시
                        .then() // Mono<Void>로 변환
                )
                .doOnSuccess(v -> System.out.println("--- KeycloakAdminService: 사용자 삭제 성공 (ID: " + userId + ") ---"))
                .onErrorResume(e -> {
                    System.err.println("--- KeycloakAdminService: deleteUser 전체 과정 중 오류 발생: " + e.getMessage() + " ---");
                    // 이미 NoSuchElementException 또는 SecurityException 등으로 변환되었을 수 있음
                    if (e instanceof NoSuchElementException || e instanceof SecurityException) {
                        return Mono.error(e);
                    }
                    return Mono.error(new RuntimeException("사용자 삭제 처리 중 오류가 발생했습니다.", e));
                });
    }
}
