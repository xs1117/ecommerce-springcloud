package org.example.merchant.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.merchant.security.AuthenticatedUser;
import org.example.merchant.service.MerchantApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant/applications")
public class MerchantApplicationController {

    private final MerchantApplicationService applicationService;

    public MerchantApplicationController(MerchantApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(Authentication authentication,
                                                     @Valid @RequestBody ApplyRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(applicationService.apply(
                user.userId(),
                user.username(),
                request.shopName(),
                request.businessScope(),
                request.contactPhone()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<List<Map<String, Object>>> myApplications(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(applicationService.myApplications(user.userId()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> myStatus(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(applicationService.myLatestStatus(user.userId()));
    }

    public record ApplyRequest(@NotBlank String shopName, String businessScope, String contactPhone) {
    }
}

