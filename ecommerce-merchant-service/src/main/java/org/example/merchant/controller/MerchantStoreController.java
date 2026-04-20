package org.example.merchant.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.merchant.domain.MerchantProductStatus;
import org.example.merchant.security.AuthenticatedUser;
import org.example.merchant.service.MerchantStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant")
public class MerchantStoreController {

    private final MerchantStoreService storeService;

    public MerchantStoreController(MerchantStoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping("/stores")
    public ResponseEntity<Map<String, Object>> createStore(Authentication authentication,
                                                           @Valid @RequestBody CreateStoreRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.createStore(
                user.userId(),
                request.storeName(),
                request.storeIntro(),
                request.storeImageUrl(),
                request.mainCategory(),
                request.tags()
        ));
    }

    @PutMapping("/stores/{storeId}")
    public ResponseEntity<Map<String, Object>> updateStore(Authentication authentication,
                                                           @PathVariable("storeId") Long storeId,
                                                           @RequestBody UpdateStoreRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.updateStore(
                user.userId(),
                storeId,
                request.storeName(),
                request.storeIntro(),
                request.storeImageUrl(),
                request.mainCategory(),
                request.tags()
        ));
    }

    @GetMapping("/stores/me")
    public ResponseEntity<List<Map<String, Object>>> myStores(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.myStores(user.userId()));
    }

    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> publishProduct(Authentication authentication,
                                                              @Valid @RequestBody PublishProductRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.publishProduct(
                user.userId(),
                request.storeId(),
                request.title(),
                request.description(),
                request.imageUrl(),
                request.category(),
                request.tags(),
                request.price(),
                request.stock()
        ));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<Map<String, Object>> updateProduct(Authentication authentication,
                                                             @PathVariable("productId") Long productId,
                                                             @RequestBody UpdateProductRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.updateProduct(
                user.userId(),
                productId,
                request.title(),
                request.description(),
                request.imageUrl(),
                request.category(),
                request.tags(),
                request.price(),
                request.stock(),
                request.status()
        ));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Map<String, Object>> deleteProduct(Authentication authentication,
                                                             @PathVariable("productId") Long productId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.deleteProduct(user.userId(), productId));
    }

    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<List<Map<String, Object>>> myProducts(Authentication authentication, @PathVariable("storeId") Long storeId) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.myProducts(user.userId(), storeId));
    }

    @GetMapping("/products/hot")
    public ResponseEntity<List<Map<String, Object>>> hotProducts() {
        return ResponseEntity.ok(storeService.hotProducts());
    }

    @GetMapping("/public/stores")
    public ResponseEntity<List<Map<String, Object>>> publicStores(@RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(storeService.listPublicStores(limit));
    }

    @GetMapping("/public/stores/search")
    public ResponseEntity<List<Map<String, Object>>> publicSearchStores(@RequestParam(name = "keyword", required = false) String keyword,
                                                                        @RequestParam(name = "sort", defaultValue = "relevance") String sort,
                                                                        @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(storeService.searchPublicStores(keyword, sort, limit));
    }

    @GetMapping("/public/stores/{storeId}")
    public ResponseEntity<Map<String, Object>> publicStoreDetail(@PathVariable("storeId") Long storeId) {
        return ResponseEntity.ok(storeService.getPublicStore(storeId));
    }

    @GetMapping("/public/stores/{storeId}/products")
    public ResponseEntity<List<Map<String, Object>>> publicStoreProducts(@PathVariable("storeId") Long storeId,
                                                                         @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(storeService.listPublicStoreProducts(storeId, limit));
    }

    @GetMapping("/public/products/search")
    public ResponseEntity<List<Map<String, Object>>> publicSearchProducts(@RequestParam(name = "keyword", required = false) String keyword,
                                                                          @RequestParam(name = "sort", defaultValue = "relevance") String sort,
                                                                          @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(storeService.searchPublicProducts(keyword, sort, limit));
    }

    @GetMapping("/public/products/{productId}")
    public ResponseEntity<Map<String, Object>> publicProductDetail(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(storeService.getPublicProduct(productId));
    }

    @GetMapping("/public/products/{productId}/comments")
    public ResponseEntity<List<Map<String, Object>>> publicProductComments(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(storeService.listPublicProductComments(productId));
    }

    @PostMapping("/internal/products/stock")
    public ResponseEntity<Map<String, Object>> syncProductStock(@Valid @RequestBody InternalStockSyncRequest request) {
        return ResponseEntity.ok(storeService.syncProductStock(request.productId(), request.stock()));
    }

    @GetMapping("/internal/products/{productId}/availability")
    public ResponseEntity<Map<String, Object>> productAvailability(@PathVariable("productId") Long productId) {
        return ResponseEntity.ok(storeService.productAvailability(productId));
    }

    @GetMapping("/internal/products/index")
    public ResponseEntity<Map<String, Object>> listProductsForImageIndex(@RequestParam(name = "updatedAfter", required = false) String updatedAfter,
                                                                         @RequestParam(name = "cursorId", required = false) Long cursorId,
                                                                         @RequestParam(name = "limit", required = false) Integer limit) {
        LocalDateTime updatedAfterTime = parseDateTime(updatedAfter);
        return ResponseEntity.ok(storeService.listProductsForImageIndex(updatedAfterTime, cursorId, limit));
    }

    @PostMapping("/products/{productId}/comments")
    public ResponseEntity<Map<String, Object>> createComment(Authentication authentication,
                                                             @PathVariable("productId") Long productId,
                                                             @Valid @RequestBody CreateCommentRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(storeService.createProductComment(
                user.userId(),
                user.username(),
                user.nickname(),
                productId,
                request.content(),
                request.imageUrls()
        ));
    }

    @GetMapping("/public/products/recommend")
    public ResponseEntity<List<Map<String, Object>>> publicRecommendProducts(@RequestParam(name = "keywords", required = false) List<String> keywords,
                                                                             @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(storeService.recommendPublicProducts(keywords, limit));
    }

    @PostMapping("/internal/orders/paid")
    public ResponseEntity<Map<String, Object>> syncPaidOrder(@Valid @RequestBody InternalPaidOrderRequest request) {
        List<MerchantStoreService.PaidItem> items = request.items().stream()
                .map(item -> new MerchantStoreService.PaidItem(item.productId(), item.quantity()))
                .toList();
        storeService.applyPaidOrder(request.orderNo(), items);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record CreateStoreRequest(@NotBlank String storeName,
                                     String storeIntro,
                                     String storeImageUrl,
                                     String mainCategory,
                                     String tags) {
    }

    public record UpdateStoreRequest(String storeName,
                                     String storeIntro,
                                     String storeImageUrl,
                                     String mainCategory,
                                     String tags) {
    }

    public record PublishProductRequest(
            @NotNull Long storeId,
            @NotBlank String title,
            String description,
            String imageUrl,
            String category,
            String tags,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @Min(0) Integer stock
    ) {
    }

    public record UpdateProductRequest(
            String title,
            String description,
            String imageUrl,
            String category,
            String tags,
            BigDecimal price,
            Integer stock,
            MerchantProductStatus status
    ) {
    }

    public record CreateCommentRequest(
            @NotBlank String content,
            String imageUrls
    ) {
    }

    public record InternalStockSyncRequest(
            @NotNull Long productId,
            @NotNull @Min(0) Integer stock
    ) {
    }

    public record InternalPaidOrderRequest(
            @NotBlank String orderNo,
            @NotNull List<@Valid InternalPaidItem> items
    ) {
    }

    public record InternalPaidItem(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return LocalDateTime.parse(value.trim().replace(" ", "T"));
        }
    }
}

