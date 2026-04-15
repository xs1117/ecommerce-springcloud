package org.example.inventory.repository;

import org.example.inventory.domain.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByReservationNo(String reservationNo);
    List<InventoryReservation> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.reservationNo = :reservationNo")
    Optional<InventoryReservation> findByReservationNoForUpdate(@Param("reservationNo") String reservationNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.orderNo = :orderNo")
    List<InventoryReservation> findByOrderNoForUpdate(@Param("orderNo") String orderNo);
}

