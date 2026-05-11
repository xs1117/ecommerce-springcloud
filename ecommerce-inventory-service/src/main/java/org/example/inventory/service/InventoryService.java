package org.example.inventory.service;

import jakarta.transaction.Transactional;
import org.example.inventory.config.InventoryRedissonProperties;
import org.example.inventory.domain.InventoryReservation;
import org.example.inventory.domain.InventorySku;
import org.example.inventory.domain.ReservationStatus;
import org.example.inventory.dto.AdjustStockRequest;
import org.example.inventory.dto.ConfirmRequest;
import org.example.inventory.dto.LockRequest;
import org.example.inventory.dto.ReleaseRequest;
import org.example.inventory.integration.IntegrationEndpointResolver;
import org.example.inventory.mq.InventoryEventPublisher;
import org.example.inventory.mq.InventoryReservedEvent;
import org.example.inventory.mq.OrderCreatedEvent;
import org.example.inventory.repository.InventoryReservationRepository;
import org.example.inventory.repository.InventorySkuRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class InventoryService {

    private final InventorySkuRepository skuRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryEventPublisher eventPublisher;
    private final InventoryAlertService inventoryAlertService;
    private final IntegrationEndpointResolver endpointResolver;
    private final RestTemplate restTemplate;
    private final RedissonClient redissonClient;
    private final long lockWaitTimeMillis;
    private final long lockLeaseTimeMillis;
    private final String merchantServiceId;
    private final String merchantBaseUrl;

    public InventoryService(InventorySkuRepository skuRepository,
                            InventoryReservationRepository reservationRepository,
                            InventoryEventPublisher eventPublisher,
                            InventoryAlertService inventoryAlertService,
                            IntegrationEndpointResolver endpointResolver,
                            RedissonClient redissonClient,
                            InventoryRedissonProperties redissonProperties,
                            @Value("${app.integration.merchant.service-id:ecommerce-merchant-service}") String merchantServiceId,
                            @Value("${app.integration.merchant.base-url:http://localhost:8084}") String merchantBaseUrl) {
        this.skuRepository = skuRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryAlertService = inventoryAlertService;
        this.endpointResolver = endpointResolver;
        this.restTemplate = new RestTemplate();
        this.redissonClient = redissonClient;
        this.lockWaitTimeMillis = redissonProperties.getLockWaitTime().toMillis();
        this.lockLeaseTimeMillis = redissonProperties.getLockLeaseTime().toMillis();
        this.merchantServiceId = merchantServiceId;
        this.merchantBaseUrl = merchantBaseUrl;
    }

    @Transactional
    public Map<String, Object> adjustStock(AdjustStockRequest request) {
        return withProductLocks(List.of(request.productId()), () -> adjustStockInternal(request));
    }

    @Transactional
    public Map<String, Object> lock(LockRequest request) {
        return withProductLocks(List.of(request.productId()), () -> reserveInternal(
                request.orderNo(),
                request.reservationNo(),
                request.productId(),
                request.quantity()
        ));
    }

    @Transactional
    public Map<String, Object> confirm(ConfirmRequest request) {
        InventoryReservation reservation = reservationRepository.findByReservationNo(request.reservationNo())
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        return withProductLocks(List.of(reservation.getProductId()), () -> confirmInternal(request.reservationNo()));
    }

    @Transactional
    public Map<String, Object> release(ReleaseRequest request) {
        InventoryReservation reservation = reservationRepository.findByReservationNo(request.reservationNo())
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        return withProductLocks(List.of(reservation.getProductId()), () -> releaseInternal(request.reservationNo()));
    }

    public List<InventorySku> listAll() {
        return skuRepository.findAll();
    }

    @Transactional
    public void confirmByOrderNo(String orderNo) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderNo(orderNo);
        withProductLocks(reservations.stream().map(InventoryReservation::getProductId).toList(), () -> {
            confirmByOrderNoInternal(orderNo);
            return null;
        });
    }

    @Transactional
    public void releaseByOrderNo(String orderNo) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderNo(orderNo);
        withProductLocks(reservations.stream().map(InventoryReservation::getProductId).toList(), () -> {
            releaseByOrderNoInternal(orderNo);
            return null;
        });
    }

    @Transactional
    public void tryReserveForOrder(OrderCreatedEvent event) {
        withProductLocks(event.items().stream().map(OrderCreatedEvent.OrderSkuItem::productId).toList(), () -> {
            boolean success = true;
            String reason = "";
            for (OrderCreatedEvent.OrderSkuItem item : event.items()) {
                String reservationNo = event.orderNo() + "-" + item.productId();
                Map<String, Object> result = reserveInternal(event.orderNo(), reservationNo, item.productId(), item.quantity());
                success = Boolean.TRUE.equals(result.get("ok"));
                if (!success) {
                    reason = String.valueOf(result.getOrDefault("message", "lock failed"));
                    break;
                }
            }
            eventPublisher.publishReserved(new InventoryReservedEvent(UUID.randomUUID().toString(), event.orderNo(), success, reason));
            return null;
        });
    }

    private Map<String, Object> adjustStockInternal(AdjustStockRequest request) {
        InventorySku sku = skuRepository.findByProductId(request.productId()).orElseGet(InventorySku::new);
        if (sku.getId() == null) {
            sku.setProductId(request.productId());
            sku.setCreatedAt(LocalDateTime.now());
            sku.setLockedStock(0);
        }
        int locked = sku.getLockedStock() == null ? 0 : sku.getLockedStock();
        int available = Math.max(0, request.totalStock() - locked);
        sku.setTotalStock(request.totalStock());
        sku.setAvailableStock(available);
        sku.setWarnThreshold(request.warnThreshold());
        sku.setUpdatedAt(LocalDateTime.now());
        skuRepository.save(sku);
        inventoryAlertService.checkAndAlert(sku);
        syncMerchantStock(sku.getProductId(), sku.getAvailableStock());
        return Map.of("ok", true, "productId", sku.getProductId(), "availableStock", sku.getAvailableStock());
    }

    private Map<String, Object> reserveInternal(String orderNo, String reservationNo, Long productId, Integer quantity) {
        if (reservationRepository.findByReservationNoForUpdate(reservationNo).isPresent()) {
            return Map.of("ok", true, "reservationNo", reservationNo);
        }
        InventorySku sku = skuRepository.findByProductIdForUpdate(productId).orElseGet(() -> autoCreateSku(productId));
        if (sku != null && sku.getId() != null) {
            sku = skuRepository.findByProductIdForUpdate(productId).orElse(sku);
        }
        if (sku == null) {
            return Map.of("ok", false, "message", "sku not found", "productId", productId);
        }
        if (sku.getAvailableStock() < quantity) {
            return Map.of("ok", false, "message", "insufficient stock", "productId", productId);
        }
        sku.setAvailableStock(sku.getAvailableStock() - quantity);
        sku.setLockedStock(sku.getLockedStock() + quantity);
        sku.setUpdatedAt(LocalDateTime.now());
        skuRepository.save(sku);

        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setOrderNo(orderNo);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.LOCKED);
        reservation.setCreatedAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        inventoryAlertService.checkAndAlert(sku);
        syncMerchantStock(sku.getProductId(), sku.getAvailableStock());
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("reservationNo", reservationNo);
        return result;
    }

    private Map<String, Object> confirmInternal(String reservationNo) {
        InventoryReservation reservation = reservationRepository.findByReservationNoForUpdate(reservationNo)
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return Map.of("ok", true, "reservationNo", reservationNo);
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return Map.of("ok", false, "message", "reservation already released", "reservationNo", reservationNo);
        }
        InventorySku sku = skuRepository.findByProductIdForUpdate(reservation.getProductId()).orElse(null);
        if (sku == null) {
            return Map.of("ok", false, "message", "sku not found", "productId", reservation.getProductId());
        }
        sku.setLockedStock(Math.max(0, sku.getLockedStock() - reservation.getQuantity()));
        sku.setUpdatedAt(LocalDateTime.now());
        skuRepository.save(sku);
        syncMerchantStock(sku.getProductId(), sku.getAvailableStock());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        return Map.of("ok", true, "reservationNo", reservationNo);
    }

    private Map<String, Object> releaseInternal(String reservationNo) {
        InventoryReservation reservation = reservationRepository.findByReservationNoForUpdate(reservationNo)
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return Map.of("ok", true, "reservationNo", reservationNo);
        }
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return Map.of("ok", false, "message", "reservation already confirmed", "reservationNo", reservationNo);
        }
        InventorySku sku = skuRepository.findByProductIdForUpdate(reservation.getProductId()).orElse(null);
        if (sku == null) {
            return Map.of("ok", false, "message", "sku not found", "productId", reservation.getProductId());
        }
        sku.setLockedStock(Math.max(0, sku.getLockedStock() - reservation.getQuantity()));
        sku.setAvailableStock(sku.getAvailableStock() + reservation.getQuantity());
        sku.setUpdatedAt(LocalDateTime.now());
        skuRepository.save(sku);
        syncMerchantStock(sku.getProductId(), sku.getAvailableStock());

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        inventoryAlertService.checkAndAlert(sku);
        return Map.of("ok", true, "reservationNo", reservationNo);
    }

    private void confirmByOrderNoInternal(String orderNo) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderNoForUpdate(orderNo);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.LOCKED) {
                InventorySku sku = skuRepository.findByProductIdForUpdate(reservation.getProductId()).orElse(null);
                if (sku != null) {
                    sku.setLockedStock(Math.max(0, sku.getLockedStock() - reservation.getQuantity()));
                    sku.setUpdatedAt(LocalDateTime.now());
                    skuRepository.save(sku);
                    syncMerchantStock(sku.getProductId(), sku.getAvailableStock());
                }
                reservation.setStatus(ReservationStatus.CONFIRMED);
                reservation.setUpdatedAt(LocalDateTime.now());
                reservationRepository.save(reservation);
            }
        }
    }

    private void releaseByOrderNoInternal(String orderNo) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderNoForUpdate(orderNo);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == ReservationStatus.RELEASED || reservation.getStatus() == ReservationStatus.CONFIRMED) {
                continue;
            }
            InventorySku sku = skuRepository.findByProductIdForUpdate(reservation.getProductId()).orElse(null);
            if (sku == null) {
                continue;
            }
            sku.setLockedStock(Math.max(0, sku.getLockedStock() - reservation.getQuantity()));
            sku.setAvailableStock(sku.getAvailableStock() + reservation.getQuantity());
            sku.setUpdatedAt(LocalDateTime.now());
            skuRepository.save(sku);
            syncMerchantStock(sku.getProductId(), sku.getAvailableStock());
            reservation.setStatus(ReservationStatus.RELEASED);
            reservation.setUpdatedAt(LocalDateTime.now());
            reservationRepository.save(reservation);
            inventoryAlertService.checkAndAlert(sku);
        }
    }

    private <T> T withProductLocks(Collection<Long> productIds, Supplier<T> action) {
        List<Long> sortedProductIds = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (sortedProductIds.isEmpty()) {
            return action.get();
        }
        List<RLock> locks = sortedProductIds.stream()
                .map(this::productLock)
                .toList();
        try {
            lockSequentially(locks);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        unlockSequentially(locks);
                    }
                });
                return action.get();
            }
            try {
                return action.get();
            } finally {
                unlockSequentially(locks);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InventoryLockAcquisitionException("库存锁获取失败，请稍后重试");
        }
    }

    private void lockSequentially(List<RLock> locks) throws InterruptedException {
        List<RLock> acquired = new ArrayList<>();
        for (RLock lock : locks) {
            boolean locked = lock.tryLock(lockWaitTimeMillis, lockLeaseTimeMillis, TimeUnit.MILLISECONDS);
            if (!locked) {
                unlockSequentially(acquired);
                throw new InventoryLockAcquisitionException("库存繁忙，请稍后重试");
            }
            acquired.add(lock);
        }
    }

    private void unlockSequentially(List<RLock> locks) {
        for (int i = locks.size() - 1; i >= 0; i--) {
            RLock lock = locks.get(i);
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private RLock productLock(Long productId) {
        return redissonClient.getLock("inventory:sku:" + productId);
    }

    private void syncMerchantStock(Long productId, Integer stock) {
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(merchantServiceId, merchantBaseUrl);
            restTemplate.postForEntity(
                    targetBaseUrl + "/api/merchant/internal/products/stock",
                    Map.of("productId", productId, "stock", stock == null ? 0 : stock),
                    Map.class
            );
        } catch (Exception ignored) {
        }
    }

    private InventorySku autoCreateSku(Long productId) {
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(merchantServiceId, merchantBaseUrl);
            @SuppressWarnings("unchecked")
            Map<String, Object> product = restTemplate.getForObject(
                    targetBaseUrl + "/api/merchant/public/products/" + productId,
                    Map.class
            );
            if (product == null) {
                return null;
            }
            int stock = Integer.parseInt(String.valueOf(product.getOrDefault("stock", 0)));
            InventorySku sku = new InventorySku();
            sku.setProductId(productId);
            sku.setTotalStock(stock);
            sku.setAvailableStock(stock);
            sku.setLockedStock(0);
            sku.setWarnThreshold(Math.max(0, stock / 10));
            sku.setCreatedAt(LocalDateTime.now());
            sku.setUpdatedAt(LocalDateTime.now());
            return skuRepository.save(sku);
        } catch (Exception ignored) {
            return null;
        }
    }
}

