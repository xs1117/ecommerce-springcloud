package org.example.controller;

import org.example.service.MerchantCatalogClient;
import org.example.service.UserContentClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final MerchantCatalogClient merchantCatalogClient;
    private final UserContentClient userContentClient;

    public HomeController(MerchantCatalogClient merchantCatalogClient, UserContentClient userContentClient) {
        this.merchantCatalogClient = merchantCatalogClient;
        this.userContentClient = userContentClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> home() {
        List<Map<String, Object>> featuredProducts = merchantCatalogClient.hotProducts(8).stream()
                .map(this::normalizeProduct)
                .toList();
        List<Map<String, Object>> featuredStores = merchantCatalogClient.listStores(6).stream()
                .map(this::normalizeStore)
                .toList();

        List<Map<String, Object>> banners = defaultBanners();
        List<Map<String, Object>> notices = userContentClient.activeNotices();
        if (notices == null || notices.isEmpty()) {
            notices = defaultNotices();
        }
        List<Map<String, Object>> promotions = defaultPromotions();

        return ResponseEntity.ok(map(
                "hero", map(
                        "title", "淘宝风格商城首页",
                        "subtitle", "搜索商品或店铺，快速直达你想看的内容",
                        "buttonText", "开始逛逛",
                        "searchHint", "支持商品 / 店铺模糊搜索"
                ),
                "banners", banners,
                "categories", List.of(
                        map("name", "手机数码", "icon", "📱"),
                        map("name", "电脑办公", "icon", "💻"),
                        map("name", "家电家居", "icon", "🏠"),
                        map("name", "服饰鞋包", "icon", "👟"),
                        map("name", "美妆个护", "icon", "💄"),
                        map("name", "食品生鲜", "icon", "🍎"),
                        map("name", "母婴玩具", "icon", "🧸"),
                        map("name", "运动户外", "icon", "🏀"),
                        map("name", "图书文创", "icon", "📚"),
                        map("name", "汽车用品", "icon", "🚗"),
                        map("name", "宠物生活", "icon", "🐶"),
                        map("name", "家装建材", "icon", "🛠️")
                ),
                "hotKeywords", List.of("手机", "耳机", "笔记本", "跑鞋", "生鲜", "美妆"),
                "featuredProducts", featuredProducts,
                "featuredStores", featuredStores,
                "notices", notices,
                "promotions", promotions
        ));
    }

    @GetMapping("/banners/{id}")
    public ResponseEntity<Map<String, Object>> bannerDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(findById(defaultBanners(), id, "活动不存在"));
    }

    @GetMapping("/notices/{id}")
    public ResponseEntity<Map<String, Object>> noticeDetail(@PathVariable("id") Long id) {
        List<Map<String, Object>> notices = userContentClient.activeNotices();
        if (notices == null || notices.isEmpty()) {
            notices = defaultNotices();
        }
        return ResponseEntity.ok(findById(notices, id, "公告不存在"));
    }

    @GetMapping("/promotions")
    public ResponseEntity<List<Map<String, Object>>> promotions() {
        return ResponseEntity.ok(defaultPromotions());
    }

    @GetMapping("/promotions/{id}")
    public ResponseEntity<Map<String, Object>> promotionDetail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(findById(defaultPromotions(), id, "促销不存在"));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "q", required = false) String q,
                                                       @RequestParam(value = "type", defaultValue = "all") String type,
                                                       @RequestParam(value = "sort", defaultValue = "relevance") String sort,
                                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                                       @RequestParam(value = "size", defaultValue = "24") int size) {
        String normalizedKeyword = firstNonBlank(keyword, q);
        String normalizedType = normalizeType(type);
        String normalizedSort = normalizeSort(sort);
        int currentPage = Math.max(page, 1);
        int limit = Math.max(1, Math.min(size, 48));

        List<Map<String, Object>> allProducts = merchantCatalogClient.searchProducts(normalizedKeyword, normalizedSort, 500)
                .stream().map(this::normalizeProduct).toList();
        List<Map<String, Object>> allStores = merchantCatalogClient.searchStores(normalizedKeyword, normalizedSort, 500)
                .stream().map(this::normalizeStore).toList();

        List<Map<String, Object>> products = paginate(allProducts, currentPage, limit);
        List<Map<String, Object>> stores = paginate(allStores, currentPage, limit);

        List<Map<String, Object>> items = switch (normalizedType) {
            case "product" -> products;
            case "store" -> stores;
            default -> paginate(mergeItems(allProducts, allStores, normalizedSort), currentPage, limit);
        };

        int selectedTotal = switch (normalizedType) {
            case "product" -> allProducts.size();
            case "store" -> allStores.size();
            default -> allProducts.size() + allStores.size();
        };
        int totalPages = selectedTotal == 0 ? 1 : (int) Math.ceil((double) selectedTotal / limit);

        return ResponseEntity.ok(map(
                "keyword", normalizedKeyword,
                "type", normalizedType,
                "sort", normalizedSort,
                "page", currentPage,
                "size", limit,
                "total", allProducts.size() + allStores.size(),
                "selectedTotal", selectedTotal,
                "totalPages", totalPages,
                "productTotal", allProducts.size(),
                "storeTotal", allStores.size(),
                "products", products,
                "stores", stores,
                "items", items
        ));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> recommendations(@RequestParam(value = "history", required = false) String history,
                                                                @RequestParam(value = "limit", defaultValue = "8") int limit) {
        List<String> keywords = parseHistory(history);
        List<Map<String, Object>> items = merchantCatalogClient.recommendProducts(keywords, Math.max(1, Math.min(limit, 12)))
                .stream()
                .map(this::normalizeProduct)
                .toList();
        return ResponseEntity.ok(map("keywords", keywords, "items", items));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<Map<String, Object>> productDetail(@PathVariable("id") Long id) {
        Map<String, Object> product = normalizeProduct(merchantCatalogClient.productDetail(id));
        if (product.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        Long storeId = toLong(product.get("storeId"));
        Map<String, Object> store = storeId == null ? Map.of() : normalizeStore(merchantCatalogClient.storeDetail(storeId));
        List<Map<String, Object>> relatedProducts = storeId == null
                ? List.of()
                : merchantCatalogClient.storeProducts(storeId, 8).stream()
                .map(this::normalizeProduct)
                .filter(item -> !Objects.equals(toLong(item.get("id")), id))
                .limit(6)
                .toList();

        if (relatedProducts.isEmpty() && StringUtils.hasText(asText(product.get("category")))) {
            relatedProducts = merchantCatalogClient.searchProducts(asText(product.get("category")), "sales-desc", 8).stream()
                    .map(this::normalizeProduct)
                    .filter(item -> !Objects.equals(toLong(item.get("id")), id))
                    .limit(6)
                    .toList();
        }

        return ResponseEntity.ok(map(
                "product", product,
                "store", store,
                "relatedProducts", relatedProducts
        ));
    }

    @GetMapping("/stores/{id}")
    public ResponseEntity<Map<String, Object>> storeDetail(@PathVariable("id") Long id) {
        Map<String, Object> store = normalizeStore(merchantCatalogClient.storeDetail(id));
        if (store.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "店铺不存在");
        }
        List<Map<String, Object>> products = merchantCatalogClient.storeProducts(id, 80)
                .stream().map(this::normalizeProduct).toList();

        return ResponseEntity.ok(map(
                "store", store,
                "products", products
        ));
    }

    private List<Map<String, Object>> mergeItems(List<Map<String, Object>> products, List<Map<String, Object>> stores, String sort) {
        ArrayList<Map<String, Object>> items = new ArrayList<>(products.size() + stores.size());
        items.addAll(products);
        items.addAll(stores);
        items.sort((left, right) -> {
            int result = switch (sort) {
                case "price-asc" -> compareNumber(left.get("price"), right.get("price"));
                case "price-desc" -> compareNumber(right.get("price"), left.get("price"));
                case "sales-desc" -> compareNumber(right.get("salesCount"), left.get("salesCount"));
                case "stock-desc" -> compareNumber(right.get("stock"), left.get("stock"));
                case "newest" -> compareText(right.get("updatedAt"), left.get("updatedAt"));
                default -> compareNumber(right.get("salesCount"), left.get("salesCount"));
            };
            if (result != 0) {
                return result;
            }
            return compareText(right.get("updatedAt"), left.get("updatedAt"));
        });
        return items;
    }

    private <T> List<T> paginate(List<T> source, int page, int size) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int safePage = Math.max(page, 1);
        int from = (safePage - 1) * size;
        if (from >= source.size()) {
            return List.of();
        }
        int to = Math.min(from + size, source.size());
        return source.subList(from, to);
    }

    private Map<String, Object> normalizeProduct(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(source);
        result.putIfAbsent("type", "product");
        result.put("title", asText(result.get("title")));
        if (!StringUtils.hasText(asText(result.get("subtitle")))) {
            result.put("subtitle", asText(result.get("description")));
        }
        if (!StringUtils.hasText(asText(result.get("tag")))) {
            result.put("tag", "推荐");
        }
        return result;
    }

    private Map<String, Object> normalizeStore(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(source);
        result.put("type", "store");
        result.put("title", asText(result.get("title")).isEmpty() ? asText(result.get("storeName")) : asText(result.get("title")));
        if (!StringUtils.hasText(asText(result.get("tag")))) {
            result.put("tag", "精选");
        }
        return result;
    }

    private int compareNumber(Object left, Object right) {
        BigDecimal leftValue = toBigDecimal(left);
        BigDecimal rightValue = toBigDecimal(right);
        return leftValue.compareTo(rightValue);
    }

    private int compareText(Object left, Object right) {
        String leftValue = asText(left);
        String rightValue = asText(right);
        return leftValue.compareTo(rightValue);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeType(String type) {
        String value = asText(type).toLowerCase();
        if ("product".equals(value) || "store".equals(value) || "all".equals(value)) {
            return value;
        }
        return "all";
    }

    private String normalizeSort(String sort) {
        String value = asText(sort).toLowerCase();
        return switch (value) {
            case "price-asc", "price-desc", "sales-desc", "stock-desc", "newest" -> value;
            default -> "relevance";
        };
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : "";
    }

    private List<String> parseHistory(String history) {
        if (!StringUtils.hasText(history)) {
            return List.of();
        }
        return List.of(history.split(","))
                .stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> {
                    int index = item.indexOf(':');
                    return index >= 0 ? item.substring(index + 1) : item;
                })
                .filter(item -> !item.isEmpty())
                .distinct()
                .limit(8)
                .toList();
    }

    private Map<String, Object> map(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private List<Map<String, Object>> defaultBanners() {
        return List.of(
                map(
                        "id", 1L,
                        "title", "春季大促",
                        "subtitle", "全场满199减30",
                        "tone", "sale",
                        "detail", "活动时间：4月1日-4月30日；同店铺实付满199元立减30元；每天每账号限享3次，退款按比例回退优惠。",
                        "rules", List.of("单店铺订单实付金额统计", "秒杀商品与虚拟商品不参与", "优惠不可叠加平台神券")
                ),
                map(
                        "id", 2L,
                        "title", "品牌直降",
                        "subtitle", "限时补贴，爆款抢先看",
                        "tone", "brand",
                        "detail", "活动时间：每周五20:00-23:59；精选品牌专区直降5%-15%，库存有限，先到先得。",
                        "rules", List.of("活动库存售完即止", "单用户每个SKU限购2件", "活动价不与店铺会员价同享")
                ),
                map(
                        "id", 3L,
                        "title", "新人礼包",
                        "subtitle", "注册登录就能领券包",
                        "tone", "gift",
                        "detail", "新用户注册后7天内可领取新人券包（含满100减10、满200减25）。券包有效期15天。",
                        "rules", List.of("每个账号仅可领取一次", "老用户无法重复领取", "优惠券可在结算页按门槛使用")
                )
        );
    }

    private List<Map<String, Object>> defaultNotices() {
        return List.of(
                map(
                        "id", 101L,
                        "title", "会员日",
                        "content", "全场最高满500减80",
                        "detail", "每月18日为平台会员日，跨店凑单后按平台规则自动匹配最优立减，部分品牌另有赠品活动。"
                ),
                map(
                        "id", 102L,
                        "title", "包邮政策",
                        "content", "部分商品满59元包邮",
                        "detail", "店铺标注“包邮”商品默认包邮；非包邮商品同店铺实付满59元免基础运费，偏远地区按模板加收。"
                ),
                map(
                        "id", 103L,
                        "title", "售后保障",
                        "content", "平台统一客服，购物更安心",
                        "detail", "签收后7天内可发起售后，支持退货/换货/管理员介入。平台将保留沟通记录并按规则仲裁。"
                )
        );
    }

    private List<Map<String, Object>> defaultPromotions() {
        return List.of(
                map(
                        "id", 201L,
                        "title", "积分兑券日",
                        "subtitle", "积分商城券限时8折兑换",
                        "tag", "积分促销",
                        "detail", "每周三 10:00-22:00，积分兑换券成本下调20%，库存有限，兑换成功后可在支付时直接抵扣。",
                        "rules", List.of("仅限实名认证账号参与", "优惠券兑换后不支持退回积分", "订单取消后券不返还")
                ),
                map(
                        "id", 202L,
                        "title", "满额返积分",
                        "subtitle", "消费金额1:1返积分",
                        "tag", "积分返利",
                        "detail", "订单支付成功后，按实付金额向下取整返还积分，积分可用于兑换优惠券，到账后在右上角头像处可查看。",
                        "rules", List.of("仅已支付订单参与", "退款订单将扣回对应积分", "积分当日到账")
                )
        );
    }

    private Map<String, Object> findById(List<Map<String, Object>> source, Long id, String notFoundMessage) {
        return source.stream()
                .filter(item -> Objects.equals(toLong(item.get("id")), id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage));
    }
}
