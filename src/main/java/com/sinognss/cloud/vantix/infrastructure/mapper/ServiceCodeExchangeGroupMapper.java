package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceCodeExchangeGroupMapper {
    @Select("SELECT batch.spec_code AS spec_code, batch.generation_source AS generation_source, "
            + "spec.display_name AS display_name, spec.service_type AS service_type, "
            + "spec.duration_days AS duration_days, COUNT(*) AS available_count, "
            + "MIN(code.expire_at) AS earliest_expire_at "
            + "FROM service_code code "
            + "JOIN service_code_generate_batch batch ON batch.id = code.generate_batch_id "
            + "JOIN service_duration_config spec ON spec.spec_code = batch.spec_code "
            + "WHERE code.owner_company_id = #{companyId} AND code.status = 'PENDING' "
            + "AND code.expire_at > #{now} "
            + "GROUP BY batch.spec_code, batch.generation_source, spec.display_name, spec.service_type, "
            + "spec.duration_days "
            + "ORDER BY spec.duration_days, earliest_expire_at, "
            + "batch.spec_code, batch.generation_source")
    List<ExchangeGroupRow> selectAvailableGroups(@Param("companyId") Long companyId,
                                                  @Param("now") LocalDateTime now);

    class ExchangeGroupRow {
        private String specCode;
        private String generationSource;
        private String displayName;
        private String serviceType;
        private Integer durationDays;
        private Long availableCount;
        private LocalDateTime earliestExpireAt;

        public String getSpecCode() { return specCode; }
        public void setSpecCode(String specCode) { this.specCode = specCode; }
        public String getGenerationSource() { return generationSource; }
        public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getServiceType() { return serviceType; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }
        public Integer getDurationDays() { return durationDays; }
        public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
        @Deprecated
        public Integer getDurationValue() { return durationDays; }
        @Deprecated
        public void setDurationValue(Integer durationValue) { this.durationDays = durationValue; }
        @Deprecated
        public String getDurationUnit() { return "DAY"; }
        @Deprecated
        public void setDurationUnit(String ignored) { }
        public Long getAvailableCount() { return availableCount; }
        public void setAvailableCount(Long availableCount) { this.availableCount = availableCount; }
        public LocalDateTime getEarliestExpireAt() { return earliestExpireAt; }
        public void setEarliestExpireAt(LocalDateTime earliestExpireAt) { this.earliestExpireAt = earliestExpireAt; }
    }
}
