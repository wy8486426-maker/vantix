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

    List<Long> selectSyncCandidates(@Param("staleBefore") LocalDateTime staleBefore,
                                    @Param("limit") int limit);

    int updateCorsSnapshot(@Param("update") ServiceAccountCorsSnapshotUpdate update);
}
