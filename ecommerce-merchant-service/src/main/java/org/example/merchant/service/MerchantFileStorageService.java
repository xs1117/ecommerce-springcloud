package org.example.merchant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MerchantFileStorageService {

    private static final Set<String> ALLOWED_CATEGORY = Set.of("store", "product");

    private final Path rootPath;

    public MerchantFileStorageService(@Value("${app.upload.merchant-root:./data/uploads/merchant}") String rootDir) {
        this.rootPath = Path.of(rootDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> upload(String category, MultipartFile file) {
        if (!ALLOWED_CATEGORY.contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的上传类型");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的图片文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持图片文件上传");
        }

        String ext = getExtension(file.getOriginalFilename());
        LocalDate now = LocalDate.now();
        Path dir = rootPath.resolve(Path.of(category, String.valueOf(now.getYear()), String.format("%02d", now.getMonthValue()), String.format("%02d", now.getDayOfMonth())));
        String fileName = UUID.randomUUID() + ext;
        Path target = dir.resolve(fileName).normalize();

        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "图片上传失败", ex);
        }

        String relative = rootPath.relativize(target).toString().replace('\\', '/');
        String url = "/files/merchant/" + relative;
        return Map.of("url", url, "path", target.toString(), "category", category);
    }

    private String getExtension(String originalName) {
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            return ".bin";
        }
        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        if (ext.length() > 12) {
            return ".bin";
        }
        return ext;
    }
}


