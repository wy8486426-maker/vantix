package com.sinognss.cloud.vantix.application.servicecode.generation;

import com.sinognss.cloud.vantix.domain.servicecode.GenerationSource;

import java.time.LocalDateTime;

public record GenerationOrderPageQuery(long current, long size, String keyword,
                                       GenerationSource generationSource, String status,
                                       Long ownerCompanyId, LocalDateTime createdFrom,
                                       LocalDateTime createdTo) {
}
