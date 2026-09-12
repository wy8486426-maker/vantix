package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.offline.OfflineOrderImportService;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@Validated
@RequestMapping("/api/service-codes/offline-import")
public class OfflineServiceCodeImportController {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final OfflineOrderImportService importService;

    public OfflineServiceCodeImportController(OfflineOrderImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = importService.createTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("线下订单服务码导入模板.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object importOrders(@RequestParam @Positive Long companyId,
                               @RequestPart("file") MultipartFile file) {
        return CommonResultAdapter.success(importService.importFile(companyId, file));
    }
}