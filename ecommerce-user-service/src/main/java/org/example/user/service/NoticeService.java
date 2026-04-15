package org.example.user.service;

import org.example.user.domain.MallNotice;
import org.example.user.repository.MallNoticeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoticeService {

    private final MallNoticeRepository mallNoticeRepository;

    public NoticeService(MallNoticeRepository mallNoticeRepository) {
        this.mallNoticeRepository = mallNoticeRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> activeNotices() {
        return mallNoticeRepository.findByStatusOrderBySortNoDescIdDesc(1)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminNotices() {
        return mallNoticeRepository.findAllByOrderBySortNoDescIdDesc()
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> createNotice(String title, String content, Integer sortNo, Integer status) {
        MallNotice notice = new MallNotice();
        notice.setTitle(cleanText(title));
        notice.setContent(cleanText(content));
        notice.setSortNo(sortNo == null ? 0 : sortNo);
        notice.setStatus(status == null ? 1 : (status == 1 ? 1 : 0));
        return toView(mallNoticeRepository.save(notice));
    }

    @Transactional
    public Map<String, Object> updateNoticeStatus(Long noticeId, Integer status) {
        MallNotice notice = mallNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在"));
        notice.setStatus(status != null && status == 1 ? 1 : 0);
        return toView(mallNoticeRepository.save(notice));
    }

    @Transactional
    public Map<String, Object> updateNotice(Long noticeId, String title, String content, Integer sortNo, Integer status) {
        MallNotice notice = mallNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在"));
        notice.setTitle(cleanText(title));
        notice.setContent(cleanText(content));
        notice.setSortNo(sortNo == null ? 0 : sortNo);
        notice.setStatus(status == null ? notice.getStatus() : (status == 1 ? 1 : 0));
        return toView(mallNoticeRepository.save(notice));
    }

    @Transactional
    public Map<String, Object> deleteNotice(Long noticeId) {
        MallNotice notice = mallNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在"));
        mallNoticeRepository.delete(notice);
        return Map.of("ok", true, "id", noticeId);
    }

    private String cleanText(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题和内容不能为空");
        }
        return clean;
    }

    private Map<String, Object> toView(MallNotice notice) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", notice.getId());
        result.put("title", notice.getTitle());
        result.put("content", notice.getContent());
        result.put("sortNo", notice.getSortNo());
        result.put("status", notice.getStatus());
        result.put("createdAt", notice.getCreatedAt());
        result.put("updatedAt", notice.getUpdatedAt());
        return result;
    }
}


