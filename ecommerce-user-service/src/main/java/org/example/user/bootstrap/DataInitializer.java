package org.example.user.bootstrap;

import org.example.user.domain.UserAccount;
import org.example.user.domain.UserRole;
import org.example.user.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        ensureUser("admin", "管理员", UserRole.ADMIN, "123456");
        ensureUser("user", "普通会员", UserRole.USER, "123456");
    }

    private void ensureUser(String username, String nickname, UserRole role, String rawPassword) {
        if (userAccountRepository.existsByUsername(username)) {
            return;
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(1);
        user.setPoints(0);
        user.setMemberLevel("BRONZE");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userAccountRepository.save(user);
    }
}

