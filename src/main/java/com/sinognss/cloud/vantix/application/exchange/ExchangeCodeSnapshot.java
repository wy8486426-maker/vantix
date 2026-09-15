package com.sinognss.cloud.vantix.application.exchange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sinognss.cloud.vantix.common.LegacyDurationCompatibility;

import java.time.LocalDateTime;

/** Immutable exchange snapshot. The creator also reads the retired JSON shape. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ExchangeCodeSnapshot {
    private final Long serviceCodeId;
    private final String code;
    private final Long ownerCompanyId;
    private final Long assignedUserId;
    private final String specCode;
    private final String serviceType;
    private final Integer durationDays;
    private final Integer codeSilenceDays;
    private final LocalDateTime expireAt;

    public ExchangeCodeSnapshot(Long serviceCodeId, String code, Long ownerCompanyId,
                                Long assignedUserId, String specCode, String serviceType,
                                Integer durationDays, Integer codeSilenceDays,
                                LocalDateTime expireAt) {
        this.serviceCodeId = serviceCodeId;
        this.code = code;
        this.ownerCompanyId = ownerCompanyId;
        this.assignedUserId = assignedUserId;
        this.specCode = specCode;
        this.serviceType = serviceType;
        this.durationDays = durationDays;
        this.codeSilenceDays = codeSilenceDays;
        this.expireAt = expireAt;
    }

    /** Reads both the days contract and legacy durationValue/durationUnit JSON. */
    @JsonCreator
    public ExchangeCodeSnapshot(
            @JsonProperty("serviceCodeId") Long serviceCodeId,
            @JsonProperty("code") String code,
            @JsonProperty("ownerCompanyId") Long ownerCompanyId,
            @JsonProperty("assignedUserId") Long assignedUserId,
            @JsonProperty("specCode") String specCode,
            @JsonProperty("serviceType") String serviceType,
            @JsonProperty("durationDays") Integer durationDays,
            @JsonProperty("codeSilenceDays") Integer codeSilenceDays,
            @JsonProperty("expireAt") LocalDateTime expireAt,
            @JsonProperty("durationValue") Integer legacyDurationValue,
            @JsonProperty("durationUnit") String legacyDurationUnit,
            @JsonProperty("codeSilenceMonths") Integer legacyCodeSilenceMonths) {
        this(serviceCodeId, code, ownerCompanyId, assignedUserId, specCode, serviceType,
                durationDays != null ? durationDays
                        : LegacyDurationCompatibility.toDays(legacyDurationValue, legacyDurationUnit),
                codeSilenceDays != null ? codeSilenceDays
                        : LegacyDurationCompatibility.monthsToDays(legacyCodeSilenceMonths), expireAt);
    }

    /** Source compatibility for the retired Java snapshot constructor. */
    @Deprecated
    public ExchangeCodeSnapshot(Long serviceCodeId, String serviceCode, Long ownerCompanyId,
                                Long assignedUserId, String serviceType, Integer durationValue,
                                String durationUnit, Integer codeSilenceMonths,
                                LocalDateTime expireAt) {
        this(serviceCodeId, serviceCode, ownerCompanyId, assignedUserId, null, serviceType,
                LegacyDurationCompatibility.toDays(durationValue, durationUnit),
                LegacyDurationCompatibility.monthsToDays(codeSilenceMonths), expireAt);
    }

    public Long serviceCodeId() { return serviceCodeId; }
    public String code() { return code; }
    public Long ownerCompanyId() { return ownerCompanyId; }
    public Long assignedUserId() { return assignedUserId; }
    public String specCode() { return specCode; }
    public String serviceType() { return serviceType; }
    public Integer durationDays() { return durationDays; }
    public Integer codeSilenceDays() { return codeSilenceDays; }
    public LocalDateTime expireAt() { return expireAt; }

    // Bean getters make the new fields the only serialized JSON properties.
    public Long getServiceCodeId() { return serviceCodeId; }
    public String getCode() { return code; }
    public Long getOwnerCompanyId() { return ownerCompanyId; }
    public Long getAssignedUserId() { return assignedUserId; }
    public String getSpecCode() { return specCode; }
    public String getServiceType() { return serviceType; }
    public Integer getDurationDays() { return durationDays; }
    public Integer getCodeSilenceDays() { return codeSilenceDays; }
    public LocalDateTime getExpireAt() { return expireAt; }
}
