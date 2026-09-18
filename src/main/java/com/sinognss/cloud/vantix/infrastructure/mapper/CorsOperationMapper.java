package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.cors.CorsOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CorsOperationMapper extends BaseMapper<CorsOperation> {
    @Select("SELECT * FROM cors_operation WHERE biz_type = #{bizType} AND biz_id = #{bizId} LIMIT 1")
    CorsOperation selectByBusiness(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("SELECT * FROM cors_operation WHERE biz_type = #{bizType} AND biz_id = #{bizId} LIMIT 1 FOR UPDATE")
    CorsOperation selectByBusinessForUpdate(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("SELECT * FROM cors_operation WHERE id = #{id} FOR UPDATE")
    CorsOperation selectByIdForUpdate(@Param("id") Long id);

    @Select({"<script>",
            "SELECT id FROM cors_operation",
            "WHERE operation_type = 'BATCH_CREATE_ACCOUNT' AND biz_type = 'EXCHANGE_BATCH' AND status IN ('PENDING', 'RETRY_WAIT')",
            "AND (next_retry_at IS NULL OR next_retry_at &lt;= #{now})",
            "ORDER BY created_at, id LIMIT #{limit}",
            "</script>"})
    List<Long> selectDueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select({"<script>",
            "SELECT id FROM cors_operation WHERE operation_type = 'TEST_ACCOUNT_CREATE'",
            "AND biz_type = 'TEST_ACCOUNT_ISSUE_BATCH' AND status IN ('PENDING', 'RETRY_WAIT')",
            "AND (next_retry_at IS NULL OR next_retry_at &lt;= #{now})",
            "ORDER BY created_at, id LIMIT #{limit}",
            "</script>"})
    List<Long> selectDueTestAccountIssueIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select({"<script>",
            "SELECT id FROM cors_operation WHERE operation_type = 'RENEW_ACCOUNT'",
            "AND biz_type = 'ACCOUNT_RENEWAL' AND status IN ('PENDING', 'RETRY_WAIT')",
            "AND (next_retry_at IS NULL OR next_retry_at &lt;= #{now})",
            "ORDER BY created_at, id LIMIT #{limit}",
            "</script>"})
    List<Long> selectDueRenewalIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select({"<script>",
            "SELECT * FROM cors_operation WHERE operation_type = 'BATCH_CREATE_ACCOUNT' AND biz_type = 'EXCHANGE_BATCH' AND status = 'CLAIMED'",
            "AND claimed_at &lt; #{cutoff} ORDER BY claimed_at, id LIMIT #{limit}",
            "</script>"})
    List<CorsOperation> selectStaleClaimed(@Param("cutoff") LocalDateTime cutoff,
                                           @Param("limit") int limit);

    @Select({"<script>",
            "SELECT * FROM cors_operation WHERE operation_type = 'TEST_ACCOUNT_CREATE'",
            "AND biz_type = 'TEST_ACCOUNT_ISSUE_BATCH' AND status = 'CLAIMED'",
            "AND claimed_at &lt; #{cutoff} ORDER BY claimed_at, id LIMIT #{limit}",
            "</script>"})
    List<CorsOperation> selectStaleTestAccountIssueClaimed(@Param("cutoff") LocalDateTime cutoff,
                                                            @Param("limit") int limit);

    @Select({"<script>",
            "SELECT * FROM cors_operation WHERE operation_type = 'RENEW_ACCOUNT'",
            "AND biz_type = 'ACCOUNT_RENEWAL' AND status = 'CLAIMED'",
            "AND claimed_at &lt; #{cutoff} ORDER BY claimed_at, id LIMIT #{limit}",
            "</script>"})
    List<CorsOperation> selectStaleRenewalClaimed(@Param("cutoff") LocalDateTime cutoff,
                                                   @Param("limit") int limit);

    @Update("UPDATE cors_operation SET status = 'CLAIMED', claimed_at = #{now}, "
            + "first_attempt_at = COALESCE(first_attempt_at, #{now}), "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = #{expectedStatus} AND version = #{expectedVersion}")
    int claim(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
              @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = 'CLAIMED', claimed_at = #{now}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = #{expectedStatus} AND version = #{expectedVersion}")
    int claimRenewal(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                     @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET first_attempt_at = #{now}, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIMED' AND version = #{expectedVersion} "
            + "AND first_attempt_at IS NULL")
    int initializeRenewalFirstAttempt(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                                      @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = 'RETRY_WAIT', retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, claimed_at = NULL, last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIMED' AND version = #{expectedVersion}")
    int scheduleRetry(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                      @Param("retryCount") int retryCount, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                      @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                      @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = 'SUCCEEDED', next_retry_at = NULL, claimed_at = NULL, "
            + "last_error_code = NULL, last_error_message = NULL, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIMED' AND version = #{expectedVersion}")
    int markSucceeded(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                      @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = 'FAILED', next_retry_at = NULL, claimed_at = NULL, "
            + "last_error_code = #{errorCode}, last_error_message = #{errorMessage}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIMED' AND version = #{expectedVersion}")
    int markFailed(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                   @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                   @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = 'MANUAL_REVIEW', next_retry_at = NULL, claimed_at = NULL, "
            + "last_error_code = #{errorCode}, last_error_message = #{errorMessage}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIMED' AND version = #{expectedVersion}")
    int markManualReview(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                         @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = 'MANUAL_REVIEW', next_retry_at = NULL, claimed_at = NULL, "
            + "last_error_code = #{errorCode}, last_error_message = #{errorMessage}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status IN ('PENDING', 'RETRY_WAIT') AND version = #{expectedVersion}")
    int markPendingManualReview(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                                @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                                @Param("now") LocalDateTime now);

    @Update("UPDATE cors_operation SET status = #{newStatus}, retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, claimed_at = NULL, last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIMED' AND version = #{expectedVersion}")
    int recoverClaimed(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                       @Param("newStatus") String newStatus, @Param("retryCount") int retryCount,
                       @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now);
}
