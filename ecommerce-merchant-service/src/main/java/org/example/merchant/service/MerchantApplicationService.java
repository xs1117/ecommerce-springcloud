package org.example.merchant.service;

import org.example.merchant.domain.MerchantApplication;
import org.example.merchant.domain.MerchantApplicationStatus;
import org.example.merchant.repository.MerchantApplicationRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MerchantApplicationService {

    private static final Duration APPLY_STATUS_TTL = Duration.ofDays(1);

    private final MerchantApplicationRepository applicationRepository;
    private final StringRedisTemplate redisTemplate;
    public MerchantApplicationService(MerchantApplicationRepository applicationRepository,
                                      StringRedisTemplate redisTemplate) {
        this.applicationRepository = applicationRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Map<String, Object> apply(Long userId, String username, String shopName, String businessScope, String contactPhone) {
        if (!StringUtils.hasText(shopName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺名称不能为空");
        }
        if (applicationRepository.existsByApplicantUserIdAndStatus(userId, MerchantApplicationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "你已有待审核申请");
        }
        MerchantApplication application = new MerchantApplication();
        application.setApplicantUserId(userId);
        application.setApplicantUsername(username);
        application.setShopName(shopName.trim());
        application.setBusinessScope(StringUtils.hasText(businessScope) ? businessScope.trim() : null);
        application.setContactPhone(StringUtils.hasText(contactPhone) ? contactPhone.trim() : null);
        application.setStatus(MerchantApplicationStatus.PENDING);
        MerchantApplication saved = applicationRepository.save(application);
        cacheApplyStatus(userId, saved.getStatus().name());
        return toView(saved);
    }

    public List<Map<String, Object>> myApplications(Long userId) {
        return applicationRepository.findByApplicantUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toView)
                .toList();
    }

    public Map<String, Object> myLatestStatus(Long userId) {
        String key = applyStatusKey(userId);
        String cached = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(cached)) {
            return Map.of("userId", userId, "status", cached, "source", "redis");
        }
        MerchantApplicationStatus status = applicationRepository
                .findByApplicantUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .map(MerchantApplication::getStatus)
                .orElse(null);
        if (status == null) {
            return Map.of("userId", userId, "status", "NONE", "source", "db");
        }
        cacheApplyStatus(userId, status.name());
        return Map.of("userId", userId, "status", status.name(), "source", "db");
    }

    public List<Map<String, Object>> listForAdmin(MerchantApplicationStatus status) {
        List<MerchantApplication> applications = status == null
                ? applicationRepository.findAll()
                : applicationRepository.findByStatusOrderByCreatedAtDesc(status);
        return applications.stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> review(Long applicationId, Long reviewerUserId, boolean approved, String comment) {
        MerchantApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "申请不存在"));
        if (application.getStatus() != MerchantApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该申请已审核");
        }
        application.setStatus(approved ? MerchantApplicationStatus.APPROVED : MerchantApplicationStatus.REJECTED);
        application.setReviewComment(StringUtils.hasText(comment) ? comment.trim() : null);
        application.setReviewerUserId(reviewerUserId);
        application.setReviewedAt(LocalDateTime.now());
        MerchantApplication saved = applicationRepository.save(application);
        cacheApplyStatus(saved.getApplicantUserId(), saved.getStatus().name());
        return toView(saved);
    }

    public boolean hasApprovedApplication(Long userId) {
        return applicationRepository.findTopByApplicantUserIdAndStatusOrderByCreatedAtDesc(userId, MerchantApplicationStatus.APPROVED)
                .isPresent();
    }

    private Map<String, Object> toView(MerchantApplication application) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("applicantUserId", application.getApplicantUserId());
        result.put("applicantUsername", application.getApplicantUsername());
        result.put("shopName", application.getShopName());
        result.put("businessScope", application.getBusinessScope() == null ? "" : application.getBusinessScope());
        result.put("contactPhone", application.getContactPhone() == null ? "" : application.getContactPhone());
        result.put("status", application.getStatus());
        result.put("reviewComment", application.getReviewComment() == null ? "" : application.getReviewComment());
        result.put("reviewerUserId", application.getReviewerUserId() == null ? 0L : application.getReviewerUserId());
        result.put("reviewedAt", application.getReviewedAt());
        result.put("createdAt", application.getCreatedAt());
        result.put("updatedAt", application.getUpdatedAt());
        return result;
    }

    private void cacheApplyStatus(Long userId, String status) {
        redisTemplate.opsForValue().set(applyStatusKey(userId), status, APPLY_STATUS_TTL);
    }

    private String applyStatusKey(Long userId) {
        return "merchant:apply:status:" + userId;
    }
}


