package org.example.cart.controller;

import jakarta.validation.Valid;
import org.example.cart.dto.AddCartItemRequest;
import org.example.cart.dto.CartBehaviorRequest;
import org.example.cart.dto.UpdateCartItemRequest;
import org.example.cart.security.AuthenticatedUser;
import org.example.cart.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(Authentication authentication) {
        return ResponseEntity.ok(cartService.summary(currentUser(authentication).userId()));
    }

    @GetMapping("/items")
    public ResponseEntity<?> items(Authentication authentication) {
        return ResponseEntity.ok(cartService.listItems(currentUser(authentication).userId()));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(Authentication authentication) {
        return ResponseEntity.ok(cartService.count(currentUser(authentication).userId()));
    }

    @GetMapping("/behaviors")
    public ResponseEntity<?> behaviors(Authentication authentication, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(cartService.recentBehaviors(currentUser(authentication).userId(), limit));
    }

    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> add(Authentication authentication, @Valid @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(currentUser(authentication).userId(), request));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<Map<String, Object>> update(Authentication authentication,
                                                      @PathVariable String itemId,
                                                      @Valid @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateItem(currentUser(authentication).userId(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Map<String, Object>> remove(Authentication authentication, @PathVariable String itemId) {
        return ResponseEntity.ok(cartService.removeItem(currentUser(authentication).userId(), itemId));
    }

    @DeleteMapping("/items")
    public ResponseEntity<Map<String, Object>> clear(Authentication authentication) {
        return ResponseEntity.ok(cartService.clearCart(currentUser(authentication).userId()));
    }

    @PostMapping("/behavior")
    public ResponseEntity<Map<String, Object>> behavior(Authentication authentication, @Valid @RequestBody CartBehaviorRequest request) {
        return ResponseEntity.ok(cartService.recordBehavior(currentUser(authentication).userId(), request));
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("未登录");
        }
        return user;
    }
}

