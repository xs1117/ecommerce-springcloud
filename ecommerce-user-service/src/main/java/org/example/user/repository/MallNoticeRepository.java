package org.example.user.repository;

import org.example.user.domain.MallNotice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MallNoticeRepository extends JpaRepository<MallNotice, Long> {
    List<MallNotice> findByStatusOrderBySortNoDescIdDesc(Integer status);

    List<MallNotice> findAllByOrderBySortNoDescIdDesc();
}

