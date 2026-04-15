package org.example.user.repository;

import org.example.user.domain.CouponTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {
    List<CouponTemplate> findByStatusOrderByPointsCostAsc(Integer status);

    List<CouponTemplate> findAllByOrderByIdDesc();
}

