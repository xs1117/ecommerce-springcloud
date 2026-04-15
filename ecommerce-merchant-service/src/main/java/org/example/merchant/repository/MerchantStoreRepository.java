package org.example.merchant.repository;

import org.example.merchant.domain.MerchantStore;
import org.example.merchant.domain.MerchantStoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantStoreRepository extends JpaRepository<MerchantStore, Long> {

    Optional<MerchantStore> findByOwnerUserId(Long ownerUserId);

    Optional<MerchantStore> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    List<MerchantStore> findAllByOwnerUserId(Long ownerUserId);

    List<MerchantStore> findAllByStatusOrderByCreatedAtDesc(MerchantStoreStatus status);

    Optional<MerchantStore> findByIdAndStatus(Long id, MerchantStoreStatus status);
}

