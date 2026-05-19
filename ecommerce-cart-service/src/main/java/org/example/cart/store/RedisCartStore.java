package org.example.cart.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RedisCartStore implements CartStore {

    private static final String CART_ITEMS_KEY_PREFIX = "cart:items:";
    private static final String CART_BEHAVIOR_KEY_PREFIX = "cart:behavior:";
    private static final int BEHAVIOR_LIMIT = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCartStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> loadItems(Long userId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(cartItemsKey(userId));
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.values().stream()
                .map(String::valueOf)
                .map(this::parseMap)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public Map<String, Object> getItem(Long userId, String itemId) {
        Object raw = redisTemplate.opsForHash().get(cartItemsKey(userId), itemId);
        if (raw == null) {
            return null;
        }
        return parseMap(String.valueOf(raw));
    }

    @Override
    public void upsertItem(Long userId, Map<String, Object> item) {
        try {
            redisTemplate.opsForHash().put(cartItemsKey(userId), String.valueOf(item.get("id")), objectMapper.writeValueAsString(item));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "购物车保存失败", ex);
        }
    }

    @Override
    public void deleteItem(Long userId, String itemId) {
        redisTemplate.opsForHash().delete(cartItemsKey(userId), itemId);
    }

    @Override
    public void clearItems(Long userId) {
        redisTemplate.delete(cartItemsKey(userId));
    }

    @Override
    public List<Map<String, Object>> recentBehaviors(Long userId, int limit) {
        int max = Math.max(1, Math.min(limit, BEHAVIOR_LIMIT));
        List<String> values = redisTemplate.opsForList().range(cartBehaviorKey(userId), 0, max - 1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::parseMap)
                .collect(Collectors.toList());
    }

    @Override
    public void recordBehavior(Long userId, Map<String, Object> event) {
        try {
            String key = cartBehaviorKey(userId);
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(event));
            redisTemplate.opsForList().trim(key, 0, BEHAVIOR_LIMIT - 1);
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "购物车数据解析失败", ex);
        }
    }

    private String cartItemsKey(Long userId) {
        return CART_ITEMS_KEY_PREFIX + userId;
    }

    private String cartBehaviorKey(Long userId) {
        return CART_BEHAVIOR_KEY_PREFIX + userId;
    }
}

