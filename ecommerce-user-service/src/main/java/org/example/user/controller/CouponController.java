package org.example.user.controller;

import jakarta.validation.constraints.NotNull;
import org.example.user.security.AuthenticatedUser;
import org.example.user.service.CouponService;
import org.example.user.service.MemberService;
import org.example.user.service.NoticeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/user/coupons")
public class CouponController {

    private final CouponService couponService;
    private final MemberService memberService;
    private final NoticeService noticeService;

    public CouponController(CouponService couponService, MemberService memberService, NoticeService noticeService) {
        this.couponService = couponService;
        this.memberService = memberService;
        this.noticeService = noticeService;
    }

    @GetMapping("/templates")
    public ResponseEntity<?> templates() {
        return ResponseEntity.ok(couponService.templates());
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(couponService.userCoupons(user.userId()));
    }

    @GetMapping("/notices")
    public ResponseEntity<?> notices() {
        return ResponseEntity.ok(noticeService.activeNotices());
    }

    @PostMapping("/redeem")
    public ResponseEntity<?> redeem(Authentication authentication, @RequestBody RedeemRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(couponService.redeem(user.userId(), request.templateId()));
    }

    @PostMapping("/internal/use-for-order")
    public ResponseEntity<?> useForOrder(@RequestBody UseForOrderRequest request) {
        return ResponseEntity.ok(couponService.useForOrder(request.userId(), request.couponId(), request.orderNo(), request.orderAmount()));
    }

    @PostMapping("/internal/points/earn-order")
    public ResponseEntity<?> earnPointsForOrder(@RequestBody EarnPointsRequest request) {
        return ResponseEntity.ok(memberService.addPoints(
                request.userId(),
                Math.max(0, request.delta()),
                request.reason() == null || request.reason().isBlank() ? "订单消费赠送积分" : request.reason()
        ));
    }

    public record RedeemRequest(@NotNull Long templateId) {
    }

    public record UseForOrderRequest(@NotNull Long userId, Long couponId, String orderNo, BigDecimal orderAmount) {
    }

    public record EarnPointsRequest(@NotNull Long userId, int delta, String reason) {
    }
}

