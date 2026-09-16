package com.sinognss.cloud.vantix.integration.usercenter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PageUtil<T>(Long totalCount, Long pageSize, Long totalPage, Long currPage, List<T> list) {
}
