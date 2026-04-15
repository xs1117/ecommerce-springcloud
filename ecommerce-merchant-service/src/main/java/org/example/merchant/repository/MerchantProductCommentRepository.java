package org.example.merchant.repository;

import org.example.merchant.domain.MerchantProductComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantProductCommentRepository extends JpaRepository<MerchantProductComment, Long> {

    List<MerchantProductComment> findAllByProductIdOrderByCreatedAtDesc(Long productId);

    Optional<MerchantProductComment> findByIdAndProductId(Long id, Long productId);

    void deleteAllByProductId(Long productId);
}

