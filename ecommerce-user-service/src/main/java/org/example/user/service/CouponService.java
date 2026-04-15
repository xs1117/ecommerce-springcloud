package org.example.user.service;

import org.example.user.domain.CouponStatus;
import org.example.user.domain.CouponTemplate;
import org.example.user.domain.UserCoupon;
import org.example.user.repository.CouponTemplateRepository;
import org.example.user.repository.UserCouponRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final MemberService memberService;

    public CouponService(CouponTemplateRepository couponTemplateRepository,
                         UserCouponRepository userCouponRepository,
                         MemberService memberService) {
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> templates() {
        return couponTemplateRepository.findByStatusOrderByPointsCostAsc(1)
                .stream()
                .map(this::toTemplateView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminTemplates() {
        return couponTemplateRepository.findAllByOrderByIdDesc()
                .stream()
                .map(this::toTemplateView)
                .toList();
    }

    @Transactional
    public Map<String, Object> createTemplate(String name,
                                              Integer pointsCost,
                                              BigDecimal threshold,
                                              BigDecimal discountAmount,
                                              String description,
                                              Integer status) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券名称不能为空");
        }
        if (pointsCost == null || pointsCost <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "兑换积分必须大于0");
        }
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "使用门槛不能为负数");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠金额必须大于0");
        }

        CouponTemplate template = new CouponTemplate();
        template.setName(cleanName);
        template.setPointsCost(pointsCost);
        template.setThreshold(threshold);
        template.setDiscountAmount(discountAmount);
        template.setDescription(description == null ? "" : description.trim());
        template.setStatus(status != null && status == 0 ? 0 : 1);
        return toTemplateView(couponTemplateRepository.save(template));
    }

    @Transactional
    public Map<String, Object> updateTemplateStatus(Long templateId, Integer status) {
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券模板不存在"));
        template.setStatus(status != null && status == 0 ? 0 : 1);
        return toTemplateView(couponTemplateRepository.save(template));
    }

    @Transactional
    public Map<String, Object> updateTemplate(Long templateId,
                                              String name,
                                              Integer pointsCost,
                                              BigDecimal threshold,
                                              BigDecimal discountAmount,
                                              String description,
                                              Integer status) {
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券模板不存在"));
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券名称不能为空");
        }
        if (pointsCost == null || pointsCost <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "兑换积分必须大于0");
        }
        if (threshold == null || threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "使用门槛不能为负数");
        }
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠金额必须大于0");
        }

        template.setName(cleanName);
        template.setPointsCost(pointsCost);
        template.setThreshold(threshold);
        template.setDiscountAmount(discountAmount);
        template.setDescription(description == null ? "" : description.trim());
        template.setStatus(status != null && status == 0 ? 0 : 1);
        return toTemplateView(couponTemplateRepository.save(template));
    }

    @Transactional
    public Map<String, Object> deleteTemplate(Long templateId) {
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券模板不存在"));
        couponTemplateRepository.delete(template);
        return Map.of("ok", true, "id", templateId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> userCoupons(Long userId) {
        return userCouponRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toUserCouponView)
                .toList();
    }

    @Transactional
    public Map<String, Object> redeem(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "优惠券模板不存在"));
        if (!Integer.valueOf(1).equals(template.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券暂不可兑换");
        }

        memberService.addPoints(userId, -Math.abs(template.getPointsCost()), "积分兑换优惠券:" + template.getName());

        UserCoupon coupon = new UserCoupon();
        coupon.setUserId(userId);
        coupon.setTemplateId(template.getId());
        coupon.setCode("CPN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        coupon.setThreshold(template.getThreshold());
        coupon.setDiscountAmount(template.getDiscountAmount());
        coupon.setStatus(CouponStatus.AVAILABLE);
        coupon = userCouponRepository.save(coupon);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("coupon", toUserCouponView(coupon));
        result.put("member", memberService.profile(userId));
        return result;
    }

    @Transactional
    public Map<String, Object> useForOrder(Long userId, Long couponId, String orderNo, BigDecimal orderAmount) {
        if (couponId == null || couponId <= 0) {
            return Map.of("ok", true, "discountAmount", BigDecimal.ZERO, "finalAmount", safeAmount(orderAmount), "couponApplied", false);
        }
        UserCoupon coupon = userCouponRepository.findByIdAndUserId(couponId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户优惠券不存在"));
        if (coupon.getStatus() != CouponStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "优惠券不可用");
        }
        BigDecimal safeOrderAmount = safeAmount(orderAmount);
        if (safeOrderAmount.compareTo(coupon.getThreshold()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单金额未达到优惠券门槛");
        }

        BigDecimal discount = coupon.getDiscountAmount();
        BigDecimal finalAmount = safeOrderAmount.subtract(discount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        coupon.setStatus(CouponStatus.USED);
        coupon.setOrderNo(orderNo);
        coupon.setUsedAt(LocalDateTime.now());
        userCouponRepository.save(coupon);

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("couponApplied", true);
        result.put("couponId", coupon.getId());
        result.put("discountAmount", discount);
        result.put("finalAmount", finalAmount);
        result.put("couponCode", coupon.getCode());
        return result;
    }

    private Map<String, Object> toTemplateView(CouponTemplate template) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("pointsCost", template.getPointsCost());
        result.put("threshold", template.getThreshold());
        result.put("discountAmount", template.getDiscountAmount());
        result.put("status", template.getStatus());
        result.put("description", template.getDescription());
        return result;
    }

    private Map<String, Object> toUserCouponView(UserCoupon coupon) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", coupon.getId());
        result.put("templateId", coupon.getTemplateId());
        result.put("code", coupon.getCode());
        result.put("threshold", coupon.getThreshold());
        result.put("discountAmount", coupon.getDiscountAmount());
        result.put("status", coupon.getStatus().name());
        result.put("orderNo", coupon.getOrderNo());
        result.put("createdAt", coupon.getCreatedAt());
        result.put("usedAt", coupon.getUsedAt());
        return result;
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}

