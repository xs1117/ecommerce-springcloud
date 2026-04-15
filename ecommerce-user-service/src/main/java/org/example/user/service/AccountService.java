package org.example.user.service;

import org.example.user.domain.UserAccount;
import org.example.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserAccountRepository userAccountRepository,
                          PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> me(Long userId) {
        UserAccount user = findUser(userId);
        return toView(user);
    }

    @Transactional
    public Map<String, Object> updateProfile(Long userId, String nickname, String avatarUrl) {
        UserAccount user = findUser(userId);
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname.trim());
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl.trim() : null);
        }
        return toView(userAccountRepository.save(user));
    }

    @Transactional
    public Map<String, Object> changePassword(Long userId, String currentPassword, String newPassword) {
        if (!StringUtils.hasText(currentPassword) || !StringUtils.hasText(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少6位");
        }
        UserAccount user = findUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);
        return Map.of("success", true, "message", "密码修改成功");
    }

    private UserAccount findUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private Map<String, Object> toView(UserAccount user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                "role", user.getRole(),
                "points", user.getPoints(),
                "memberLevel", user.getMemberLevel(),
                "status", user.getStatus(),
                "createdAt", user.getCreatedAt(),
                "updatedAt", user.getUpdatedAt()
        );
    }
}



