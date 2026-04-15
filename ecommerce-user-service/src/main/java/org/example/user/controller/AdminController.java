package org.example.user.controller;

import jakarta.validation.constraints.NotNull;
import org.example.user.domain.UserRole;
import org.example.user.security.AuthenticatedUser;
import org.example.user.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> users() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable("id") Long id, @RequestBody StatusRequest request) {
        return ResponseEntity.ok(adminService.updateStatus(id, request.status()));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(@PathVariable("id") Long id, @RequestBody RoleRequest request) {
        return ResponseEntity.ok(adminService.changeRole(id, request.role()));
    }

    @GetMapping("/me")
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

    @GetMapping("/coupon-templates")
    public ResponseEntity<List<Map<String, Object>>> couponTemplates() {
        return ResponseEntity.ok(adminService.listCouponTemplates());
    }

    @PostMapping("/coupon-templates")
    public ResponseEntity<Map<String, Object>> createCouponTemplate(@RequestBody CouponTemplateRequest request) {
        return ResponseEntity.ok(adminService.createCouponTemplate(
                request.name(),
                request.pointsCost(),
                request.threshold(),
                request.discountAmount(),
                request.description(),
                request.status()
        ));
    }

    @PutMapping("/coupon-templates/{id}/status")
    public ResponseEntity<Map<String, Object>> updateCouponTemplateStatus(@PathVariable("id") Long id,
                                                                           @RequestBody StatusRequest request) {
        return ResponseEntity.ok(adminService.updateCouponTemplateStatus(id, request.status()));
    }

    @PutMapping("/coupon-templates/{id}")
    public ResponseEntity<Map<String, Object>> updateCouponTemplate(@PathVariable("id") Long id,
                                                                     @RequestBody CouponTemplateRequest request) {
        return ResponseEntity.ok(adminService.updateCouponTemplate(
                id,
                request.name(),
                request.pointsCost(),
                request.threshold(),
                request.discountAmount(),
                request.description(),
                request.status()
        ));
    }

    @DeleteMapping("/coupon-templates/{id}")
    public ResponseEntity<Map<String, Object>> deleteCouponTemplate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.deleteCouponTemplate(id));
    }

    @GetMapping("/notices")
    public ResponseEntity<List<Map<String, Object>>> notices() {
        return ResponseEntity.ok(adminService.listNotices());
    }

    @PostMapping("/notices")
    public ResponseEntity<Map<String, Object>> createNotice(@RequestBody NoticeRequest request) {
        return ResponseEntity.ok(adminService.createNotice(
                request.title(),
                request.content(),
                request.sortNo(),
                request.status()
        ));
    }

    @PutMapping("/notices/{id}/status")
    public ResponseEntity<Map<String, Object>> updateNoticeStatus(@PathVariable("id") Long id,
                                                                   @RequestBody StatusRequest request) {
        return ResponseEntity.ok(adminService.updateNoticeStatus(id, request.status()));
    }

    @PutMapping("/notices/{id}")
    public ResponseEntity<Map<String, Object>> updateNotice(@PathVariable("id") Long id,
                                                             @RequestBody NoticeRequest request) {
        return ResponseEntity.ok(adminService.updateNotice(
                id,
                request.title(),
                request.content(),
                request.sortNo(),
                request.status()
        ));
    }

    @DeleteMapping("/notices/{id}")
    public ResponseEntity<Map<String, Object>> deleteNotice(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.deleteNotice(id));
    }

    public record StatusRequest(@NotNull Integer status) {
    }

    public record RoleRequest(@NotNull UserRole role) {
    }

    public record CouponTemplateRequest(String name,
                                        Integer pointsCost,
                                        BigDecimal threshold,
                                        BigDecimal discountAmount,
                                        String description,
                                        Integer status) {
    }

    public record NoticeRequest(String title, String content, Integer sortNo, Integer status) {
    }
}

