package org.example.merchant.repository;

import org.example.merchant.domain.MerchantApplication;
import org.example.merchant.domain.MerchantApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication, Long> {

    boolean existsByApplicantUserIdAndStatus(Long applicantUserId, MerchantApplicationStatus status);

    List<MerchantApplication> findByApplicantUserIdOrderByCreatedAtDesc(Long applicantUserId);

    List<MerchantApplication> findByStatusOrderByCreatedAtDesc(MerchantApplicationStatus status);

    Optional<MerchantApplication> findTopByApplicantUserIdAndStatusOrderByCreatedAtDesc(Long applicantUserId, MerchantApplicationStatus status);
}

