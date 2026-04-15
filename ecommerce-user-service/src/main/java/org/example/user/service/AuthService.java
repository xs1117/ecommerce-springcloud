package org.example.user.service;

import org.example.user.domain.UserAccount;
import org.example.user.domain.UserRole;
import org.example.user.repository.UserAccountRepository;
import org.example.user.security.AuthenticatedUser;
import org.example.user.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserAccountRepository userAccountRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public AuthResult register(String username, String nickname, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        String normalizedUsername = username.trim();
        if (userAccountRepository.existsByUsername(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        UserAccount user = new UserAccount();
        user.setUsername(normalizedUsername);
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setStatus(1);
        user.setPoints(0);
        user.setMemberLevel("BRONZE");
        user = userAccountRepository.save(user);
        return toResult(user, tokenService.issueToken(user));
    }

    public AuthResult login(String username, String password, UserRole expectedRole) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        UserAccount user = userAccountRepository.findByUsernameAndRole(username.trim(), expectedRole)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不存在"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "密码不正确");
        }
        return toResult(user, tokenService.issueToken(user));
    }

    public AuthenticatedUser me(AuthenticatedUser currentUser) {
        return currentUser;
    }

    private AuthResult toResult(UserAccount user, String token) {
        return new AuthResult(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                permissionsOf(user.getRole()),
                user.getPoints(),
                user.getMemberLevel()
        );
    }

    private List<String> permissionsOf(UserRole role) {
        return role == UserRole.ADMIN
                ? List.of("user:read", "user:write", "user:status", "member:manage", "admin:dashboard")
                : List.of("user:read", "member:read", "member:points");
    }
}

