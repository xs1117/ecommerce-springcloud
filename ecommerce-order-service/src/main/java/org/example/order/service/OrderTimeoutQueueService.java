package org.example.order.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.order.domain.OrderStatus;
import org.example.order.mapper.OrderShardMapper;
import org.example.order.mq.OrderEventPublisher;
import org.redisson.api.RBlockingDeque;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class OrderTimeoutQueueService {

	private static final Logger log = LoggerFactory.getLogger(OrderTimeoutQueueService.class);
	private static final String TIMEOUT_QUEUE_KEY = "order:timeout:blocking";

	private final RedissonClient redissonClient;
	private final OrderShardMapper orderShardMapper;
	private final OrderEventPublisher eventPublisher;
	private final OrderDistributedLockService orderDistributedLockService;
	private final TransactionTemplate transactionTemplate;
	private final int payTimeoutMinutes;

	private ExecutorService consumerExecutor;
	private volatile boolean running;

	public OrderTimeoutQueueService(RedissonClient redissonClient,
									OrderShardMapper orderShardMapper,
									OrderEventPublisher eventPublisher,
									OrderDistributedLockService orderDistributedLockService,
									TransactionTemplate transactionTemplate,
									@Value("${app.order.pay-timeout-minutes:10}") int payTimeoutMinutes) {
		this.redissonClient = redissonClient;
		this.orderShardMapper = orderShardMapper;
		this.eventPublisher = eventPublisher;
		this.orderDistributedLockService = orderDistributedLockService;
		this.transactionTemplate = transactionTemplate;
		this.payTimeoutMinutes = Math.max(1, payTimeoutMinutes);
	}

	public void enqueueTimeoutClose(String orderNo) {
		if (orderNo == null || orderNo.isBlank()) {
			return;
		}
		RBlockingDeque<String> blockingDeque = redissonClient.getBlockingDeque(TIMEOUT_QUEUE_KEY);
		RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingDeque);
		delayedQueue.offer(orderNo, payTimeoutMinutes, TimeUnit.MINUTES);
	}

	@PostConstruct
	public void startConsumer() {
		running = true;
		consumerExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "order-timeout-consumer");
			t.setDaemon(true);
			return t;
		});
		consumerExecutor.submit(this::consumeLoop);
	}

	@PreDestroy
	public void shutdownConsumer() {
		running = false;
		if (consumerExecutor != null) {
			consumerExecutor.shutdownNow();
		}
	}

	private void consumeLoop() {
		RBlockingDeque<String> blockingDeque = redissonClient.getBlockingDeque(TIMEOUT_QUEUE_KEY);
		while (running) {
			try {
				String orderNo = blockingDeque.poll(2, TimeUnit.SECONDS);
				if (orderNo == null || orderNo.isBlank()) {
					continue;
				}
				timeoutClose(orderNo);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception ex) {
				log.warn("process timeout order failed", ex);
			}
		}
	}

	private void timeoutClose(String orderNo) {
		orderDistributedLockService.withOrderLock(orderNo, () -> {
			transactionTemplate.executeWithoutResult(status -> closeIfStillWaitPay(orderNo));
			return null;
		});
	}

	private void closeIfStillWaitPay(String orderNo) {
		int shard = shardByOrderNo(orderNo);
		String orderTable = "order_info_" + shard;
		int updated = orderShardMapper.updateOrderStatusIfCurrent(
				orderTable,
				orderNo,
				OrderStatus.CLOSED.name(),
				OrderStatus.WAIT_PAY.name()
		);
		if (updated > 0) {
			eventPublisher.publishOrderClosed(UUID.randomUUID().toString(), orderNo);
		}
	}

	private int shardByOrderNo(String orderNo) {
		char c = orderNo.charAt(orderNo.length() - 1);
		return Character.isDigit(c) ? (c - '0') % 2 : 0;
	}
}

