package com.sinognss.cloud.vantix.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceCodeExchangeGroupMapper {
    @Select("SELECT batch.spec_code AS spec_code, batch.generation_source AS generation_source, "
            + "spec.service_type AS service_type, spec.duration_value AS duration_value, "
            + "spec.duration_unit AS duration_unit, COUNT(*) AS available_count, "
            + "MIN(code.expire_at) AS earliest_expire_at "
            + "FROM service_code code "
            + "JOIN service_code_generate_batch batch ON batch.id = code.generate_batch_id "
            + "JOIN service_duration_config spec ON spec.spec_code = batch.spec_code "
            + "WHERE code.owner_company_id = #{companyId} AND code.status = 'PENDING' "
            + "AND code.expire_at > #{now} "
            + "GROUP BY batch.spec_code, batch.generation_source, spec.service_type, "
            + "spec.duration_value, spec.duration_unit "
            + "ORDER BY spec.duration_value, spec.duration_unit, earliest_expire_at, "
            + "batch.spec_code, batch.generation_source")
    List<ExchangeGroupRow> selectAvailableGroups(@Param("companyId") Long companyId,
                                                  @Param("now") LocalDateTime now);

    class ExchangeGroupRow {
        private String specCode;
        private String generationSource;
        private String serviceType;
        private Integer durationValue;
        private String durationUnit;
        private Long availableCount;
        private LocalDateTime earliestExpireAt;

        public String getSpecCode() { return specCode; }
        public void setSpecCode(String specCode) { this.specCode = specCode; }
        public String getGenerationSource() { return generationSource; }
        public void setGenerationSource(String generationSource) { this.generationSource = generationSource; }
        public String getServiceType() { return serviceType; }
        public void setServiceType(String serviceType) { this.serviceType = serviceType; }
        public Integer getDurationValue() { return durationValue; }
        public void setDurationValue(Integer durationValue) { this.durationValue = durationValue; }
        public String getDurationUnit() { return durationUnit; }
        public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
        public Long getAvailableCount() { return availableCount; }
        public void setAvailableCount(Long availableCount) { this.availableCount = availableCount; }
        public LocalDateTime getEarliestExpireAt() { return earliestExpireAt; }
        public void setEarliestExpireAt(LocalDateTime earliestExpireAt) { this.earliestExpireAt = earliestExpireAt; }
    }
}
