package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.account.ServiceAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ServiceAccountMapper extends BaseMapper<ServiceAccount> {
    int insertBatch(@Param("accounts") List<ServiceAccount> accounts);

    @Select("SELECT account.* FROM service_account account "
            + "JOIN exchange_detail detail ON detail.id = account.exchange_detail_id "
            + "WHERE account.exchange_batch_id = #{batchId} ORDER BY detail.detail_index")
    List<ServiceAccount> selectByExchangeBatchId(@Param("batchId") Long batchId);

    List<Long> selectDueWaitingActivationIds(@Param("now") LocalDateTime now,
                                             @Param("limit") int limit);

    List<Long> selectDueOtherIds(@Param("now") LocalDateTime now,
                                 @Param("limit") int limit);

    List<Long> selectDueForceActivationCandidateIds(@Param("now") LocalDateTime now,
                                                     @Param("limit") int limit);

    @Select("SELECT * FROM service_account WHERE id = #{id} FOR UPDATE")
    ServiceAccount selectByIdForUpdate(@Param("id") Long id);

    int updateCorsSnapshot(@Param("update") ServiceAccountCorsSnapshotUpdate update);

    int updateStatusSyncSuccess(@Param("update") ServiceAccountStatusSyncScheduleUpdate update);

    int updateStatusSyncFailure(@Param("update") ServiceAccountStatusSyncScheduleUpdate update);
}
