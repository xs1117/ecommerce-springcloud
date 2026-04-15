package org.example.user.repository;

import org.example.user.domain.PointsLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsLedgerRepository extends JpaRepository<PointsLedger, Long> {
}

