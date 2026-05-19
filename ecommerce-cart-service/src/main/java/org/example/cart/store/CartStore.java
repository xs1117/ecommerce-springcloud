package org.example.cart.store;

import java.util.List;
import java.util.Map;

public interface CartStore {
    List<Map<String, Object>> loadItems(Long userId);

    Map<String, Object> getItem(Long userId, String itemId);

    void upsertItem(Long userId, Map<String, Object> item);

    void deleteItem(Long userId, String itemId);

    void clearItems(Long userId);

    List<Map<String, Object>> recentBehaviors(Long userId, int limit);

    void recordBehavior(Long userId, Map<String, Object> event);
}

