package org.example.user.repository;

import org.example.user.domain.UserAccount;
import org.example.user.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByUsernameAndRole(String username, UserRole role);
    boolean existsByUsername(String username);
}

