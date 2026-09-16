package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.config.ServiceDurationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ServiceDurationConfigMapper extends BaseMapper<ServiceDurationConfig> {
    @Update("UPDATE service_duration_config "
            + "SET display_name = #{displayName}, code_silence_days = #{codeSilenceDays}, "
            + "account_silence_days = #{accountSilenceDays}, enabled = #{enabled}, remark = #{remark}, "
            + "updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{id}")
    int updateMutableFields(@Param("id") Long id,
                            @Param("displayName") String displayName,
                            @Param("codeSilenceDays") Integer codeSilenceDays,
                            @Param("accountSilenceDays") Integer accountSilenceDays,
                            @Param("enabled") Boolean enabled,
                            @Param("remark") String remark,
                            @Param("updatedBy") Long updatedBy);

    @Select("SELECT * FROM service_duration_config WHERE enabled = 1 "
            + "ORDER BY duration_days, display_name, service_type")
    List<ServiceDurationConfig> selectEnabled();

    @Select("SELECT * FROM service_duration_config WHERE spec_code = #{specCode} "
            + "AND enabled = 1 LIMIT 1 FOR UPDATE")
    ServiceDurationConfig selectEnabledBySpecCode(@Param("specCode") String specCode);

    @Select({"<script>",
            "SELECT * FROM service_duration_config WHERE spec_code IN",
            "<foreach item='specCode' collection='specCodes' open='(' separator=',' close=')'>",
            "#{specCode}",
            "</foreach>",
            "</script>"})
    List<ServiceDurationConfig> selectBySpecCodes(@Param("specCodes") List<String> specCodes);
}
