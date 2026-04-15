package org.example.inventory.repository;

import org.example.inventory.domain.InventorySku;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface InventorySkuRepository extends JpaRepository<InventorySku, Long> {
    Optional<InventorySku> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventorySku s where s.productId = :productId")
    Optional<InventorySku> findByProductIdForUpdate(@Param("productId") Long productId);
}

