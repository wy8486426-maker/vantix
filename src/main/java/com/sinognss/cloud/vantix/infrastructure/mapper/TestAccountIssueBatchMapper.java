package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.account.TestAccountIssueBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TestAccountIssueBatchMapper extends BaseMapper<TestAccountIssueBatch> {
    @Select("SELECT * FROM test_account_issue_batch WHERE request_id = #{requestId} LIMIT 1")
    TestAccountIssueBatch selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM test_account_issue_batch WHERE id = #{id} LIMIT 1 FOR UPDATE")
    TestAccountIssueBatch selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE test_account_issue_batch SET status = 'COMPLETED', completed_at = #{completedAt}, "
            + "last_error_code = NULL, last_error_message = NULL, updated_at = #{completedAt} "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int complete(@Param("id") Long id, @Param("completedAt") LocalDateTime completedAt);

    @Update("UPDATE test_account_issue_batch SET status = 'FAILED', last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int fail(@Param("id") Long id, @Param("errorCode") String errorCode,
             @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE test_account_issue_batch SET status = 'MANUAL_REVIEW', last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND status = 'PROCESSING'")
    int markManualReview(@Param("id") Long id, @Param("errorCode") String errorCode,
                         @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);
}
