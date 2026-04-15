package org.example.cart.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.cart.integration.IntegrationEndpointResolver;
import org.example.cart.dto.AddCartItemRequest;
import org.example.cart.dto.CartBehaviorRequest;
import org.example.cart.dto.UpdateCartItemRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final String CART_ITEMS_KEY_PREFIX = "cart:items:";
    private static final String CART_BEHAVIOR_KEY_PREFIX = "cart:behavior:";
    private static final int BEHAVIOR_LIMIT = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IntegrationEndpointResolver endpointResolver;
    private final RestTemplate restTemplate;
    private final String merchantServiceId;
    private final String merchantBaseUrl;

    public CartService(StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper,
                       IntegrationEndpointResolver endpointResolver,
                       @Value("${app.integration.merchant.service-id:ecommerce-merchant-service}") String merchantServiceId,
                       @Value("${app.integration.merchant.base-url:http://localhost:8084}") String merchantBaseUrl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.endpointResolver = endpointResolver;
        this.restTemplate = new RestTemplate();
        this.merchantServiceId = merchantServiceId;
        this.merchantBaseUrl = merchantBaseUrl;
    }

    public Map<String, Object> summary(Long userId) {
        List<Map<String, Object>> items = loadItems(userId).stream()
                .map(this::normalizeItem)
                .sorted(itemComparator())
                .toList();
        return buildSummary(userId, items);
    }

    public List<Map<String, Object>> listItems(Long userId) {
        return loadItems(userId).stream()
                .map(this::normalizeItem)
                .sorted(itemComparator())
                .toList();
    }

    public Map<String, Object> addItem(Long userId, AddCartItemRequest request) {
        validateAddRequest(request);
        List<Map<String, Object>> items = loadItems(userId);
        Map<String, Object> existing = findItemByProductId(items, request.productId()).orElse(null);
        long now = Instant.now().toEpochMilli();
        int quantity = clampQuantity(Optional.ofNullable(request.quantity()).orElse(1));
        ProductAvailability availability = fetchProductAvailability(request.productId());
        ensureSellable(availability);
        int stockLimit = availability.stock();
        int requestedLimit = existing == null
                ? resolveMaxQuantity(request.maxQuantity())
                : resolveMaxQuantity(intValue(existing.get("maxQuantity"), request.maxQuantity() == null ? 99 : request.maxQuantity()));
        int maxQuantity = Math.min(stockLimit, requestedLimit);
        if (maxQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品库存不足，暂不可加入购物车");
        }

        Map<String, Object> saved = existing == null ? new LinkedHashMap<>() : new LinkedHashMap<>(existing);
        saved.putIfAbsent("id", UUID.randomUUID().toString());
        saved.put("userId", userId);
        saved.put("productId", request.productId());
        saved.put("storeId", request.storeId());
        saved.put("storeName", request.storeName());
        saved.put("title", request.title().trim());
        saved.put("description", StringUtils.hasText(request.description()) ? request.description().trim() : null);
        saved.put("coverImageUrl", StringUtils.hasText(request.coverImageUrl()) ? request.coverImageUrl().trim() : null);
        saved.put("price", normalizeMoney(request.price()));
        int nextQuantity = existing == null ? quantity : intValue(existing.get("quantity"), 1) + quantity;
        if (nextQuantity > maxQuantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数量不能超过可购上限");
        }
        saved.put("quantity", clampQuantity(nextQuantity));
        saved.put("maxQuantity", maxQuantity);
        saved.put("selected", request.selected() == null ? Boolean.TRUE : request.selected());
        saved.put("source", StringUtils.hasText(request.source()) ? request.source().trim() : "product");
        saved.put("behaviorDetail", StringUtils.hasText(request.behaviorDetail()) ? request.behaviorDetail().trim() : null);
        saved.put("createdAt", existing == null ? now : longValue(existing.get("createdAt"), now));
        saved.put("updatedAt", now);

        upsertItem(userId, saved);
        recordBehavior(userId, toBehaviorMap("ADD_ITEM", request.productId(), String.valueOf(saved.get("id")), saved.get("quantity"), request.source(), request.behaviorDetail()));
        return normalizeItem(saved);
    }

    public Map<String, Object> updateItem(Long userId, String itemId, UpdateCartItemRequest request) {
        Map<String, Object> item = getItemMap(userId, itemId);
        long now = Instant.now().toEpochMilli();
        int maxQuantity = resolveMaxQuantity(intValue(item.get("maxQuantity"), 99));
        if (request.quantity() != null) {
            ProductAvailability availability = fetchProductAvailability(longValue(item.get("productId"), null));
            ensureSellable(availability);
            maxQuantity = Math.min(maxQuantity, availability.stock());
            if (request.quantity() > maxQuantity) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数量不能超过可购上限");
            }
            item.put("quantity", clampQuantity(request.quantity()));
            item.put("maxQuantity", maxQuantity);
        }
        if (request.selected() != null) {
            item.put("selected", request.selected());
        }
        if (StringUtils.hasText(request.source())) {
            item.put("source", request.source().trim());
        }
        if (request.behaviorDetail() != null) {
            item.put("behaviorDetail", StringUtils.hasText(request.behaviorDetail()) ? request.behaviorDetail().trim() : null);
        }
        item.put("updatedAt", now);
        upsertItem(userId, item);
        recordBehavior(userId, toBehaviorMap("UPDATE_ITEM", longValue(item.get("productId"), null), itemId, item.get("quantity"), request.source(), request.behaviorDetail()));
        return normalizeItem(item);
    }

    public Map<String, Object> removeItem(Long userId, String itemId) {
        Map<String, Object> item = getItemMap(userId, itemId);
        redisTemplate.opsForHash().delete(cartItemsKey(userId), itemId);
        recordBehavior(userId, toBehaviorMap("REMOVE_ITEM", longValue(item.get("productId"), null), itemId, item.get("quantity"), String.valueOf(item.getOrDefault("source", "cart")), "移除购物车商品"));
        return normalizeItem(item);
    }

    public Map<String, Object> clearCart(Long userId) {
        List<Map<String, Object>> items = listItems(userId);
        redisTemplate.delete(cartItemsKey(userId));
        recordBehavior(userId, toBehaviorMap("CLEAR_CART", null, null, items.size(), "cart", "清空购物车"));
        return Map.of("success", true, "removed", items.size());
    }

    public Map<String, Object> count(Long userId) {
        Map<String, Object> summary = summary(userId);
        return Map.of("count", summary.get("totalQuantity"));
    }

    public Map<String, Object> recordBehavior(Long userId, CartBehaviorRequest request) {
        Map<String, Object> event = toBehaviorMap(
                request.action(),
                request.productId(),
                request.itemId(),
                request.quantity(),
                request.source(),
                request.detail()
        );
        recordBehavior(userId, event);
        return event;
    }

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

    private Map<String, Object> buildSummary(Long userId, List<Map<String, Object>> items) {
        int totalQuantity = 0;
        int selectedQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal selectedAmount = BigDecimal.ZERO;

        for (Map<String, Object> item : items) {
            int quantity = intValue(item.get("quantity"), 1);
            BigDecimal price = moneyValue(item.get("price"));
            BigDecimal lineAmount = price.multiply(BigDecimal.valueOf(quantity));
            totalQuantity += quantity;
            totalAmount = totalAmount.add(lineAmount);
            item.put("lineAmount", normalizeMoney(lineAmount));
            if (Boolean.TRUE.equals(item.get("selected"))) {
                selectedQuantity += quantity;
                selectedAmount = selectedAmount.add(lineAmount);
            }
        }

        List<Map<String, Object>> behaviors = recentBehaviors(userId, 10);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("itemCount", items.size());
        result.put("totalQuantity", totalQuantity);
        result.put("selectedQuantity", selectedQuantity);
        result.put("totalAmount", normalizeMoney(totalAmount));
        result.put("selectedAmount", normalizeMoney(selectedAmount));
        result.put("behaviors", behaviors);
        return result;
    }

    private List<Map<String, Object>> loadItems(Long userId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(cartItemsKey(userId));
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.values().stream()
                .map(String::valueOf)
                .map(this::parseMap)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void upsertItem(Long userId, Map<String, Object> item) {
        try {
            redisTemplate.opsForHash().put(cartItemsKey(userId), String.valueOf(item.get("id")), objectMapper.writeValueAsString(item));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "购物车保存失败", ex);
        }
    }

    private Map<String, Object> getItemMap(Long userId, String itemId) {
        Object raw = redisTemplate.opsForHash().get(cartItemsKey(userId), itemId);
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "购物车商品不存在");
        }
        return parseMap(String.valueOf(raw));
    }

    private Optional<Map<String, Object>> findItemByProductId(List<Map<String, Object>> items, Long productId) {
        return items.stream()
                .filter(item -> productId.equals(longValue(item.get("productId"), null)))
                .findFirst();
    }

    private void validateAddRequest(AddCartItemRequest request) {
        if (request == null || request.productId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品信息不能为空");
        }
        if (!StringUtils.hasText(request.title())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品标题不能为空");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品价格不能为空");
        }
    }

    private void recordBehavior(Long userId, Map<String, Object> event) {
        try {
            String key = cartBehaviorKey(userId);
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(event));
            redisTemplate.opsForList().trim(key, 0, BEHAVIOR_LIMIT - 1);
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> toBehaviorMap(String action, Long productId, String itemId, Object quantity, String source, String detail) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("action", action);
        event.put("productId", productId);
        event.put("itemId", itemId);
        event.put("quantity", quantity == null ? 0 : quantity);
        event.put("source", StringUtils.hasText(source) ? source.trim() : "cart");
        event.put("detail", StringUtils.hasText(detail) ? detail.trim() : null);
        event.put("createdAt", Instant.now().toString());
        return event;
    }

    private Map<String, Object> normalizeItem(Map<String, Object> item) {
        Map<String, Object> result = new LinkedHashMap<>(item);
        result.put("id", String.valueOf(item.get("id")));
        result.put("userId", longValue(item.get("userId"), null));
        result.put("productId", longValue(item.get("productId"), null));
        result.put("storeId", longValue(item.get("storeId"), null));
        result.put("storeName", item.get("storeName"));
        result.put("title", item.get("title"));
        result.put("description", item.get("description"));
        result.put("coverImageUrl", item.get("coverImageUrl"));
        result.put("price", normalizeMoney(moneyValue(item.get("price"))));
        result.put("quantity", intValue(item.get("quantity"), 1));
        result.put("maxQuantity", resolveMaxQuantity(intValue(item.get("maxQuantity"), 99)));
        result.put("selected", Boolean.TRUE.equals(item.get("selected")));
        result.put("source", item.get("source"));
        result.put("behaviorDetail", item.get("behaviorDetail"));
        result.put("createdAt", item.get("createdAt"));
        result.put("updatedAt", item.get("updatedAt"));
        BigDecimal lineAmount = moneyValue(item.get("price")).multiply(BigDecimal.valueOf(intValue(item.get("quantity"), 1)));
        result.put("lineAmount", normalizeMoney(lineAmount));
        return result;
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "购物车数据解析失败", ex);
        }
    }

    private Comparator<Map<String, Object>> itemComparator() {
        return Comparator.comparingLong((Map<String, Object> item) -> longValue(item.get("createdAt"), 0L)).reversed();
    }

    private String cartItemsKey(Long userId) {
        return CART_ITEMS_KEY_PREFIX + userId;
    }

    private String cartBehaviorKey(Long userId) {
        return CART_BEHAVIOR_KEY_PREFIX + userId;
    }

    private int clampQuantity(int quantity) {
        return Math.max(1, Math.min(quantity, 99));
    }

    private int resolveMaxQuantity(Integer value) {
        if (value == null) {
            return 99;
        }
        return Math.max(1, Math.min(value, 99));
    }

    private ProductAvailability fetchProductAvailability(Long productId) {
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品信息不能为空");
        }
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(merchantServiceId, merchantBaseUrl);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    targetBaseUrl + "/api/merchant/internal/products/" + productId + "/availability",
                    Map.class
            );
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品暂不可购买");
            }
            boolean sellable = Boolean.TRUE.equals(response.get("sellable"));
            int stock = Math.max(0, intValue(response.get("stock"), 0));
            String reason = String.valueOf(response.getOrDefault("unavailableReason", ""));
            return new ProductAvailability(sellable, stock, reason);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
    }

    private void ensureSellable(ProductAvailability availability) {
        if (availability.sellable() && availability.stock() > 0) {
            return;
        }
        if ("ADMIN_BLOCKED".equalsIgnoreCase(availability.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已被平台下架，暂不可购买");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品库存不足，暂不可加入购物车");
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private Long longValue(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private BigDecimal moneyValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record ProductAvailability(boolean sellable, int stock, String reason) {
    }
}


