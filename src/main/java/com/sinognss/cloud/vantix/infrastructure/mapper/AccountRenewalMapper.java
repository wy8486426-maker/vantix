package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.renewal.AccountRenewal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AccountRenewalMapper extends BaseMapper<AccountRenewal> {
    @Select("SELECT * FROM account_renewal WHERE id = #{id} FOR UPDATE")
    AccountRenewal selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM account_renewal WHERE request_id = #{requestId} LIMIT 1")
    AccountRenewal selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM account_renewal WHERE request_id = #{requestId} LIMIT 1 FOR UPDATE")
    AccountRenewal selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Select("SELECT * FROM account_renewal WHERE service_account_id = #{accountId} "
            + "AND status IN ('PROCESSING', 'MANUAL_REVIEW') ORDER BY id DESC LIMIT 1")
    AccountRenewal selectActiveByAccount(@Param("accountId") Long accountId);

    @Update("UPDATE account_renewal SET status = 'COMPLETED', completed_at = #{now}, "
            + "last_error_code = NULL, last_error_message = NULL, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND version = #{expectedVersion}")
    int complete(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                 @Param("now") LocalDateTime now);

    @Update("UPDATE account_renewal SET status = 'FAILED', last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND version = #{expectedVersion}")
    int fail(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
             @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
             @Param("now") LocalDateTime now);

    @Update("UPDATE account_renewal SET status = 'MANUAL_REVIEW', last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND version = #{expectedVersion}")
    int markManualReview(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                         @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE account_renewal SET last_error_code = #{errorCode}, last_error_message = #{errorMessage}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND version = #{expectedVersion}")
    int updateRetryError(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                         @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                         @Param("now") LocalDateTime now);
}
