package com.sinognss.cloud.vantix.integration.usercenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyCommonVO(Long id, String name) {
}
