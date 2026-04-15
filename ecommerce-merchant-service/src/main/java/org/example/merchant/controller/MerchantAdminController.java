package org.example.merchant.controller;

import jakarta.validation.Valid;
import org.example.merchant.domain.MerchantApplicationStatus;
import org.example.merchant.domain.MerchantStoreStatus;
import org.example.merchant.security.AuthenticatedUser;
import org.example.merchant.service.MerchantApplicationService;
import org.example.merchant.service.MerchantStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/merchant")
@PreAuthorize("hasRole('ADMIN')")
public class MerchantAdminController {

    private final MerchantApplicationService applicationService;
    private final MerchantStoreService storeService;

    public MerchantAdminController(MerchantApplicationService applicationService,
                                   MerchantStoreService storeService) {
        this.applicationService = applicationService;
        this.storeService = storeService;
    }

    @GetMapping("/applications")
    public ResponseEntity<List<Map<String, Object>>> listApplications(@RequestParam(name = "status", required = false) MerchantApplicationStatus status) {
        return ResponseEntity.ok(applicationService.listForAdmin(status));
    }

    @GetMapping("/stores")
    public ResponseEntity<List<Map<String, Object>>> stores(@RequestParam(name = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(storeService.adminListStores(keyword));
    }

    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<List<Map<String, Object>>> storeProducts(@PathVariable("storeId") Long storeId,
                                                                   @RequestParam(name = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(storeService.adminListStoreProducts(storeId, keyword));
    }

    @PostMapping("/applications/{id}/review")
    public ResponseEntity<Map<String, Object>> review(Authentication authentication,
                                                      @PathVariable("id") Long id,
                                                      @Valid @RequestBody ReviewRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(applicationService.review(id, user.userId(), request.approved(), request.comment()));
    }

    @PostMapping("/stores/{storeId}/status")
    public ResponseEntity<Map<String, Object>> updateStoreStatus(@PathVariable("storeId") Long storeId,
                                                                 @Valid @RequestBody StoreStatusRequest request) {
        return ResponseEntity.ok(storeService.adminUpdateStoreStatus(storeId, request.status()));
    }

    @PostMapping("/stores/{storeId}/products/{productId}/off-shelf")
    public ResponseEntity<Map<String, Object>> forceOffShelf(@PathVariable("storeId") Long storeId,
                                                             @PathVariable("productId") Long productId) {
        return ResponseEntity.ok(storeService.adminForceOffShelfProduct(storeId, productId));
    }

    @DeleteMapping("/products/{productId}/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(@PathVariable("productId") Long productId,
                                                             @PathVariable("commentId") Long commentId) {
        return ResponseEntity.ok(storeService.adminDeleteProductComment(productId, commentId));
    }

    public record ReviewRequest(boolean approved, String comment) {
    }

    public record StoreStatusRequest(MerchantStoreStatus status) {
    }
}

