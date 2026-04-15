package org.example.user.service;

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
import java.util.UUID;

@Service
public class UserFileStorageService {

    private final Path rootPath;

    public UserFileStorageService(@Value("${app.upload.user-root:./data/uploads/user}") String rootDir) {
        this.rootPath = Path.of(rootDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> uploadAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的头像图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持图片文件上传");
        }
        String ext = getExtension(file.getOriginalFilename());
        LocalDate now = LocalDate.now();
        Path dir = rootPath.resolve(Path.of("avatar", String.valueOf(now.getYear()), String.format("%02d", now.getMonthValue()), String.format("%02d", now.getDayOfMonth())));
        String fileName = UUID.randomUUID() + ext;
        Path target = dir.resolve(fileName).normalize();

        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像上传失败", ex);
        }

        String relative = rootPath.relativize(target).toString().replace('\\', '/');
        String url = "/files/user/" + relative;
        return Map.of("url", url, "path", target.toString());
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

