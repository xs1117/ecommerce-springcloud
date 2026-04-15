package org.example.merchant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.merchant.integration.IntegrationEndpointResolver;
import org.example.merchant.domain.MerchantProductOffShelfReason;
import org.example.merchant.domain.MerchantProduct;
import org.example.merchant.domain.MerchantProductComment;
import org.example.merchant.domain.MerchantProductStatus;
import org.example.merchant.domain.MerchantStore;
import org.example.merchant.domain.MerchantStoreStatus;
import org.example.merchant.repository.MerchantProductCommentRepository;
import org.example.merchant.repository.MerchantProductRepository;
import org.example.merchant.repository.MerchantStoreRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class MerchantStoreService {

    private static final Logger log = LoggerFactory.getLogger(MerchantStoreService.class);

    private static final String HOT_PRODUCT_KEY = "merchant:product:hot";
    private static final String RECOMMEND_PRODUCT_KEY_PREFIX = "merchant:product:recommend:";
    private static final String ORDER_PAID_APPLIED_KEY_PREFIX = "merchant:order:paid:applied:";
    private static final Duration HOT_PRODUCT_TTL = Duration.ofMinutes(10);
    private static final Duration RECOMMEND_PRODUCT_TTL = Duration.ofMinutes(15);
    private static final String[] CITY_POOL = {"上海", "深圳", "广州", "杭州", "成都", "南京", "武汉", "苏州"};

    private final MerchantStoreRepository storeRepository;
    private final MerchantProductRepository productRepository;
    private final MerchantProductCommentRepository commentRepository;
    private final MerchantApplicationService applicationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IntegrationEndpointResolver endpointResolver;
    private final RestTemplate restTemplate;
    private final String inventoryServiceId;
    private final String inventoryBaseUrl;

    public MerchantStoreService(MerchantStoreRepository storeRepository,
                                MerchantProductRepository productRepository,
                                MerchantProductCommentRepository commentRepository,
                                MerchantApplicationService applicationService,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                IntegrationEndpointResolver endpointResolver,
                                @Value("${app.integration.inventory.service-id:ecommerce-inventory-service}") String inventoryServiceId,
                                @Value("${app.integration.inventory.base-url:http://localhost:8086}") String inventoryBaseUrl) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.commentRepository = commentRepository;
        this.applicationService = applicationService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.endpointResolver = endpointResolver;
        this.restTemplate = new RestTemplate();
        this.inventoryServiceId = inventoryServiceId;
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    @Transactional
    public Map<String, Object> createStore(Long userId,
                                           String storeName,
                                           String storeIntro,
                                           String storeImageUrl,
                                           String mainCategory,
                                           String tags) {
        if (!applicationService.hasApprovedApplication(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先通过商家入驻审核");
        }
        if (storeRepository.findByOwnerUserId(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每位商家仅允许创建一个店铺");
        }
        if (!StringUtils.hasText(storeName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺名称不能为空");
        }
        MerchantStore store = new MerchantStore();
        store.setOwnerUserId(userId);
        store.setStoreName(storeName.trim());
        store.setStoreIntro(StringUtils.hasText(storeIntro) ? storeIntro.trim() : null);
        store.setStoreImageUrl(StringUtils.hasText(storeImageUrl) ? storeImageUrl.trim() : null);
        store.setMainCategory(normalizeStoreCategory(mainCategory));
        store.setTags(StringUtils.hasText(tags) ? tags.trim() : "精选");
        store.setStatus(MerchantStoreStatus.ACTIVE);
        return toStoreView(storeRepository.save(store));
    }

    @Transactional
    public Map<String, Object> updateStore(Long userId,
                                           Long storeId,
                                           String storeName,
                                           String storeIntro,
                                           String storeImageUrl,
                                           String mainCategory,
                                           String tags) {
        MerchantStore store = storeRepository.findByIdAndOwnerUserId(storeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在或无权限"));
        if (StringUtils.hasText(storeName)) {
            store.setStoreName(storeName.trim());
        }
        store.setStoreIntro(StringUtils.hasText(storeIntro) ? storeIntro.trim() : null);
        store.setStoreImageUrl(StringUtils.hasText(storeImageUrl) ? storeImageUrl.trim() : null);
        if (mainCategory != null) {
            store.setMainCategory(normalizeStoreCategory(mainCategory));
        }
        if (tags != null) {
            store.setTags(StringUtils.hasText(tags) ? tags.trim() : "精选");
        }
        return toStoreView(storeRepository.save(store));
    }

    public List<Map<String, Object>> myStores(Long userId) {
        return storeRepository.findAllByOwnerUserId(userId)
                .stream()
                .map(this::toStoreView)
                .toList();
    }

    @Transactional
    public Map<String, Object> publishProduct(Long userId,
                                              Long storeId,
                                              String title,
                                              String description,
                                              String imageUrl,
                                              String category,
                                              String tags,
                                              BigDecimal price,
                                              Integer stock) {
        MerchantStore store = storeRepository.findByIdAndOwnerUserId(storeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在或无权限"));
        if (store.getStatus() != MerchantStoreStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "店铺不可上架商品");
        }
        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品标题不能为空");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品价格必须大于0");
        }
        if (stock == null || stock < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不能为负数");
        }

        MerchantProduct product = new MerchantProduct();
        product.setStoreId(storeId);
        product.setTitle(title.trim());
        product.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        product.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl.trim() : null);
        product.setCategory(StringUtils.hasText(category) ? normalizeCategory(category) : inferCategory(title, description));
        product.setTags(StringUtils.hasText(tags) ? tags.trim() : "推荐");
        product.setPrice(price);
        product.setStock(stock);
        product.setStatus(MerchantProductStatus.ON_SHELF);
        product.setOffShelfReason(null);
        product.setSalesCount(0);
        reconcileShelfStatus(product);

        MerchantProduct saved = productRepository.save(product);
        syncInventoryStock(saved.getId(), saved.getStock());
        evictCatalogCache();
        return toProductView(saved);
    }

    @Transactional
    public Map<String, Object> updateProduct(Long userId,
                                             Long productId,
                                             String title,
                                             String description,
                                             String imageUrl,
                                             String category,
                                             String tags,
                                             BigDecimal price,
                                             Integer stock,
                                             MerchantProductStatus status) {
        MerchantStore store = storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在或无权限"));
        MerchantProduct product = productRepository.findByIdAndStoreId(productId, store.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在或无权限"));
        if (StringUtils.hasText(title)) {
            product.setTitle(title.trim());
        }
        if (description != null) {
            product.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        }
        if (imageUrl != null) {
            product.setImageUrl(StringUtils.hasText(imageUrl) ? imageUrl.trim() : null);
        }
        if (category != null) {
            product.setCategory(StringUtils.hasText(category) ? normalizeCategory(category) : inferCategory(product.getTitle(), product.getDescription()));
        }
        if (tags != null) {
            product.setTags(StringUtils.hasText(tags) ? tags.trim() : productTag(product));
        }
        if (price != null) {
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品价格必须大于0");
            }
            product.setPrice(price);
        }
        if (stock != null) {
            if (stock < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不能为负数");
            }
            product.setStock(stock);
        }
        if (status != null) {
            if (status == MerchantProductStatus.ON_SHELF
                    && MerchantProductOffShelfReason.ADMIN_BLOCKED == product.getOffShelfReason()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品已被管理员下架，暂不可上架");
            }
            product.setStatus(status);
            if (status == MerchantProductStatus.ON_SHELF) {
                product.setOffShelfReason(null);
            } else if (MerchantProductOffShelfReason.ADMIN_BLOCKED != product.getOffShelfReason()) {
                product.setOffShelfReason(MerchantProductOffShelfReason.MERCHANT_MANUAL);
            }
        }
        reconcileShelfStatus(product);
        MerchantProduct saved = productRepository.save(product);
        syncInventoryStock(saved.getId(), saved.getStock());
        evictCatalogCache();
        return toProductView(saved);
    }

    public List<Map<String, Object>> myProducts(Long userId, Long storeId) {
        storeRepository.findByIdAndOwnerUserId(storeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在或无权限"));
        return productRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId)
                .stream()
                .map(this::toProductView)
                .toList();
    }

    public List<Map<String, Object>> hotProducts() {
        String cached = redisTemplate.opsForValue().get(HOT_PRODUCT_KEY);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                redisTemplate.delete(HOT_PRODUCT_KEY);
            }
        }
        List<Map<String, Object>> products = productRepository
                .findTop10ByStatusOrderBySalesCountDescCreatedAtDesc(MerchantProductStatus.ON_SHELF)
                .stream()
                .map(this::toProductView)
                .toList();
        try {
            redisTemplate.opsForValue().set(HOT_PRODUCT_KEY, objectMapper.writeValueAsString(products), HOT_PRODUCT_TTL);
        } catch (Exception ignored) {
        }
        return products;
    }

    @Transactional
    public Map<String, Object> syncProductStock(Long productId, Integer stock) {
        MerchantProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        product.setStock(Math.max(0, stock == null ? 0 : stock));
        reconcileShelfStatus(product);
        MerchantProduct saved = productRepository.save(product);
        evictCatalogCache();
        return Map.of(
                "success", true,
                "message", "库存已同步",
                "product", toProductView(saved)
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildInventorySnapshot() {
        try {
            productRepository.findAll().forEach(product -> {
                try {
                    syncInventoryStock(product.getId(), product.getStock());
                } catch (Exception ex) {
                    log.warn("sync inventory snapshot failed for product {}, reason={}", product.getId(), ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.warn("rebuild inventory snapshot failed, reason={}", ex.getMessage());
        }
    }

    @Transactional
    public void applyPaidOrder(String orderNo, List<PaidItem> items) {
        if (!StringUtils.hasText(orderNo) || items == null || items.isEmpty()) {
            return;
        }
        String idempotentKey = ORDER_PAID_APPLIED_KEY_PREFIX + orderNo;
        Boolean firstApply = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofDays(7));
        if (!Boolean.TRUE.equals(firstApply)) {
            return;
        }
        for (PaidItem item : items) {
            if (item.productId() == null || item.quantity() == null || item.quantity() <= 0) {
                continue;
            }
            MerchantProduct product = productRepository.findById(item.productId()).orElse(null);
            if (product == null) {
                continue;
            }
            int nextSales = (product.getSalesCount() == null ? 0 : product.getSalesCount()) + item.quantity();
            // stock is managed by inventory service and should not be deducted in merchant projection.
            product.setSalesCount(nextSales);
            productRepository.save(product);
        }
        evictCatalogCache();
    }

    public List<Map<String, Object>> listPublicStores(Integer limit) {
        int max = clampLimit(limit, 6, 60);
        return storeRepository.findAllByStatusOrderByCreatedAtDesc(MerchantStoreStatus.ACTIVE)
                .stream()
                .limit(max)
                .map(this::toPublicStoreView)
                .toList();
    }

    public Map<String, Object> getPublicStore(Long storeId) {
        MerchantStore store = storeRepository.findByIdAndStatus(storeId, MerchantStoreStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在"));
        return toPublicStoreView(store);
    }

    public List<Map<String, Object>> listPublicStoreProducts(Long storeId, Integer limit) {
        if (storeRepository.findByIdAndStatus(storeId, MerchantStoreStatus.ACTIVE).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
        }
        int max = clampLimit(limit, 24, 80);
        return productRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId)
                .stream()
                .filter(this::isPublicVisibleProduct)
                .sorted(publicProductComparator("sales-desc"))
                .limit(max)
                .map(this::toPublicProductView)
                .toList();
    }

    public Map<String, Object> getPublicProduct(Long productId) {
        MerchantProduct product = loadPublicVisibleProduct(productId);
        return toPublicProductView(product);
    }

    public List<Map<String, Object>> listPublicProductComments(Long productId) {
        loadPublicVisibleProduct(productId);
        return commentRepository.findAllByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toCommentView)
                .toList();
    }

    @Transactional
    public Map<String, Object> createProductComment(Long userId,
                                                    String username,
                                                    String nickname,
                                                    Long productId,
                                                    String content,
                                                    String imageUrls) {
        loadPublicVisibleProduct(productId);
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评论内容不能为空");
        }
        MerchantProductComment comment = new MerchantProductComment();
        comment.setProductId(productId);
        comment.setUserId(userId);
        comment.setUsername(StringUtils.hasText(username) ? username.trim() : "user-" + userId);
        comment.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : comment.getUsername());
        comment.setContent(content.trim());
        comment.setImageUrls(StringUtils.hasText(imageUrls) ? imageUrls.trim() : null);
        return toCommentView(commentRepository.save(comment));
    }

    public List<Map<String, Object>> searchPublicProducts(String keyword, String sort, Integer limit) {
        int max = clampLimit(limit, 24, 80);
        String normalized = normalizeText(keyword);
        return productRepository.findAll()
                .stream()
                .filter(this::isPublicVisibleProduct)
                .filter(product -> matchPublicProduct(product, normalized))
                .sorted(publicProductComparator(sort))
                .limit(max)
                .map(this::toPublicProductView)
                .toList();
    }

    @Transactional
    public Map<String, Object> deleteProduct(Long userId, Long productId) {
        MerchantStore store = storeRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在或无权限"));
        MerchantProduct product = productRepository.findByIdAndStoreId(productId, store.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在或无权限"));
        commentRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
        evictCatalogCache();
        return Map.of(
                "success", true,
                "message", "商品已删除",
                "productId", productId
        );
    }

    public List<Map<String, Object>> searchPublicStores(String keyword, String sort, Integer limit) {
        int max = clampLimit(limit, 24, 80);
        String normalized = normalizeText(keyword);
        return storeRepository.findAllByStatusOrderByCreatedAtDesc(MerchantStoreStatus.ACTIVE)
                .stream()
                .filter(store -> matchPublicStore(store, normalized))
                .sorted(publicStoreComparator(sort))
                .limit(max)
                .map(this::toPublicStoreView)
                .toList();
    }

    public List<Map<String, Object>> recommendPublicProducts(List<String> keywords, Integer limit) {
        int max = clampLimit(limit, 8, 30);
        List<String> normalized = keywords == null ? List.of() : keywords.stream().map(this::normalizeText).filter(item -> !item.isEmpty()).toList();
        String cacheKey = recommendCacheKey(normalized, max);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                redisTemplate.delete(cacheKey);
            }
        }

        List<MerchantProduct> base = productRepository.findAllByStatusOrderBySalesCountDescCreatedAtDesc(MerchantProductStatus.ON_SHELF);
        List<ScoredProduct> scored = base.stream()
                .map(product -> new ScoredProduct(product, scoreRecommendation(product, normalized)))
                .sorted((a, b) -> {
                    int compare = Integer.compare(b.score(), a.score());
                    if (compare != 0) {
                        return compare;
                    }
                    return Integer.compare(b.product().getSalesCount(), a.product().getSalesCount());
                })
                .toList();

        List<MerchantProduct> selected;
        if (!normalized.isEmpty() && scored.stream().anyMatch(item -> item.score() > 0)) {
            selected = scored.stream().filter(item -> item.score() > 0).map(ScoredProduct::product).limit(max).toList();
        } else {
            ArrayList<MerchantProduct> fallback = new ArrayList<>(base.stream().limit(Math.max(max * 2, 12)).toList());
            if (fallback.size() > 1) {
                java.util.Collections.shuffle(fallback, ThreadLocalRandom.current());
            }
            selected = fallback.stream().limit(max).toList();
        }

        List<Map<String, Object>> result = selected.stream().map(this::toPublicProductView).toList();
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), RECOMMEND_PRODUCT_TTL);
        } catch (Exception ignored) {
        }
        return result;
    }

    @Transactional
    public Map<String, Object> adminUpdateStoreStatus(Long storeId, MerchantStoreStatus status) {
        MerchantStore store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在"));
        store.setStatus(status);
        return toStoreView(storeRepository.save(store));
    }

    public List<Map<String, Object>> adminListStores() {
        return storeRepository.findAll().stream().map(this::toStoreView).toList();
    }

    public List<Map<String, Object>> adminListStores(String keyword) {
        String normalized = normalizeText(keyword);
        return storeRepository.findAll().stream()
                .filter(store -> !StringUtils.hasText(normalized)
                        || contains(store.getStoreName(), normalized)
                        || contains(store.getStoreIntro(), normalized)
                        || contains(store.getMainCategory(), normalized)
                        || contains(store.getTags(), normalized)
                        || contains(String.valueOf(store.getOwnerUserId()), normalized))
                .sorted(Comparator.comparing(MerchantStore::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toStoreView)
                .toList();
    }

    public List<Map<String, Object>> adminListStoreProducts(Long storeId, String keyword) {
        if (storeRepository.findById(storeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
        }
        String normalized = normalizeText(keyword);
        return productRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId)
                .stream()
                .filter(product -> !StringUtils.hasText(normalized)
                        || contains(product.getTitle(), normalized)
                        || contains(product.getDescription(), normalized)
                        || contains(product.getCategory(), normalized)
                        || contains(product.getTags(), normalized)
                        || contains(String.valueOf(product.getId()), normalized))
                .map(this::toProductView)
                .toList();
    }

    @Transactional
    public Map<String, Object> adminForceOffShelfProduct(Long storeId, Long productId) {
        storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在"));
        MerchantProduct product = productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        product.setStatus(MerchantProductStatus.OFF_SHELF);
        product.setOffShelfReason(MerchantProductOffShelfReason.ADMIN_BLOCKED);
        MerchantProduct saved = productRepository.save(product);
        evictCatalogCache();
        return Map.of(
                "success", true,
                "message", "商品已强制下架",
                "product", toProductView(saved)
        );
    }

    public Map<String, Object> productAvailability(Long productId) {
        MerchantProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        boolean sellable = isSellable(product);
        int stock = Math.max(0, product.getStock() == null ? 0 : product.getStock());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", product.getId());
        result.put("status", product.getStatus());
        result.put("offShelfReason", product.getOffShelfReason());
        result.put("stock", stock);
        result.put("sellable", sellable);
        result.put("unavailableReason", sellable ? "" : unavailableReason(product));
        return result;
    }

    @Transactional
    public Map<String, Object> adminDeleteProductComment(Long productId, Long commentId) {
        MerchantProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        MerchantProductComment comment = commentRepository.findByIdAndProductId(commentId, product.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评论不存在"));
        commentRepository.delete(comment);
        return Map.of(
                "success", true,
                "message", "评论已删除",
                "commentId", commentId
        );
    }

    private Comparator<MerchantProduct> publicProductComparator(String sort) {
        String normalized = normalizeText(sort);
        return switch (normalized) {
            case "price-asc" -> Comparator.comparing(MerchantProduct::getPrice);
            case "price-desc" -> Comparator.comparing(MerchantProduct::getPrice, Comparator.reverseOrder());
            case "stock-desc" -> Comparator.comparing(MerchantProduct::getStock, Comparator.reverseOrder());
            case "newest" -> Comparator.comparing(MerchantProduct::getUpdatedAt, Comparator.reverseOrder());
            default -> Comparator.comparing(MerchantProduct::getSalesCount, Comparator.reverseOrder())
                    .thenComparing(MerchantProduct::getUpdatedAt, Comparator.reverseOrder());
        };
    }

    private Comparator<MerchantStore> publicStoreComparator(String sort) {
        String normalized = normalizeText(sort);
        return switch (normalized) {
            case "newest" -> Comparator.comparing(MerchantStore::getUpdatedAt, Comparator.reverseOrder());
            case "price-asc", "price-desc", "stock-desc" -> Comparator.comparingInt(this::storeProductCount).reversed();
            default -> Comparator.comparingInt(this::storeFollowers).reversed().thenComparing(MerchantStore::getUpdatedAt, Comparator.reverseOrder());
        };
    }

    private boolean matchPublicProduct(MerchantProduct product, String keyword) {
        if (!isPublicVisibleProduct(product)) {
            return false;
        }
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        MerchantStore store = activeStore(product.getStoreId());
        if (store == null) {
            return false;
        }
        String category = inferCategory(product.getTitle(), product.getDescription());
        return contains(product.getTitle(), keyword)
                || contains(product.getDescription(), keyword)
                || contains(product.getCategory(), keyword)
                || contains(product.getTags(), keyword)
                || contains(store.getStoreName(), keyword)
                || contains(category, keyword);
    }

    private boolean isPublicVisibleProduct(MerchantProduct product) {
        if (product == null) {
            return false;
        }
        if (!isSellable(product)) {
            return false;
        }
        return activeStore(product.getStoreId()) != null;
    }

    private boolean isSellable(MerchantProduct product) {
        if (product == null) {
            return false;
        }
        if (product.getStatus() != MerchantProductStatus.ON_SHELF) {
            return false;
        }
        if (activeStore(product.getStoreId()) == null) {
            return false;
        }
        return product.getStock() != null && product.getStock() > 0;
    }

    private String unavailableReason(MerchantProduct product) {
        if (MerchantProductOffShelfReason.ADMIN_BLOCKED == product.getOffShelfReason()) {
            return "ADMIN_BLOCKED";
        }
        if (product.getStock() == null || product.getStock() <= 0) {
            return "OUT_OF_STOCK";
        }
        if (product.getStatus() == MerchantProductStatus.OFF_SHELF) {
            return "OFF_SHELF";
        }
        if (activeStore(product.getStoreId()) == null) {
            return "STORE_INACTIVE";
        }
        return "UNSELLABLE";
    }

    private MerchantProduct loadPublicVisibleProduct(Long productId) {
        MerchantProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (!isPublicVisibleProduct(product)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private boolean matchPublicStore(MerchantStore store, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return contains(store.getStoreName(), keyword)
                || contains(store.getStoreIntro(), keyword)
                || contains(store.getMainCategory(), keyword)
                || contains(store.getTags(), keyword)
                || contains(storeCity(store), keyword)
                || contains(inferStoreCategory(store), keyword);
    }

    private int scoreRecommendation(MerchantProduct product, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return product.getSalesCount();
        }
        MerchantStore store = activeStore(product.getStoreId());
        String category = inferCategory(product.getTitle(), product.getDescription());
        String productTags = product.getTags();
        int score = 0;
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            score += contains(product.getTitle(), keyword) ? 80 : 0;
            score += contains(product.getDescription(), keyword) ? 40 : 0;
            score += contains(product.getCategory(), keyword) ? 35 : 0;
            score += contains(productTags, keyword) ? 25 : 0;
            score += contains(category, keyword) ? 30 : 0;
            score += (store != null && contains(store.getStoreName(), keyword)) ? 20 : 0;
        }
        return score;
    }

    private Map<String, Object> toStoreView(MerchantStore store) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", store.getId());
        result.put("ownerUserId", store.getOwnerUserId());
        result.put("storeName", store.getStoreName());
        result.put("storeIntro", store.getStoreIntro() == null ? "" : store.getStoreIntro());
        result.put("storeImageUrl", store.getStoreImageUrl() == null ? "" : store.getStoreImageUrl());
        result.put("mainCategory", store.getMainCategory() == null ? "" : store.getMainCategory());
        result.put("tags", store.getTags() == null ? "" : store.getTags());
        result.put("status", store.getStatus());
        result.put("createdAt", store.getCreatedAt());
        result.put("updatedAt", store.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toPublicStoreView(MerchantStore store) {
        Map<String, Object> result = toStoreView(store);
        result.put("title", store.getStoreName());
        result.put("category", StringUtils.hasText(store.getMainCategory()) ? store.getMainCategory().trim() : inferStoreCategory(store));
        result.put("city", storeCity(store));
        result.put("rating", storeRating(store));
        result.put("followers", storeFollowers(store));
        result.put("productCount", storeProductCount(store));
        result.put("tag", StringUtils.hasText(store.getTags()) ? firstTag(store.getTags()) : storeTag(store));
        result.put("slogan", storeSlogan(store));
        return result;
    }

    private Map<String, Object> toProductView(MerchantProduct product) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", product.getId());
        result.put("storeId", product.getStoreId());
        result.put("title", product.getTitle());
        result.put("description", product.getDescription() == null ? "" : product.getDescription());
        result.put("imageUrl", product.getImageUrl() == null ? "" : product.getImageUrl());
        result.put("category", product.getCategory() == null ? "" : product.getCategory());
        result.put("tags", product.getTags() == null ? "" : product.getTags());
        result.put("price", product.getPrice());
        result.put("stock", product.getStock());
        result.put("salesCount", product.getSalesCount());
        result.put("status", product.getStatus());
        result.put("offShelfReason", product.getOffShelfReason());
        result.put("sellable", isSellable(product));
        result.put("createdAt", product.getCreatedAt());
        result.put("updatedAt", product.getUpdatedAt());
        return result;
    }

    private void reconcileShelfStatus(MerchantProduct product) {
        int stock = Math.max(0, product.getStock() == null ? 0 : product.getStock());
        product.setStock(stock);
        if (stock <= 0) {
            product.setStatus(MerchantProductStatus.OFF_SHELF);
            if (MerchantProductOffShelfReason.ADMIN_BLOCKED != product.getOffShelfReason()) {
                product.setOffShelfReason(MerchantProductOffShelfReason.OUT_OF_STOCK);
            }
            return;
        }
        if (MerchantProductOffShelfReason.OUT_OF_STOCK == product.getOffShelfReason()) {
            product.setStatus(MerchantProductStatus.ON_SHELF);
            product.setOffShelfReason(null);
        }
    }

    private Map<String, Object> toCommentView(MerchantProductComment comment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("productId", comment.getProductId());
        result.put("userId", comment.getUserId());
        result.put("username", comment.getUsername());
        result.put("nickname", StringUtils.hasText(comment.getNickname()) ? comment.getNickname() : comment.getUsername());
        result.put("content", comment.getContent());
        result.put("imageUrls", comment.getImageUrls() == null ? "" : comment.getImageUrls());
        result.put("createdAt", comment.getCreatedAt());
        result.put("updatedAt", comment.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toPublicProductView(MerchantProduct product) {
        MerchantStore store = activeStore(product.getStoreId());
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品对应店铺不存在");
        }
        Map<String, Object> result = toProductView(product);
        result.put("type", "product");
        result.put("title", product.getTitle());
        result.put("subtitle", shortSubtitle(product));
        result.put("storeName", store.getStoreName());
        result.put("storeImageUrl", store.getStoreImageUrl() == null ? "" : store.getStoreImageUrl());
        result.put("category", StringUtils.hasText(product.getCategory()) ? product.getCategory().trim() : inferCategory(product.getTitle(), product.getDescription()));
        result.put("city", storeCity(store));
        result.put("rating", storeRating(store));
        result.put("tag", StringUtils.hasText(product.getTags()) ? firstTag(product.getTags()) : productTag(product));
        return result;
    }

    private void evictCatalogCache() {
        redisTemplate.delete(HOT_PRODUCT_KEY);
        try {
            var keys = redisTemplate.keys(RECOMMEND_PRODUCT_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    private void syncInventoryStock(Long productId, Integer stock) {
        if (productId == null) {
            return;
        }
        try {
            String targetBaseUrl = endpointResolver.resolveBaseUrl(inventoryServiceId, inventoryBaseUrl);
            restTemplate.postForEntity(
                    targetBaseUrl + "/api/inventory/adjust",
                    Map.of(
                            "productId", productId,
                            "totalStock", stock == null ? 0 : stock,
                            "warnThreshold", Math.max(0, (stock == null ? 0 : stock) / 10)
                    ),
                    Map.class
            );
        } catch (Exception ex) {
            log.warn("sync inventory stock failed for product {}, reason={}", productId, ex.getMessage());
        }
    }

    private String recommendCacheKey(List<String> keywords, int limit) {
        String joined = (keywords == null || keywords.isEmpty()) ? "_default" : String.join("|", keywords);
        return RECOMMEND_PRODUCT_KEY_PREFIX + joined + ":" + limit;
    }

    private String firstTag(String tags) {
        if (!StringUtils.hasText(tags)) {
            return "推荐";
        }
        String[] split = tags.split("[,，|/]");
        for (String item : split) {
            if (StringUtils.hasText(item)) {
                return item.trim();
            }
        }
        return tags.trim();
    }

    private MerchantStore activeStore(Long storeId) {
        if (storeId == null) {
            return null;
        }
        return storeRepository.findByIdAndStatus(storeId, MerchantStoreStatus.ACTIVE).orElse(null);
    }

    private String shortSubtitle(MerchantProduct product) {
        String description = product.getDescription();
        if (!StringUtils.hasText(description)) {
            return productTag(product) + " · 精选商品";
        }
        String trimmed = description.trim();
        return trimmed.length() > 28 ? trimmed.substring(0, 28) + "..." : trimmed;
    }

    private String inferStoreCategory(MerchantStore store) {
        if (StringUtils.hasText(store.getMainCategory())) {
            return store.getMainCategory().trim();
        }
        List<MerchantProduct> products = productRepository.findTop8ByStoreIdAndStatusOrderBySalesCountDescCreatedAtDesc(store.getId(), MerchantProductStatus.ON_SHELF);
        if (products.isEmpty()) {
            return "综合店铺";
        }
        return products.stream()
                .map(item -> inferCategory(item.getTitle(), item.getDescription()))
                .collect(Collectors.groupingBy(item -> item, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("综合店铺");
    }

    private String normalizeStoreCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "综合店铺";
        }
        String normalized = normalizeCategory(category);
        return StringUtils.hasText(normalized) ? normalized : "综合店铺";
    }

    private String normalizeCategory(String category) {
        String raw = category == null ? "" : category.trim();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = normalizeText(raw);
        if (containsAny(text, "手机", "数码", "3c", "平板", "耳机", "手表", "电子")) {
            return "手机数码";
        }
        if (containsAny(text, "电脑", "办公", "笔记本", "键盘", "鼠标", "打印")) {
            return "电脑办公";
        }
        if (containsAny(text, "家电", "家居", "家装", "建材", "家具", "厨具", "空气炸锅")) {
            return "家电家居";
        }
        if (containsAny(text, "服饰", "鞋", "包", "穿搭", "男装", "女装")) {
            return "服饰鞋包";
        }
        if (containsAny(text, "美妆", "护肤", "个护", "彩妆", "香水")) {
            return "美妆个护";
        }
        if (containsAny(text, "食品", "生鲜", "零食", "水果", "粮油")) {
            return "食品生鲜";
        }
        if (containsAny(text, "母婴", "玩具", "婴儿", "童装")) {
            return "母婴玩具";
        }
        if (containsAny(text, "运动", "户外", "健身", "跑步", "露营")) {
            return "运动户外";
        }
        if (containsAny(text, "图书", "文创", "文具", "学习", "教育")) {
            return "图书文创";
        }
        if (containsAny(text, "汽车", "车品", "机油", "轮胎")) {
            return "汽车用品";
        }
        if (containsAny(text, "宠物", "猫", "狗", "水族")) {
            return "宠物生活";
        }
        if (containsAny(text, "家装", "建材", "五金")) {
            return "家装建材";
        }
        return raw;
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String inferCategory(String title, String description) {
        String text = normalizeText((title == null ? "" : title) + " " + (description == null ? "" : description));
        if (text.contains("手机") || text.contains("耳机") || text.contains("手表") || text.contains("平板")) {
            return "手机数码";
        }
        if (text.contains("笔记本") || text.contains("键盘") || text.contains("鼠标") || text.contains("办公")) {
            return "电脑办公";
        }
        if (text.contains("家电") || text.contains("机器人") || text.contains("加湿") || text.contains("料理") || text.contains("空气炸锅")) {
            return "家电家居";
        }
        if (text.contains("鞋") || text.contains("服") || text.contains("包") || text.contains("穿搭")) {
            return "服饰鞋包";
        }
        if (text.contains("护肤") || text.contains("美妆") || text.contains("彩妆") || text.contains("个护")) {
            return "美妆个护";
        }
        if (text.contains("生鲜") || text.contains("牛排") || text.contains("水果") || text.contains("食品")) {
            return "食品生鲜";
        }
        return "精选好物";
    }

    private String storeCity(MerchantStore store) {
        if (store == null || store.getId() == null) {
            return "全国";
        }
        return CITY_POOL[(int) (store.getId() % CITY_POOL.length)];
    }

    private double storeRating(MerchantStore store) {
        if (store == null || store.getId() == null) {
            return 4.6;
        }
        double score = 4.5 + (store.getId() % 5) * 0.1;
        return Math.min(score, 4.9);
    }

    private int storeFollowers(MerchantStore store) {
        if (store == null || store.getId() == null) {
            return 1000;
        }
        return 1000 + Math.toIntExact(store.getId() * 237);
    }

    private int storeProductCount(MerchantStore store) {
        return productRepository.findAllByStoreIdAndStatusOrderBySalesCountDescCreatedAtDesc(store.getId(), MerchantProductStatus.ON_SHELF).size();
    }

    private String storeTag(MerchantStore store) {
        if (StringUtils.hasText(store.getTags())) {
            return firstTag(store.getTags());
        }
        int count = storeProductCount(store);
        if (count >= 12) {
            return "大牌";
        }
        if (count >= 6) {
            return "热门";
        }
        return "精选";
    }

    private String storeSlogan(MerchantStore store) {
        if (StringUtils.hasText(store.getStoreIntro())) {
            return store.getStoreIntro().trim();
        }
        return "好店上新，欢迎选购";
    }

    private String productTag(MerchantProduct product) {
        if (StringUtils.hasText(product.getTags())) {
            return firstTag(product.getTags());
        }
        int sales = product.getSalesCount() == null ? 0 : product.getSalesCount();
        if (sales >= 1000) {
            return "爆款";
        }
        if (sales >= 300) {
            return "热卖";
        }
        return "推荐";
    }

    private boolean contains(String source, String keyword) {
        return normalizeText(source).contains(normalizeText(keyword));
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase();
    }

    private int clampLimit(Integer raw, int defaultValue, int maxValue) {
        int target = raw == null ? defaultValue : raw;
        if (target <= 0) {
            target = defaultValue;
        }
        return Math.min(target, maxValue);
    }

    public record PaidItem(Long productId, Integer quantity) {
    }

    private record ScoredProduct(MerchantProduct product, int score) {
    }
}
