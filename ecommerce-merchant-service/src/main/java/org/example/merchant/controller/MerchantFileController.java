package org.example.merchant.controller;

import org.example.merchant.service.MerchantFileStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/merchant/upload")
public class MerchantFileController {

    private final MerchantFileStorageService fileStorageService;

    public MerchantFileController(MerchantFileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = "/{category}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@PathVariable("category") String category,
                                                      @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(fileStorageService.upload(category, file));
    }
}

