package org.example.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.user.domain.UserRole;
import org.example.user.security.AuthenticatedUser;
import org.example.user.service.AuthResult;
import org.example.user.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/user/auth/register")
    public ResponseEntity<AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request.username(), request.nickname(), request.password()));
    }

    @PostMapping("/api/user/auth/login")
    public ResponseEntity<?> userLogin(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request.username(), request.password(), UserRole.USER));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", resolveReason(ex)));
        }
    }

    @PostMapping("/api/admin/auth/login")
    public ResponseEntity<?> adminLogin(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request.username(), request.password(), UserRole.ADMIN));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", resolveReason(ex)));
        }
    }

    @GetMapping("/api/user/auth/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "userId", user.userId(),
                "username", user.username(),
                "nickname", user.nickname(),
                "role", user.role(),
                "permissions", user.permissions(),
                "points", user.points(),
                "memberLevel", user.memberLevel()
        ));
    }

    @PostMapping("/api/user/auth/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok(Map.of("success", true, "message", "已退出登录"));
    }

    private String resolveReason(ResponseStatusException ex) {
        return ex.getReason() == null || ex.getReason().isBlank() ? "登录失败" : ex.getReason();
    }

    public record RegisterRequest(@NotBlank String username, String nickname, @NotBlank String password) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}

