package org.example.user.service;

import org.example.user.domain.UserAccount;
import org.example.user.domain.UserRole;
import org.example.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final CouponService couponService;
    private final NoticeService noticeService;

    public AdminService(UserAccountRepository userAccountRepository,
                        CouponService couponService,
                        NoticeService noticeService) {
        this.userAccountRepository = userAccountRepository;
        this.couponService = couponService;
        this.noticeService = noticeService;
    }

    public List<Map<String, Object>> listUsers() {
        return userAccountRepository.findAll().stream().map(this::toView).toList();
    }

    public Map<String, Object> updateStatus(Long id, Integer status) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setStatus(status);
        return toView(userAccountRepository.save(user));
    }

    public Map<String, Object> changeRole(Long id, UserRole role) {
        UserAccount user = userAccountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setRole(role);
        return toView(userAccountRepository.save(user));
    }

    public List<Map<String, Object>> listCouponTemplates() {
        return couponService.adminTemplates();
    }

    public Map<String, Object> createCouponTemplate(String name,
                                                    Integer pointsCost,
                                                    java.math.BigDecimal threshold,
                                                    java.math.BigDecimal discountAmount,
                                                    String description,
                                                    Integer status) {
        return couponService.createTemplate(name, pointsCost, threshold, discountAmount, description, status);
    }

    public Map<String, Object> updateCouponTemplateStatus(Long id, Integer status) {
        return couponService.updateTemplateStatus(id, status);
    }

    public Map<String, Object> updateCouponTemplate(Long id,
                                                    String name,
                                                    Integer pointsCost,
                                                    java.math.BigDecimal threshold,
                                                    java.math.BigDecimal discountAmount,
                                                    String description,
                                                    Integer status) {
        return couponService.updateTemplate(id, name, pointsCost, threshold, discountAmount, description, status);
    }

    public Map<String, Object> deleteCouponTemplate(Long id) {
        return couponService.deleteTemplate(id);
    }

    public List<Map<String, Object>> listNotices() {
        return noticeService.adminNotices();
    }

    public Map<String, Object> createNotice(String title, String content, Integer sortNo, Integer status) {
        return noticeService.createNotice(title, content, sortNo, status);
    }

    public Map<String, Object> updateNoticeStatus(Long id, Integer status) {
        return noticeService.updateNoticeStatus(id, status);
    }

    public Map<String, Object> updateNotice(Long id, String title, String content, Integer sortNo, Integer status) {
        return noticeService.updateNotice(id, title, content, sortNo, status);
    }

    public Map<String, Object> deleteNotice(Long id) {
        return noticeService.deleteNotice(id);
    }

    private Map<String, Object> toView(UserAccount user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole(),
                "status", user.getStatus(),
                "points", user.getPoints(),
                "memberLevel", user.getMemberLevel()
        );
    }
}

