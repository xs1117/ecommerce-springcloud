package org.example.user.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.user.security.AuthenticatedUser;
import org.example.user.service.AccountService;
import org.example.user.service.UserFileStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/user/account")
public class AccountController {

    private final AccountService accountService;
    private final UserFileStorageService userFileStorageService;

    public AccountController(AccountService accountService,
                             UserFileStorageService userFileStorageService) {
        this.accountService = accountService;
        this.userFileStorageService = userFileStorageService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.me(user.userId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(Authentication authentication,
                                                             @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.updateProfile(user.userId(), request.nickname(), request.avatarUrl()));
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(Authentication authentication,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.changePassword(user.userId(), request.currentPassword(), request.newPassword()));
    }

    @PostMapping(value = "/upload/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(userFileStorageService.uploadAvatar(file));
    }

    public record UpdateProfileRequest(String nickname, String avatarUrl) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }
}

