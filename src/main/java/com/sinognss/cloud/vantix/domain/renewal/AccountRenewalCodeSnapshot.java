package com.sinognss.cloud.vantix.domain.renewal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** Immutable renewal snapshot captured at renewal reservation time. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AccountRenewalCodeSnapshot {
    private final Long serviceCodeId;
    private final String code;
    private final Long ownerCompanyId;
    private final String specCode;
    private final String serviceType;
    private final Integer durationDays;
    private final Integer codeSilenceDays;
    private final LocalDateTime expireAt;

    @JsonCreator
    public AccountRenewalCodeSnapshot(
            @JsonProperty("serviceCodeId") Long serviceCodeId,
            @JsonProperty("code") String code,
            @JsonProperty("ownerCompanyId") Long ownerCompanyId,
            @JsonProperty("specCode") String specCode,
            @JsonProperty("serviceType") String serviceType,
            @JsonProperty("durationDays") Integer durationDays,
            @JsonProperty("codeSilenceDays") Integer codeSilenceDays,
            @JsonProperty("expireAt") LocalDateTime expireAt) {
        this.serviceCodeId = serviceCodeId;
        this.code = code;
        this.ownerCompanyId = ownerCompanyId;
        this.specCode = specCode;
        this.serviceType = serviceType;
        this.durationDays = durationDays;
        this.codeSilenceDays = codeSilenceDays;
        this.expireAt = expireAt;
    }

    public Long serviceCodeId() { return serviceCodeId; }
    public String code() { return code; }
    public Long ownerCompanyId() { return ownerCompanyId; }
    public String specCode() { return specCode; }
    public String serviceType() { return serviceType; }
    public Integer durationDays() { return durationDays; }
    public Integer codeSilenceDays() { return codeSilenceDays; }
    public LocalDateTime expireAt() { return expireAt; }

    public Long getServiceCodeId() { return serviceCodeId; }
    public String getCode() { return code; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public String getSpecCode() { return specCode; }
    public String getServiceType() { return serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public Integer getCodeSilenceDays() { return codeSilenceDays; }
    public LocalDateTime getExpireAt() { return expireAt; }
}
