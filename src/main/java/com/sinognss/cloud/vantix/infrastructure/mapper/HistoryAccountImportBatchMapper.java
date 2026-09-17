package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.account.HistoryAccountImportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface HistoryAccountImportBatchMapper extends BaseMapper<HistoryAccountImportBatch> {
    @Select("SELECT * FROM history_account_import_batch WHERE request_id = #{requestId} LIMIT 1")
    HistoryAccountImportBatch selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM history_account_import_batch WHERE id = #{id} LIMIT 1 FOR UPDATE")
    HistoryAccountImportBatch selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE history_account_import_batch SET status = 'COMPLETED', completed_at = #{completedAt}, "
            + "updated_at = #{completedAt} WHERE id = #{id} AND status = 'PROCESSING'")
    int complete(@Param("id") Long id, @Param("completedAt") LocalDateTime completedAt);
}
