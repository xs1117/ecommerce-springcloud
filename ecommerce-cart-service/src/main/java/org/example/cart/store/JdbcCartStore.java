package org.example.cart.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JdbcCartStore implements CartStore {

    private static final String SELECT_ITEMS = "SELECT item_json FROM cart_item WHERE user_id = ? ORDER BY created_at DESC";
    private static final String SELECT_ITEM = "SELECT item_json FROM cart_item WHERE user_id = ? AND item_id = ?";
    private static final String ITEM_EXISTS = "SELECT COUNT(1) FROM cart_item WHERE user_id = ? AND item_id = ?";
    private static final String INSERT_ITEM = "INSERT INTO cart_item (user_id, item_id, created_at, updated_at, item_json) VALUES (?,?,?,?,?)";
    private static final String UPDATE_ITEM = "UPDATE cart_item SET updated_at = ?, item_json = ? WHERE user_id = ? AND item_id = ?";
    private static final String DELETE_ITEM = "DELETE FROM cart_item WHERE user_id = ? AND item_id = ?";
    private static final String CLEAR_ITEMS = "DELETE FROM cart_item WHERE user_id = ?";
    private static final String SELECT_BEHAVIORS = "SELECT event_json FROM cart_behavior WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
    private static final String INSERT_BEHAVIOR = "INSERT INTO cart_behavior (user_id, event_id, created_at, event_json) VALUES (?,?,?,?)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCartStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> loadItems(Long userId) {
        List<String> values = jdbcTemplate.queryForList(SELECT_ITEMS, String.class, userId);
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
                .map(this::parseMap)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public Map<String, Object> getItem(Long userId, String itemId) {
        try {
            String json = jdbcTemplate.queryForObject(SELECT_ITEM, String.class, userId, itemId);
            if (json == null) {
                return null;
            }
            return parseMap(json);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public void upsertItem(Long userId, Map<String, Object> item) {
        try {
            String itemId = String.valueOf(item.get("id"));
            long createdAt = longValue(item.get("createdAt"), Instant.now().toEpochMilli());
            long updatedAt = longValue(item.get("updatedAt"), createdAt);
            String json = objectMapper.writeValueAsString(item);
            Integer exists = jdbcTemplate.queryForObject(ITEM_EXISTS, Integer.class, userId, itemId);
            if (exists != null && exists > 0) {
                jdbcTemplate.update(UPDATE_ITEM, updatedAt, json, userId, itemId);
            } else {
                jdbcTemplate.update(INSERT_ITEM, userId, itemId, createdAt, updatedAt, json);
            }
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "购物车保存失败", ex);
        }
    }

    @Override
    public void deleteItem(Long userId, String itemId) {
        jdbcTemplate.update(DELETE_ITEM, userId, itemId);
    }

    @Override
    public void clearItems(Long userId) {
        jdbcTemplate.update(CLEAR_ITEMS, userId);
    }

    @Override
    public List<Map<String, Object>> recentBehaviors(Long userId, int limit) {
        List<String> values = jdbcTemplate.queryForList(SELECT_BEHAVIORS, String.class, userId, limit);
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
            String eventId = String.valueOf(event.get("id"));
            long createdAt = Instant.now().toEpochMilli();
            String json = objectMapper.writeValueAsString(event);
            jdbcTemplate.update(INSERT_BEHAVIOR, userId, eventId, createdAt, json);
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

    private long longValue(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }
}

