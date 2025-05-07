package com.localy.userservice.user_service.service;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface UserService {
    Mono<Map> getUserInfoFromKeycloak(String accessToken);
}