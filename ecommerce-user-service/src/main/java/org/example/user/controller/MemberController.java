package org.example.user.controller;

import org.example.user.security.AuthenticatedUser;
import org.example.user.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> profile(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(memberService.profile(user.userId()));
    }

    @PostMapping("/points/earn")
    public ResponseEntity<Map<String, Object>> earn(Authentication authentication, @RequestBody PointsRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(memberService.addPoints(user.userId(), request.delta(), request.reason()));
    }

    @PostMapping("/points/consume")
    public ResponseEntity<Map<String, Object>> consume(Authentication authentication, @RequestBody PointsRequest request) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ResponseEntity.ok(memberService.addPoints(user.userId(), -Math.abs(request.delta()), request.reason()));
    }

    public record PointsRequest(int delta, String reason) {
    }
}

