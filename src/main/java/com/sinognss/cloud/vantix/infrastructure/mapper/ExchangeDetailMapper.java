package com.sinognss.cloud.vantix.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sinognss.cloud.vantix.domain.exchange.ExchangeDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExchangeDetailMapper extends BaseMapper<ExchangeDetail> {
    int insertBatch(@Param("details") List<ExchangeDetail> details);

    @Select("SELECT * FROM exchange_detail WHERE exchange_batch_id = #{batchId} ORDER BY detail_index")
    List<ExchangeDetail> selectByBatchId(@Param("batchId") Long batchId);

    @Update({"<script>",
            "UPDATE exchange_detail SET status = 'COMPLETED',",
            "cors_account_id = CASE detail_index",
            "<foreach collection='accounts' item='item'> WHEN #{item.index} THEN #{item.accountId}</foreach>",
            "END, account = CASE detail_index",
            "<foreach collection='accounts' item='item'> WHEN #{item.index} THEN #{item.account}</foreach>",
            "END, completed_at = #{completedAt}, updated_at = #{completedAt}",
            "WHERE exchange_batch_id = #{batchId} AND status = 'PROCESSING' AND detail_index IN",
            "<foreach collection='accounts' item='item' open='(' separator=',' close=')'>#{item.index}</foreach>",
            "</script>"})
    int completeBatchDetails(@Param("batchId") Long batchId,
                             @Param("accounts") List<CompletedAccountRow> accounts,
                             @Param("completedAt") LocalDateTime completedAt);

    @Update("UPDATE exchange_detail SET status = 'FAILED', last_error_code = #{errorCode}, "
            + "last_error_message = #{errorMessage}, updated_at = #{updatedAt} "
            + "WHERE exchange_batch_id = #{batchId} AND status = 'PROCESSING'")
    int failByBatchId(@Param("batchId") Long batchId, @Param("errorCode") String errorCode,
                      @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);

    class CompletedAccountRow {
        private final int index;
        private final String accountId;
        private final String account;

        public CompletedAccountRow(int index, String accountId, String account) {
            this.index = index;
            this.accountId = accountId;
            this.account = account;
        }

        public int getIndex() { return index; }
        public String getAccountId() { return accountId; }
        public String getAccount() { return account; }
    }
}
