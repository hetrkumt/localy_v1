package com.localy.userservice.user_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private WebClient keycloakWebClient;

    @Override
    public Mono<Map> getUserInfoFromKeycloak(String accessToken) {
        return keycloakWebClient.get()
                .uri("/userinfo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new RuntimeException("Keycloak UserInfo API 호출 실패: " + response.statusCode() + " - " + errorBody)));
                })
                .bodyToMono(Map.class);
    }
}