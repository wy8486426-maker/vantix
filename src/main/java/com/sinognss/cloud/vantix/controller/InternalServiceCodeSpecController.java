package com.sinognss.cloud.vantix.controller;

import com.sinognss.cloud.vantix.application.servicecode.generation.ServiceCodeSpecView;
import com.sinognss.cloud.vantix.common.api.CommonResultAdapter;
import com.sinognss.cloud.vantix.infrastructure.mapper.ServiceDurationConfigMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/service-code-specs")
public class InternalServiceCodeSpecController {
    private final ServiceDurationConfigMapper durationConfigMapper;

    public InternalServiceCodeSpecController(ServiceDurationConfigMapper durationConfigMapper) {
        this.durationConfigMapper = durationConfigMapper;
    }

    @GetMapping
    public Object listEnabledSpecs() {
        return CommonResultAdapter.success(durationConfigMapper.selectEnabled().stream()
                .map(ServiceCodeSpecView::from).toList());
    }
}