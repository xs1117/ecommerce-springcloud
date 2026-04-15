package org.example.user.repository;

import org.example.user.domain.CouponStatus;
import org.example.user.domain.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
    List<UserCoupon> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserCoupon> findByIdAndUserId(Long id, Long userId);

    List<UserCoupon> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, CouponStatus status);
}

