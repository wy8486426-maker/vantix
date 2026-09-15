package com.sinognss.cloud.vantix.application.servicecode;

import com.sinognss.cloud.vantix.domain.servicecode.ServiceCodeStatus;

public record ServiceCodePageQuery(long current,
                                  long size,
                                  String keyword,
                                  ServiceCodeStatus status,
                                  DisplayStatus displayStatus,
                                  String specCode,
                                  Integer durationDays,
                                  String sourceOrderNo,
                                  Long ownerCompanyId) {
}
