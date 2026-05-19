package org.example.cart.store;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Primary
public class CartStoreRouter implements CartStore {

    private static final Logger log = LoggerFactory.getLogger(CartStoreRouter.class);

    private final RedisCartStore redisStore;
    private final JdbcCartStore dbStore;
    private final CartStoreProperties properties;

    public CartStoreRouter(RedisCartStore redisStore, JdbcCartStore dbStore, CartStoreProperties properties) {
        this.redisStore = redisStore;
        this.dbStore = dbStore;
        this.properties = properties;
    }

    @PostConstruct
    void logStoreMode() {
        log.info("Cart store mode: read={}, write={}", properties.getRead(), properties.getWrite());
    }

    @Override
    public List<Map<String, Object>> loadItems(Long userId) {
        return selectReadStore().loadItems(userId);
    }

    @Override
    public Map<String, Object> getItem(Long userId, String itemId) {
        return selectReadStore().getItem(userId, itemId);
    }

    @Override
    public void upsertItem(Long userId, Map<String, Object> item) {
        writeToStores(store -> store.upsertItem(userId, item));
    }

    @Override
    public void deleteItem(Long userId, String itemId) {
        writeToStores(store -> store.deleteItem(userId, itemId));
    }

    @Override
    public void clearItems(Long userId) {
        writeToStores(store -> store.clearItems(userId));
    }

    @Override
    public List<Map<String, Object>> recentBehaviors(Long userId, int limit) {
        return selectReadStore().recentBehaviors(userId, limit);
    }

    @Override
    public void recordBehavior(Long userId, Map<String, Object> event) {
        writeToStores(store -> store.recordBehavior(userId, event));
    }

    private CartStore selectReadStore() {
        return properties.getRead() == CartStoreProperties.ReadStore.DB ? dbStore : redisStore;
    }

    private void writeToStores(Consumer<CartStore> action) {
        CartStoreProperties.WriteStore writeStore = properties.getWrite();
        if (writeStore == CartStoreProperties.WriteStore.BOTH) {
            action.accept(redisStore);
            action.accept(dbStore);
            return;
        }
        if (writeStore == CartStoreProperties.WriteStore.DB) {
            action.accept(dbStore);
            return;
        }
        action.accept(redisStore);
    }
}

