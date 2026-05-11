package org.example.order.service;

import org.example.order.config.OrderRedissonProperties;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class OrderDistributedLockService {

    private final RedissonClient redissonClient;
    private final long lockWaitTimeMillis;
    private final long lockLeaseTimeMillis;

    public OrderDistributedLockService(RedissonClient redissonClient,
                                       OrderRedissonProperties redissonProperties) {
        this.redissonClient = redissonClient;
        this.lockWaitTimeMillis = redissonProperties.getLockWaitTime().toMillis();
        this.lockLeaseTimeMillis = redissonProperties.getLockLeaseTime().toMillis();
    }

    public <T> T withOrderLock(String orderNo, Supplier<T> action) {
        if (orderNo == null || orderNo.isBlank()) {
            return action.get();
        }
        RLock lock = redissonClient.getLock("order:lock:" + orderNo);
        try {
            boolean locked = lock.tryLock(lockWaitTimeMillis, lockLeaseTimeMillis, TimeUnit.MILLISECONDS);
            if (!locked) {
                throw new OrderLockAcquisitionException("订单繁忙，请稍后重试");
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        unlock(lock);
                    }
                });
                return action.get();
            }
            try {
                return action.get();
            } finally {
                unlock(lock);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OrderLockAcquisitionException("订单锁获取失败，请稍后重试");
        }
    }

    private void unlock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception ignored) {
        }
    }
}

