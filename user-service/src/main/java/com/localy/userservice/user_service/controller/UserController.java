package com.localy.userservice.user_service.controller;

import com.localy.userservice.user_service.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public Mono<Map> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String accessToken = jwt.getTokenValue();
        return userService.getUserInfoFromKeycloak(accessToken);
    }
}