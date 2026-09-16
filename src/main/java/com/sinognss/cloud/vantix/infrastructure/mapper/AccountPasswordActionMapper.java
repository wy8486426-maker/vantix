package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.password.AccountPasswordAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AccountPasswordActionMapper extends BaseMapper<AccountPasswordAction> {
    @Select("SELECT * FROM account_password_action WHERE request_id = #{requestId} LIMIT 1")
    AccountPasswordAction selectByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM account_password_action WHERE request_id = #{requestId} LIMIT 1 FOR UPDATE")
    AccountPasswordAction selectByRequestIdForUpdate(@Param("requestId") String requestId);

    @Select("SELECT * FROM account_password_action WHERE id = #{id} FOR UPDATE")
    AccountPasswordAction selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM account_password_action WHERE active_reset_account_id = #{serviceAccountId} "
            + "AND action_type = 'RESET' AND status IN ('PROCESSING', 'MANUAL_REVIEW') LIMIT 1")
    AccountPasswordAction selectActiveResetByServiceAccountId(@Param("serviceAccountId") Long serviceAccountId);

    @Update("UPDATE account_password_action SET status = #{newStatus}, "
            + "active_reset_account_id = #{activeResetAccountId}, last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, completed_at = #{completedAt}, "
            + "version = version + 1, updated_at = #{now} "
            + "WHERE id = #{id} AND status = 'PROCESSING' AND version = #{expectedVersion}")
    int transitionFromProcessing(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion,
                                 @Param("newStatus") String newStatus, @Param("errorCode") String errorCode,
                                 @Param("errorMessage") String errorMessage,
                                 @Param("completedAt") LocalDateTime completedAt,
                                 @Param("now") LocalDateTime now,
                                 @Param("activeResetAccountId") Long activeResetAccountId);
}
