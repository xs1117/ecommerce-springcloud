package org.example.merchant.repository;

import org.example.merchant.domain.MerchantProduct;
import org.example.merchant.domain.MerchantProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MerchantProductRepository extends JpaRepository<MerchantProduct, Long> {

    List<MerchantProduct> findTop10ByStatusOrderBySalesCountDescCreatedAtDesc(MerchantProductStatus status);

    List<MerchantProduct> findTop20ByStatusOrderBySalesCountDescCreatedAtDesc(MerchantProductStatus status);

    List<MerchantProduct> findAllByStatusOrderBySalesCountDescCreatedAtDesc(MerchantProductStatus status);

    List<MerchantProduct> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<MerchantProduct> findAllByStoreIdAndStatusOrderBySalesCountDescCreatedAtDesc(Long storeId, MerchantProductStatus status);

    List<MerchantProduct> findTop8ByStoreIdAndStatusOrderBySalesCountDescCreatedAtDesc(Long storeId, MerchantProductStatus status);

    Optional<MerchantProduct> findByIdAndStoreId(Long id, Long storeId);

    Optional<MerchantProduct> findByIdAndStatus(Long id, MerchantProductStatus status);

    Page<MerchantProduct> findByIdGreaterThan(Long id, Pageable pageable);

    Page<MerchantProduct> findByUpdatedAtGreaterThanEqualAndIdGreaterThan(LocalDateTime updatedAt, Long id, Pageable pageable);
}

